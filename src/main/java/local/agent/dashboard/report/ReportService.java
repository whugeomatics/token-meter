package local.agent.dashboard.report;

import local.agent.dashboard.domain.Report;
import local.agent.dashboard.domain.ReportQuery;
import local.agent.dashboard.domain.TokenTotals;
import local.agent.dashboard.domain.UsageEvent;
import local.agent.dashboard.store.UsageStore;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ReportService {
    private final UsageStore usageStore;
    private final ZoneId zone;

    public ReportService(UsageStore usageStore, ZoneId zone) {
        this.usageStore = usageStore;
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    public Report report(ReportQuery query) throws SQLException {
        List<UsageEvent> events = usageStore.loadEvents(query.startDate(), query.endDate());
        Aggregator aggregator = new Aggregator(query);
        for (UsageEvent event : events) {
            if (query.contains(event.timestamp())) {
                aggregator.add(event);
            }
        }
        return aggregator.toReport();
    }

    private static final class Aggregator {
        private final ReportQuery query;
        private final TokenTotals summary = new TokenTotals();
        private final Map<LocalDate, DailyBucket> daily = new LinkedHashMap<>();
        private final Map<String, ModelBucket> models = new HashMap<>();
        private final Map<String, SessionBucket> sessions = new HashMap<>();

        Aggregator(ReportQuery query) {
            this.query = query;
            LocalDate date = query.startDate();
            while (!date.isAfter(query.endDate())) {
                daily.put(date, new DailyBucket(date));
                date = date.plusDays(1);
            }
        }

        void add(UsageEvent event) {
            summary.add(event.usage());
            LocalDate date = event.timestamp().atZone(query.zone()).toLocalDate();
            daily.computeIfAbsent(date, DailyBucket::new).add(event);
            models.computeIfAbsent(event.model(), ModelBucket::new).add(event);
            sessions.computeIfAbsent(event.sessionId(), SessionBucket::new).add(event);
        }

        Report toReport() {
            return new ReportPayload(query, summary, new ArrayList<>(daily.values()),
                    models.values().stream()
                            .sorted(Comparator.comparingLong((ModelBucket bucket) -> bucket.totals.totalTokens).reversed())
                            .toList(),
                    sessions.values().stream()
                            .sorted(Comparator.comparing((SessionBucket bucket) -> bucket.startedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList());
        }
    }

    private static final class SessionBucket {
        final String sessionId;
        final TokenTotals totals = new TokenTotals();
        final Set<String> models = new HashSet<>();
        Instant startedAt;
        Instant endedAt;

        SessionBucket(String sessionId) {
            this.sessionId = sessionId;
        }

        void add(UsageEvent event) {
            totals.add(event.usage());
            models.add(event.model());
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class ModelBucket {
        final String model;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        Instant startedAt;
        Instant endedAt;

        ModelBucket(String model) {
            this.model = model;
        }

        void add(UsageEvent event) {
            totals.add(event.usage());
            sessions.add(event.sessionId());
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class DailyBucket {
        final LocalDate date;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();

        DailyBucket(LocalDate date) {
            this.date = date;
        }

        void add(UsageEvent event) {
            totals.add(event.usage());
            sessions.add(event.sessionId());
        }
    }

    private record ReportPayload(ReportQuery query, TokenTotals summary, List<DailyBucket> daily,
                                 List<ModelBucket> models, List<SessionBucket> sessions) implements Report {
        public String toJson() {
            StringBuilder out = new StringBuilder();
            out.append('{');
            out.append("\"range\":{")
                    .append("\"kind\":\"").append(Json.escape(query.kind())).append("\",")
                    .append("\"start_date\":\"").append(query.startDate()).append("\",")
                    .append("\"end_date\":\"").append(query.endDate()).append("\",")
                    .append("\"timezone\":\"").append(Json.escape(query.zone().getId())).append("\"")
                    .append("},");
            out.append("\"summary\":{").append(summary.jsonFields()).append("},");
            out.append("\"daily\":");
            out.append(Json.array(daily, bucket -> "{"
                    + "\"date\":\"" + bucket.date + "\","
                    + bucket.totals.jsonFields() + ","
                    + "\"session_count\":" + bucket.sessions.size()
                    + "}"));
            out.append(',');
            out.append("\"models\":");
            out.append(Json.array(models, bucket -> "{"
                    + "\"model\":\"" + Json.escape(bucket.model) + "\","
                    + bucket.totals.jsonFields() + ","
                    + "\"session_count\":" + bucket.sessions.size() + ","
                    + "\"active_seconds\":" + activeSeconds(bucket.startedAt, bucket.endedAt)
                    + "}"));
            out.append(',');
            out.append("\"sessions\":");
            out.append(Json.array(sessions, bucket -> "{"
                    + "\"session_id\":\"" + Json.escape(bucket.sessionId) + "\","
                    + "\"started_at\":\"" + formatInstant(bucket.startedAt, query.zone()) + "\","
                    + "\"ended_at\":\"" + formatInstant(bucket.endedAt, query.zone()) + "\","
                    + "\"active_seconds\":" + activeSeconds(bucket.startedAt, bucket.endedAt) + ","
                    + "\"models\":" + Json.stringArray(bucket.models.stream().sorted().toList()) + ","
                    + bucket.totals.jsonFields()
                    + "}"));
            out.append("}");
            return out.toString();
        }
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
