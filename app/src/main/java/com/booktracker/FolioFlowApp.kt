package com.booktracker

import android.app.Application
import androidx.room.Room
import com.booktracker.data.BookDatabase
import com.booktracker.data.BookRepository
import com.booktracker.data.ReadingSettings

/** The app's only dependency container: one database, repository and settings store. */
class FolioFlowApp : Application() {
    val settings by lazy { ReadingSettings(this) }
    val repository by lazy {
        BookRepository(
            this,
            Room.databaseBuilder(this, BookDatabase::class.java, "folio_flow.db").build(),
        )
    }
}
