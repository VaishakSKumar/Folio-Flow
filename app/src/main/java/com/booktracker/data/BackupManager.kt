package com.booktracker.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Where a saved file went, in words. Android doesn't name the folder, so this is the best it gives. */
fun backupDestination(authority: String?): String = when (authority) {
    "com.google.android.apps.docs.storage" -> "Google Drive"
    "com.android.providers.downloads.documents" -> "Downloads"
    "com.android.externalstorage.documents" -> "your phone's storage"
    else -> "the location you chose"
}

class BackupManager(
    private val context: Context,
    private val database: BookDatabase,
    private val settings: ReadingSettings,
) {
    private val covers = File(context.filesDir, "covers")
    private val incomingCovers = File(context.filesDir, "covers-incoming")
    private val safetyCopy = File(context.filesDir, "before-import.zip")
    private val resolver = context.contentResolver

    fun fileName(uri: Uri): String {
        val cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        val name = cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        return name ?: uri.lastPathSegment.orEmpty()
    }

    suspend fun export(target: Uri) {
        val data = snapshot()
        withContext(Dispatchers.IO) {
            // "wt" truncates, so saving over an older backup leaves none of its bytes behind.
            val output = checkNotNull(resolver.openOutputStream(target, "wt")) { "Destination unavailable" }
            output.use { writeBackup(data, covers, it) }
        }
    }

    /** Reads only the manifest, so a wrong file is refused quickly and nothing is unpacked. */
    suspend fun preview(source: Uri): BackupData = withContext(Dispatchers.IO) {
        open(source).use { readBackup(it) }
    }

    /** The current data is saved first, so Undo works and a failed import has lost nothing. */
    suspend fun restore(source: Uri) {
        val current = snapshot()
        withContext(Dispatchers.IO) { safetyCopy.outputStream().use { writeBackup(current, covers, it) } }
        replaceWith(open(source))
    }

    suspend fun undoRestore() {
        replaceWith(safetyCopy.inputStream())
    }

    private fun open(source: Uri): InputStream =
        checkNotNull(resolver.openInputStream(source)) { "File unavailable" }

    private suspend fun snapshot(): BackupData = database.withTransaction {
        BackupData(
            savedAt = System.currentTimeMillis(),
            books = database.books().all().map { it.copy(coverPath = it.coverPath?.let { path -> File(path).name }) },
            sessions = database.sessions().all(),
            notes = database.notes().all(),
            goals = settings.goals.value,
            lastBookId = settings.lastBookId,
        )
    }

    /**
     * Unpacks everything before touching the database, so a damaged file changes nothing. New covers
     * land before the rows that use them, and old ones leave after, so no book points at a lost file.
     */
    private suspend fun replaceWith(input: InputStream) {
        val data = withContext(Dispatchers.IO) {
            incomingCovers.deleteRecursively()
            incomingCovers.mkdirs()
            covers.mkdirs()
            input.use { readBackup(it, incomingCovers) }.also {
                incomingCovers.listFiles().orEmpty().forEach { file -> file.renameTo(File(covers, file.name)) }
            }
        }
        val books = data.books.map { it.copy(coverPath = it.coverPath?.let { name -> File(covers, name).path }) }
        try {
            database.withTransaction {
                database.books().deleteAll() // Sessions and notes go with them through the cascades.
                database.books().insertAll(books)
                database.sessions().insertAll(data.sessions)
                database.notes().insertAll(data.notes)
            }
            settings.replaceWith(data.goals, data.lastBookId)
        } finally {
            removeUnusedCovers()
        }
    }

    private suspend fun removeUnusedCovers() {
        val used = database.books().all().mapNotNull { it.coverPath }.map { File(it).name }.toSet()
        withContext(Dispatchers.IO) {
            incomingCovers.deleteRecursively()
            covers.listFiles().orEmpty().filter { it.name !in used }.forEach { it.delete() }
        }
    }
}
