package local.token.meter.domain;

import local.token.meter.http.BadRequestException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public record PeriodComparison(String period, String currentLabel, String previousLabel,
                               ReportQuery current, ReportQuery previous) {
    public static PeriodComparison previous(String period, LocalDate today, ZoneId zone, String teamId, String userId) {
        return previous(period, today, zone, teamId, userId, "");
    }

    public static PeriodComparison previous(String period, LocalDate today, ZoneId zone, String teamId, String userId,
                                            String tool) {
        return switch (period) {
            case "day" -> day(today, zone, teamId, userId, tool);
            case "week" -> week(today, zone, teamId, userId, tool);
            case "month" -> month(today, zone, teamId, userId, tool);
            default -> throw new BadRequestException("period must be one of day, week, or month");
        };
    }

    private static PeriodComparison day(LocalDate today, ZoneId zone, String teamId, String userId, String tool) {
        ReportQuery current = new ReportQuery("day", today, today, zone, teamId, userId, tool);
        LocalDate yesterday = today.minusDays(1);
        ReportQuery previous = new ReportQuery("day", yesterday, yesterday, zone, teamId, userId, tool);
        return new PeriodComparison("day", "Today", "Yesterday", current, previous);
    }

    private static PeriodComparison week(LocalDate today, ZoneId zone, String teamId, String userId, String tool) {
        LocalDate currentStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate previousStart = currentStart.minusWeeks(1);
        long elapsedDays = today.toEpochDay() - currentStart.toEpochDay();
        ReportQuery current = new ReportQuery("week", currentStart, today, zone, teamId, userId, tool);
        ReportQuery previous = new ReportQuery("week", previousStart, previousStart.plusDays(elapsedDays), zone,
                teamId, userId, tool);
        return new PeriodComparison("week", "This Week", "Previous Week", current, previous);
    }

    private static PeriodComparison month(LocalDate today, ZoneId zone, String teamId, String userId, String tool) {
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth previousMonth = currentMonth.minusMonths(1);
        int previousDay = Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth());
        ReportQuery current = new ReportQuery("month", currentMonth.atDay(1), today, zone, teamId, userId, tool);
        ReportQuery previous = new ReportQuery("month", previousMonth.atDay(1), previousMonth.atDay(previousDay), zone,
                teamId, userId, tool);
        return new PeriodComparison("month", "This Month", "Previous Month", current, previous);
    }
}
