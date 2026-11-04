package com.booktracker.ui.hub

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.booktracker.R
import com.booktracker.ui.theme.Ground
import com.booktracker.ui.theme.InkSoft
import com.booktracker.ui.theme.RectoIcons
import com.booktracker.ui.theme.StatWell
import com.booktracker.ui.theme.TactileButton
import com.booktracker.ui.theme.ambientGlow
import kotlinx.coroutines.delay

/** How long the celebration stays before it closes by itself. */
private const val ShowMillis = 3200L
private val RayLight = Color(0xFFF2F1ED)

@Immutable
data class LoggedPages(val title: String, val pages: Int, val page: Int, val totalPages: Int)

/**
 * Shown once a run of page taps settles. It covers the whole screen, closes itself, and any tap
 * closes it sooner.
 */
@Composable
fun LogCelebration(logged: LoggedPages, streak: Int, accent: Color, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val dismiss by rememberUpdatedState(onDismiss)
    val pop = remember { Animatable(0.7f) }
    LaunchedEffect(logged) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    LaunchedEffect(logged) {
        delay(ShowMillis)
        dismiss()
    }
    val pagesLabel = if (logged.pages == 1) "+1 page" else "+${logged.pages} pages"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ground.copy(alpha = 0.94f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 140.dp)
                    .size(300.dp)
                    .ambientGlow(accent, strength = 0.35f),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.6f))
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        .size(300.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color(0xFFE6E5E1)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(272.dp)
                            .border(6.dp, Color.White, CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val rays = 20
                            val sweep = 360f / rays
                            for (index in 0 until rays) {
                                drawArc(
                                    color = if (index % 2 == 0) Color.White else RayLight,
                                    startAngle = index * sweep,
                                    sweepAngle = sweep,
                                    useCenter = true,
                                )
                            }
                        }
                        Image(
                            painterResource(R.drawable.celebration_reader),
                            contentDescription = "A smiling book holding a huge stack of pages",
                            modifier = Modifier.size(256.dp),
                        )
                    }
                }
                Text(
                    pagesLabel,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 48.sp,
                        lineHeight = 52.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "$pagesLabel logged"
                        },
                )
                Text(
                    "${logged.title} · now on page ${logged.page} of ${logged.totalPages}",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (streak > 0) {
                    StatWell(
                        value = if (streak == 1) "1-day streak" else "$streak-day streak",
                        label = "kept going",
                        icon = RectoIcons.Streak,
                        iconTint = Color(0xFFE2A34F),
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                TactileButton(
                    onClick = onDismiss,
                    accent = accent,
                    corner = 18.dp,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Keep reading") }
                Text(
                    "Closes by itself in a moment",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
