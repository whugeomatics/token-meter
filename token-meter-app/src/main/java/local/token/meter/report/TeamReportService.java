package local.token.meter.report;

import local.token.meter.domain.Report;
import local.token.meter.domain.ReportQuery;
import local.token.meter.domain.PeriodComparison;
import local.token.meter.domain.Snapshot;
import local.token.meter.domain.StoredTeamUsageEvent;
import local.token.meter.domain.TeamUploadRecord;
import local.token.meter.domain.TokenTotals;
import local.token.meter.http.BadRequestException;
import local.token.meter.ingestion.UsageEventKeys;
import local.token.meter.store.TeamUsageStore;
import local.token.meter.util.Json;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class TeamReportService {
    private static final long MAX_ACTIVE_GAP_SECONDS = 30L * 60L;

    private final TeamUsageStore store;
    private final ZoneId zone;

    public TeamReportService(TeamUsageStore store, ZoneId zone) {
        this.store = store;
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    public Report report(Map<String, String> query) throws SQLException {
        String period = query.getOrDefault("period", "");
        String compare = query.getOrDefault("compare", "");
        if (period.isBlank() && compare.isBlank()) {
            return report(ReportQuery.from(query, zone));
        }
        if ("previous".equals(compare)) {
            return periodComparison(query, period);
        }
        throw new BadRequestException("period and compare must use compare=previous");
    }

    public Report report(ReportQuery query) throws SQLException {
        Aggregator aggregator = new Aggregator(query);
        for (StoredTeamUsageEvent event : store.loadTeamEvents(query.startDate(), query.endDate())) {
            if (query.contains(event.timestamp()) && query.matchesTeam(event.teamId())
                    && query.matchesUser(event.userId()) && query.matchesTool(event.tool())) {
                aggregator.add(event);
            }
        }
        for (TeamUploadRecord upload : store.loadTeamUploads(query.startDate(), query.endDate())) {
            if (query.matchesTeam(upload.teamId()) && query.matchesUser(upload.userId())) {
                aggregator.add(upload);
            }
        }
        return aggregator.toReport();
    }

    private Report periodComparison(Map<String, String> params, String period) throws SQLException {
        String teamId = params.getOrDefault("team_id", "");
        String userId = params.getOrDefault("user_id", "");
        String tool = params.getOrDefault("tool", "");
        PeriodComparison comparison = PeriodComparison.previous(period, LocalDate.now(zone), zone, teamId, userId, tool);
        ReportQuery currentQuery = comparison.current();
        ReportQuery previousQuery = comparison.previous();
        Aggregator current = new Aggregator(currentQuery);
        Aggregator previous = new Aggregator(previousQuery);

        for (StoredTeamUsageEvent event : store.loadTeamEvents(previousQuery.startDate(), currentQuery.endDate())) {
            if (currentQuery.contains(event.timestamp()) && currentQuery.matchesTeam(event.teamId())
                    && currentQuery.matchesUser(event.userId()) && currentQuery.matchesTool(event.tool())) {
                current.add(event);
            } else if (previousQuery.contains(event.timestamp()) && previousQuery.matchesTeam(event.teamId())
                    && previousQuery.matchesUser(event.userId()) && previousQuery.matchesTool(event.tool())) {
                previous.add(event);
            }
        }
        for (TeamUploadRecord upload : store.loadTeamUploads(currentQuery.startDate(), currentQuery.endDate())) {
            if (currentQuery.matchesTeam(upload.teamId()) && currentQuery.matchesUser(upload.userId())) {
                current.add(upload);
            }
        }
        return current.toReport(comparisonJson(comparison, current, previous, currentQuery, previousQuery));
    }

    private static final class Aggregator {
        private final ReportQuery query;
        private final TokenTotals summary = new TokenTotals();
        private final Map<String, Integer> sourceKindCounts = new HashMap<>();
        private final Map<String, Integer> sourceQualityCounts = new HashMap<>();
        private final Set<String> users = new HashSet<>();
        private final Set<String> devices = new HashSet<>();
        private final Set<String> sessions = new HashSet<>();
        private final Map<String, TeamBucket> teamBuckets = new HashMap<>();
        private final Map<String, UserBucket> userBuckets = new HashMap<>();
        private final Map<String, DeviceBucket> deviceBuckets = new HashMap<>();
        private final Map<String, ModelBucket> modelBuckets = new HashMap<>();
        private final Map<String, ToolBucket> toolBuckets = new HashMap<>();
        private final Map<String, TeamModelBucket> teamModelBuckets = new HashMap<>();
        private final Map<LocalDate, DailyBucket> dailyBuckets = new LinkedHashMap<>();
        private final Map<String, UserDailyBucket> userDailyBuckets = new HashMap<>();
        private final Map<String, UploadHealthBucket> uploadHealthBuckets = new HashMap<>();
        private final ActiveWindows activeWindows = new ActiveWindows();
        private final Set<String> callKeys = new HashSet<>();
        private String teamId = "";
        private int eventCount;
        private Instant startedAt;
        private Instant endedAt;

        Aggregator(ReportQuery query) {
            this.query = query;
            LocalDate date = query.startDate();
            while (!date.isAfter(query.endDate())) {
                dailyBuckets.put(date, new DailyBucket(date));
                date = date.plusDays(1);
            }
        }

        void add(StoredTeamUsageEvent event) {
            teamId = event.teamId();
            summary.add(event.tool(), event.usage());
            addSourceDimension(sourceKindCounts, event.sourceKind());
            addSourceQuality(sourceQualityCounts, event.sourceQuality());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
            activeWindows.add(sessionKey(event), event.timestamp());
            users.add(event.userId());
            devices.add(event.deviceId());
            sessions.add(sessionKey(event));
            LocalDate date = event.timestamp().atZone(query.zone()).toLocalDate();
            dailyBuckets.computeIfAbsent(date, DailyBucket::new).add(event);
            teamBuckets.computeIfAbsent(event.teamId(), TeamBucket::new).add(event);
            userBuckets.computeIfAbsent(event.teamId() + "|" + event.userId(),
                    id -> new UserBucket(event.teamId(), event.userId(), event.userDisplayName())).add(event);
            deviceBuckets.computeIfAbsent(event.teamId() + "|" + event.deviceId(),
                    id -> new DeviceBucket(event.teamId(), event.deviceId(), event.userId(), event.deviceDisplayName())).add(event);
            modelBuckets.computeIfAbsent(event.model(), ModelBucket::new).add(event);
            toolBuckets.computeIfAbsent(event.tool(), ToolBucket::new).add(event);
            teamModelBuckets.computeIfAbsent(date + "|" + event.teamId() + "|" + event.userId() + "|"
                            + event.tool() + "|" + event.model(),
                    id -> new TeamModelBucket(date, event.teamId(), event.userId(), event.userDisplayName(),
                            event.tool(), event.model())).add(event);
            userDailyBuckets.computeIfAbsent(date + "|" + event.teamId() + "|" + event.userId(),
                    id -> new UserDailyBucket(date, event.teamId(), event.userId(), event.userDisplayName())).add(event);
        }

        private static void addSourceQuality(Map<String, Integer> counts, String sourceQuality) {
            addSourceDimension(counts, sourceQuality);
        }

        private static void addSourceDimension(Map<String, Integer> counts, String value) {
            String normalized = value == null || value.isBlank() ? "unknown" : value;
            counts.merge(normalized, 1, Integer::sum);
        }

        void add(TeamUploadRecord upload) {
            teamBuckets.computeIfAbsent(upload.teamId(), TeamBucket::new).add(upload);
            uploadHealthBuckets.computeIfAbsent(upload.teamId() + "|" + upload.userId() + "|" + upload.deviceId(),
                    id -> new UploadHealthBucket(upload.teamId(), upload.userId(), upload.deviceId())).add(upload);
        }

        Report toReport() {
            return toReport("");
        }

        Report toReport(String comparisonJson) {
            return new TeamReportPayload(query, teamId, summary, users.size(), devices.size(), sessions.size(),
                    eventCount, callKeys.size(), activeWindows.activeSeconds(), sourceKindCounts, sourceQualityCounts,
                    sortedTeams(), sortedUsers(), sortedDevices(), sortedModels(), sortedTools(), sortedTeamModels(),
                    new ArrayList<>(dailyBuckets.values()), sortedUserDaily(), sortedUploadHealth(), sortedUploads(),
                    comparisonJson);
        }

        private List<TeamBucket> sortedTeams() {
            return teamBuckets.values().stream()
                    .sorted(Comparator.comparingLong((TeamBucket bucket) -> bucket.totals.totalTokens).reversed())
                    .toList();
        }

        private List<UserBucket> sortedUsers() {
            return userBuckets.values().stream()
                    .sorted(Comparator.comparingLong((UserBucket bucket) -> bucket.totals.totalTokens).reversed())
                    .toList();
        }

        private List<DeviceBucket> sortedDevices() {
            return deviceBuckets.values().stream()
                    .sorted(Comparator.comparingLong((DeviceBucket bucket) -> bucket.totals.totalTokens).reversed())
                    .toList();
        }

        private List<ModelBucket> sortedModels() {
            return modelBuckets.values().stream()
                    .sorted(Comparator.comparingLong((ModelBucket bucket) -> bucket.totals.totalTokens).reversed())
                    .toList();
        }

        private List<ToolBucket> sortedTools() {
            return toolBuckets.values().stream()
                    .sorted(Comparator.comparingLong((ToolBucket bucket) -> bucket.totals.totalTokens).reversed())
                    .toList();
        }

        private List<TeamModelBucket> sortedTeamModels() {
            return teamModelBuckets.values().stream()
                    .sorted(Comparator.comparing((TeamModelBucket bucket) -> bucket.date, Comparator.reverseOrder())
                            .thenComparing(bucket -> bucket.teamId)
                            .thenComparing(bucket -> bucket.userId)
                            .thenComparing(bucket -> bucket.tool)
                            .thenComparing(bucket -> bucket.model)
                            .thenComparing(Comparator.comparingLong((TeamModelBucket bucket) -> bucket.totals.totalTokens).reversed()))
                    .toList();
        }

        private List<UserDailyBucket> sortedUserDaily() {
            return userDailyBuckets.values().stream()
                    .sorted(Comparator.comparing((UserDailyBucket bucket) -> bucket.date)
                            .thenComparing(bucket -> bucket.userId))
                    .toList();
        }

        private List<TeamUploadRecord> sortedUploads() {
            return teamBuckets.values().stream()
                    .flatMap(bucket -> bucket.uploads.stream())
                    .sorted(Comparator.comparing(TeamUploadRecord::uploadTime).reversed())
                    .limit(50)
                    .toList();
        }

        private List<UploadHealthBucket> sortedUploadHealth() {
            return uploadHealthBuckets.values().stream()
                    .sorted(Comparator.comparing((UploadHealthBucket bucket) -> bucket.latestUploadTime)
                            .thenComparing(bucket -> bucket.userId)
                            .thenComparing(bucket -> bucket.deviceId))
                    .toList();
        }
    }

    private static final class TeamBucket {
        final String teamId;
        final TokenTotals totals = new TokenTotals();
        final Set<String> users = new HashSet<>();
        final Set<String> devices = new HashSet<>();
        final Set<String> sessions = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        final List<TeamUploadRecord> uploads = new ArrayList<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;
        String lastUploadAt = "";

        TeamBucket(String teamId) {
            this.teamId = teamId;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.tool(), event.usage());
            users.add(event.userId());
            devices.add(event.deviceId());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }

        void add(TeamUploadRecord upload) {
            uploads.add(upload);
            if (lastUploadAt.isBlank() || upload.uploadTime().compareTo(lastUploadAt) > 0) {
                lastUploadAt = upload.uploadTime();
            }
        }
    }

    private static final class UserBucket {
        final String teamId;
        final String userId;
        final String displayName;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final Set<String> devices = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;
        String lastSeenAt = "";

        UserBucket(String teamId, String userId, String displayName) {
            this.teamId = teamId;
            this.userId = userId;
            this.displayName = displayName;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.tool(), event.usage());
            sessions.add(sessionKey(event));
            devices.add(event.deviceId());
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
            lastSeenAt = event.timestamp().toString();
        }
    }

    private static final class DeviceBucket {
        final String teamId;
        final String deviceId;
        final String userId;
        final String displayName;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;
        String lastSeenAt = "";

        DeviceBucket(String teamId, String deviceId, String userId, String displayName) {
            this.teamId = teamId;
            this.deviceId = deviceId;
            this.userId = userId;
            this.displayName = displayName == null || displayName.isBlank() ? deviceId : displayName;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.tool(), event.usage());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
            lastSeenAt = event.timestamp().toString();
        }
    }

    private static final class ModelBucket {
        final String model;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        ModelBucket(String model) {
            this.model = model;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.tool(), event.usage());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class ToolBucket {
        final String tool;
        final TokenTotals totals = new TokenTotals();
        final Map<String, Integer> sourceKindCounts = new HashMap<>();
        final Map<String, Integer> sourceQualityCounts = new HashMap<>();
        final Set<String> sessions = new HashSet<>();
        final Set<String> users = new HashSet<>();
        final Set<String> devices = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;

        ToolBucket(String tool) {
            this.tool = tool;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.tool(), event.usage());
            Aggregator.addSourceDimension(sourceKindCounts, event.sourceKind());
            Aggregator.addSourceQuality(sourceQualityCounts, event.sourceQuality());
            sessions.add(sessionKey(event));
            users.add(event.userId());
            devices.add(event.deviceId());
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
        }
    }

    private static final class TeamModelBucket {
        final LocalDate date;
        final String teamId;
        final String userId;
        final String displayName;
        final String tool;
        final String model;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        TeamModelBucket(LocalDate date, String teamId, String userId, String displayName, String tool, String model) {
            this.date = date;
            this.teamId = teamId;
            this.userId = userId;
            this.displayName = displayName;
            this.tool = tool;
            this.model = model;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.tool(), event.usage());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class DailyBucket {
        final LocalDate date;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final Set<String> users = new HashSet<>();
        final Set<String> devices = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        DailyBucket(LocalDate date) {
            this.date = date;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.tool(), event.usage());
            sessions.add(sessionKey(event));
            users.add(event.userId());
            devices.add(event.deviceId());
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class UserDailyBucket {
        final LocalDate date;
        final String teamId;
        final String userId;
        final String displayName;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        UserDailyBucket(LocalDate date, String teamId, String userId, String displayName) {
            this.date = date;
            this.teamId = teamId;
            this.userId = userId;
            this.displayName = displayName;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.tool(), event.usage());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class UploadHealthBucket {
        final String teamId;
        final String userId;
        final String deviceId;
        final List<TeamUploadRecord> recentUploads = new ArrayList<>();
        String latestUploadTime = "";
        String latestStatus = "";
        int latestAccepted;
        int latestDuplicate;
        int latestRejected;

        UploadHealthBucket(String teamId, String userId, String deviceId) {
            this.teamId = teamId;
            this.userId = userId;
            this.deviceId = deviceId;
        }

        void add(TeamUploadRecord upload) {
            recentUploads.add(upload);
            recentUploads.sort(Comparator.comparing(TeamUploadRecord::uploadTime).reversed());
            if (recentUploads.size() > 3) {
                recentUploads.remove(recentUploads.size() - 1);
            }
            if (latestUploadTime.isBlank() || upload.uploadTime().compareTo(latestUploadTime) > 0) {
                latestUploadTime = upload.uploadTime();
                latestStatus = upload.status();
                latestAccepted = upload.accepted();
                latestDuplicate = upload.duplicate();
                latestRejected = upload.rejected();
            }
        }
    }

    private static String comparisonJson(PeriodComparison comparison, Aggregator current, Aggregator previous, ReportQuery currentQuery,
                                         ReportQuery previousQuery) {
        return ",\"comparison\":{\"period\":\"" + Json.escape(comparison.period()) + "\","
                + "\"current\":" + comparisonSummaryJson(comparison.currentLabel(), current, currentQuery) + ","
                + "\"previous\":" + comparisonSummaryJson(comparison.previousLabel(), previous, previousQuery) + ","
                + "\"delta\":" + comparisonDeltaJson(current, previous) + ","
                + "\"daily\":" + comparisonDailyJson(current, previous, currentQuery, previousQuery) + ","
                + "\"users\":" + comparisonUsersJson(current, previous) + ","
                + "\"models\":" + comparisonModelsJson(current, previous) + ","
                + "\"tools\":" + comparisonToolsJson(current, previous)
                + "}";
    }

    private static String comparisonSummaryJson(String label, Aggregator aggregator, ReportQuery query) {
        return "{\"label\":\"" + label + "\",\"start_date\":\"" + query.startDate() + "\",\"end_date\":\""
                + query.endDate() + "\",\"total_tokens\":" + aggregator.summary.totalTokens
                + ",\"usage_event_count\":" + aggregator.eventCount
                + ",\"call_count\":" + aggregator.callKeys.size()
                + ",\"sessions\":" + aggregator.sessions.size()
                + ",\"users\":" + aggregator.users.size()
                + ",\"devices\":" + aggregator.devices.size() + "}";
    }

    private static String comparisonDeltaJson(Aggregator current, Aggregator previous) {
        long tokenDelta = current.summary.totalTokens - previous.summary.totalTokens;
        return "{\"total_tokens\":" + tokenDelta
                + ",\"total_tokens_rate\":" + decimal(rate(tokenDelta, previous.summary.totalTokens))
                + ",\"usage_event_count\":" + (current.eventCount - previous.eventCount)
                + ",\"call_count\":" + (current.callKeys.size() - previous.callKeys.size())
                + ",\"sessions\":" + (current.sessions.size() - previous.sessions.size())
                + ",\"users\":" + (current.users.size() - previous.users.size())
                + ",\"devices\":" + (current.devices.size() - previous.devices.size()) + "}";
    }

    private static String comparisonDailyJson(Aggregator current, Aggregator previous, ReportQuery currentQuery,
                                              ReportQuery previousQuery) {
        List<String> rows = new ArrayList<>();
        long days = ChronoUnit.DAYS.between(currentQuery.startDate(), currentQuery.endDate());
        for (int index = 0; index <= days; index++) {
            LocalDate currentDate = currentQuery.startDate().plusDays(index);
            LocalDate previousDate = previousQuery.startDate().plusDays(index);
            DailyBucket currentBucket = current.dailyBuckets.get(currentDate);
            DailyBucket previousBucket = previous.dailyBuckets.get(previousDate);
            long currentTokens = currentBucket == null ? 0L : currentBucket.totals.totalTokens;
            long previousTokens = previousBucket == null ? 0L : previousBucket.totals.totalTokens;
            long delta = currentTokens - previousTokens;
            rows.add("{\"day_index\":" + index
                    + ",\"label\":\"" + dayLabel(currentDate) + "\""
                    + ",\"current_date\":\"" + currentDate + "\""
                    + ",\"previous_date\":\"" + previousDate + "\""
                    + ",\"current_total_tokens\":" + currentTokens
                    + ",\"previous_total_tokens\":" + previousTokens
                    + ",\"delta_total_tokens\":" + delta
                    + ",\"delta_total_tokens_rate\":" + decimal(rate(delta, previousTokens)) + "}");
        }
        return "[" + String.join(",", rows) + "]";
    }

    private static String comparisonUsersJson(Aggregator current, Aggregator previous) {
        Set<String> keys = new HashSet<>();
        keys.addAll(current.userBuckets.keySet());
        keys.addAll(previous.userBuckets.keySet());
        return "[" + keys.stream()
                .map(key -> {
                    UserBucket currentBucket = current.userBuckets.get(key);
                    UserBucket previousBucket = previous.userBuckets.get(key);
                    UserBucket identity = currentBucket == null ? previousBucket : currentBucket;
                    long currentTokens = currentBucket == null ? 0L : currentBucket.totals.totalTokens;
                    long previousTokens = previousBucket == null ? 0L : previousBucket.totals.totalTokens;
                    long delta = currentTokens - previousTokens;
                    String json = "{\"team_id\":\"" + Json.escape(identity.teamId)
                            + "\",\"user_id\":\"" + Json.escape(identity.userId)
                            + "\",\"display_name\":\"" + Json.escape(identity.displayName)
                            + "\",\"current_total_tokens\":" + currentTokens
                            + ",\"previous_total_tokens\":" + previousTokens
                            + ",\"delta_total_tokens\":" + delta
                            + ",\"delta_total_tokens_rate\":" + decimal(rate(delta, previousTokens)) + "}";
                    return new ComparisonRow(json, delta);
                })
                .sorted(Comparator.comparingLong((ComparisonRow row) -> Math.abs(row.delta)).reversed())
                .limit(8)
                .map(row -> row.json)
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private static String comparisonModelsJson(Aggregator current, Aggregator previous) {
        Set<String> keys = new HashSet<>();
        keys.addAll(current.modelBuckets.keySet());
        keys.addAll(previous.modelBuckets.keySet());
        return "[" + keys.stream()
                .map(key -> {
                    ModelBucket currentBucket = current.modelBuckets.get(key);
                    ModelBucket previousBucket = previous.modelBuckets.get(key);
                    long currentTokens = currentBucket == null ? 0L : currentBucket.totals.totalTokens;
                    long previousTokens = previousBucket == null ? 0L : previousBucket.totals.totalTokens;
                    long delta = currentTokens - previousTokens;
                    String json = "{\"model\":\"" + Json.escape(key)
                            + "\",\"current_total_tokens\":" + currentTokens
                            + ",\"previous_total_tokens\":" + previousTokens
                            + ",\"delta_total_tokens\":" + delta
                            + ",\"delta_total_tokens_rate\":" + decimal(rate(delta, previousTokens)) + "}";
                    return new ComparisonRow(json, delta);
                })
                .sorted(Comparator.comparingLong((ComparisonRow row) -> Math.abs(row.delta)).reversed())
                .limit(8)
                .map(row -> row.json)
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private static String comparisonToolsJson(Aggregator current, Aggregator previous) {
        Set<String> keys = new HashSet<>();
        keys.addAll(current.toolBuckets.keySet());
        keys.addAll(previous.toolBuckets.keySet());
        return "[" + keys.stream()
                .map(key -> {
                    ToolBucket currentBucket = current.toolBuckets.get(key);
                    ToolBucket previousBucket = previous.toolBuckets.get(key);
                    long currentTokens = currentBucket == null ? 0L : currentBucket.totals.totalTokens;
                    long previousTokens = previousBucket == null ? 0L : previousBucket.totals.totalTokens;
                    long delta = currentTokens - previousTokens;
                    String json = "{\"tool\":\"" + Json.escape(key)
                            + "\",\"current_total_tokens\":" + currentTokens
                            + ",\"previous_total_tokens\":" + previousTokens
                            + ",\"delta_total_tokens\":" + delta
                            + ",\"delta_total_tokens_rate\":" + decimal(rate(delta, previousTokens)) + "}";
                    return new ComparisonRow(json, delta);
                })
                .sorted(Comparator.comparingLong((ComparisonRow row) -> Math.abs(row.delta)).reversed())
                .limit(8)
                .map(row -> row.json)
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private record ComparisonRow(String json, long delta) {
    }

    private static String dayLabel(LocalDate date) {
        String value = date.getDayOfWeek().toString().substring(0, 3).toLowerCase(Locale.ROOT);
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static double rate(long delta, long previous) {
        if (previous == 0L) {
            return delta == 0L ? 0.0d : 1.0d;
        }
        return (double) delta / (double) previous;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private record TeamReportPayload(ReportQuery query, String teamId, TokenTotals summary, int users, int devices,
                                     int sessions, int eventCount, int callCount, long activeSeconds,
                                     Map<String, Integer> sourceKindCounts,
                                     Map<String, Integer> sourceQualityCounts, List<TeamBucket> teamBuckets,
                                     List<UserBucket> userBuckets, List<DeviceBucket> deviceBuckets,
                                     List<ModelBucket> modelBuckets, List<ToolBucket> toolBuckets,
                                     List<TeamModelBucket> teamModelBuckets, List<DailyBucket> dailyBuckets,
                                     List<UserDailyBucket> userDailyBuckets,
                                     List<UploadHealthBucket> uploadHealthBuckets,
                                     List<TeamUploadRecord> uploads, String comparisonJson) implements Report {
        public String toJson() {
            return "{"
                    + "\"range\":{\"days\":" + rangeDays() + ",\"timezone\":\"" + Json.escape(query.zone().getId())
                    + "\",\"start_date\":\"" + query.startDate() + "\",\"end_date\":\"" + query.endDate() + "\"},"
                    + "\"summary\":{\"team_id\":\"" + Json.escape(teamId) + "\"," + summary.jsonFields()
                    + ",\"source_kind\":" + sourceDimensionJson(sourceKindCounts)
                    + ",\"source_quality\":" + sourceQualityJson(sourceQualityCounts)
                    + ",\"sessions\":" + sessions + ",\"users\":" + users + ",\"devices\":" + devices
                    + derivedJson(summary, eventCount, callCount, sessions, activeSeconds) + "},"
                    + "\"teams\":" + Json.array(teamBuckets, this::teamJson) + ","
                    + "\"users\":" + Json.array(userBuckets, this::userJson) + ","
                    + "\"devices\":" + Json.array(deviceBuckets, this::deviceJson) + ","
                    + "\"models\":" + Json.array(modelBuckets, this::modelJson) + ","
                    + "\"tools\":" + Json.array(toolBuckets, this::toolJson) + ","
                    + "\"team_models\":" + Json.array(teamModelBuckets, this::teamModelJson) + ","
                    + "\"daily\":" + Json.array(dailyBuckets, this::dailyJson) + ","
                    + "\"user_daily\":" + Json.array(userDailyBuckets, this::userDailyJson) + ","
                    + "\"upload_health\":" + Json.array(uploadHealthBuckets, this::uploadHealthJson) + ","
                    + "\"uploads\":" + Json.array(uploads, this::uploadJson)
                    + comparisonJson
                    + "}";
        }

        private long rangeDays() {
            return query.endDate().toEpochDay() - query.startDate().toEpochDay() + 1;
        }

        private String teamJson(TeamBucket bucket) {
            return "{\"team_id\":\"" + Json.escape(bucket.teamId) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size() + ",\"users\":" + bucket.users.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"devices\":" + bucket.devices.size() + ",\"last_upload_at\":\""
                    + formatInstant(parseInstant(bucket.lastUploadAt), query.zone()) + "\"}";
        }

        private String userJson(UserBucket bucket) {
            return "{\"team_id\":\"" + Json.escape(bucket.teamId) + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName)
                    + "\"," + bucket.totals.jsonFields() + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"devices\":" + bucket.devices.size() + ",\"last_seen_at\":\""
                    + formatInstant(parseInstant(bucket.lastSeenAt), query.zone()) + "\"}";
        }

        private String deviceJson(DeviceBucket bucket) {
            return "{\"team_id\":\"" + Json.escape(bucket.teamId) + "\",\"device_id\":\"" + Json.escape(bucket.deviceId)
                    + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"last_seen_at\":\""
                    + formatInstant(parseInstant(bucket.lastSeenAt), query.zone()) + "\"}";
        }

        private String modelJson(ModelBucket bucket) {
            return "{\"model\":\"" + Json.escape(bucket.model) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds()) + "}";
        }

        private String toolJson(ToolBucket bucket) {
            return "{\"tool\":\"" + Json.escape(bucket.tool) + "\"," + bucket.totals.jsonFields()
                    + ",\"source_kind\":" + sourceDimensionJson(bucket.sourceKindCounts)
                    + ",\"source_quality\":" + sourceQualityJson(bucket.sourceQualityCounts)
                    + ",\"sessions\":" + bucket.sessions.size()
                    + ",\"users\":" + bucket.users.size()
                    + ",\"devices\":" + bucket.devices.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds()) + "}";
        }

        private String teamModelJson(TeamModelBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\",\"team_id\":\"" + Json.escape(bucket.teamId)
                    + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\",\"tool\":\""
                    + Json.escape(bucket.tool) + "\",\"model\":\""
                    + Json.escape(bucket.model) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"started_at\":\"" + formatInstant(bucket.startedAt, query.zone()) + "\""
                    + ",\"ended_at\":\"" + formatInstant(bucket.endedAt, query.zone()) + "\"}";
        }

        private String dailyJson(DailyBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size() + ",\"users\":" + bucket.users.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"devices\":" + bucket.devices.size() + "}";
        }

        private String userDailyJson(UserDailyBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\",\"team_id\":\"" + Json.escape(bucket.teamId)
                    + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds()) + "}";
        }

        private String uploadJson(TeamUploadRecord upload) {
            return "{\"team_id\":\"" + Json.escape(upload.teamId()) + "\",\"user_id\":\""
                    + Json.escape(upload.userId()) + "\",\"device_id\":\"" + Json.escape(upload.deviceId())
                    + "\",\"upload_date\":\"" + Json.escape(upload.uploadDate()) + "\",\"upload_time\":\""
                    + formatInstant(parseInstant(upload.uploadTime()), query.zone()) + "\",\"event_count\":"
                    + upload.eventCount() + ",\"accepted\":" + upload.accepted() + ",\"duplicate\":"
                    + upload.duplicate() + ",\"rejected\":" + upload.rejected() + ",\"status\":\""
                    + Json.escape(upload.status()) + "\",\"message\":\"" + Json.escape(upload.message()) + "\"}";
        }

        private String uploadHealthJson(UploadHealthBucket bucket) {
            Instant latestUploadAt = parseInstant(bucket.latestUploadTime);
            long gapSeconds = uploadGapSeconds(latestUploadAt);
            return "{\"team_id\":\"" + Json.escape(bucket.teamId) + "\",\"user_id\":\""
                    + Json.escape(bucket.userId) + "\",\"device_id\":\"" + Json.escape(bucket.deviceId)
                    + "\",\"last_upload_at\":\"" + formatInstant(latestUploadAt, query.zone())
                    + "\",\"upload_gap_seconds\":" + gapSeconds
                    + ",\"health_status\":\"" + Json.escape(healthStatus(bucket.latestStatus, gapSeconds))
                    + "\",\"latest_status\":\"" + Json.escape(bucket.latestStatus)
                    + "\",\"latest_accepted\":" + bucket.latestAccepted
                    + ",\"latest_duplicate\":" + bucket.latestDuplicate
                    + ",\"latest_rejected\":" + bucket.latestRejected
                    + ",\"recent_uploads\":" + Json.array(bucket.recentUploads, this::uploadJson) + "}";
        }

        private static String derivedJson(TokenTotals totals, int eventCount, int callCount, int sessions, long activeSeconds) {
            return ",\"usage_event_count\":" + eventCount
                    + ",\"call_count\":" + callCount
                    + ",\"active_seconds\":" + activeSeconds
                    + ",\"avg_tokens_per_session\":" + decimal(sessions == 0 ? 0.0d : (double) totals.totalTokens / sessions)
                    + ",\"avg_tokens_per_call\":" + decimal(callCount == 0 ? 0.0d : (double) totals.totalTokens / callCount);
        }

        private static String decimal(double value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }

        private static String sourceQualityJson(Map<String, Integer> counts) {
            return sourceDimensionJson(counts);
        }

        private static String sourceDimensionJson(Map<String, Integer> counts) {
            if (counts.isEmpty()) {
                return "{\"unknown\":0}";
            }
            return "{" + counts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> "\"" + Json.escape(entry.getKey()) + "\":" + entry.getValue())
                    .reduce((left, right) -> left + "," + right)
                    .orElse("") + "}";
        }

        private static long uploadGapSeconds(Instant latestUploadAt) {
            if (latestUploadAt == null) {
                return 0L;
            }
            return Math.max(0L, Instant.now().getEpochSecond() - latestUploadAt.getEpochSecond());
        }

        private static String healthStatus(String latestStatus, long gapSeconds) {
            if (!"ok".equals(latestStatus)) {
                return "error";
            }
            if (gapSeconds > 7200L) {
                return "stale";
            }
            if (gapSeconds > 1800L) {
                return "warning";
            }
            return "ok";
        }
    }

    private static String sessionKey(StoredTeamUsageEvent event) {
        return event.teamId() + "|" + event.userId() + "|" + event.deviceId() + "|" + event.sessionId();
    }

    private static String callKey(StoredTeamUsageEvent event) {
        return event.teamId() + "|" + event.userId() + "|" + event.deviceId() + "|"
                + UsageEventKeys.call(event.tool(), event.sessionId(), event.model(), event.timestamp().toString(),
                event.usage());
    }

    private static long activeSeconds(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return 0L;
        }
        return endedAt.getEpochSecond() - startedAt.getEpochSecond();
    }

    private static final class ActiveWindows {
        private final Map<String, ActiveWindow> windows = new HashMap<>();

        void add(String key, Instant timestamp) {
            windows.computeIfAbsent(key, ignored -> new ActiveWindow()).add(timestamp);
        }

        long activeSeconds() {
            long total = 0L;
            for (ActiveWindow window : windows.values()) {
                total += window.activeSeconds();
            }
            return total;
        }
    }

    private static final class ActiveWindow {
        final List<Instant> timestamps = new ArrayList<>();

        void add(Instant timestamp) {
            timestamps.add(timestamp);
        }

        long activeSeconds() {
            if (timestamps.size() < 2) {
                return 0L;
            }
            timestamps.sort(Comparator.naturalOrder());
            long total = 0L;
            Instant previous = timestamps.get(0);
            for (int i = 1; i < timestamps.size(); i++) {
                Instant current = timestamps.get(i);
                long gapSeconds = TeamReportService.activeSeconds(previous, current);
                if (gapSeconds <= MAX_ACTIVE_GAP_SECONDS) {
                    total += gapSeconds;
                }
                previous = current;
            }
            return total;
        }
    }

    private static String formatInstant(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "";
        }
        ZonedDateTime dateTime = instant.atZone(zone);
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }

    private static Instant min(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        return right.isBefore(left) ? right : left;
    }

    private static Instant max(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        return right.isAfter(left) ? right : left;
    }
}
