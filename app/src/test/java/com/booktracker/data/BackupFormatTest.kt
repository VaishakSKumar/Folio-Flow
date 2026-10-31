package com.booktracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupFormatTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val finished = BookEntity(
        id = 4,
        title = "Emma \"the\" novel",
        author = "Jane Austen",
        totalPages = 300,
        currentPage = 300,
        status = BookStatus.COMPLETED,
        startDate = 1_000L,
        finishDate = 2_000L,
        daysToComplete = 12,
        pausedDays = 1,
        pageBeforeCompletion = 280,
        isFavorite = true,
        coverPath = "cover-1.jpg",
        glowColor = -123456,
    )
    private val reading = BookEntity(id = 9, title = "Kim", author = "", totalPages = 120, startDate = 3_000L)
    private val data = BackupData(
        savedAt = 5_000L,
        books = listOf(finished, reading),
        sessions = listOf(ReadingSessionEntity(7, 9, 20_000L, 0, 10)),
        notes = listOf(BookNoteEntity(3, 4, NoteKind.QUOTE, "Line one\nline \"two\"", 10L, 20L)),
        goals = ReadingGoals(30, setOf(DayOfWeek.SUNDAY), LocalDate.of(2026, 11, 2)),
        lastBookId = 9,
    )

    private fun zipOf(data: BackupData): ByteArray {
        val covers = folder.newFolder().also { it.resolve("cover-1.jpg").writeText("image") }
        return ByteArrayOutputStream().also { writeBackup(data, covers, it) }.toByteArray()
    }

    private fun bytesOf(entry: String, text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use {
            it.putNextEntry(ZipEntry(entry))
            it.write(text.toByteArray())
        }
        return bytes.toByteArray()
    }

    @Test
    fun everyFieldSurvivesARoundTrip() {
        assertEquals(data, decodeBackup(encodeBackup(data)))
    }

    @Test
    fun coversAreRestoredFromTheZip() {
        val target = folder.newFolder()
        val read = readBackup(ByteArrayInputStream(zipOf(data)), target)
        assertEquals(data, read)
        assertEquals("image", target.resolve("cover-1.jpg").readText())
    }

    @Test
    fun previewUnpacksNothing() {
        assertEquals(data, readBackup(ByteArrayInputStream(zipOf(data))))
    }

    @Test
    fun aMissingCoverFallsBackToTheBinding() {
        val noCover = ByteArrayOutputStream().also { writeBackup(data, folder.newFolder(), it) }.toByteArray()
        val book = readBackup(ByteArrayInputStream(noCover), folder.newFolder()).books.first()
        assertNull(book.coverPath)
        assertNull(book.glowColor)
    }

    @Test
    fun filesThatAreNotZipsAreRefused() {
        val error = assertThrows(BackupFormatException::class.java) {
            readBackup(ByteArrayInputStream("%PDF-1.7 not a backup".toByteArray()))
        }
        assertFalse(error.tooNew)
    }

    @Test
    fun otherZipsAreRefused() {
        assertThrows(BackupFormatException::class.java) {
            readBackup(ByteArrayInputStream(bytesOf("photo.jpg", "image")))
        }
        assertThrows(BackupFormatException::class.java) {
            readBackup(ByteArrayInputStream(bytesOf("backup.json", "{\"format\":\"other\"}")))
        }
    }

    @Test
    fun aDamagedManifestIsRefused() {
        assertThrows(BackupFormatException::class.java) { decodeBackup("{\"format\":") }
    }

    @Test
    fun laterVersionsAskForAnUpdate() {
        val later = encodeBackup(data).replace("\"version\":1", "\"version\":2")
        val error = assertThrows(BackupFormatException::class.java) { decodeBackup(later) }
        assertTrue(error.tooNew)
    }

    @Test
    fun sessionsWithoutABookAreRefused() {
        val orphan = data.copy(sessions = listOf(ReadingSessionEntity(1, 99, 20_000L, 0, 1)))
        assertThrows(BackupFormatException::class.java) { decodeBackup(encodeBackup(orphan)) }
    }

    @Test
    fun coverNamesCannotLeaveTheFolder() {
        val escaping = data.copy(books = listOf(finished.copy(coverPath = "../../databases/folio_flow.db")))
        assertThrows(BackupFormatException::class.java) { decodeBackup(encodeBackup(escaping)) }
    }

    @Test
    fun destinationsAreNamedWhenAndroidReportsThem() {
        assertEquals("Google Drive", backupDestination("com.google.android.apps.docs.storage"))
        assertEquals("the location you chose", backupDestination("com.example.cloud"))
    }
}
