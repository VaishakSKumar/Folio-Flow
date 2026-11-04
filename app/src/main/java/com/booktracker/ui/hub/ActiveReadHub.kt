package com.booktracker.ui.hub

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booktracker.ui.library.coverAccent
import com.booktracker.ui.theme.Alert
import com.booktracker.ui.theme.Ink
import com.booktracker.ui.theme.InkFaint
import com.booktracker.ui.theme.InkSoft
import com.booktracker.ui.theme.LocalAccent
import com.booktracker.ui.theme.LocalReadingColors
import com.booktracker.ui.theme.RectoIcons
import com.booktracker.ui.theme.RectoMark
import com.booktracker.ui.theme.SectionLabel
import com.booktracker.ui.theme.StatWell
import com.booktracker.ui.theme.SurfaceTone
import com.booktracker.ui.theme.TactileButton
import com.booktracker.ui.theme.TactileCard
import com.booktracker.ui.theme.TactileIconButton
import com.booktracker.ui.theme.TactileTextField
import com.booktracker.ui.theme.Well
import com.booktracker.ui.theme.ambientGlow
import com.booktracker.ui.theme.pressedWell
import com.booktracker.ui.theme.raised
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    val booksRead: Int = 0,
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

/** How long the page taps must pause before the celebration counts them up and shows. */
private const val LogSettleMillis = 900L

/**
 * Taps made in quick succession on one book. [startPage] is where the run began, so each tap adds
 * to the last even before the database has caught up.
 */
private data class LogRun(val bookId: Long? = null, val startPage: Int = 0, val pages: Int = 0, val version: Int = 0)

/** The colour this book lights the room with. */
fun ActiveBook.accent(): Color = coverAccent(coverPath, title, glowColor)

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
                message = "${book.title} moved to the Vault",
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
            EmptyHub(state, actions.onAddBook)
        } else {
            HubContent(state, actions, deck, celebrating, completeBook, cover)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun HubHeader(onAddBook: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RectoMark(Modifier.size(28.dp))
            Text("Recto", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        TactileIconButton(onClick = onAddBook, icon = RectoIcons.Add, contentDescription = "Add a book", size = 44.dp, corner = 14.dp)
    }
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
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val locked = celebrating != null
    var confirmation by remember { mutableStateOf<BookConfirmation?>(null) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var run by remember { mutableStateOf(LogRun()) }
    var logged by remember { mutableStateOf<LoggedPages?>(null) }
    val latestBooks by rememberUpdatedState(state.books)

    LaunchedEffect(run.version) {
        val settled = run
        if (settled.bookId == null) return@LaunchedEffect
        delay(LogSettleMillis)
        val book = latestBooks.firstOrNull { it.id == settled.bookId }
        val page = settled.startPage + settled.pages
        if (book != null && settled.pages > 0 && page < book.totalPages) {
            logged = LoggedPages(book.title, settled.pages, page, book.totalPages)
        }
        run = LogRun(version = settled.version + 1)
    }

    /** Moves the page by [delta], counting from any taps the database hasn't shown yet. */
    fun log(book: ActiveBook, delta: Int) {
        val ongoing = run.bookId == book.id
        val start = if (ongoing) run.startPage else book.currentPage
        val from = start + (if (ongoing) run.pages else 0)
        val target = (from + delta).coerceIn(0, book.totalPages)
        if (target == from) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        actions.onSetPage(book.id, target)
        run = LogRun(book.id, start, target - start, run.version + 1)
        // Asked here, never just because a restored book is on its last page.
        if (target >= book.totalPages) confirmation = BookConfirmation.Complete
    }

    val shownPage = if (run.bookId == activeBook.id) run.startPage + run.pages else activeBook.currentPage
    val shownBook = activeBook.copy(currentPage = shownPage)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        HubHeader(actions.onAddBook)
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Reading now", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (state.books.size > 1) {
                Text(
                    "${state.books.indexOf(activeBook) + 1} of ${state.books.size} · swipe",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = InkSoft,
                )
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().padding(horizontal = 24.dp, vertical = 40.dp).ambientGlow(accent, 0.5f, 0.7f))
            BookDeck(
                state = deck,
                items = state.books,
                itemKey = ActiveBook::id,
                enabled = !locked,
            ) { book ->
                val shown = if (book.id == activeBook.id) shownBook else book
                BookHeroCard(shown, if (book.id == activeBook.id) accent else book.accent()) { coverModifier -> cover(book, coverModifier) }
            }
            celebrating?.let { CompletionCelebration(it.title, it.accent(), Modifier.matchParentSize()) }
        }
        BookDots(deck, state.books, accent, enabled = !locked)

        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Log pages", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            TextButton(onClick = { showCustom = true }, enabled = !locked) {
                Text("Set exact page", color = InkSoft, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1, 5).forEach { amount ->
                PageStepper(
                    amount = amount,
                    accent = accent,
                    canRemove = !locked && shownBook.currentPage > 0,
                    canAdd = !locked && shownBook.pagesLeft > 0,
                    onRemove = { log(activeBook, -amount) },
                    onAdd = { log(activeBook, amount) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatWell(
                value = if (state.streak == 1) "1 day" else "${state.streak} days",
                label = "streak",
                icon = RectoIcons.Streak,
                iconTint = Color(0xFFE2A34F),
                modifier = Modifier.weight(1f),
            )
            StatWell(
                value = if (state.pacing.today == 1) "1 page" else "${state.pacing.today} pages",
                label = "today",
                icon = RectoIcons.Book,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TactileButton(
                onClick = { confirmation = BookConfirmation.Complete },
                enabled = !locked,
                modifier = Modifier.weight(1f),
            ) {
                Icon(RectoIcons.Done, contentDescription = null, tint = if (locked) InkFaint else accent, modifier = Modifier.size(18.dp))
                Text("Finished", style = MaterialTheme.typography.labelLarge)
            }
            TactileButton(onClick = { actions.onOpenNotes(activeBook.id) }, enabled = !locked, modifier = Modifier.weight(1f)) {
                Icon(RectoIcons.Notes, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Notes", style = MaterialTheme.typography.labelLarge)
            }
            TactileButton(
                onClick = { confirmation = BookConfirmation.Delete },
                enabled = !locked,
                contentColor = Alert,
                modifier = Modifier.weight(1f),
            ) {
                Icon(RectoIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Delete", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(22.dp))
        ChallengeRow(state, actions.onOpenChallenges)
        Spacer(Modifier.height(14.dp))
        PacingHealthCard(state.pacing, activeBook)
        Spacer(Modifier.height(16.dp))
    }

    logged?.let { LogCelebration(it, state.streak, accent, onDismiss = { logged = null }) }

    if (showCustom) {
        CustomPageDialog(
            book = shownBook,
            onDismiss = { showCustom = false },
            onConfirm = { page ->
                showCustom = false
                log(activeBook, page - shownBook.currentPage)
            },
        )
    }

    confirmation?.let { kind ->
        BookConfirmationDialog(
            kind = kind,
            book = activeBook,
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                run = LogRun(version = run.version + 1)
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
                    "${book.title} will move to your Vault with its Days to Complete."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (isDelete) "Delete" else "Mark finished", color = if (isDelete) Alert else LocalAccent.current)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) } },
    )
}

@Composable
private fun EmptyHub(state: HubUiState, onAddBook: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        HubHeader(onAddBook)
        Box(
            Modifier.fillMaxWidth().padding(top = 28.dp).height(290.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(220.dp).ambientGlow(accent, 0.3f, 0.8f))
            Box(
                Modifier.size(width = 196.dp, height = 260.dp).pressedWell(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val dash = Color(0xFF3A3733)
                Column(
                    Modifier
                        .size(width = 172.dp, height = 236.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = dash,
                                cornerRadius = CornerRadius(20.dp.toPx()),
                                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))),
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    Icon(RectoIcons.Deck, contentDescription = null, tint = InkFaint, modifier = Modifier.size(56.dp))
                    Text("Your deck is empty", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = InkFaint)
                }
            }
        }
        Text(
            "Build habits",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 24.dp).semantics { heading() },
        )
        Text("Start. Record.", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 6.dp))
        Text(
            "Add the book you're reading. Recto puts it on top of your deck and lights the room with its cover.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = InkSoft,
            modifier = Modifier.padding(top = 8.dp),
        )
        TactileButton(
            onClick = onAddBook,
            accent = accent,
            corner = 18.dp,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp),
        ) {
            Icon(RectoIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Add a book")
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyStat("${state.streak}", "day streak", Modifier.weight(1f))
            EmptyStat("${state.pacing.today}", "pages today", Modifier.weight(1f))
            EmptyStat("${state.booksRead}", "books read", Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptyStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.pressedWell(16.dp).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkSoft)
    }
}

/** The tall touch area makes each small dot a usable target for tapping straight to a book. */
@Composable
private fun BookDots(deck: BookDeckState, books: List<ActiveBook>, accent: Color, enabled: Boolean) {
    if (books.size < 2) return
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        books.forEachIndexed { index, book ->
            val selected = deck.frontKey == book.id
            Box(
                Modifier
                    .size(width = 28.dp, height = 32.dp)
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
                        .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else Color(0xFF3A3733)),
                )
            }
        }
    }
}

/** Cover on the left, progress on the right. Every card has the same height, so the deck lines up. */
@Composable
private fun BookHeroCard(book: ActiveBook, accent: Color, cover: @Composable (Modifier) -> Unit) {
    val progress by animateFloatAsState(book.fraction, spring(stiffness = 300f), label = "progress")
    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        cover(
            Modifier
                .width(106.dp)
                .height(160.dp)
                .shadow(10.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).height(160.dp)) {
            Text(
                "${(book.fraction * 100).roundToInt()}% read",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                color = accent,
            )
            Text(
                book.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (book.author.isNotBlank()) {
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${book.currentPage}",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum, lnum"),
                )
                Text("/ ${book.totalPages} pages", style = MaterialTheme.typography.bodyMedium, color = InkSoft, modifier = Modifier.padding(bottom = 4.dp))
            }
            Canvas(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .pressedWell(4.dp)
                    .clearAndSetSemantics {
                        contentDescription = "Reading progress"
                        stateDescription = "Page ${book.currentPage} of ${book.totalPages}"
                        progressBarRangeInfo = ProgressBarRangeInfo(book.fraction, 0f..1f)
                    },
            ) {
                if (progress > 0f) {
                    drawRoundRect(accent.copy(alpha = 0.35f), size = Size(size.width * progress, size.height + 4.dp.toPx()), topLeft = Offset(0f, -2.dp.toPx()), cornerRadius = CornerRadius(size.height))
                    drawRoundRect(accent, size = Size(size.width * progress, size.height), cornerRadius = CornerRadius(size.height / 2))
                }
            }
            val estimate = book.pagesPerDay
                ?.takeIf { it > 0f && book.pagesLeft > 0 }
                ?.let { " · about ${ceil(book.pagesLeft / it).toInt()} days" }
                .orEmpty()
            Text(
                "${book.pagesLeft} pages left$estimate",
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** `[-] 1 [+]`: the number is a fixed label, only the two keys move the page. */
@Composable
private fun PageStepper(
    amount: Int,
    accent: Color,
    canRemove: Boolean,
    canAdd: Boolean,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noun = if (amount == 1) "page" else "pages"
    Row(
        modifier.raised(20.dp, SurfaceTone).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TactileIconButton(onRemove, RectoIcons.Remove, "Remove $amount $noun", tint = InkSoft, enabled = canRemove)
        Text(
            "$amount",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            modifier = Modifier.clearAndSetSemantics { },
        )
        TactileIconButton(onAdd, RectoIcons.Add, "Add $amount $noun", tint = accent, enabled = canAdd, glow = accent)
    }
}

@Composable
private fun ChallengeRow(state: HubUiState, onClick: () -> Unit) {
    TactileCard(Modifier.clickable(onClickLabel = "Open challenges", onClick = onClick)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(RectoIcons.Challenge, contentDescription = null, tint = LocalAccent.current, modifier = Modifier.size(22.dp))
            Column {
                SectionLabel("Today's challenge")
                Text(state.challengeLine, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 2.dp))
            }
        }
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
            TactileTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(5) },
                label = "Page",
                placeholder = "${book.currentPage}",
                keyboardType = KeyboardType.Number,
                supportingText = "Between 0 and ${book.totalPages}",
                isError = text.isNotEmpty() && !valid,
                onDone = { if (valid) onConfirm(page!!) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(page!!) }, enabled = valid) { Text("Save", color = if (valid) LocalAccent.current else InkFaint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) } },
    )
}

@Composable
private fun PacingHealthCard(pacing: PacingHealth, book: ActiveBook) {
    val colors = LocalReadingColors.current
    val (label, statusColor) = when (pacing.status) {
        PacingStatus.Ahead -> "▲  Ahead" to colors.ahead
        PacingStatus.OnPace -> "●  On pace" to colors.onPace
        PacingStatus.Behind -> "▼  Behind" to colors.behind
        PacingStatus.Paused -> "Busy week" to InkSoft
        PacingStatus.NoGoal -> "Set a daily goal" to InkSoft
        PacingStatus.NotEnoughData -> "Still learning your pace" to InkSoft
    }
    val hasVerdict = pacing.status in setOf(PacingStatus.Ahead, PacingStatus.OnPace, PacingStatus.Behind)
    val average = pacing.rollingAverage
    TactileCard {
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
                    color = InkSoft,
                )
            } else {
                Text(
                    when (pacing.status) {
                        PacingStatus.Paused -> "Pacing is resting this week. One page keeps your streak."
                        PacingStatus.NoGoal -> "Pick pages per day and this card compares it with your last 7 days."
                        else -> "Log three reading days to see how you compare with your target."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
            }
        }
    }
}

/** The bar spans 0–150% of target, so the tick sits at two thirds and overshoot stays visible. */
@Composable
private fun TargetBar(ratio: Float, fill: Color) {
    val tick = Ink
    val progress by animateFloatAsState((ratio / 1.5f).coerceIn(0f, 1f), label = "pacing")
    Canvas(Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics { }) {
        val barHeight = 8.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(Well, Offset(0f, top), Size(size.width, barHeight), radius)
        drawRoundRect(fill, Offset(0f, top), Size(size.width * progress, barHeight), radius)
        val x = size.width * (1f / 1.5f)
        drawLine(tick, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}
