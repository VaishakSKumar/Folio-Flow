package com.booktracker.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate

const val MAX_REST_DAYS = 2
const val BUSY_WEEK_DAYS = 6L

@Immutable
data class ReadingGoals(
    val targetPerDay: Int = 25,
    val restDays: Set<DayOfWeek> = emptySet(),
    val busyUntil: LocalDate? = null,
) {
    fun isBusy(today: LocalDate): Boolean = busyUntil?.let { !today.isAfter(it) } == true
}

/** Small preferences that aren't records, kept out of Room so they need no schema. */
class ReadingSettings(context: Context) {
    private val prefs = context.getSharedPreferences("reading_settings", Context.MODE_PRIVATE)

    private val _goals = MutableStateFlow(
        ReadingGoals(
            targetPerDay = prefs.getInt(KEY_TARGET, 25),
            restDays = prefs.getStringSet(KEY_REST_DAYS, emptySet()).orEmpty().map(DayOfWeek::valueOf).toSet(),
            busyUntil = prefs.getString(KEY_BUSY_UNTIL, null)?.let(LocalDate::parse),
        ),
    )
    val goals: StateFlow<ReadingGoals> = _goals

    var lastBookId: Long?
        get() = prefs.getLong(KEY_LAST_BOOK, NO_BOOK).takeIf { it != NO_BOOK }
        set(value) = prefs.edit { putLong(KEY_LAST_BOOK, value ?: NO_BOOK) }

    fun setTarget(pagesPerDay: Int) {
        _goals.value = _goals.value.copy(targetPerDay = pagesPerDay)
        prefs.edit { putInt(KEY_TARGET, pagesPerDay) }
    }

    /** Adding is ignored at the limit, because too many rest days would hollow out the streak. */
    fun toggleRestDay(day: DayOfWeek) {
        val current = _goals.value.restDays
        val next = when {
            day in current -> current - day
            current.size < MAX_REST_DAYS -> current + day
            else -> return
        }
        _goals.value = _goals.value.copy(restDays = next)
        prefs.edit { putStringSet(KEY_REST_DAYS, next.map { it.name }.toSet()) }
    }

    fun setBusyUntil(date: LocalDate?) {
        _goals.value = _goals.value.copy(busyUntil = date)
        prefs.edit { putString(KEY_BUSY_UNTIL, date?.toString()) }
    }

    private companion object {
        const val KEY_TARGET = "target_per_day"
        const val KEY_REST_DAYS = "rest_days"
        const val KEY_BUSY_UNTIL = "busy_until"
        const val KEY_LAST_BOOK = "last_book"
        const val NO_BOOK = -1L
    }
}
