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
    private static final long MAX_ACTIVE_GAP_SECONDS = 30L * 60L;

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
        private final ActiveWindows activeWindows = new ActiveWindows();
        private int eventCount;
        private Instant startedAt;
        private Instant endedAt;

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
            eventCount++;
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
            activeWindows.add(event.sessionId(), event.timestamp());
            LocalDate date = event.timestamp().atZone(query.zone()).toLocalDate();
            daily.computeIfAbsent(date, DailyBucket::new).add(event);
            models.computeIfAbsent(event.model(), ModelBucket::new).add(event);
            sessions.computeIfAbsent(event.sessionId(), SessionBucket::new).add(event);
        }

        Report toReport() {
            return new ReportPayload(query, summary, eventCount, sessions.size(), activeWindows.activeSeconds(),
                    new ArrayList<>(daily.values()),
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
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        SessionBucket(String sessionId) {
            this.sessionId = sessionId;
        }

        void add(UsageEvent event) {
            totals.add(event.usage());
            models.add(event.model());
            eventCount++;
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
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

        void add(UsageEvent event) {
            totals.add(event.usage());
            sessions.add(event.sessionId());
            activeWindows.add(event.sessionId(), event.timestamp());
            eventCount++;
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class DailyBucket {
        final LocalDate date;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        DailyBucket(LocalDate date) {
            this.date = date;
        }

        void add(UsageEvent event) {
            totals.add(event.usage());
            sessions.add(event.sessionId());
            activeWindows.add(event.sessionId(), event.timestamp());
            eventCount++;
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private record ReportPayload(ReportQuery query, TokenTotals summary, int eventCount, int sessionCount,
                                 long activeSeconds, List<DailyBucket> daily,
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
            out.append("\"summary\":{").append(summary.jsonFields())
                    .append(derivedJson(summary, eventCount, sessionCount, activeSeconds)).append("},");
            out.append("\"daily\":");
            out.append(Json.array(daily, bucket -> "{"
                    + "\"date\":\"" + bucket.date + "\","
                    + bucket.totals.jsonFields() + ","
                    + "\"session_count\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + "}"));
            out.append(',');
            out.append("\"models\":");
            out.append(Json.array(models, bucket -> "{"
                    + "\"model\":\"" + Json.escape(bucket.model) + "\","
                    + bucket.totals.jsonFields() + ","
                    + "\"session_count\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + "}"));
            out.append(',');
            out.append("\"sessions\":");
            out.append(Json.array(sessions, bucket -> "{"
                    + "\"session_id\":\"" + Json.escape(bucket.sessionId) + "\","
                    + "\"started_at\":\"" + formatInstant(bucket.startedAt, query.zone()) + "\","
                    + "\"ended_at\":\"" + formatInstant(bucket.endedAt, query.zone()) + "\","
                    + "\"active_seconds\":" + ReportService.activeSeconds(bucket.startedAt, bucket.endedAt) + ","
                    + "\"models\":" + Json.stringArray(bucket.models.stream().sorted().toList()) + ","
                    + "\"usage_event_count\":" + bucket.eventCount + ","
                    + "\"avg_tokens_per_call\":" + decimal(bucket.eventCount == 0 ? 0.0d : (double) bucket.totals.totalTokens / bucket.eventCount) + ","
                    + bucket.totals.jsonFields()
                    + "}"));
            out.append("}");
            return out.toString();
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
                long gapSeconds = ReportService.activeSeconds(previous, current);
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
