package com.booktracker.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OVERLAY_MILLIS = 1200L

/**
 * Covers the form, draws a tick, then calls [onFinished] once. It takes all touches and Back, so
 * the form can't be used or left twice. With system animations off, the tick simply appears.
 */
@Composable
fun BookAddedOverlay(title: String, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val finish by rememberUpdatedState(onFinished)
    val appear = remember { Animatable(0f) }
    val pop = remember { Animatable(0.6f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { appear.animateTo(1f, tween(160)) }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f)) }
        launch { tick.animateTo(1f, tween(360, delayMillis = 120, easing = FastOutSlowInEasing)) }
        delay(OVERLAY_MILLIS)
        finish()
    }
    BackHandler { }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(colors.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(
                Modifier
                    .padding(bottom = 16.dp)
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .clip(CircleShape)
                    .background(colors.primary),
            ) {
                val full = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.74f, size.height * 0.34f)
                }
                val measure = PathMeasure().apply { setPath(full, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * tick.value, drawn, true)
                drawPath(
                    drawn,
                    colors.onPrimary,
                    style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            Text(
                "Added to Reading Now",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
