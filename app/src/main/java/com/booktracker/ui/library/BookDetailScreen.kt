package com.booktracker.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.booktracker.data.BookNoteEntity
import com.booktracker.data.NoteKind
import com.booktracker.ui.theme.LocalReadingColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One screen for reading and finished books. [finished] is null while a book is being read. */
@Immutable
data class BookDetail(
    val id: Long,
    val title: String,
    val author: String,
    val coverPath: String?,
    val currentPage: Int,
    val totalPages: Int,
    val started: LocalDate,
    val finished: LocalDate?,
    val daysToComplete: Int?,
    /** The page Restore to Active returns to. */
    val restorePage: Int?,
)

enum class BookDetailTab(val label: String) { Details("Details"), Notes("Notes") }

@Immutable
data class BookDetailActions(
    val onBack: () -> Unit,
    val onRestore: () -> Unit,
    /** A null note id creates a new note. */
    val onSaveNote: (noteId: Long?, kind: NoteKind, text: String) -> Unit,
    val onDeleteNote: (Long) -> Unit,
)

private val NoteKind.label: String
    get() = when (this) {
        NoteKind.NOTE -> "Note"
        NoteKind.TAKEAWAY -> "Takeaway"
        NoteKind.QUOTE -> "Quote"
    }

private val NoteKind.prompt: String
    get() = when (this) {
        NoteKind.NOTE -> "What's on your mind?"
        NoteKind.TAKEAWAY -> "What do you want to remember?"
        NoteKind.QUOTE -> "Copy the line you loved"
    }

private val DateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    book: BookDetail,
    notes: List<BookNoteEntity>,
    actions: BookDetailActions,
    cover: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: BookDetailTab = BookDetailTab.Details,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                BookDetailTab.entries.forEach {
                    Tab(selected = tab == it, onClick = { tab = it }, text = { Text(it.label) })
                }
            }
            when (tab) {
                BookDetailTab.Details -> DetailsPane(book, actions.onRestore, cover)
                BookDetailTab.Notes -> NotesPane(notes, actions)
            }
        }
    }
}

@Composable
private fun DetailsPane(book: BookDetail, onRestore: () -> Unit, cover: @Composable (Modifier) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            cover(Modifier.width(112.dp).height(168.dp).clip(RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (book.finished != null) {
                        "${book.started.format(DateFormat)} – ${book.finished.format(DateFormat)}"
                    } else {
                        "Started ${book.started.format(DateFormat)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when (val days = book.daysToComplete) {
                        null -> "Page ${book.currentPage} of ${book.totalPages}"
                        1 -> "Finished in 1 day"
                        else -> "Finished in $days days"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (book.finished != null) {
            // Restoring clears the finish date, so the screen says that before the button is pressed.
            Text(
                "Returns to Reading at page ${book.restorePage ?: book.totalPages} of ${book.totalPages}. " +
                    "Its finish date and days badge will be cleared.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRestore, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Restore to Active")
            }
        }
    }
}

@Composable
private fun NotesPane(notes: List<BookNoteEntity>, actions: BookDetailActions) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var noteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var kind by rememberSaveable { mutableStateOf(NoteKind.NOTE) }
    var text by rememberSaveable { mutableStateOf("") }

    fun open(note: BookNoteEntity?) {
        noteId = note?.id
        kind = note?.kind ?: NoteKind.NOTE
        text = note?.text.orEmpty()
        editing = true
    }

    if (editing) {
        NoteEditor(
            isExisting = noteId != null,
            kind = kind,
            text = text,
            onKindChange = { kind = it },
            onTextChange = { text = it },
            onSave = {
                if (text.isNotBlank()) actions.onSaveNote(noteId, kind, text)
                editing = false
            },
            onDiscard = { editing = false },
            onDelete = {
                noteId?.let(actions.onDeleteNote)
                editing = false
            },
        )
    } else {
        NotesList(notes, onAdd = { open(null) }, onOpen = { open(it) })
    }
}

@Composable
private fun NotesList(
    notes: List<BookNoteEntity>,
    onAdd: () -> Unit,
    onOpen: (BookNoteEntity) -> Unit,
) {
    val hairline = LocalReadingColors.current.hairline
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("+ Add note")
            }
        }
        if (notes.isEmpty()) {
            item {
                Text(
                    "Save a thought, a takeaway or a line you loved. It stays with this book.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(notes, key = { it.id }) { note ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, hairline),
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Edit note") { onOpen(note) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${note.kind.label} · ${note.updatedDate().format(DateFormat)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(note.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun BookNoteEntity.updatedDate(): LocalDate =
    Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** Back saves, so a thought can't be lost by leaving. Only Discard throws text away. */
@Composable
private fun NoteEditor(
    isExisting: Boolean,
    kind: NoteKind,
    text: String,
    onKindChange: (NoteKind) -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onSave)
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NoteKind.entries.forEach {
                FilterChip(
                    selected = kind == it,
                    onClick = { onKindChange(it) },
                    label = { Text(it.label) },
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(kind.prompt) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isExisting) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                }
            }
            TextButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Discard") }
            Button(
                onClick = onSave,
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("Save") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
