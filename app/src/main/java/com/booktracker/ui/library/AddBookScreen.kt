package com.booktracker.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Immutable
data class BookResult(
    val title: String,
    val author: String,
    val pages: Int?,
    val coverUrl: String? = null,
)

/** @param coverUri a picked image or result URL; save picked images with [saveCoverImage]. */
@Immutable
data class NewBook(
    val title: String,
    val author: String,
    val totalPages: Int,
    val currentPage: Int,
    val coverUri: String?,
)

/**
 * @param results matches for the latest [onSearch] query.
 * @param onSearch null hides search, as nothing should be offered until a catalogue is connected.
 * @param onScanBarcode null hides the scanner button.
 * @param scanned a book found by the barcode scanner, which fills the form like a tapped result.
 * @param onSave stores the book and returns its id. The success overlay waits for it, so it can
 * never appear for a book that wasn't saved.
 * @param onAdded called with that id after the overlay, to return to the Read dashboard.
 */
@Composable
fun AddBookScreen(
    onSave: suspend (NewBook) -> Long,
    onAdded: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    results: List<BookResult> = emptyList(),
    scanned: BookResult? = null,
    onSearch: ((String) -> Unit)? = null,
    onScanBarcode: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var pages by rememberSaveable { mutableStateOf("") }
    var currentPage by rememberSaveable { mutableStateOf("") }
    var coverUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showCoverSheet by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var addedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    fun fill(book: BookResult) {
        title = book.title
        author = book.author
        pages = book.pages?.toString().orEmpty()
        coverUri = book.coverUrl
    }

    LaunchedEffect(scanned) { scanned?.let(::fill) }

    val totalPages = pages.toIntOrNull()
    val startPage = currentPage.toIntOrNull() ?: 0
    val valid = title.isNotBlank() && totalPages != null && totalPages > 0 && startPage in 0..totalPages

    fun save() {
        saving = true
        saveFailed = false
        scope.launch {
            try {
                addedId = onSave(NewBook(title.trim(), author.trim(), totalPages!!, startPage, coverUri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailed = true
            } finally {
                saving = false
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Add a book", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Cancel") }
            }
            if (onSearch != null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearch(it)
                    },
                    label = { Text("Search title, author or ISBN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (onScanBarcode != null) {
                OutlinedButton(onClick = onScanBarcode, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Scan barcode")
                }
            }
            results.forEach { book ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { fill(book) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(book.author, book.pages?.let { "$it pages" }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onSearch != null || onScanBarcode != null) {
                HorizontalDivider()
                Text("Book details", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(pages, { pages = it }, "Total pages")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BookCover(
                    coverPath = coverUri,
                    title = title,
                    author = "",
                    modifier = Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(3.dp)),
                )
                OutlinedButton(
                    onClick = { showCoverSheet = true },
                    modifier = Modifier.height(48.dp),
                ) { Text(if (coverUri == null) "Add cover image" else "Change cover") }
            }
            NumberField(currentPage, { currentPage = it }, "Already on page (optional)")
            if (saveFailed) {
                Text(
                    "Couldn't save this book. Please try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Button(
                onClick = ::save,
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Add to Reading now") }
        }
        addedId?.let { id -> BookAddedOverlay(title.trim(), onFinished = { onAdded(id) }) }
    }

    if (showCoverSheet) {
        CoverUploadSheet(
            hasCover = coverUri != null,
            onPicked = {
                coverUri = it.toString()
                showCoverSheet = false
            },
            onRemove = {
                coverUri = null
                showCoverSheet = false
            },
            onDismiss = { showCoverSheet = false },
        )
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
