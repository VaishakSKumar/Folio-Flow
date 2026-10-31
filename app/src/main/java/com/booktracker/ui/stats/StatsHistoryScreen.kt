package com.booktracker.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.booktracker.ui.theme.LocalReadingColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class AveragePeriod(val label: String, val unit: String) {
    Day("Day", "pages / day"),
    Week("Week", "pages / week"),
    Month("Month", "pages / month"),
}

@Immutable
data class RollingAverage(
    val period: AveragePeriod,
    val value: Float,
    val previousValue: Float,
    /** Oldest first; the last entry is the current bucket. */
    val buckets: List<Int>,
)

@Immutable
data class DayCount(val date: LocalDate, val pages: Int)

@Immutable
data class FinishedBook(
    val id: Long,
    val title: String,
    val author: String,
    val started: LocalDate,
    val finished: LocalDate,
    /** Stored on the book when it was completed; see calculateDaysToComplete. */
    val daysToComplete: Int,
    val readNumber: Int = 1,
    val favorite: Boolean = false,
    val coverPath: String? = null,
)

@Immutable
data class StatsUiState(
    val averages: List<RollingAverage>,
    val days: List<DayCount>,
    val targetPerDay: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val finished: List<FinishedBook>,
)

@Immutable
data class ArchiveActions(
    val onOpenBook: (Long) -> Unit,
    val onToggleFavorite: (Long) -> Unit,
    val onDeleteBook: (Long) -> Unit,
)

enum class StatsTab { Stats, Archive }
private enum class ArchiveView(val label: String) { Shelf("Grid shelf"), Timeline("Timeline") }

@Composable
fun StatsHistoryScreen(
    state: StatsUiState,
    tab: StatsTab,
    cover: @Composable (FinishedBook, Modifier) -> Unit,
    archiveActions: ArchiveActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (tab) {
            StatsTab.Stats -> StatsPane(state)
            StatsTab.Archive -> ArchivePane(state.finished, cover, archiveActions)
        }
    }
}

@Composable
private fun StatsPane(state: StatsUiState) {
    var selected by rememberSaveable { mutableStateOf(AveragePeriod.Day) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.averages.forEach { average ->
                    MetricCard(
                        average = average,
                        selected = average.period == selected,
                        onClick = { selected = average.period },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            val average = state.averages.first { it.period == selected }
            TrendCard(average)
        }
        item { HeatmapCard(state) }
    }
}

@Composable
private fun PaperCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, LocalReadingColors.current.hairline),
        content = content,
    )
}

@Composable
private fun MetricCard(
    average: RollingAverage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change = average.value - average.previousValue
    val changeLabel = when {
        change.roundToInt() > 0 -> "▲ ${change.roundToInt()}"
        change.roundToInt() < 0 -> "▼ ${-change.roundToInt()}"
        else -> "No change"
    }
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else LocalReadingColors.current.hairline,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(average.period.label, style = MaterialTheme.typography.labelMedium)
            Text(
                average.value.roundToInt().toString(),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum, lnum"),
            )
            Text(
                average.period.unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                changeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendCard(average: RollingAverage) {
    val bar = MaterialTheme.colorScheme.outline
    val current = MaterialTheme.colorScheme.primary
    val rule = MaterialTheme.colorScheme.onSurfaceVariant
    PaperCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pages per ${average.period.label.lowercase()}", style = MaterialTheme.typography.titleMedium)
            val summary = "${average.buckets.size} ${average.period.label.lowercase()}s, " +
                "average ${average.value.roundToInt()}, latest ${average.buckets.lastOrNull() ?: 0}"
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics { contentDescription = summary },
            ) {
                val top = (average.buckets.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                val slot = size.width / average.buckets.size
                val width = (slot - 2.dp.toPx()).coerceAtMost(14.dp.toPx())
                average.buckets.forEachIndexed { index, value ->
                    val height = size.height * value / top
                    drawRoundRect(
                        color = if (index == average.buckets.lastIndex) current else bar,
                        topLeft = Offset(index * slot + (slot - width) / 2, size.height - height),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                val y = size.height * (1f - average.value / top)
                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

/** 0 = no reading; steps are shares of the goal, so colour means the same whatever the goal is. */
fun heatLevel(pages: Int, targetPerDay: Int): Int = when {
    pages <= 0 -> 0
    targetPerDay <= 0 -> 2
    pages < targetPerDay * 0.5f -> 1
    pages < targetPerDay -> 2
    pages < targetPerDay * 1.5f -> 3
    else -> 4
}

private val HeatCell = 13.dp
private val HeatGap = 3.dp
private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
private fun HeatmapCard(state: StatsUiState) {
    val colors = LocalReadingColors.current
    val weeks = remember(state.days) {
        state.days.dropWhile { it.date.dayOfWeek.value != 1 }.chunked(7)
    }
    val listState = rememberLazyListState()
    var picked by remember { mutableStateOf<DayCount?>(null) }
    LaunchedEffect(weeks.size) {
        if (weeks.isNotEmpty()) listState.scrollToItem(weeks.lastIndex)
    }

    PaperCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Consistency", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${state.currentStreak} day streak · best ${state.bestStreak}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(HeatGap)) {
                items(weeks) { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(HeatGap)) {
                        week.forEach { day ->
                            val level = heatLevel(day.pages, state.targetPerDay)
                            Box(
                                Modifier
                                    .size(HeatCell)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.heat[level])
                                    .clickable { picked = day }
                                    .semantics {
                                        contentDescription = "${day.date.format(DayFormat)}, ${day.pages} pages"
                                    },
                            )
                        }
                    }
                }
            }
            Text(
                picked?.let { "${it.date.format(DayFormat)} · ${it.pages} pages" }
                    ?: "Tap a day to see its pages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HeatGap),
            ) {
                Text(
                    "Less",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 4.dp),
                )
                colors.heat.forEach {
                    Box(Modifier.size(HeatCell).clip(RoundedCornerShape(3.dp)).background(it))
                }
                Text(
                    "150% of goal",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivePane(
    books: List<FinishedBook>,
    cover: @Composable (FinishedBook, Modifier) -> Unit,
    actions: ArchiveActions,
) {
    var view by rememberSaveable { mutableStateOf(ArchiveView.Shelf) }
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<FinishedBook?>(null) }
    // Actions stay hidden until a book is tapped, so the shelf itself carries no buttons.
    val overlay: @Composable (FinishedBook) -> Unit = { book ->
        if (book.id == revealedId) {
            ArchiveActionOverlay(
                book = book,
                onDismiss = { revealedId = null },
                onToggleFavorite = { actions.onToggleFavorite(book.id) },
                onOpen = {
                    revealedId = null
                    actions.onOpenBook(book.id)
                },
                onDelete = {
                    revealedId = null
                    deleting = book
                },
            )
        }
    }
    val onReveal: (FinishedBook) -> Unit = { revealedId = it.id }
    deleting?.let { book ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this book?") },
            text = {
                Text(
                    "${book.title}, its reading sessions and its notes will be removed. " +
                        "Its pages leave your Stats, and this can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        actions.onDeleteBook(book.id)
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
            ArchiveView.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = view == option,
                    onClick = { view = option },
                    shape = SegmentedButtonDefaults.itemShape(index, ArchiveView.entries.size),
                ) { Text(option.label) }
            }
        }
        if (books.isEmpty()) {
            Text(
                "Books you finish land here, with the days each one took.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Crossfade(view, label = "archiveView") { shown ->
                when (shown) {
                    ArchiveView.Shelf -> GridShelf(books, cover, onReveal, overlay)
                    ArchiveView.Timeline -> TimelineView(books, cover, onReveal, overlay)
                }
            }
        }
    }
}

/** Floats over the tapped book. A popup, because three 48dp buttons are wider than a shelf cover. */
@Composable
private fun ArchiveActionOverlay(
    book: FinishedBook,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Row {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (book.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (book.favorite) "Remove from favorites" else "Add to favorites",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                IconButton(onClick = onOpen) {
                    Icon(Icons.Filled.Info, contentDescription = "Open details and notes")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete book")
                }
            }
        }
    }
}

@Composable
private fun FavoriteMark(modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.background) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = "Favorite",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(4.dp).size(14.dp),
        )
    }
}

@Composable
private fun DaysBadge(book: FinishedBook, modifier: Modifier = Modifier) {
    val label = if (book.daysToComplete == 1) "1 day" else "${book.daysToComplete} days"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics { contentDescription = "Took $label to complete" },
        )
    }
}

@Composable
private fun GridShelf(
    books: List<FinishedBook>,
    cover: @Composable (FinishedBook, Modifier) -> Unit,
    onReveal: (FinishedBook) -> Unit,
    overlay: @Composable (FinishedBook) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(books, key = { it.id }) { book ->
            // No horizontal grid spacing: each cell's plank touches the next, forming one shelf.
            Column {
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .clickable(onClickLabel = "Show actions") { onReveal(book) }
                        .semantics { contentDescription = listOf(book.title, book.author).filter(String::isNotBlank).joinToString(" by ") },
                ) {
                    // The badge sits where a drawn cover prints its author; TalkBack still gets the name.
                    cover(
                        book.copy(author = ""),
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp, bottomStart = 2.dp)),
                    )
                    DaysBadge(book, Modifier.align(Alignment.BottomStart).padding(6.dp))
                    if (book.favorite) FavoriteMark(Modifier.align(Alignment.TopEnd).padding(4.dp))
                    overlay(book)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
    }
}

@Composable
private fun TimelineView(
    books: List<FinishedBook>,
    cover: @Composable (FinishedBook, Modifier) -> Unit,
    onReveal: (FinishedBook) -> Unit,
    overlay: @Composable (FinishedBook) -> Unit,
) {
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val rangeFormat = remember { DateTimeFormatter.ofPattern("d MMM") }
    val groups = remember(books) {
        books.sortedByDescending { it.finished }.groupBy { it.finished.format(monthFormat) }
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        groups.forEach { (month, monthBooks) ->
            item(key = month) {
                Text(
                    month,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            items(monthBooks, key = { it.id }) { book ->
                // The overlay sits outside the Row, where it would otherwise add a gap.
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .clickable(onClickLabel = "Show actions") { onReveal(book) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        cover(book, Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(3.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append(book.started.format(rangeFormat))
                                    append(" – ")
                                    append(book.finished.format(rangeFormat))
                                    if (book.readNumber > 1) append(" · read ${book.readNumber}")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (book.favorite) FavoriteMark()
                        DaysBadge(book)
                    }
                    overlay(book)
                }
            }
        }
    }
}
