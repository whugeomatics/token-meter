package local.token.meter.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PeriodComparisonTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void dayComparesTodayWithYesterday() {
        PeriodComparison comparison = PeriodComparison.previous("day", LocalDate.of(2026, 5, 21), ZONE, "", "");

        assertEquals(LocalDate.of(2026, 5, 21), comparison.current().startDate());
        assertEquals(LocalDate.of(2026, 5, 21), comparison.current().endDate());
        assertEquals(LocalDate.of(2026, 5, 20), comparison.previous().startDate());
        assertEquals(LocalDate.of(2026, 5, 20), comparison.previous().endDate());
    }

    @Test
    void weekComparesWeekToDateWithPreviousWeekSameElapsedDays() {
        PeriodComparison comparison = PeriodComparison.previous("week", LocalDate.of(2026, 5, 21), ZONE, "", "");

        assertEquals(LocalDate.of(2026, 5, 18), comparison.current().startDate());
        assertEquals(LocalDate.of(2026, 5, 21), comparison.current().endDate());
        assertEquals(LocalDate.of(2026, 5, 11), comparison.previous().startDate());
        assertEquals(LocalDate.of(2026, 5, 14), comparison.previous().endDate());
    }

    @Test
    void monthComparesMonthToDateWithPreviousMonthSameDayCappedAtMonthEnd() {
        PeriodComparison comparison = PeriodComparison.previous("month", LocalDate.of(2026, 3, 31), ZONE, "", "");

        assertEquals(LocalDate.of(2026, 3, 1), comparison.current().startDate());
        assertEquals(LocalDate.of(2026, 3, 31), comparison.current().endDate());
        assertEquals(LocalDate.of(2026, 2, 1), comparison.previous().startDate());
        assertEquals(LocalDate.of(2026, 2, 28), comparison.previous().endDate());
    }
}
