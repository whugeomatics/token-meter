package local.agent.dashboard.report;

import local.agent.dashboard.domain.Report;
import local.agent.dashboard.domain.ReportQuery;
import local.agent.dashboard.domain.Snapshot;
import local.agent.dashboard.domain.StoredTeamUsageEvent;
import local.agent.dashboard.domain.TeamUploadRecord;
import local.agent.dashboard.domain.TokenTotals;
import local.agent.dashboard.store.TeamUsageStore;
import local.agent.dashboard.util.Json;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    public Report report(ReportQuery query) throws SQLException {
        Aggregator aggregator = new Aggregator(query);
        for (StoredTeamUsageEvent event : store.loadTeamEvents(query.startDate(), query.endDate())) {
            if (query.contains(event.timestamp()) && query.matchesTeam(event.teamId()) && query.matchesUser(event.userId())) {
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

    private static final class Aggregator {
        private final ReportQuery query;
        private final TokenTotals summary = new TokenTotals();
        private final Set<String> users = new HashSet<>();
        private final Set<String> devices = new HashSet<>();
        private final Set<String> sessions = new HashSet<>();
        private final Map<String, TeamBucket> teamBuckets = new HashMap<>();
        private final Map<String, UserBucket> userBuckets = new HashMap<>();
        private final Map<String, DeviceBucket> deviceBuckets = new HashMap<>();
        private final Map<String, ModelBucket> modelBuckets = new HashMap<>();
        private final Map<String, TeamModelBucket> teamModelBuckets = new HashMap<>();
        private final Map<LocalDate, DailyBucket> dailyBuckets = new LinkedHashMap<>();
        private final Map<String, UserDailyBucket> userDailyBuckets = new HashMap<>();
        private final Map<String, UploadHealthBucket> uploadHealthBuckets = new HashMap<>();
        private final ActiveWindows activeWindows = new ActiveWindows();
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
            summary.add(event.usage());
            eventCount++;
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
            teamModelBuckets.computeIfAbsent(date + "|" + event.teamId() + "|" + event.userId() + "|" + event.model(),
                    id -> new TeamModelBucket(date, event.teamId(), event.userId(), event.userDisplayName(), event.model())).add(event);
            userDailyBuckets.computeIfAbsent(date + "|" + event.teamId() + "|" + event.userId(),
                    id -> new UserDailyBucket(date, event.teamId(), event.userId(), event.userDisplayName())).add(event);
        }

        void add(TeamUploadRecord upload) {
            teamBuckets.computeIfAbsent(upload.teamId(), TeamBucket::new).add(upload);
            uploadHealthBuckets.computeIfAbsent(upload.teamId() + "|" + upload.userId() + "|" + upload.deviceId(),
                    id -> new UploadHealthBucket(upload.teamId(), upload.userId(), upload.deviceId())).add(upload);
        }

        Report toReport() {
            return new TeamReportPayload(query, teamId, summary, users.size(), devices.size(), sessions.size(),
                    eventCount, activeWindows.activeSeconds(), sortedTeams(), sortedUsers(), sortedDevices(),
                    sortedModels(), sortedTeamModels(), new ArrayList<>(dailyBuckets.values()), sortedUserDaily(),
                    sortedUploadHealth(), sortedUploads());
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

        private List<TeamModelBucket> sortedTeamModels() {
            return teamModelBuckets.values().stream()
                    .sorted(Comparator.comparing((TeamModelBucket bucket) -> bucket.date, Comparator.reverseOrder())
                            .thenComparing(bucket -> bucket.teamId)
                            .thenComparing(bucket -> bucket.userId)
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
        final List<TeamUploadRecord> uploads = new ArrayList<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;
        String lastUploadAt = "";

        TeamBucket(String teamId) {
            this.teamId = teamId;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            users.add(event.userId());
            devices.add(event.deviceId());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
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
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            devices.add(event.deviceId());
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
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
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
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
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        ModelBucket(String model) {
            this.model = model;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class TeamModelBucket {
        final LocalDate date;
        final String teamId;
        final String userId;
        final String displayName;
        final String model;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        TeamModelBucket(LocalDate date, String teamId, String userId, String displayName, String model) {
            this.date = date;
            this.teamId = teamId;
            this.userId = userId;
            this.displayName = displayName;
            this.model = model;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
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
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        DailyBucket(LocalDate date) {
            this.date = date;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            users.add(event.userId());
            devices.add(event.deviceId());
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
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
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            activeWindows.add(sessionKey(event), event.timestamp());
            eventCount++;
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

    private record TeamReportPayload(ReportQuery query, String teamId, TokenTotals summary, int users, int devices,
                                     int sessions, int eventCount, long activeSeconds, List<TeamBucket> teamBuckets,
                                     List<UserBucket> userBuckets, List<DeviceBucket> deviceBuckets,
                                     List<ModelBucket> modelBuckets, List<TeamModelBucket> teamModelBuckets,
                                     List<DailyBucket> dailyBuckets,
                                     List<UserDailyBucket> userDailyBuckets,
                                     List<UploadHealthBucket> uploadHealthBuckets,
                                     List<TeamUploadRecord> uploads) implements Report {
        public String toJson() {
            return "{"
                    + "\"range\":{\"days\":" + rangeDays() + ",\"timezone\":\"" + Json.escape(query.zone().getId())
                    + "\",\"start_date\":\"" + query.startDate() + "\",\"end_date\":\"" + query.endDate() + "\"},"
                    + "\"summary\":{\"team_id\":\"" + Json.escape(teamId) + "\"," + summary.jsonFields()
                    + ",\"sessions\":" + sessions + ",\"users\":" + users + ",\"devices\":" + devices
                    + derivedJson(summary, eventCount, sessions, activeSeconds) + "},"
                    + "\"teams\":" + Json.array(teamBuckets, this::teamJson) + ","
                    + "\"users\":" + Json.array(userBuckets, this::userJson) + ","
                    + "\"devices\":" + Json.array(deviceBuckets, this::deviceJson) + ","
                    + "\"models\":" + Json.array(modelBuckets, this::modelJson) + ","
                    + "\"team_models\":" + Json.array(teamModelBuckets, this::teamModelJson) + ","
                    + "\"daily\":" + Json.array(dailyBuckets, this::dailyJson) + ","
                    + "\"user_daily\":" + Json.array(userDailyBuckets, this::userDailyJson) + ","
                    + "\"upload_health\":" + Json.array(uploadHealthBuckets, this::uploadHealthJson) + ","
                    + "\"uploads\":" + Json.array(uploads, this::uploadJson)
                    + "}";
        }

        private long rangeDays() {
            return query.endDate().toEpochDay() - query.startDate().toEpochDay() + 1;
        }

        private String teamJson(TeamBucket bucket) {
            return "{\"team_id\":\"" + Json.escape(bucket.teamId) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size() + ",\"users\":" + bucket.users.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"devices\":" + bucket.devices.size() + ",\"last_upload_at\":\""
                    + formatInstant(parseInstant(bucket.lastUploadAt), query.zone()) + "\"}";
        }

        private String userJson(UserBucket bucket) {
            return "{\"team_id\":\"" + Json.escape(bucket.teamId) + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName)
                    + "\"," + bucket.totals.jsonFields() + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"devices\":" + bucket.devices.size() + ",\"last_seen_at\":\""
                    + formatInstant(parseInstant(bucket.lastSeenAt), query.zone()) + "\"}";
        }

        private String deviceJson(DeviceBucket bucket) {
            return "{\"team_id\":\"" + Json.escape(bucket.teamId) + "\",\"device_id\":\"" + Json.escape(bucket.deviceId)
                    + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"last_seen_at\":\""
                    + formatInstant(parseInstant(bucket.lastSeenAt), query.zone()) + "\"}";
        }

        private String modelJson(ModelBucket bucket) {
            return "{\"model\":\"" + Json.escape(bucket.model) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds()) + "}";
        }

        private String teamModelJson(TeamModelBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\",\"team_id\":\"" + Json.escape(bucket.teamId)
                    + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\",\"model\":\""
                    + Json.escape(bucket.model) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"started_at\":\"" + formatInstant(bucket.startedAt, query.zone()) + "\""
                    + ",\"ended_at\":\"" + formatInstant(bucket.endedAt, query.zone()) + "\"}";
        }

        private String dailyJson(DailyBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size() + ",\"users\":" + bucket.users.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + ",\"devices\":" + bucket.devices.size() + "}";
        }

        private String userDailyJson(UserDailyBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\",\"team_id\":\"" + Json.escape(bucket.teamId)
                    + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
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

        private static String derivedJson(TokenTotals totals, int eventCount, int sessions, long activeSeconds) {
            return ",\"usage_event_count\":" + eventCount
                    + ",\"active_seconds\":" + activeSeconds
                    + ",\"avg_tokens_per_session\":" + decimal(sessions == 0 ? 0.0d : (double) totals.totalTokens / sessions)
                    + ",\"avg_tokens_per_call\":" + decimal(eventCount == 0 ? 0.0d : (double) totals.totalTokens / eventCount);
        }

        private static String decimal(double value) {
            return String.format(Locale.ROOT, "%.2f", value);
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
        return Instant.parse(value);
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
