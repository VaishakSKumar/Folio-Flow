package com.booktracker.ui.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.booktracker.data.MAX_REST_DAYS
import com.booktracker.data.ReadingGoals
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val MIN_TARGET = 5f
private const val MAX_TARGET = 100f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitPlanScreen(
    goals: ReadingGoals,
    today: LocalDate,
    onTargetChange: (Int) -> Unit,
    onToggleRestDay: (DayOfWeek) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragTarget by remember(goals.targetPerDay) { mutableFloatStateOf(goals.targetPerDay.toFloat()) }
    val busy = goals.isBusy(today)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Habit plan", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Done") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Daily target", style = MaterialTheme.typography.titleMedium)
                Text("${dragTarget.roundToInt()} pages a day", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = dragTarget,
                    onValueChange = { dragTarget = it },
                    onValueChangeFinished = { onTargetChange(dragTarget.roundToInt()) },
                    valueRange = MIN_TARGET..MAX_TARGET,
                    steps = 18,
                    modifier = Modifier.semantics { contentDescription = "Daily target in pages" },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Rest days", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pick up to $MAX_REST_DAYS days. They never break a streak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = day in goals.restDays
                        FilterChip(
                            selected = selected,
                            enabled = selected || goals.restDays.size < MAX_REST_DAYS,
                            onClick = { onToggleRestDay(day) },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Busy week", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "For 7 days, one page is enough and pacing stops judging.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = busy, onCheckedChange = onBusyChange)
            }
        }
    }
}
