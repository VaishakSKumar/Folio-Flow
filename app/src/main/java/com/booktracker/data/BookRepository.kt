package com.booktracker.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.booktracker.ui.library.extractGlowColor
import com.booktracker.ui.library.saveCoverImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class BookRepository(
    private val context: Context,
    private val database: BookDatabase,
) {
    private val books = database.books()
    private val sessions = database.sessions()
    private val notes = database.notes()

    val readingBooks: Flow<List<BookEntity>> = books.observeReading()
    val completedBooks: Flow<List<BookEntity>> = books.observeCompleted()
    val allSessions: Flow<List<ReadingSessionEntity>> = sessions.observeSince(Long.MIN_VALUE)

    fun book(id: Long): Flow<BookEntity?> = books.observe(id)

    fun notesFor(bookId: Long): Flow<List<BookNoteEntity>> = notes.observeForBook(bookId)

    fun sessionsSince(date: LocalDate): Flow<List<ReadingSessionEntity>> =
        sessions.observeSince(date.toEpochDay())

    /** Picked images are copied before the row is written, so a book never points at a lost file. */
    suspend fun addBook(
        title: String,
        author: String,
        totalPages: Int,
        startPage: Int,
        coverUri: Uri?,
    ): Long {
        val path = coverUri?.let { saveCoverImage(context, it) }
        return books.insert(
            BookEntity(
                title = title,
                author = author,
                totalPages = totalPages,
                currentPage = startPage,
                startDate = System.currentTimeMillis(),
                coverPath = path,
                glowColor = path?.let { extractGlowColor(it) },
            ),
        )
    }

    /** One update carries path and colour, so the Hub never shows a new cover with the old glow. */
    suspend fun changeCover(bookId: Long, coverUri: Uri) {
        val old = books.find(bookId)?.coverPath
        val path = saveCoverImage(context, coverUri)
        books.setCover(bookId, path, extractGlowColor(path))
        deleteFile(old)
    }

    suspend fun setPage(bookId: Long, page: Int) {
        val book = books.find(bookId) ?: return
        val clamped = page.coerceIn(0, book.totalPages)
        if (clamped == book.currentPage) return
        database.withTransaction {
            sessions.insert(
                ReadingSessionEntity(
                    bookId = bookId,
                    epochDay = LocalDate.now().toEpochDay(),
                    startPage = book.currentPage,
                    endPage = clamped,
                ),
            )
            books.setCurrentPage(bookId, clamped)
        }
    }

    suspend fun completeBook(bookId: Long) {
        val book = books.find(bookId) ?: return
        val finishDate = System.currentTimeMillis()
        val days = calculateDaysToComplete(book.startDate, finishDate, book.pausedDays)
        books.markCompleted(bookId, finishDate, days)
    }

    /** Undo and Restore to Active. Sessions were never touched, so stats and streaks stay right. */
    suspend fun restoreBook(bookId: Long) {
        books.restore(bookId)
    }

    suspend fun toggleFavorite(bookId: Long) {
        books.toggleFavorite(bookId)
    }

    /** A null [noteId] creates a note. Blank text is ignored, so no empty notes are stored. */
    suspend fun saveNote(noteId: Long?, bookId: Long, kind: NoteKind, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        if (noteId == null) {
            notes.insert(
                BookNoteEntity(bookId = bookId, kind = kind, text = clean, createdAt = now, updatedAt = now),
            )
        } else {
            notes.update(noteId, kind, clean, now)
        }
    }

    suspend fun deleteNote(noteId: Long) {
        notes.delete(noteId)
    }

    suspend fun deleteBook(bookId: Long) {
        val cover = books.find(bookId)?.coverPath
        books.delete(bookId)
        deleteFile(cover)
    }

    private suspend fun deleteFile(path: String?) {
        if (path != null) withContext(Dispatchers.IO) { File(path).delete() }
    }
}
