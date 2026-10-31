package com.booktracker.ui.hub

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booktracker.data.BookEntity
import com.booktracker.data.BookNoteEntity
import com.booktracker.data.BookRepository
import com.booktracker.data.NoteKind
import com.booktracker.data.ReadingSessionEntity
import com.booktracker.data.ReadingSettings
import com.booktracker.data.calculateDaysToComplete
import com.booktracker.ui.library.BookDetail
import com.booktracker.ui.library.NewBook
import com.booktracker.ui.stats.FinishedBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Room is the single source of truth: every action writes there and the screen follows. */
class ActiveBooksViewModel(
    private val repository: BookRepository,
    private val settings: ReadingSettings,
) : ViewModel() {

    /** The book the Hub opens on. Adding a book sets it, so the deck returns with that book in front. */
    val lastBookId = MutableStateFlow(settings.lastBookId)

    // Eager, so a book added on another screen is in the list before the Hub comes back.
    val books: StateFlow<List<ActiveBook>> = combine(
        repository.readingBooks,
        repository.sessionsSince(LocalDate.now().minusDays(14)),
    ) { books, sessions ->
        val entries = sessions.map(ReadingSessionEntity::toEntry)
        books.map { it.toActiveBook(entries) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val finishedBooks: StateFlow<List<FinishedBook>> = repository.completedBooks
        .map { books -> books.map(BookEntity::toFinishedBook) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bookDetail(bookId: Long): Flow<BookDetail?> =
        repository.book(bookId).map { it?.toBookDetail() }

    fun notes(bookId: Long): Flow<List<BookNoteEntity>> = repository.notesFor(bookId)

    fun showBook(bookId: Long) {
        lastBookId.value = bookId
        settings.lastBookId = bookId
    }

    suspend fun addBook(book: NewBook): Long {
        val id = repository.addBook(
            title = book.title,
            author = book.author,
            totalPages = book.totalPages,
            startPage = book.currentPage,
            coverUri = book.coverUri?.let(Uri::parse),
        )
        showBook(id)
        return id
    }

    fun setPage(bookId: Long, page: Int) {
        viewModelScope.launch { repository.setPage(bookId, page) }
    }

    fun changeCover(bookId: Long, coverUri: Uri) {
        viewModelScope.launch { repository.changeCover(bookId, coverUri) }
    }

    fun completeBook(bookId: Long) {
        viewModelScope.launch { repository.completeBook(bookId) }
    }

    fun restoreBook(bookId: Long) {
        viewModelScope.launch { repository.restoreBook(bookId) }
    }

    fun toggleFavorite(bookId: Long) {
        viewModelScope.launch { repository.toggleFavorite(bookId) }
    }

    fun saveNote(noteId: Long?, bookId: Long, kind: NoteKind, text: String) {
        viewModelScope.launch { repository.saveNote(noteId, bookId, kind, text) }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch { repository.deleteNote(noteId) }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch { repository.deleteBook(bookId) }
    }
}

private fun ReadingSessionEntity.toEntry() = ReadingEntry(
    bookId = bookId,
    date = LocalDate.ofEpochDay(epochDay),
    startPage = startPage,
    endPage = endPage,
)

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun BookEntity.toFinishedBook(): FinishedBook {
    val finishDate = checkNotNull(finishDate) { "Completed book $id has no finish date" }
    return FinishedBook(
        id = id,
        title = title,
        author = author,
        started = startDate.toLocalDate(),
        finished = finishDate.toLocalDate(),
        daysToComplete = daysToComplete ?: calculateDaysToComplete(startDate, finishDate, pausedDays),
        favorite = isFavorite,
        coverPath = coverPath,
    )
}

private fun BookEntity.toBookDetail() = BookDetail(
    id = id,
    title = title,
    author = author,
    coverPath = coverPath,
    currentPage = currentPage,
    totalPages = totalPages,
    started = startDate.toLocalDate(),
    finished = finishDate?.toLocalDate(),
    daysToComplete = daysToComplete,
    restorePage = pageBeforeCompletion,
)

private fun BookEntity.toActiveBook(entries: List<ReadingEntry>) = ActiveBook(
    id = id,
    title = title,
    author = author,
    currentPage = currentPage,
    totalPages = totalPages,
    pagesPerDay = recentPagesPerDay(id, entries, LocalDate.now()),
    coverPath = coverPath,
    glowColor = glowColor,
)
