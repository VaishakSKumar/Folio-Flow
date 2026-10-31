package com.booktracker.ui.hub

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.booktracker.ui.library.CoverTexture
import com.booktracker.ui.theme.LocalReadingColors
import com.booktracker.ui.theme.softShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil

@Immutable
data class ActiveBook(
    val id: Long,
    val title: String,
    val author: String,
    val currentPage: Int,
    val totalPages: Int,
    /** This book's own pace, null until [recentPagesPerDay] has enough days to go on. */
    val pagesPerDay: Float? = null,
    val coverPath: String? = null,
    /** From extractGlowColor, stored with the book so it isn't recalculated on every rotation. */
    val glowColor: Int? = null,
) {
    val fraction: Float get() = (currentPage.toFloat() / totalPages).coerceIn(0f, 1f)
    val pagesLeft: Int get() = (totalPages - currentPage).coerceAtLeast(0)
}

/** One page update for one book. Daily totals are always derived from these. */
@Immutable
data class ReadingEntry(
    val bookId: Long,
    val date: LocalDate,
    val startPage: Int,
    val endPage: Int,
)

/** Pages across all books. Each book floors at 0, so a correction can't eat another book's pages. */
fun pagesOn(date: LocalDate, entries: List<ReadingEntry>): Int =
    entries
        .filter { it.date == date }
        .groupBy { it.bookId }
        .values
        .sumOf { bookEntries -> bookEntries.sumOf { it.endPage - it.startPage }.coerceAtLeast(0) }

fun recentPagesPerDay(bookId: Long, entries: List<ReadingEntry>, today: LocalDate): Float? {
    val recent = entries.filter {
        it.bookId == bookId && it.date.isAfter(today.minusDays(14)) && !it.date.isAfter(today)
    }
    if (recent.map { it.date }.distinct().size < 3) return null
    return recent.sumOf { it.endPage - it.startPage }.coerceAtLeast(0) / 14f
}

enum class PacingStatus { Ahead, OnPace, Behind, Paused, NoGoal, NotEnoughData }

/**
 * @param lastEightDays pages per calendar day, oldest first, today last.
 * @param targetPerDay the goal in force today, 0 when none is set.
 * @param busy a Busy week is on, so the card stops judging.
 */
@Immutable
data class PacingHealth(
    val lastEightDays: List<Int>,
    val targetPerDay: Int,
    val busy: Boolean = false,
) {
    val today: Int get() = lastEightDays.last()

    /** Today counts only once it helps, so a morning check never reads as a slump. */
    val rollingAverage: Float
        get() {
            val withToday = lastEightDays.takeLast(7).average().toFloat()
            val completedDays = lastEightDays.dropLast(1).takeLast(7).average().toFloat()
            return maxOf(withToday, completedDays)
        }

    val ratio: Float get() = rollingAverage / targetPerDay

    val status: PacingStatus
        get() = when {
            busy -> PacingStatus.Paused
            targetPerDay <= 0 -> PacingStatus.NoGoal
            lastEightDays.count { it > 0 } < 3 -> PacingStatus.NotEnoughData
            ratio >= 1.10f -> PacingStatus.Ahead
            ratio >= 0.90f -> PacingStatus.OnPace
            else -> PacingStatus.Behind
        }
}

@Immutable
data class HubUiState(
    val books: List<ActiveBook>,
    /** The book to open on, normally the last one used. */
    val lastBookId: Long?,
    val pacing: PacingHealth,
    val challengeLine: String,
    val streak: Int,
)

@Immutable
data class HubActions(
    val onOpenChallenges: () -> Unit,
    val onSetPage: (bookId: Long, page: Int) -> Unit,
    val onActiveBookChange: (Long) -> Unit,
    val onAddBook: () -> Unit,
    val onCompleteBook: (Long) -> Unit,
    val onRestoreBook: (Long) -> Unit,
    val onDeleteBook: (Long) -> Unit,
    val onOpenNotes: (Long) -> Unit,
)

private enum class BookConfirmation { Complete, Delete }

/**
 * The completion flow lives here, above the empty state: finishing the last book must not lose its
 * Undo. The write is in a `finally`, so leaving the screen mid-celebration still completes the book.
 */
@Composable
fun ActiveReadHubScreen(
    state: HubUiState,
    actions: HubActions,
    cover: @Composable (ActiveBook, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deck = rememberBookDeckState(state.lastBookId)
    val onActiveBookChange by rememberUpdatedState(actions.onActiveBookChange)
    LaunchedEffect(deck) {
        snapshotFlow { deck.frontKey }.filterNotNull().collect { onActiveBookChange(it) }
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    var celebrating by remember { mutableStateOf<ActiveBook?>(null) }

    val completeBook: (ActiveBook) -> Unit = { book ->
        scope.launch {
            try {
                celebrating = book
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(CelebrationMillis)
                deck.playExit()
            } finally {
                celebrating = null
                actions.onCompleteBook(book.id)
            }
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "${book.title} moved to Archive",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                deck.frontKey = book.id
                actions.onRestoreBook(book.id)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        if (state.books.isEmpty()) {
            EmptyHub(actions.onAddBook)
        } else {
            HubContent(state, actions, deck, celebrating, completeBook, cover)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** An uploaded cover glows in its Palette colour, and a leather cover in its texture's colour. */
private fun ActiveBook.backlight(): Color = when {
    coverPath == null -> CoverTexture.forTitle(title).glow
    glowColor != null -> Color(glowColor)
    else -> Color.Transparent
}

@Composable
private fun HubContent(
    state: HubUiState,
    actions: HubActions,
    deck: BookDeckState,
    celebrating: ActiveBook?,
    onCompleteBook: (ActiveBook) -> Unit,
    cover: @Composable (ActiveBook, Modifier) -> Unit,
) {
    val activeBook = state.books.firstOrNull { it.id == deck.frontKey } ?: state.books.first()
    val scope = rememberCoroutineScope()
    val locked = celebrating != null
    var confirmation by remember { mutableStateOf<BookConfirmation?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Reading now", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = actions.onAddBook) { Text("+ Add book") }
        }
        Box(Modifier.fillMaxWidth()) {
            val backlight = activeBook.backlight()
            val glow by animateColorAsState(backlight.copy(alpha = backlight.alpha * 0.55f), tween(250), label = "glow")
            Box(
                Modifier
                    .matchParentSize()
                    .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 28.dp)
                    .softShadow(glow, 56.dp, MaterialTheme.colorScheme.background),
            )
            BookDeck(
                state = deck,
                items = state.books,
                itemKey = ActiveBook::id,
                enabled = !locked,
            ) { book ->
                BookHeroCard(
                    book = book,
                    actionsEnabled = !locked,
                    onComplete = { confirmation = BookConfirmation.Complete },
                    onDelete = { confirmation = BookConfirmation.Delete },
                    onNotes = { actions.onOpenNotes(book.id) },
                    onSetPage = { page ->
                        actions.onSetPage(book.id, page)
                        // Asked here, never just because a restored book is on its last page.
                        if (page >= book.totalPages) confirmation = BookConfirmation.Complete
                    },
                ) { coverModifier -> cover(book, coverModifier) }
            }
            celebrating?.let { CompletionCelebration(it.title, it.backlight(), Modifier.matchParentSize()) }
        }
        BookDots(deck, state.books, enabled = !locked)
        ChallengeRow(state, actions.onOpenChallenges)
        PacingHealthCard(state.pacing, activeBook)
    }

    confirmation?.let { kind ->
        BookConfirmationDialog(
            kind = kind,
            book = activeBook,
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                when (kind) {
                    BookConfirmation.Complete -> onCompleteBook(activeBook)
                    BookConfirmation.Delete -> scope.launch {
                        deck.playExit()
                        actions.onDeleteBook(activeBook.id)
                    }
                }
            },
        )
    }
}

@Composable
private fun BookConfirmationDialog(
    kind: BookConfirmation,
    book: ActiveBook,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isDelete = kind == BookConfirmation.Delete
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDelete) "Delete this book?" else "Finished it?") },
        text = {
            Text(
                if (isDelete) {
                    "${book.title}, its reading sessions and its notes will be removed. This can't be undone."
                } else {
                    "${book.title} will move to your Archive with its Days to Complete."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (isDelete) "Delete" else "Mark finished") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyHub(onAddBook: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("What are you reading?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Add a book to start tracking your pages.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddBook, modifier = Modifier.height(48.dp)) { Text("Add your first book") }
    }
}

/** The tall touch area makes each small dot a usable target for tapping straight to a book. */
@Composable
private fun BookDots(deck: BookDeckState, books: List<ActiveBook>, enabled: Boolean) {
    if (books.size < 2) return
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        books.forEachIndexed { index, book ->
            val selected = deck.frontKey == book.id
            Box(
                Modifier
                    .size(width = 24.dp, height = 32.dp)
                    .clickable(enabled = enabled, onClickLabel = "Show book ${index + 1}") {
                        scope.launch { deck.goTo(book.id) }
                    }
                    .semantics {
                        contentDescription = "Book ${index + 1} of ${books.size}"
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun BookHeroCard(
    book: ActiveBook,
    actionsEnabled: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onNotes: () -> Unit,
    onSetPage: (Int) -> Unit,
    cover: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            // Two lines are always reserved, so every card in the deck has the same height.
            Text(
                book.title,
                style = MaterialTheme.typography.headlineSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            cover(
                Modifier
                    .width(112.dp)
                    .height(168.dp)
                    .shadow(10.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp)),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ProgressRing(book)
            }
        }
        PageLogRow(book, actionsEnabled, onSetPage)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onComplete, enabled = actionsEnabled) { Text("Mark finished") }
            TextButton(onClick = onNotes, enabled = actionsEnabled) { Text("Notes") }
            TextButton(onClick = onDelete, enabled = actionsEnabled) { Text("Delete") }
        }
    }
}

@Composable
private fun ProgressRing(book: ActiveBook, diameter: Dp = 160.dp) {
    val animated by animateFloatAsState(book.fraction, spring(stiffness = 300f), label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val fill = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(diameter)
            .clearAndSetSemantics {
                contentDescription = "Reading progress"
                stateDescription = "Page ${book.currentPage} of ${book.totalPages}"
                progressBarRangeInfo = ProgressBarRangeInfo(book.fraction, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, inset, arcSize, style = Stroke(stroke))
            drawArc(fill, -90f, 360f * animated, false, inset, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${book.currentPage}",
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum, lnum"),
            )
            Text(
                "of ${book.totalPages} pages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaperCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalReadingColors.current.hairline),
        content = content,
    )
}

@Composable
private fun ChallengeRow(state: HubUiState, onClick: () -> Unit) {
    PaperCard(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Today's challenge · ${state.streak} day streak",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(state.challengeLine, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Lives on the card itself, so logging needs no scrolling and follows the deck's front book. */
@Composable
private fun PageLogRow(book: ActiveBook, enabled: Boolean, onSetPage: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var showCustom by rememberSaveable { mutableStateOf(false) }

    fun commit(page: Int) {
        val clamped = page.coerceIn(0, book.totalPages)
        if (clamped != book.currentPage) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSetPage(clamped)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = { commit(book.currentPage + 1) },
            enabled = enabled && book.pagesLeft > 0,
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text("+1") }
        FilledTonalButton(
            onClick = { commit(book.currentPage + 5) },
            enabled = enabled && book.pagesLeft > 0,
            modifier = Modifier.weight(1f).height(48.dp),
        ) { Text("+5") }
        OutlinedButton(
            onClick = { showCustom = true },
            enabled = enabled,
            modifier = Modifier.weight(1.4f).height(48.dp),
        ) { Text("Custom") }
    }

    if (showCustom) {
        CustomPageDialog(
            book = book,
            onDismiss = { showCustom = false },
            onConfirm = {
                commit(it)
                showCustom = false
            },
        )
    }
}

@Composable
private fun CustomPageDialog(book: ActiveBook, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val page = text.toIntOrNull()
    val valid = page != null && page in 0..book.totalPages
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What page are you on?") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(5) },
                singleLine = true,
                label = { Text("Page") },
                supportingText = { Text("Between 0 and ${book.totalPages}") },
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(page!!) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(page!!) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PacingHealthCard(pacing: PacingHealth, book: ActiveBook) {
    val colors = LocalReadingColors.current
    val (label, statusColor) = when (pacing.status) {
        PacingStatus.Ahead -> "▲  Ahead" to colors.ahead
        PacingStatus.OnPace -> "●  On pace" to colors.onPace
        PacingStatus.Behind -> "▼  Behind" to colors.behind
        PacingStatus.Paused -> "Busy week" to MaterialTheme.colorScheme.onSurfaceVariant
        PacingStatus.NoGoal -> "Set a daily goal" to MaterialTheme.colorScheme.onSurfaceVariant
        PacingStatus.NotEnoughData -> "Still learning your pace" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val hasVerdict = pacing.status in setOf(PacingStatus.Ahead, PacingStatus.OnPace, PacingStatus.Behind)
    val average = pacing.rollingAverage
    PaperCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pacing health", style = MaterialTheme.typography.titleMedium)
                Text(label, style = MaterialTheme.typography.labelLarge, color = statusColor)
            }
            if (hasVerdict) {
                Text(
                    "%.1f pages a day over 7 days, against a target of %d.".format(average, pacing.targetPerDay),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TargetBar(pacing.ratio, statusColor)
                val estimate = book.pagesPerDay
                    ?.takeIf { it > 0f }
                    ?.let { " · finish in about ${ceil(book.pagesLeft / it).toInt()} days" }
                    .orEmpty()
                Text(
                    "${pacing.today} of ${pacing.targetPerDay} pages today$estimate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    when (pacing.status) {
                        PacingStatus.Paused -> "Pacing is resting this week. One page keeps your streak."
                        PacingStatus.NoGoal -> "Pick pages per day and this card compares it with your last 7 days."
                        else -> "Log three reading days to see how you compare with your target."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The bar spans 0–150% of target, so the tick sits at two thirds and overshoot stays visible. */
@Composable
private fun TargetBar(ratio: Float, fill: Color) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val tick = MaterialTheme.colorScheme.onSurface
    val progress by animateFloatAsState((ratio / 1.5f).coerceIn(0f, 1f), label = "pacing")
    Canvas(Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics { }) {
        val barHeight = 8.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2)
        drawRoundRect(track, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, barHeight), radius)
        drawRoundRect(fill, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width * progress, barHeight), radius)
        val x = size.width * (1f / 1.5f)
        drawLine(tick, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}
