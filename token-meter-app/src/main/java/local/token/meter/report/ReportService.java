package local.token.meter.report;

import local.token.meter.domain.Report;
import local.token.meter.domain.ReportQuery;
import local.token.meter.domain.PeriodComparison;
import local.token.meter.domain.TokenTotals;
import local.token.meter.domain.UsageEvent;
import local.token.meter.http.BadRequestException;
import local.token.meter.ingestion.UsageEventKeys;
import local.token.meter.store.UsageStore;
import local.token.meter.util.Json;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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
        List<UsageEvent> events = usageStore.loadEvents(query.startDate(), query.endDate());
        Aggregator aggregator = new Aggregator(query);
        for (UsageEvent event : events) {
            if (query.contains(event.timestamp()) && query.matchesTool(event.tool())) {
                aggregator.add(event);
            }
        }
        return aggregator.toReport();
    }

    public Report sessions(Map<String, String> params) throws SQLException {
        ReportQuery query = sessionQuery(params);
        int pageSize = pageSize(params);
        int page = page(params);
        Map<String, SessionBucket> sessions = new HashMap<>();
        for (UsageEvent event : usageStore.loadEvents(query.startDate(), query.endDate())) {
            if (query.contains(event.timestamp()) && query.matchesTool(event.tool())) {
                sessions.computeIfAbsent(event.sessionId(), SessionBucket::new).add(event);
            }
        }
        List<SessionBucket> sorted = sessions.values().stream()
                .sorted(Comparator.comparing((SessionBucket bucket) -> bucket.startedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int total = sorted.size();
        int from = Math.min(total, (page - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        return new SessionPagePayload(query, page, pageSize, total, sorted.subList(from, to));
    }

    private ReportQuery sessionQuery(Map<String, String> params) {
        String period = params.getOrDefault("period", "");
        String compare = params.getOrDefault("compare", "");
        if (period.isBlank() && compare.isBlank()) {
            return ReportQuery.from(params, zone);
        }
        if ("previous".equals(compare)) {
            return PeriodComparison.previous(period, LocalDate.now(zone), zone, "", "",
                    params.getOrDefault("tool", "")).current();
        }
        throw new BadRequestException("period and compare must use compare=previous");
    }

    private static int page(Map<String, String> params) {
        int value = intParam(params, "page", 1);
        if (value < 1) {
            throw new BadRequestException("page must be >= 1");
        }
        return value;
    }

    private static int pageSize(Map<String, String> params) {
        int value = intParam(params, "page_size", 10);
        if (value < 1 || value > 10) {
            throw new BadRequestException("page_size must be between 1 and 10");
        }
        return value;
    }

    private static int intParam(Map<String, String> params, String name, int defaultValue) {
        String value = params.get(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private Report periodComparison(Map<String, String> params, String period) throws SQLException {
        String tool = params.getOrDefault("tool", "");
        PeriodComparison comparison = PeriodComparison.previous(period, LocalDate.now(zone), zone, "", "", tool);
        ReportQuery currentQuery = comparison.current();
        ReportQuery previousQuery = comparison.previous();
        Aggregator current = new Aggregator(currentQuery);
        Aggregator previous = new Aggregator(previousQuery);
        for (UsageEvent event : usageStore.loadEvents(previousQuery.startDate(), currentQuery.endDate())) {
            if (currentQuery.contains(event.timestamp()) && currentQuery.matchesTool(event.tool())) {
                current.add(event);
            } else if (previousQuery.contains(event.timestamp()) && previousQuery.matchesTool(event.tool())) {
                previous.add(event);
            }
        }
        return current.toReport(comparisonJson(comparison, current, previous, currentQuery, previousQuery));
    }

    private static final class Aggregator {
        private final ReportQuery query;
        private final TokenTotals summary = new TokenTotals();
        private final Map<String, Integer> sourceKindCounts = new HashMap<>();
        private final Map<String, Integer> sourceQualityCounts = new HashMap<>();
        private final Map<LocalDate, DailyBucket> daily = new LinkedHashMap<>();
        private final Map<String, ModelBucket> models = new HashMap<>();
        private final Map<String, ToolBucket> tools = new HashMap<>();
        private final Map<String, SessionBucket> sessions = new HashMap<>();
        private final ActiveWindows activeWindows = new ActiveWindows();
        private final Set<String> callKeys = new HashSet<>();
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
            summary.add(event.tool(), event.usage());
            addSourceDimension(sourceKindCounts, event.sourceKind());
            addSourceQuality(sourceQualityCounts, event.sourceQuality());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
            activeWindows.add(event.sessionId(), event.timestamp());
            LocalDate date = event.timestamp().atZone(query.zone()).toLocalDate();
            daily.computeIfAbsent(date, DailyBucket::new).add(event);
            models.computeIfAbsent(event.model(), ModelBucket::new).add(event);
            tools.computeIfAbsent(event.tool(), ToolBucket::new).add(event);
            sessions.computeIfAbsent(event.sessionId(), SessionBucket::new).add(event);
        }

        private static void addSourceQuality(Map<String, Integer> counts, String sourceQuality) {
            addSourceDimension(counts, sourceQuality);
        }

        private static void addSourceDimension(Map<String, Integer> counts, String value) {
            String normalized = value == null || value.isBlank() ? "unknown" : value;
            counts.merge(normalized, 1, Integer::sum);
        }

        Report toReport() {
            return toReport("");
        }

        Report toReport(String comparisonJson) {
            return new ReportPayload(query, summary, eventCount, callKeys.size(), sessions.size(), activeWindows.activeSeconds(),
                    sourceKindCounts, sourceQualityCounts, new ArrayList<>(daily.values()),
                    models.values().stream()
                            .sorted(Comparator.comparingLong((ModelBucket bucket) -> bucket.totals.totalTokens).reversed())
                            .toList(),
                    tools.values().stream()
                            .sorted(Comparator.comparingLong((ToolBucket bucket) -> bucket.totals.totalTokens).reversed())
                            .toList(),
                    sessions.values().stream()
                            .sorted(Comparator.comparing((SessionBucket bucket) -> bucket.startedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList(),
                    comparisonJson);
        }
    }

    private static final class SessionBucket {
        final String sessionId;
        final TokenTotals totals = new TokenTotals();
        final Set<String> models = new HashSet<>();
        final Set<String> tools = new HashSet<>();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        SessionBucket(String sessionId) {
            this.sessionId = sessionId;
        }

        void add(UsageEvent event) {
            totals.add(event.tool(), event.usage());
            models.add(event.model());
            tools.add(event.tool());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class ModelBucket {
        final String model;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final Set<String> tools = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        ModelBucket(String model) {
            this.model = model;
        }

        void add(UsageEvent event) {
            totals.add(event.tool(), event.usage());
            sessions.add(event.sessionId());
            tools.add(event.tool());
            activeWindows.add(event.sessionId(), event.timestamp());
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
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;

        ToolBucket(String tool) {
            this.tool = tool;
        }

        void add(UsageEvent event) {
            totals.add(event.tool(), event.usage());
            Aggregator.addSourceDimension(sourceKindCounts, event.sourceKind());
            Aggregator.addSourceQuality(sourceQualityCounts, event.sourceQuality());
            sessions.add(event.sessionId());
            activeWindows.add(event.sessionId(), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
        }
    }

    private static final class DailyBucket {
        final LocalDate date;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        final ActiveWindows activeWindows = new ActiveWindows();
        final Set<String> callKeys = new HashSet<>();
        int eventCount;
        Instant startedAt;
        Instant endedAt;

        DailyBucket(LocalDate date) {
            this.date = date;
        }

        void add(UsageEvent event) {
            totals.add(event.tool(), event.usage());
            sessions.add(event.sessionId());
            activeWindows.add(event.sessionId(), event.timestamp());
            eventCount++;
            callKeys.add(callKey(event));
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private record ReportPayload(ReportQuery query, TokenTotals summary, int eventCount, int callCount, int sessionCount,
                                 long activeSeconds, Map<String, Integer> sourceKindCounts,
                                 Map<String, Integer> sourceQualityCounts, List<DailyBucket> daily,
                                 List<ModelBucket> models, List<ToolBucket> tools, List<SessionBucket> sessions,
                                 String comparisonJson) implements Report {
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
                    .append(",\"source_kind\":").append(sourceDimensionJson(sourceKindCounts))
                    .append(",\"source_quality\":").append(sourceQualityJson(sourceQualityCounts))
                    .append(derivedJson(summary, eventCount, callCount, sessionCount, activeSeconds)).append("},");
            out.append("\"daily\":");
            out.append(Json.array(daily, bucket -> "{"
                    + "\"date\":\"" + bucket.date + "\","
                    + bucket.totals.jsonFields() + ","
                    + "\"session_count\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + "}"));
            out.append(',');
            out.append("\"models\":");
            out.append(Json.array(models, bucket -> "{"
                    + "\"model\":\"" + Json.escape(bucket.model) + "\","
                    + "\"tools\":" + Json.stringArray(bucket.tools.stream().sorted().toList()) + ","
                    + bucket.totals.jsonFields() + ","
                    + "\"session_count\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + "}"));
            out.append(',');
            out.append("\"tools\":");
            out.append(Json.array(tools, bucket -> "{"
                    + "\"tool\":\"" + Json.escape(bucket.tool) + "\","
                    + bucket.totals.jsonFields() + ","
                    + "\"source_kind\":" + sourceDimensionJson(bucket.sourceKindCounts) + ","
                    + "\"source_quality\":" + sourceQualityJson(bucket.sourceQualityCounts) + ","
                    + "\"sessions\":" + bucket.sessions.size()
                    + derivedJson(bucket.totals, bucket.eventCount, bucket.callKeys.size(), bucket.sessions.size(),
                    bucket.activeWindows.activeSeconds())
                    + "}"));
            out.append(',');
            out.append("\"sessions\":");
            out.append(Json.array(sessions, bucket -> "{"
                    + "\"session_id\":\"" + Json.escape(bucket.sessionId) + "\","
                    + "\"started_at\":\"" + formatInstant(bucket.startedAt, query.zone()) + "\","
                    + "\"ended_at\":\"" + formatInstant(bucket.endedAt, query.zone()) + "\","
                    + "\"active_seconds\":" + ReportService.activeSeconds(bucket.startedAt, bucket.endedAt) + ","
                    + "\"tools\":" + Json.stringArray(bucket.tools.stream().sorted().toList()) + ","
                    + "\"models\":" + Json.stringArray(bucket.models.stream().sorted().toList()) + ","
                    + "\"usage_event_count\":" + bucket.eventCount + ","
                    + "\"call_count\":" + bucket.callKeys.size() + ","
                    + "\"avg_tokens_per_call\":" + decimal(bucket.callKeys.isEmpty() ? 0.0d : (double) bucket.totals.totalTokens / bucket.callKeys.size()) + ","
                    + bucket.totals.jsonFields()
                    + "}"));
            out.append(comparisonJson);
            out.append("}");
            return out.toString();
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
    }

    private record SessionPagePayload(ReportQuery query, int page, int pageSize, int total,
                                      List<SessionBucket> sessions) implements Report {
        public String toJson() {
            int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
            return "{"
                    + "\"range\":{\"kind\":\"" + Json.escape(query.kind()) + "\",\"start_date\":\""
                    + query.startDate() + "\",\"end_date\":\"" + query.endDate() + "\",\"timezone\":\""
                    + Json.escape(query.zone().getId()) + "\"},"
                    + "\"page\":" + page + ","
                    + "\"page_size\":" + pageSize + ","
                    + "\"total\":" + total + ","
                    + "\"total_pages\":" + totalPages + ","
                    + "\"sessions\":" + Json.array(sessions, bucket -> "{"
                    + "\"session_id\":\"" + Json.escape(bucket.sessionId) + "\","
                    + "\"started_at\":\"" + formatInstant(bucket.startedAt, query.zone()) + "\","
                    + "\"ended_at\":\"" + formatInstant(bucket.endedAt, query.zone()) + "\","
                    + "\"active_seconds\":" + ReportService.activeSeconds(bucket.startedAt, bucket.endedAt) + ","
                    + "\"tools\":" + Json.stringArray(bucket.tools.stream().sorted().toList()) + ","
                    + "\"models\":" + Json.stringArray(bucket.models.stream().sorted().toList()) + ","
                    + "\"usage_event_count\":" + bucket.eventCount + ","
                    + "\"call_count\":" + bucket.callKeys.size() + ","
                    + "\"avg_tokens_per_call\":" + decimal(bucket.callKeys.isEmpty() ? 0.0d : (double) bucket.totals.totalTokens / bucket.callKeys.size()) + ","
                    + bucket.totals.jsonFields()
                    + "}")
                    + "}";
        }
    }

    private static String comparisonJson(PeriodComparison comparison, Aggregator current, Aggregator previous,
                                         ReportQuery currentQuery, ReportQuery previousQuery) {
        return ",\"comparison\":{\"period\":\"" + Json.escape(comparison.period()) + "\","
                + "\"current\":" + comparisonSummaryJson(comparison.currentLabel(), current, currentQuery) + ","
                + "\"previous\":" + comparisonSummaryJson(comparison.previousLabel(), previous, previousQuery) + ","
                + "\"delta\":" + comparisonDeltaJson(current, previous) + ","
                + "\"daily\":" + comparisonDailyJson(current, previous, currentQuery, previousQuery) + ","
                + "\"models\":" + comparisonModelsJson(current, previous) + ","
                + "\"tools\":" + comparisonToolsJson(current, previous)
                + "}";
    }

    private static String comparisonSummaryJson(String label, Aggregator aggregator, ReportQuery query) {
        return "{\"label\":\"" + Json.escape(label) + "\",\"start_date\":\"" + query.startDate()
                + "\",\"end_date\":\"" + query.endDate() + "\",\"total_tokens\":" + aggregator.summary.totalTokens
                + ",\"usage_event_count\":" + aggregator.eventCount
                + ",\"call_count\":" + aggregator.callKeys.size()
                + ",\"sessions\":" + aggregator.sessions.size() + "}";
    }

    private static String comparisonDeltaJson(Aggregator current, Aggregator previous) {
        long tokenDelta = current.summary.totalTokens - previous.summary.totalTokens;
        return "{\"total_tokens\":" + tokenDelta
                + ",\"total_tokens_rate\":" + decimal(rate(tokenDelta, previous.summary.totalTokens))
                + ",\"usage_event_count\":" + (current.eventCount - previous.eventCount)
                + ",\"call_count\":" + (current.callKeys.size() - previous.callKeys.size())
                + ",\"sessions\":" + (current.sessions.size() - previous.sessions.size()) + "}";
    }

    private static String comparisonDailyJson(Aggregator current, Aggregator previous, ReportQuery currentQuery,
                                              ReportQuery previousQuery) {
        List<String> rows = new ArrayList<>();
        long days = ChronoUnit.DAYS.between(currentQuery.startDate(), currentQuery.endDate());
        for (int index = 0; index <= days; index++) {
            LocalDate currentDate = currentQuery.startDate().plusDays(index);
            LocalDate previousDate = previousQuery.startDate().plusDays(index);
            DailyBucket currentBucket = current.daily.get(currentDate);
            DailyBucket previousBucket = previous.daily.get(previousDate);
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

    private static String comparisonModelsJson(Aggregator current, Aggregator previous) {
        Set<String> keys = new HashSet<>();
        keys.addAll(current.models.keySet());
        keys.addAll(previous.models.keySet());
        return "[" + keys.stream()
                .map(key -> {
                    ModelBucket currentBucket = current.models.get(key);
                    ModelBucket previousBucket = previous.models.get(key);
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
        keys.addAll(current.tools.keySet());
        keys.addAll(previous.tools.keySet());
        return "[" + keys.stream()
                .map(key -> {
                    ToolBucket currentBucket = current.tools.get(key);
                    ToolBucket previousBucket = previous.tools.get(key);
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

    private record ComparisonRow(String json, long delta) {
    }

    private static long activeSeconds(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return 0L;
        }
        return endedAt.getEpochSecond() - startedAt.getEpochSecond();
    }

    private static String callKey(UsageEvent event) {
        return UsageEventKeys.call(event.tool(), event.sessionId(), event.model(), event.timestamp().toString(),
                event.usage());
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
