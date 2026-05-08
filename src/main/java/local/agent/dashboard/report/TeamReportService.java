package local.agent.dashboard.report;

import local.agent.dashboard.domain.Report;
import local.agent.dashboard.domain.ReportQuery;
import local.agent.dashboard.domain.Snapshot;
import local.agent.dashboard.domain.StoredTeamUsageEvent;
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
import java.util.Set;

public final class TeamReportService {
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
            if (query.contains(event.timestamp())) {
                aggregator.add(event);
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
        private final Map<String, UserBucket> userBuckets = new HashMap<>();
        private final Map<String, DeviceBucket> deviceBuckets = new HashMap<>();
        private final Map<String, ModelBucket> modelBuckets = new HashMap<>();
        private final Map<String, TeamModelBucket> teamModelBuckets = new HashMap<>();
        private final Map<LocalDate, DailyBucket> dailyBuckets = new LinkedHashMap<>();
        private final Map<String, UserDailyBucket> userDailyBuckets = new HashMap<>();
        private String teamId = "";

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
            users.add(event.userId());
            devices.add(event.deviceId());
            sessions.add(sessionKey(event));
            LocalDate date = event.timestamp().atZone(query.zone()).toLocalDate();
            dailyBuckets.computeIfAbsent(date, DailyBucket::new).add(event);
            userBuckets.computeIfAbsent(event.userId(), id -> new UserBucket(id, event.userDisplayName())).add(event);
            deviceBuckets.computeIfAbsent(event.deviceId(), id -> new DeviceBucket(id, event.userId(), event.deviceDisplayName())).add(event);
            modelBuckets.computeIfAbsent(event.model(), ModelBucket::new).add(event);
            teamModelBuckets.computeIfAbsent(date + "|" + event.userId() + "|" + event.model(),
                    id -> new TeamModelBucket(date, event.userId(), event.userDisplayName(), event.model())).add(event);
            userDailyBuckets.computeIfAbsent(date + "|" + event.userId(),
                    id -> new UserDailyBucket(date, event.userId(), event.userDisplayName())).add(event);
        }

        Report toReport() {
            return new TeamReportPayload(query, teamId, summary, users.size(), devices.size(), sessions.size(),
                    sortedUsers(), sortedDevices(), sortedModels(), sortedTeamModels(),
                    new ArrayList<>(dailyBuckets.values()), sortedUserDaily());
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
                    .sorted(Comparator.comparingLong((TeamModelBucket bucket) -> bucket.totals.totalTokens).reversed()
                            .thenComparing((TeamModelBucket bucket) -> bucket.date, Comparator.reverseOrder())
                            .thenComparing(bucket -> bucket.userId)
                            .thenComparing(bucket -> bucket.model))
                    .toList();
        }

        private List<UserDailyBucket> sortedUserDaily() {
            return userDailyBuckets.values().stream()
                    .sorted(Comparator.comparing((UserDailyBucket bucket) -> bucket.date)
                            .thenComparing(bucket -> bucket.userId))
                    .toList();
        }
    }

    private static final class UserBucket {
        final String userId;
        final String displayName;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final Set<String> devices = new HashSet<>();
        String lastSeenAt = "";

        UserBucket(String userId, String displayName) {
            this.userId = userId;
            this.displayName = displayName;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            devices.add(event.deviceId());
            lastSeenAt = event.timestamp().toString();
        }
    }

    private static final class DeviceBucket {
        final String deviceId;
        final String userId;
        final String displayName;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        String lastSeenAt = "";

        DeviceBucket(String deviceId, String userId, String displayName) {
            this.deviceId = deviceId;
            this.userId = userId;
            this.displayName = displayName == null || displayName.isBlank() ? deviceId : displayName;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            lastSeenAt = event.timestamp().toString();
        }
    }

    private static final class ModelBucket {
        final String model;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();

        ModelBucket(String model) {
            this.model = model;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
        }
    }

    private static final class TeamModelBucket {
        final LocalDate date;
        final String userId;
        final String displayName;
        final String model;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        Instant startedAt;
        Instant endedAt;

        TeamModelBucket(LocalDate date, String userId, String displayName, String model) {
            this.date = date;
            this.userId = userId;
            this.displayName = displayName;
            this.model = model;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
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

        DailyBucket(LocalDate date) {
            this.date = date;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
            users.add(event.userId());
            devices.add(event.deviceId());
        }
    }

    private static final class UserDailyBucket {
        final LocalDate date;
        final String userId;
        final String displayName;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();

        UserDailyBucket(LocalDate date, String userId, String displayName) {
            this.date = date;
            this.userId = userId;
            this.displayName = displayName;
        }

        void add(StoredTeamUsageEvent event) {
            totals.add(event.usage());
            sessions.add(sessionKey(event));
        }
    }

    private record TeamReportPayload(ReportQuery query, String teamId, TokenTotals summary, int users, int devices,
                                     int sessions, List<UserBucket> userBuckets, List<DeviceBucket> deviceBuckets,
                                     List<ModelBucket> modelBuckets, List<TeamModelBucket> teamModelBuckets,
                                     List<DailyBucket> dailyBuckets,
                                     List<UserDailyBucket> userDailyBuckets) implements Report {
        public String toJson() {
            return "{"
                    + "\"range\":{\"days\":" + rangeDays() + ",\"timezone\":\"" + Json.escape(query.zone().getId())
                    + "\",\"start_date\":\"" + query.startDate() + "\",\"end_date\":\"" + query.endDate() + "\"},"
                    + "\"summary\":{\"team_id\":\"" + Json.escape(teamId) + "\"," + summary.jsonFields()
                    + ",\"sessions\":" + sessions + ",\"users\":" + users + ",\"devices\":" + devices + "},"
                    + "\"users\":" + Json.array(userBuckets, this::userJson) + ","
                    + "\"devices\":" + Json.array(deviceBuckets, this::deviceJson) + ","
                    + "\"models\":" + Json.array(modelBuckets, this::modelJson) + ","
                    + "\"team_models\":" + Json.array(teamModelBuckets, this::teamModelJson) + ","
                    + "\"daily\":" + Json.array(dailyBuckets, this::dailyJson) + ","
                    + "\"user_daily\":" + Json.array(userDailyBuckets, this::userDailyJson)
                    + "}";
        }

        private long rangeDays() {
            return query.endDate().toEpochDay() - query.startDate().toEpochDay() + 1;
        }

        private String userJson(UserBucket bucket) {
            return "{\"user_id\":\"" + Json.escape(bucket.userId) + "\",\"display_name\":\"" + Json.escape(bucket.displayName)
                    + "\"," + bucket.totals.jsonFields() + ",\"sessions\":" + bucket.sessions.size()
                    + ",\"devices\":" + bucket.devices.size() + ",\"last_seen_at\":\""
                    + formatInstant(parseInstant(bucket.lastSeenAt), query.zone()) + "\"}";
        }

        private String deviceJson(DeviceBucket bucket) {
            return "{\"device_id\":\"" + Json.escape(bucket.deviceId) + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size() + ",\"last_seen_at\":\""
                    + formatInstant(parseInstant(bucket.lastSeenAt), query.zone()) + "\"}";
        }

        private String modelJson(ModelBucket bucket) {
            return "{\"model\":\"" + Json.escape(bucket.model) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size() + "}";
        }

        private String teamModelJson(TeamModelBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\",\"model\":\""
                    + Json.escape(bucket.model) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size()
                    + ",\"started_at\":\"" + formatInstant(bucket.startedAt, query.zone()) + "\""
                    + ",\"ended_at\":\"" + formatInstant(bucket.endedAt, query.zone()) + "\""
                    + ",\"active_seconds\":" + activeSeconds(bucket.startedAt, bucket.endedAt) + "}";
        }

        private String dailyJson(DailyBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size() + ",\"users\":" + bucket.users.size()
                    + ",\"devices\":" + bucket.devices.size() + "}";
        }

        private String userDailyJson(UserDailyBucket bucket) {
            return "{\"date\":\"" + bucket.date + "\",\"user_id\":\"" + Json.escape(bucket.userId)
                    + "\",\"display_name\":\"" + Json.escape(bucket.displayName) + "\"," + bucket.totals.jsonFields()
                    + ",\"sessions\":" + bucket.sessions.size() + "}";
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
