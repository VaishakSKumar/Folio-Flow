package com.booktracker.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val FORMAT = "folio-flow-backup"
private const val VERSION = 1
private const val MANIFEST = "backup.json"
private const val COVER_FOLDER = "covers/"

/** A cover is one file name, so a crafted backup can't write outside the covers folder. */
private val SafeCoverName = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*")

/** [BookEntity.coverPath] holds a bare file name here, because full paths differ between installs. */
data class BackupData(
    val savedAt: Long,
    val books: List<BookEntity>,
    val sessions: List<ReadingSessionEntity>,
    val notes: List<BookNoteEntity>,
    val goals: ReadingGoals,
    val lastBookId: Long?,
)

/** [tooNew] means a real backup from a later version of the app, not a wrong file. */
class BackupFormatException(val tooNew: Boolean = false, cause: Throwable? = null) : Exception(cause)

/** The manifest goes first, so a preview needs only the start of the file. */
fun writeBackup(data: BackupData, coverFolder: File, output: OutputStream) {
    val zip = ZipOutputStream(output)
    zip.putNextEntry(ZipEntry(MANIFEST))
    zip.write(encodeBackup(data).toByteArray())
    zip.closeEntry()
    data.books.mapNotNull { it.coverPath }.distinct().forEach { name ->
        val cover = File(coverFolder, name)
        if (cover.isFile) {
            zip.putNextEntry(ZipEntry(COVER_FOLDER + name))
            cover.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    zip.finish()
}

/**
 * Reads a backup, and copies its covers into [coverFolder] when one is given. A book whose cover is
 * missing from the file falls back to the leather binding instead of failing the whole import.
 */
fun readBackup(input: InputStream, coverFolder: File? = null): BackupData {
    val zip = ZipInputStream(input)
    try {
        if (zip.nextEntry?.name != MANIFEST) throw BackupFormatException()
        val data = decodeBackup(zip.readBytes().decodeToString())
        if (coverFolder == null) return data
        val wanted = data.books.mapNotNull { it.coverPath }.toSet()
        val found = mutableSetOf<String>()
        generateSequence { zip.nextEntry }.forEach { entry ->
            val name = entry.name.removePrefix(COVER_FOLDER)
            if (name in wanted) {
                File(coverFolder, name).outputStream().use { zip.copyTo(it) }
                found += name
            }
        }
        return data.copy(
            books = data.books.map {
                if (it.coverPath == null || it.coverPath in found) it else it.copy(coverPath = null, glowColor = null)
            },
        )
    } catch (e: ZipException) {
        throw BackupFormatException(cause = e)
    }
}

fun encodeBackup(data: BackupData): String = JSONObject()
    .put("format", FORMAT)
    .put("version", VERSION)
    .put("savedAt", data.savedAt)
    .put("books", JSONArray(data.books.map(::encodeBook)))
    .put("sessions", JSONArray(data.sessions.map(::encodeSession)))
    .put("notes", JSONArray(data.notes.map(::encodeNote)))
    .put(
        "goals",
        JSONObject()
            .put("targetPerDay", data.goals.targetPerDay)
            .put("restDays", JSONArray(data.goals.restDays.map { it.name }))
            .put("busyUntil", data.goals.busyUntil?.toString() ?: JSONObject.NULL),
    )
    .put("lastBookId", data.lastBookId ?: JSONObject.NULL)
    .toString()

fun decodeBackup(text: String): BackupData {
    try {
        val root = JSONObject(text)
        if (root.optString("format") != FORMAT) throw BackupFormatException()
        if (root.getInt("version") > VERSION) throw BackupFormatException(tooNew = true)
        val goals = root.getJSONObject("goals")
        val data = BackupData(
            savedAt = root.getLong("savedAt"),
            books = root.getJSONArray("books").objects().map(::decodeBook),
            sessions = root.getJSONArray("sessions").objects().map(::decodeSession),
            notes = root.getJSONArray("notes").objects().map(::decodeNote),
            goals = ReadingGoals(
                targetPerDay = goals.getInt("targetPerDay"),
                restDays = goals.getJSONArray("restDays").strings().map(DayOfWeek::valueOf).toSet(),
                busyUntil = goals.stringOrNull("busyUntil")?.let(LocalDate::parse),
            ),
            lastBookId = root.longOrNull("lastBookId"),
        )
        validate(data)
        return data
    } catch (e: JSONException) {
        throw BackupFormatException(cause = e)
    } catch (e: IllegalArgumentException) {
        throw BackupFormatException(cause = e) // An unknown status, note kind or weekday.
    } catch (e: DateTimeParseException) {
        throw BackupFormatException(cause = e)
    }
}

/** Checked here so a bad file is refused before the app's own data is touched. */
private fun validate(data: BackupData) {
    val ids = data.books.map { it.id }.toSet()
    val valid = ids.size == data.books.size &&
        data.sessions.all { it.bookId in ids } &&
        data.notes.all { it.bookId in ids } &&
        data.books.all { book -> book.coverPath?.matches(SafeCoverName) != false }
    if (!valid) throw BackupFormatException()
}

private fun encodeBook(book: BookEntity) = JSONObject()
    .put("id", book.id)
    .put("title", book.title)
    .put("author", book.author)
    .put("totalPages", book.totalPages)
    .put("currentPage", book.currentPage)
    .put("status", book.status.name)
    .put("startDate", book.startDate)
    .put("finishDate", book.finishDate ?: JSONObject.NULL)
    .put("daysToComplete", book.daysToComplete ?: JSONObject.NULL)
    .put("pausedDays", book.pausedDays)
    .put("pageBeforeCompletion", book.pageBeforeCompletion ?: JSONObject.NULL)
    .put("isFavorite", book.isFavorite)
    .put("coverPath", book.coverPath ?: JSONObject.NULL)
    .put("glowColor", book.glowColor ?: JSONObject.NULL)

private fun decodeBook(json: JSONObject) = BookEntity(
    id = json.getLong("id"),
    title = json.getString("title"),
    author = json.getString("author"),
    totalPages = json.getInt("totalPages"),
    currentPage = json.getInt("currentPage"),
    status = BookStatus.valueOf(json.getString("status")),
    startDate = json.getLong("startDate"),
    finishDate = json.longOrNull("finishDate"),
    daysToComplete = json.intOrNull("daysToComplete"),
    pausedDays = json.getInt("pausedDays"),
    pageBeforeCompletion = json.intOrNull("pageBeforeCompletion"),
    isFavorite = json.getBoolean("isFavorite"),
    coverPath = json.stringOrNull("coverPath"),
    glowColor = json.intOrNull("glowColor"),
)

private fun encodeSession(session: ReadingSessionEntity) = JSONObject()
    .put("id", session.id)
    .put("bookId", session.bookId)
    .put("epochDay", session.epochDay)
    .put("startPage", session.startPage)
    .put("endPage", session.endPage)

private fun decodeSession(json: JSONObject) = ReadingSessionEntity(
    id = json.getLong("id"),
    bookId = json.getLong("bookId"),
    epochDay = json.getLong("epochDay"),
    startPage = json.getInt("startPage"),
    endPage = json.getInt("endPage"),
)

private fun encodeNote(note: BookNoteEntity) = JSONObject()
    .put("id", note.id)
    .put("bookId", note.bookId)
    .put("kind", note.kind.name)
    .put("text", note.text)
    .put("createdAt", note.createdAt)
    .put("updatedAt", note.updatedAt)

private fun decodeNote(json: JSONObject) = BookNoteEntity(
    id = json.getLong("id"),
    bookId = json.getLong("bookId"),
    kind = NoteKind.valueOf(json.getString("kind")),
    text = json.getString("text"),
    createdAt = json.getLong("createdAt"),
    updatedAt = json.getLong("updatedAt"),
)

private fun JSONArray.objects() = List(length(), ::getJSONObject)
private fun JSONArray.strings() = List(length(), ::getString)
private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)
private fun JSONObject.intOrNull(key: String) = if (isNull(key)) null else getInt(key)
