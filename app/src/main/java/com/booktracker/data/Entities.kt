package com.booktracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Room stores these by name, so the column holds "READING" or "COMPLETED". */
enum class BookStatus { READING, COMPLETED }

enum class NoteKind { NOTE, TAKEAWAY, QUOTE }

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val totalPages: Int,
    val currentPage: Int = 0,
    val status: BookStatus = BookStatus.READING,
    val startDate: Long,
    val finishDate: Long? = null,
    /** Fixed at completion, so a later timezone change can't alter a finished book's badge. */
    val daysToComplete: Int? = null,
    val pausedDays: Int = 0,
    /** Completion moves [currentPage] to the end, so this is what Undo and Restore return to. */
    val pageBeforeCompletion: Int? = null,
    val isFavorite: Boolean = false,
    val coverPath: String? = null,
    val glowColor: Int? = null,
)

/** The cascade removes a book's sessions with it, so no session can outlive its book. */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index("epochDay")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val epochDay: Long,
    val startPage: Int,
    val endPage: Int,
)

/** Kept in its own table so a book can hold many notes, and cascades away with its book. */
@Entity(
    tableName = "book_notes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class BookNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val kind: NoteKind,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Calendar days from start to finish, counting both, less any on-hold days. Timestamps are compared
 * as local dates: raw milliseconds would count a book finished 2 hours after midnight as 0 days.
 */
fun calculateDaysToComplete(
    startDate: Long,
    finishDate: Long,
    pausedDays: Int = 0,
    zone: ZoneId = ZoneId.systemDefault(),
): Int {
    val start = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
    val finish = Instant.ofEpochMilli(finishDate).atZone(zone).toLocalDate()
    return (ChronoUnit.DAYS.between(start, finish).toInt() + 1 - pausedDays).coerceAtLeast(1)
}
