package com.booktracker.ui.challenges

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate

/** Any tier keeps the streak; higher tiers only earn a better badge for the day. */
enum class ChallengeTier(val label: String) {
    Tiny("Tiny"),
    Steady("Steady"),
    Deep("Deep"),
}

@Immutable
data class DayLog(val pages: Int)

/** Tiny is fixed so a hard day is always winnable, whatever the user's goal is. */
fun earnedTier(log: DayLog, targetPerDay: Int): ChallengeTier? = when {
    log.pages < 1 -> null
    targetPerDay <= 0 -> ChallengeTier.Tiny
    log.pages * 2 >= targetPerDay * 3 -> ChallengeTier.Deep
    log.pages >= targetPerDay -> ChallengeTier.Steady
    else -> ChallengeTier.Tiny
}

enum class DayOutcome { Read, Rest, Saved, Missed }

@Immutable
data class StreakResult(
    val current: Int,
    val best: Int,
    val bookmarks: Int,
    val outcomes: Map<LocalDate, DayOutcome>,
)

const val MAX_BOOKMARKS = 3
const val DAYS_PER_BOOKMARK = 7

/**
 * Replays every closed day from [from] up to [today].
 *
 * Read days extend the streak. Rest days neither extend nor break it. A missed day spends a
 * bookmark if one is held and otherwise resets the streak. Today only counts once read, so an
 * unfinished evening never shows as a break.
 */
fun evaluateStreak(
    from: LocalDate,
    today: LocalDate,
    logs: Map<LocalDate, DayLog>,
    restDays: Set<DayOfWeek>,
    targetPerDay: Int,
): StreakResult {
    var current = 0
    var best = 0
    var bookmarks = 0
    var readsTowardBookmark = 0
    val outcomes = linkedMapOf<LocalDate, DayOutcome>()

    var date = from
    while (!date.isAfter(today)) {
        val read = logs[date]?.let { earnedTier(it, targetPerDay) } != null
        when {
            read -> {
                outcomes[date] = DayOutcome.Read
                current++
                best = maxOf(best, current)
                readsTowardBookmark++
                if (readsTowardBookmark == DAYS_PER_BOOKMARK) {
                    readsTowardBookmark = 0
                    bookmarks = minOf(bookmarks + 1, MAX_BOOKMARKS)
                }
            }
            date == today -> Unit
            date.dayOfWeek in restDays -> outcomes[date] = DayOutcome.Rest
            bookmarks > 0 -> {
                bookmarks--
                outcomes[date] = DayOutcome.Saved
            }
            else -> {
                outcomes[date] = DayOutcome.Missed
                current = 0
                readsTowardBookmark = 0
            }
        }
        date = date.plusDays(1)
    }
    return StreakResult(current, best, bookmarks, outcomes)
}

enum class NudgeLevel(val label: String, val description: String) {
    Gentle("Gentle", "One reminder at your time."),
    Firm("Firm", "A second reminder before bed."),
    Commitment("Commitment", "Repeats 3 times, shows your reason, stops once you read."),
}

/** The reasons shown in reminders are the user's own words, so nudges stay personal. */
@Immutable
data class HabitPlan(
    val reason: String,
    val reminderMinuteOfDay: Int,
    val nudgeLevel: NudgeLevel,
    val restDays: Set<DayOfWeek>,
    val busyUntil: LocalDate?,
) {
    fun isBusy(today: LocalDate): Boolean = busyUntil?.let { !today.isAfter(it) } == true
}

/** After this many ignored days reminders stop pressing and ask if the plan still fits. */
const val IGNORED_DAYS_BEFORE_BACKOFF = 3

fun remindersToday(plan: HabitPlan, readToday: Boolean, ignoredDays: Int): Int = when {
    readToday -> 0
    ignoredDays >= IGNORED_DAYS_BEFORE_BACKOFF -> 1
    plan.isBusy(LocalDate.now()) -> 1
    else -> when (plan.nudgeLevel) {
        NudgeLevel.Gentle -> 1
        NudgeLevel.Firm -> 2
        NudgeLevel.Commitment -> 3
    }
}

@Immutable
data class Milestone(val title: String, val detail: String, val progress: Int, val goal: Int) {
    val earned: Boolean get() = progress >= goal
}

/** Week badges are measured against planned days, so a rest day never costs a badge. */
fun weekMilestones(readDays: Int, plannedDays: Int, deepDays: Int): List<Milestone> = listOf(
    Milestone("Good week", "Read on most of your planned days", readDays, (plannedDays - 2).coerceAtLeast(1)),
    Milestone("Full week", "Read on every planned day", readDays, plannedDays),
    Milestone("Three deep reads", "Reach the Deep tier 3 times", deepDays, 3),
)

/** Year badges allow for bad months: 40 of 52 weeks, never all of them. */
fun yearMilestones(goodWeeks: Int, booksFinished: Int, pages: Int, seasons: Int): List<Milestone> = listOf(
    Milestone("Forty weeks", "Earn Good week 40 times", goodWeeks, 40),
    Milestone("Twelve books", "Finish 12 books this year", booksFinished, 12),
    Milestone("Ten thousand pages", "Read 10,000 pages this year", pages, 10_000),
    Milestone("Four seasons", "Finish a book in every season", seasons, 4),
)
