package com.booktracker.ui.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** How long the book stays on screen for the celebration before it moves to the Archive. */
const val CelebrationMillis = 1400L

private const val STAR_COUNT = 28

private class Star(
    val angle: Float,
    val reach: Float,
    val size: Float,
    val spin: Float,
    val color: Color,
)

/** A four-pointed star of radius 1, scaled up when drawn. */
private val StarPath = Path().apply {
    moveTo(0f, -1f)
    quadraticBezierTo(0f, 0f, 1f, 0f)
    quadraticBezierTo(0f, 0f, 0f, 1f)
    quadraticBezierTo(0f, 0f, -1f, 0f)
    quadraticBezierTo(0f, 0f, 0f, -1f)
    close()
}

/**
 * Stars burst from the middle of the card behind a banner. With system animations off the stars
 * finish instantly and the banner simply appears, so the moment is still marked.
 */
@Composable
fun CompletionCelebration(title: String, glow: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val stars = remember(glow) {
        val palette = listOf(colors.tertiary, colors.primary, colors.onSurface, glow.copy(alpha = 1f))
        val random = Random(title.hashCode())
        List(STAR_COUNT) { index ->
            Star(
                angle = index * 2.4f + random.nextFloat() * 0.4f,
                reach = 0.45f + random.nextFloat() * 0.55f,
                size = 6f + random.nextFloat() * 10f,
                spin = (random.nextFloat() - 0.5f) * 540f,
                color = palette[index % palette.size],
            )
        }
    }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        burst.animateTo(1f, tween(CelebrationMillis.toInt(), easing = LinearEasing))
    }
    val bannerState = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            val travel = 1f - (1f - t).pow(3)
            val fade = (1f - (t - 0.6f) / 0.4f).coerceIn(0f, 1f)
            if (t > 0f && t < 1f) {
                stars.forEach { star ->
                    val distance = size.maxDimension * 0.62f * star.reach * travel
                    val position = center + Offset(cos(star.angle), sin(star.angle)) * distance
                    translate(position.x, position.y) {
                        rotate(star.spin * t, pivot = Offset.Zero) {
                            scale(star.size * density, pivot = Offset.Zero) {
                                drawPath(StarPath, star.color, alpha = fade)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visibleState = bannerState,
            enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.55f), initialScale = 0.7f),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Hurray! Completed", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
