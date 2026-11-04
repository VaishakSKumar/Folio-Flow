package com.booktracker.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.booktracker.ui.library.coverAccent
import com.booktracker.ui.theme.Alert
import com.booktracker.ui.theme.Ink
import com.booktracker.ui.theme.InkSoft
import com.booktracker.ui.theme.LocalAccent
import com.booktracker.ui.theme.LocalReadingColors
import com.booktracker.ui.theme.Raised
import com.booktracker.ui.theme.RectoIcons
import com.booktracker.ui.theme.SectionLabel
import com.booktracker.ui.theme.SurfaceTone
import com.booktracker.ui.theme.TactileCard
import com.booktracker.ui.theme.TactileIconButton
import com.booktracker.ui.theme.ambientGlow
import com.booktracker.ui.theme.pressedWell
import com.booktracker.ui.theme.raised
import com.booktracker.ui.theme.softShadow
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
    val glowColor: Int? = null,
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
    backupCard: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (tab) {
            StatsTab.Stats -> StatsPane(state, backupCard)
            StatsTab.Archive -> ArchivePane(state.finished, cover, archiveActions)
        }
    }
}

@Composable
private fun StatsPane(state: StatsUiState, backupCard: @Composable () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(AveragePeriod.Day) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Stats",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        item { backupCard() }
    }
}

@Composable
internal fun PaperCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TactileCard(modifier, content = content)
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
    // The chosen period is pressed in, like a key held down.
    Box(
        modifier
            .then(if (selected) Modifier.pressedWell(16.dp) else Modifier.raised(16.dp, SurfaceTone))
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(average.period.label, style = MaterialTheme.typography.labelMedium, color = if (selected) LocalAccent.current else Ink)
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
    val bar = Color(0xFF3A3733)
    val current = LocalAccent.current
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

private enum class VaultFilter(val label: String) { All("All"), Favourites("Favourites") }

private val VaultDate = DateTimeFormatter.ofPattern("d MMM")

/** The Vault: finished books as a grid, or as a timeline. Tapping a book shows its actions on it. */
@Composable
private fun ArchivePane(
    books: List<FinishedBook>,
    cover: @Composable (FinishedBook, Modifier) -> Unit,
    actions: ArchiveActions,
) {
    var view by rememberSaveable { mutableStateOf(ArchiveView.Shelf) }
    var filter by rememberSaveable { mutableStateOf(VaultFilter.All) }
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<FinishedBook?>(null) }
    val favourites = books.count { it.favorite }
    val shown = if (filter == VaultFilter.Favourites) books.filter { it.favorite } else books
    val bookActions = VaultBookActions(
        onToggleFavorite = { actions.onToggleFavorite(it.id) },
        onOpen = {
            revealedId = null
            actions.onOpenBook(it.id)
        },
        onDelete = {
            revealedId = null
            deleting = it
        },
    )
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
                ) { Text("Delete", color = Alert) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Ink) } },
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Vault", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(
                    "${books.size} finished · " + if (favourites == 1) "1 favourite" else "$favourites favourites",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
            }
            val toTimeline = view == ArchiveView.Shelf
            TactileIconButton(
                onClick = {
                    revealedId = null
                    view = if (toTimeline) ArchiveView.Timeline else ArchiveView.Shelf
                },
                icon = if (toTimeline) RectoIcons.Timeline else RectoIcons.Grid,
                contentDescription = if (toTimeline) "Show as timeline" else "Show as grid",
                size = 44.dp,
                corner = 14.dp,
                tint = InkSoft,
            )
        }
        FilterTabs(filter, onSelect = {
            revealedId = null
            filter = it
        })
        when {
            books.isEmpty() -> VaultEmpty("Books you finish land here, with the days each one took.")
            shown.isEmpty() -> VaultEmpty("Tap a finished book, then the heart, to keep it here.")
            else -> Crossfade(view, label = "vaultView") { shownView ->
                when (shownView) {
                    ArchiveView.Shelf -> VaultGrid(shown, revealedId, cover, onTap = { revealedId = if (revealedId == it.id) null else it.id }, bookActions)
                    ArchiveView.Timeline -> TimelineView(
                        shown,
                        cover,
                        onReveal = { revealedId = it.id },
                        overlay = { book ->
                            if (book.id == revealedId) {
                                ArchiveActionOverlay(book, onDismiss = { revealedId = null }, bookActions)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Immutable
private class VaultBookActions(
    val onToggleFavorite: (FinishedBook) -> Unit,
    val onOpen: (FinishedBook) -> Unit,
    val onDelete: (FinishedBook) -> Unit,
)

@Composable
private fun VaultEmpty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = InkSoft,
        modifier = Modifier.padding(24.dp),
    )
}

/** A track pressed into the ground, with the chosen tab raised out of it. */
@Composable
private fun FilterTabs(selected: VaultFilter, onSelect: (VaultFilter) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .fillMaxWidth()
            .height(48.dp)
            .pressedWell(16.dp)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VaultFilter.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isSelected) Modifier.raised(12.dp, Color(0xFF222222), depth = 4.dp) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (isSelected) Ink else InkSoft,
                )
            }
        }
    }
}

@Composable
private fun VaultGrid(
    books: List<FinishedBook>,
    revealedId: Long?,
    cover: @Composable (FinishedBook, Modifier) -> Unit,
    onTap: (FinishedBook) -> Unit,
    actions: VaultBookActions,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(books, key = { it.id }) { book ->
            VaultTile(book, book.id == revealedId, cover, onTap = { onTap(book) }, actions)
        }
    }
}

/** When tapped, the book lifts in its own light and shows Fav, Details and Delete on its cover. */
@Composable
private fun VaultTile(
    book: FinishedBook,
    revealed: Boolean,
    cover: @Composable (FinishedBook, Modifier) -> Unit,
    onTap: () -> Unit,
    actions: VaultBookActions,
) {
    val lift by animateFloatAsState(if (revealed) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "lift")
    val glow = coverAccent(book.coverPath, book.title, book.glowColor)
    val shape = RoundedCornerShape(14.dp)
    val days = if (book.daysToComplete == 1) "1 day" else "${book.daysToComplete} days"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(172.dp)) {
            if (lift > 0f) {
                Box(Modifier.matchParentSize().graphicsLayer { alpha = lift }.ambientGlow(glow, 0.55f, 0.7f))
            }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationY = -6.dp.toPx() * lift
                        val scale = 1f + 0.03f * lift
                        scaleX = scale
                        scaleY = scale
                    }
                    .softShadow(Color(0x99000000), 16.dp, Raised, 6.dp, 8.dp, 14.dp)
                    .clip(shape)
                    .clickable(onClickLabel = if (revealed) "Hide actions" else "Show actions", onClick = onTap)
                    .semantics { contentDescription = listOf(book.title, book.author).filter(String::isNotBlank).joinToString(" by ") },
            ) {
                cover(book.copy(author = ""), Modifier.fillMaxSize())
                if (book.favorite && !revealed) {
                    Icon(
                        RectoIcons.FavouriteFilled,
                        contentDescription = "Favourite",
                        tint = Alert,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(18.dp),
                    )
                }
                if (revealed) {
                    Row(
                        Modifier.matchParentSize().background(Color(0xB80A0A0A)),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TileAction(
                            icon = if (book.favorite) RectoIcons.FavouriteFilled else RectoIcons.Favourite,
                            label = "Fav",
                            description = if (book.favorite) "Remove from favourites" else "Add to favourites",
                            tint = Alert,
                            selected = book.favorite,
                            onClick = { actions.onToggleFavorite(book) },
                        )
                        TileAction(RectoIcons.Info, "Details", "Open details and notes", Ink, onClick = { actions.onOpen(book) })
                        TileAction(RectoIcons.Delete, "Delete", "Delete ${book.title}", Ink, onClick = { actions.onDelete(book) })
                    }
                }
            }
        }
        Column {
            Text(book.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Finished ${book.finished.format(VaultDate)} · $days" + if (book.readNumber > 1) " · read ${book.readNumber}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TileAction(
    icon: ImageVector,
    label: String,
    description: String,
    tint: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        TactileIconButton(onClick, icon, description, size = 44.dp, tint = tint, selected = selected)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Ink)
    }
}

/** Floats over the tapped book in the timeline, where rows are too short to hold the buttons. */
@Composable
private fun ArchiveActionOverlay(book: FinishedBook, onDismiss: () -> Unit, actions: VaultBookActions) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Row(
            Modifier.raised(22.dp, SurfaceTone).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TileAction(
                icon = if (book.favorite) RectoIcons.FavouriteFilled else RectoIcons.Favourite,
                label = "Fav",
                description = if (book.favorite) "Remove from favourites" else "Add to favourites",
                tint = Alert,
                selected = book.favorite,
                onClick = { actions.onToggleFavorite(book) },
            )
            TileAction(RectoIcons.Info, "Details", "Open details and notes", Ink, onClick = { actions.onOpen(book) })
            TileAction(RectoIcons.Delete, "Delete", "Delete ${book.title}", Ink, onClick = { actions.onDelete(book) })
        }
    }
}

@Composable
private fun DaysBadge(book: FinishedBook, modifier: Modifier = Modifier) {
    val label = if (book.daysToComplete == 1) "1 day" else "${book.daysToComplete} days"
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .pressedWell(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Took $label to complete" },
    )
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
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        groups.forEach { (month, monthBooks) ->
            item(key = month) {
                SectionLabel(month, Modifier.padding(vertical = 12.dp))
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
                        cover(book, Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(6.dp)))
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
                                color = InkSoft,
                            )
                        }
                        if (book.favorite) {
                            Icon(RectoIcons.FavouriteFilled, contentDescription = "Favourite", tint = Alert, modifier = Modifier.size(18.dp))
                        }
                        DaysBadge(book)
                    }
                    overlay(book)
                }
            }
        }
    }
}
