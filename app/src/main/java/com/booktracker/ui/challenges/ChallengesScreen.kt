package com.booktracker.ui.challenges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.booktracker.ui.theme.LocalReadingColors

private enum class ChallengeTab(val label: String) { Today("Today"), Week("Week"), Year("Year") }

enum class WeekDayState(val mark: String, val label: String) {
    Read("✓", "read"),
    Rest("–", "rest day"),
    Saved("▮", "saved by a bookmark"),
    Missed("×", "missed"),
    Upcoming("", "still to come"),
}

@Immutable
data class WeekDay(val initial: String, val name: String, val state: WeekDayState)

@Immutable
data class ChallengesUiState(
    val streak: Int,
    val bookmarks: Int,
    val challengeTitle: String,
    val tier: ChallengeTier?,
    val tinyHint: String,
    val steadyHint: String,
    val deepHint: String,
    val busy: Boolean,
    val week: List<WeekDay>,
    val weekMilestones: List<Milestone>,
    val yearMilestones: List<Milestone>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesScreen(
    state: ChallengesUiState,
    onOpenHabitPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(ChallengeTab.Today) }
    LazyColumn(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StreakHeader(state, onOpenHabitPlan) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ChallengeTab.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = tab == option,
                        onClick = { tab = option },
                        shape = SegmentedButtonDefaults.itemShape(index, ChallengeTab.entries.size),
                    ) { Text(option.label) }
                }
            }
        }
        when (tab) {
            ChallengeTab.Today -> item { TodayCard(state) }
            ChallengeTab.Week -> {
                item { WeekStrip(state.week) }
                items(state.weekMilestones.size) { MilestoneCard(state.weekMilestones[it]) }
            }
            ChallengeTab.Year -> items(state.yearMilestones.size) { MilestoneCard(state.yearMilestones[it]) }
        }
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
private fun StreakHeader(state: ChallengesUiState, onOpenHabitPlan: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("${state.streak} day streak", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${state.bookmarks} of $MAX_BOOKMARKS bookmarks saved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenHabitPlan) { Text("Habit plan") }
    }
}

@Composable
private fun TodayCard(state: ChallengesUiState) {
    PaperCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (state.busy) "Busy week: one page is enough" else "Today's challenge",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(state.challengeTitle, style = MaterialTheme.typography.headlineSmall)
            TierRow(ChallengeTier.Tiny, state.tinyHint, state.tier)
            if (!state.busy) {
                TierRow(ChallengeTier.Steady, state.steadyHint, state.tier)
                TierRow(ChallengeTier.Deep, state.deepHint, state.tier)
            }
        }
    }
}

@Composable
private fun TierRow(tier: ChallengeTier, hint: String, earned: ChallengeTier?) {
    val done = earned != null && earned >= tier
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (done) "✓" else "○",
            style = MaterialTheme.typography.labelLarge,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Column {
            Text(tier.label, style = MaterialTheme.typography.labelLarge)
            Text(
                hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekStrip(week: List<WeekDay>) {
    PaperCard {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEach { day ->
                val read = day.state == WeekDayState.Read
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(day.initial, style = MaterialTheme.typography.labelMedium)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (read) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            )
                            .clearAndSetSemantics { contentDescription = "${day.name}, ${day.state.label}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(day.state.mark, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneCard(milestone: Milestone) {
    PaperCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(milestone.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (milestone.earned) "✓ Earned" else "${milestone.progress} of ${milestone.goal}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                milestone.detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (milestone.progress.toFloat() / milestone.goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
