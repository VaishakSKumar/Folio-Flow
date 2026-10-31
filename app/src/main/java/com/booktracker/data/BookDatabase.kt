package com.booktracker.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE status = 'READING' ORDER BY startDate DESC")
    fun observeReading(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE status = 'COMPLETED' ORDER BY finishDate DESC")
    fun observeCompleted(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun find(id: Long): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun all(): List<BookEntity>

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Insert
    suspend fun insertAll(books: List<BookEntity>)

    @Query("UPDATE books SET currentPage = :page WHERE id = :id")
    suspend fun setCurrentPage(id: Long, page: Int)

    @Query("UPDATE books SET coverPath = :path, glowColor = :glowColor WHERE id = :id")
    suspend fun setCover(id: Long, path: String?, glowColor: Int?)

    @Query(
        """
        UPDATE books
        SET status = 'COMPLETED', finishDate = :finishDate, daysToComplete = :daysToComplete,
            pageBeforeCompletion = currentPage, currentPage = totalPages
        WHERE id = :id AND status = 'READING'
        """,
    )
    suspend fun markCompleted(id: Long, finishDate: Long, daysToComplete: Int)

    @Query(
        """
        UPDATE books
        SET status = 'READING', currentPage = COALESCE(pageBeforeCompletion, currentPage),
            finishDate = NULL, daysToComplete = NULL, pageBeforeCompletion = NULL
        WHERE id = :id AND status = 'COMPLETED'
        """,
    )
    suspend fun restore(id: Long)

    @Query("UPDATE books SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    /** Its sessions and notes go too, through their foreign key cascades. */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM books")
    suspend fun deleteAll()
}

@Dao
interface ReadingSessionDao {
    @Insert
    suspend fun insert(session: ReadingSessionEntity): Long

    @Insert
    suspend fun insertAll(sessions: List<ReadingSessionEntity>)

    @Query("SELECT * FROM reading_sessions")
    suspend fun all(): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions WHERE epochDay >= :fromEpochDay ORDER BY epochDay")
    fun observeSince(fromEpochDay: Long): Flow<List<ReadingSessionEntity>>
}

@Dao
interface BookNoteDao {
    @Query("SELECT * FROM book_notes WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeForBook(bookId: Long): Flow<List<BookNoteEntity>>

    @Insert
    suspend fun insert(note: BookNoteEntity): Long

    @Insert
    suspend fun insertAll(notes: List<BookNoteEntity>)

    @Query("SELECT * FROM book_notes")
    suspend fun all(): List<BookNoteEntity>

    @Query("UPDATE book_notes SET kind = :kind, text = :text, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: Long, kind: NoteKind, text: String, updatedAt: Long)

    @Query("DELETE FROM book_notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [BookEntity::class, ReadingSessionEntity::class, BookNoteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun sessions(): ReadingSessionDao
    abstract fun notes(): BookNoteDao
}
