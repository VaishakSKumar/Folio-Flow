package com.booktracker.ui.challenges

import com.booktracker.data.ReadingGoals
import com.booktracker.ui.stats.weekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

private val ChallengeTitles = listOf(
    "Read before work",
    "Finish a chapter",
    "Ten quiet minutes with a book",
    "Read somewhere new",
    "One more page at bedtime",
    "Pick it up at lunch",
    "Read instead of scrolling",
)

private fun seasonOf(date: LocalDate): Int = date.monthValue % 12 / 3

fun deepTarget(targetPerDay: Int): Int = ceil(targetPerDay * 1.5).toInt()

fun plannedDays(restDays: Set<DayOfWeek>): Int = 7 - restDays.size

private fun List<LocalDate>.readDaysIn(first: LocalDate, last: LocalDate): Int =
    count { !it.isBefore(first) && !it.isAfter(last) }

/**
 * Good weeks in [today]'s year. The current week counts as soon as it qualifies, and weeks are
 * placed in the year of their Monday so that none is counted twice.
 */
fun goodWeeksThisYear(readDays: List<LocalDate>, restDays: Set<DayOfWeek>, today: LocalDate): Int {
    val goal = (plannedDays(restDays) - 2).coerceAtLeast(1)
    return readDays
        .filter { weekStart(it).year == today.year }
        .groupBy(::weekStart)
        .count { (_, days) -> days.size >= goal }
}

/** Everything the Challenges screen shows, from the daily page totals and the reader's goals. */
fun challengesState(
    pagesByDay: Map<LocalDate, Int>,
    finishDates: List<LocalDate>,
    goals: ReadingGoals,
    today: LocalDate,
): ChallengesUiState {
    val target = goals.targetPerDay
    val logs = pagesByDay.mapValues { DayLog(it.value) }
    val firstDay = logs.keys.minOrNull() ?: today
    val streak = evaluateStreak(firstDay, today, logs, goals.restDays, target)
    val pagesToday = pagesByDay[today] ?: 0
    val readDays = logs.filterValues { earnedTier(it, target) != null }.keys.toList()

    val monday = weekStart(today)
    val sunday = monday.plusDays(6)
    val week = (0L..6L).map { offset ->
        val date = monday.plusDays(offset)
        val name = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val state = when (streak.outcomes[date]) {
            DayOutcome.Read -> WeekDayState.Read
            DayOutcome.Rest -> WeekDayState.Rest
            DayOutcome.Saved -> WeekDayState.Saved
            DayOutcome.Missed -> WeekDayState.Missed
            null -> WeekDayState.Upcoming
        }
        WeekDay(name.take(1), name, state)
    }

    val thisYear = finishDates.filter { it.year == today.year }
    return ChallengesUiState(
        streak = streak.current,
        bookmarks = streak.bookmarks,
        challengeTitle = ChallengeTitles[(today.dayOfYear - 1) % ChallengeTitles.size],
        tier = earnedTier(DayLog(pagesToday), target),
        tinyHint = "1 page",
        steadyHint = "$pagesToday of $target pages",
        deepHint = "$pagesToday of ${deepTarget(target)} pages",
        busy = goals.isBusy(today),
        week = week,
        weekMilestones = weekMilestones(
            readDays = readDays.readDaysIn(monday, sunday),
            plannedDays = plannedDays(goals.restDays),
            deepDays = logs.count { (date, log) ->
                !date.isBefore(monday) && !date.isAfter(sunday) &&
                    earnedTier(log, target) == ChallengeTier.Deep
            },
        ),
        yearMilestones = yearMilestones(
            goodWeeks = goodWeeksThisYear(readDays, goals.restDays, today),
            booksFinished = thisYear.size,
            pages = pagesByDay.filterKeys { it.year == today.year }.values.sum(),
            seasons = thisYear.map(::seasonOf).distinct().size,
        ),
    )
}

/** The Hub's one-line summary of today's challenge. */
fun challengeLine(state: ChallengesUiState, pagesToday: Int, targetPerDay: Int): String = when {
    state.tier == ChallengeTier.Deep -> "Deep reached"
    state.tier == ChallengeTier.Steady -> "Steady reached"
    state.tier == ChallengeTier.Tiny && !state.busy -> "Tiny done · ${targetPerDay - pagesToday} pages to Steady"
    state.tier == ChallengeTier.Tiny -> "Tiny done"
    else -> "Read 1 page to keep your streak"
}
