package com.booktracker.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.booktracker.ui.theme.Alert
import com.booktracker.ui.theme.InkSoft
import com.booktracker.ui.theme.Leather
import com.booktracker.ui.theme.RectoIcons
import com.booktracker.ui.theme.SectionLabel
import com.booktracker.ui.theme.TactileButton
import com.booktracker.ui.theme.TactileIconButton
import com.booktracker.ui.theme.TactileTextField
import com.booktracker.ui.theme.closeKeyboardOnBackgroundTap
import com.booktracker.ui.theme.pressedWell
import com.booktracker.ui.theme.tunedAccent
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
    // Before a cover is chosen the book will be bound in leather, so the button wears that colour.
    val accent = if (coverUri == null && title.isNotBlank()) tunedAccent(CoverTexture.forTitle(title).glow) else Leather

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
                .closeKeyboardOnBackgroundTap()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Add a book", style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.08f), modifier = Modifier.semantics { heading() })
                TactileIconButton(onClick = onBack, icon = RectoIcons.Close, contentDescription = "Close", size = 44.dp, corner = 14.dp, tint = InkSoft)
            }
            if (onSearch != null) {
                TactileTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearch(it)
                    },
                    label = "Search title, author or ISBN",
                    imeAction = ImeAction.Search,
                )
            }
            if (onScanBarcode != null) {
                TactileButton(onClick = onScanBarcode, modifier = Modifier.fillMaxWidth()) { Text("Scan barcode") }
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
                        color = InkSoft,
                    )
                }
            }
            if (onSearch != null || onScanBarcode != null) HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoverWell(coverUri, title, onClick = { showCoverSheet = true })
                Column(
                    Modifier.weight(1f).height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    SectionLabel("Cover image")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("JPG", "PNG", "WEBP", "HEIC").forEach { type ->
                            Text(
                                type,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222120))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                    }
                    Text(
                        "Up to 5 MB. Optional: the cover's colour lights your deck. Without one, the book gets a leather binding.",
                        style = MaterialTheme.typography.labelMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight),
                        color = InkSoft,
                    )
                }
            }

            TactileTextField(
                value = title,
                onValueChange = { title = it },
                label = "Title",
                placeholder = "e.g. Middlemarch",
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            )
            TactileTextField(
                value = author,
                onValueChange = { author = it },
                label = "Author (optional)",
                placeholder = "e.g. George Eliot",
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TactileTextField(
                    value = pages,
                    onValueChange = { pages = it.filter(Char::isDigit).take(5) },
                    label = "Total pages",
                    placeholder = "320",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.weight(1f),
                )
                TactileTextField(
                    value = currentPage,
                    onValueChange = { currentPage = it.filter(Char::isDigit).take(5) },
                    label = "On page (optional)",
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                    isError = totalPages != null && startPage > totalPages,
                    modifier = Modifier.weight(1f),
                )
            }
            if (saveFailed) {
                Text(
                    "Couldn't save this book. Please try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Alert,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TactileButton(onClick = onBack, corner = 18.dp, modifier = Modifier.weight(1f).height(56.dp)) { Text("Cancel") }
                TactileButton(
                    onClick = ::save,
                    enabled = valid && !saving,
                    accent = accent,
                    corner = 18.dp,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Add to deck") }
            }
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

/** A slot pressed into the page; once an image is chosen it shows the cover itself. */
@Composable
private fun CoverWell(coverUri: String?, title: String, onClick: () -> Unit) {
    val dash = Color(0xFF3A3733)
    Box(
        Modifier
            .width(112.dp)
            .height(160.dp)
            .pressedWell(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = if (coverUri == null) "Add cover" else "Change cover", onClick = onClick)
            .drawBehind {
                if (coverUri == null) {
                    drawRoundRect(
                        color = dash,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (coverUri != null) {
            BookCover(coverUri, title, "", Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(RectoIcons.Upload, contentDescription = null, tint = InkSoft, modifier = Modifier.size(26.dp))
                Text("Add cover", style = MaterialTheme.typography.labelLarge, color = InkSoft)
            }
        }
    }
}
