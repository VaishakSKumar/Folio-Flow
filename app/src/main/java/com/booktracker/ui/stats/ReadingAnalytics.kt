package com.booktracker.ui.stats

import com.booktracker.ui.hub.ReadingEntry
import com.booktracker.ui.hub.pagesOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private const val DAY_BARS = 14L
private const val WEEK_BARS = 12L
private const val MONTH_BARS = 12L
const val HEATMAP_WEEKS = 53L

/** Pages per day. Each book floors at 0 within a day, as in [pagesOn]. */
fun dailyPages(entries: List<ReadingEntry>): Map<LocalDate, Int> =
    entries.map { it.date }.distinct().associateWith { pagesOn(it, entries) }

/** Days without reading count as zero, otherwise the average would flatter gaps. */
private fun Map<LocalDate, Int>.pagesBetween(first: LocalDate, last: LocalDate): Int =
    entries.filter { !it.key.isBefore(first) && !it.key.isAfter(last) }.sumOf { it.value }

fun weekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Day compares the last 7 days, Week the last 4 weeks and Month the last 6 months. */
fun rollingAverages(daily: Map<LocalDate, Int>, today: LocalDate): List<RollingAverage> {
    fun dayAverage(end: LocalDate) = daily.pagesBetween(end.minusDays(6), end) / 7f
    fun weekAverage(end: LocalDate) = daily.pagesBetween(end.minusDays(27), end) / 4f
    fun monthAverage(end: LocalDate): Float {
        val first = end.minusMonths(6).plusDays(1)
        return daily.pagesBetween(first, end) / 6f
    }

    val thisWeek = weekStart(today)
    val thisMonth = today.withDayOfMonth(1)
    return listOf(
        RollingAverage(
            period = AveragePeriod.Day,
            value = dayAverage(today),
            previousValue = dayAverage(today.minusDays(7)),
            buckets = (DAY_BARS - 1 downTo 0).map { daily[today.minusDays(it)] ?: 0 },
        ),
        RollingAverage(
            period = AveragePeriod.Week,
            value = weekAverage(today),
            previousValue = weekAverage(today.minusDays(28)),
            buckets = (WEEK_BARS - 1 downTo 0).map {
                val start = thisWeek.minusWeeks(it)
                daily.pagesBetween(start, start.plusDays(6))
            },
        ),
        RollingAverage(
            period = AveragePeriod.Month,
            value = monthAverage(today),
            previousValue = monthAverage(today.minusMonths(6)),
            buckets = (MONTH_BARS - 1 downTo 0).map {
                val start = thisMonth.minusMonths(it)
                daily.pagesBetween(start, start.plusMonths(1).minusDays(1))
            },
        ),
    )
}

/** Monday-first full weeks up to [today], ready for the grid, zero days included. */
fun heatmapDays(daily: Map<LocalDate, Int>, today: LocalDate): List<DayCount> {
    val first = weekStart(today).minusWeeks(HEATMAP_WEEKS - 1)
    return generateSequence(first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { DayCount(it, daily[it] ?: 0) }
        .toList()
}

