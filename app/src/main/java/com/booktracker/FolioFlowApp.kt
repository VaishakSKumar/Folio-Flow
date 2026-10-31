package com.booktracker

import android.app.Application
import androidx.room.Room
import com.booktracker.data.BackupManager
import com.booktracker.data.BookDatabase
import com.booktracker.data.BookRepository
import com.booktracker.data.ReadingSettings

/** The app's only dependency container: one database, repository and settings store. */
class FolioFlowApp : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, BookDatabase::class.java, "folio_flow.db").build()
    }
    val settings by lazy { ReadingSettings(this) }
    val repository by lazy { BookRepository(this, database) }
    val backup by lazy { BackupManager(this, database, settings) }
}
