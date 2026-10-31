package com.booktracker.ui.stats

import com.booktracker.data.ReadingGoals
import com.booktracker.ui.challenges.ChallengeTier
import com.booktracker.ui.challenges.challengesState
import com.booktracker.ui.challenges.goodWeeksThisYear
import com.booktracker.ui.hub.PacingHealth
import com.booktracker.ui.hub.PacingStatus
import com.booktracker.ui.hub.ReadingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ReadingAnalyticsTest {
    private val today = LocalDate.of(2026, 10, 28) // A Wednesday.

    @Test
    fun correctionInOneBookCannotEatAnotherBooksPages() {
        val entries = listOf(
            ReadingEntry(1, today, 10, 30),
            ReadingEntry(2, today, 50, 40),
        )
        assertEquals(mapOf(today to 20), dailyPages(entries))
    }

    @Test
    fun dayAverageCountsZeroDaysAndComparesWithThePreviousWeek() {
        val daily = mapOf(today to 70, today.minusDays(8) to 14)
        val day = rollingAverages(daily, today).first { it.period == AveragePeriod.Day }
        assertEquals(10f, day.value, 0.001f)
        assertEquals(2f, day.previousValue, 0.001f)
        assertEquals(14, day.buckets.size)
        assertEquals(70, day.buckets.last())
    }

    @Test
    fun weekAndMonthAveragesDivideByTheirFullWindows() {
        val daily = mapOf(today to 40, today.minusDays(40) to 80)
        val averages = rollingAverages(daily, today).associateBy { it.period }
        assertEquals(10f, averages.getValue(AveragePeriod.Week).value, 0.001f)
        assertEquals(20f, averages.getValue(AveragePeriod.Week).previousValue, 0.001f)
        assertEquals(20f, averages.getValue(AveragePeriod.Month).value, 0.001f)
        assertEquals(12, averages.getValue(AveragePeriod.Week).buckets.size)
        assertEquals(12, averages.getValue(AveragePeriod.Month).buckets.size)
    }

    @Test
    fun heatmapStartsOnMondayAndEndsToday() {
        val days = heatmapDays(mapOf(today to 5), today)
        assertEquals(DayOfWeek.MONDAY, days.first().date.dayOfWeek)
        assertEquals(today, days.last().date)
        assertEquals(5, days.last().pages)
        assertEquals((HEATMAP_WEEKS - 1) * 7 + 3, days.size.toLong())
    }

    @Test
    fun onePageIsTinyAndTargetIsSteady() {
        val goals = ReadingGoals(targetPerDay = 20)
        assertEquals(ChallengeTier.Tiny, challengesState(mapOf(today to 1), emptyList(), goals, today).tier)
        assertEquals(ChallengeTier.Steady, challengesState(mapOf(today to 20), emptyList(), goals, today).tier)
        assertEquals(ChallengeTier.Deep, challengesState(mapOf(today to 30), emptyList(), goals, today).tier)
        assertNull(challengesState(emptyMap(), emptyList(), goals, today).tier)
    }

    @Test
    fun weekStripCoversMondayToSunday() {
        val state = challengesState(mapOf(today to 25), emptyList(), ReadingGoals(), today)
        assertEquals(7, state.week.size)
        assertEquals("Monday", state.week.first().name)
    }

    @Test
    fun goodWeekNeedsFiveOfSevenDays() {
        val monday = LocalDate.of(2026, 10, 19)
        val five = (0L..4L).map { monday.plusDays(it) }
        assertEquals(1, goodWeeksThisYear(five, emptySet(), today))
        assertEquals(0, goodWeeksThisYear(five.drop(1), emptySet(), today))
    }

    @Test
    fun busyWeekStopsPacingFromJudging() {
        val slump = listOf(1, 1, 1, 1, 1, 1, 1, 1)
        assertEquals(PacingStatus.Behind, PacingHealth(slump, 25).status)
        assertEquals(PacingStatus.Paused, PacingHealth(slump, 25, busy = true).status)
    }
}
