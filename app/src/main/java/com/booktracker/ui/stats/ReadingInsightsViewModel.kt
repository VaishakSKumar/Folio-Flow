package com.booktracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booktracker.data.BookEntity
import com.booktracker.data.BookRepository
import com.booktracker.data.BUSY_WEEK_DAYS
import com.booktracker.data.ReadingGoals
import com.booktracker.data.ReadingSessionEntity
import com.booktracker.data.ReadingSettings
import com.booktracker.ui.challenges.ChallengesUiState
import com.booktracker.ui.challenges.challengeLine
import com.booktracker.ui.challenges.challengesState
import com.booktracker.ui.challenges.evaluateStreak
import com.booktracker.ui.challenges.DayLog
import com.booktracker.ui.hub.PacingHealth
import com.booktracker.ui.hub.ReadingEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReadingInsights(
    val averages: List<RollingAverage>,
    val days: List<DayCount>,
    val goals: ReadingGoals,
    val pacing: PacingHealth,
    val challenges: ChallengesUiState,
    val challengeLine: String,
    val bestStreak: Int,
)

/** Every figure is derived from the logged sessions, so nothing can drift from the real history. */
class ReadingInsightsViewModel(
    repository: BookRepository,
    private val settings: ReadingSettings,
) : ViewModel() {

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    val goals: StateFlow<ReadingGoals> = settings.goals

    val insights: StateFlow<ReadingInsights> = combine(
        repository.allSessions,
        repository.completedBooks,
        settings.goals,
        _today,
    ) { sessions, completed, goals, today ->
        insightsFrom(sessions, completed, goals, today)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        insightsFrom(emptyList(), emptyList(), settings.goals.value, LocalDate.now()),
    )

    /** Call on resume, so an app left open overnight moves on to the new day. */
    fun refreshDay() {
        _today.value = LocalDate.now()
    }

    fun setTarget(pagesPerDay: Int) = settings.setTarget(pagesPerDay)

    fun toggleRestDay(day: DayOfWeek) = settings.toggleRestDay(day)

    fun setBusy(busy: Boolean) {
        settings.setBusyUntil(if (busy) LocalDate.now().plusDays(BUSY_WEEK_DAYS) else null)
    }
}

internal fun insightsFrom(
    sessions: List<ReadingSessionEntity>,
    completed: List<BookEntity>,
    goals: ReadingGoals,
    today: LocalDate,
): ReadingInsights {
    val entries = sessions.map {
        ReadingEntry(it.bookId, LocalDate.ofEpochDay(it.epochDay), it.startPage, it.endPage)
    }
    val daily = dailyPages(entries)
    val finishDates = completed.mapNotNull { book ->
        book.finishDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    }
    val challenges = challengesState(daily, finishDates, goals, today)
    val firstDay = daily.keys.minOrNull() ?: today
    val streak = evaluateStreak(
        firstDay,
        today,
        daily.mapValues { DayLog(it.value) },
        goals.restDays,
        goals.targetPerDay,
    )
    return ReadingInsights(
        averages = rollingAverages(daily, today),
        days = heatmapDays(daily, today),
        goals = goals,
        pacing = PacingHealth(
            lastEightDays = (7L downTo 0L).map { daily[today.minusDays(it)] ?: 0 },
            targetPerDay = goals.targetPerDay,
            busy = goals.isBusy(today),
        ),
        challenges = challenges,
        challengeLine = challengeLine(challenges, daily[today] ?: 0, goals.targetPerDay),
        bestStreak = streak.best,
    )
}
