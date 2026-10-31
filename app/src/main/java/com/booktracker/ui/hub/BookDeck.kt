package com.booktracker.ui.hub

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.booktracker.ui.theme.DeckFront
import com.booktracker.ui.theme.NeumorphicCard
import com.booktracker.ui.theme.StackSheetFar
import com.booktracker.ui.theme.StackSheetNear
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private const val MAX_VISIBLE_CARDS = 3
private const val SWIPE_THRESHOLD = 0.22f
private val PeekOffset = 20.dp
private const val SCALE_STEP = 0.07f
private const val PI_F = Math.PI.toFloat()

@Stable
class BookDeckState(initialFrontKey: Long?) {
    var frontKey by mutableStateOf(initialFrontKey)
    internal var keys by mutableStateOf(emptyList<Long>())

    /** 0 at rest, 1 once the front card has reached the back. Every card's pose derives from it. */
    internal val flight = Animatable(0f)
    internal val drag = Animatable(0f)
    internal val exit = Animatable(0f)
    internal var direction by mutableFloatStateOf(-1f)

    val frontIndex: Int get() = keys.indexOf(frontKey).coerceAtLeast(0)

    suspend fun advance(toDirection: Float = -1f) {
        if (keys.size < 2) {
            drag.animateTo(0f, spring())
            return
        }
        direction = toDirection
        flight.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
        frontKey = keys[(frontIndex + 1) % keys.size]
        drag.snapTo(0f)
        flight.snapTo(0f)
    }

    suspend fun goTo(key: Long) {
        while (frontKey != key && key in keys) advance()
    }

    /** Plays the front card out. Call before removing it, so it leaves rather than vanishes. */
    suspend fun playExit() {
        exit.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
    }
}

@Composable
fun rememberBookDeckState(initialFrontKey: Long?): BookDeckState =
    remember { BookDeckState(initialFrontKey) }

/**
 * Layered cards. A swipe or tap sends the front card out to the side and round to the back while
 * the others step forward. Only the front card shows its content; the rest are blank paper.
 * Every book's content must have the same height, so that the layers stay aligned.
 */
@Composable
fun <T> BookDeck(
    state: BookDeckState,
    items: List<T>,
    itemKey: (T) -> Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val keys = items.map(itemKey)
    SideEffect { state.keys = keys }
    LaunchedEffect(keys) {
        state.exit.snapTo(0f)
        if (state.frontKey !in keys) state.frontKey = keys.firstOrNull()
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val peekPx = with(density) { PeekOffset.toPx() }
    val exitPx = with(density) { 48.dp.toPx() }
    val advanceOnce by rememberUpdatedState<(Float) -> Unit> { direction ->
        if (!state.flight.isRunning) scope.launch { state.advance(direction) }
    }

    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val visible = min(items.size, MAX_VISIBLE_CARDS)
        val front = keys.indexOf(state.frontKey).coerceAtLeast(0)

        for (depth in visible - 1 downTo 0) {
            val item = items[(front + depth) % items.size]
            key(itemKey(item)) {
                val t = state.flight.value
                val isFront = depth == 0
                val pose = if (isFront) t * (visible - 1) else depth - t
                val swing = sin(PI_F * t) * state.direction

                val gestures = if (isFront && enabled) {
                    Modifier
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val travelled = state.drag.value
                                    if (abs(travelled) > widthPx * SWIPE_THRESHOLD) {
                                        advanceOnce(if (travelled < 0) -1f else 1f)
                                    } else {
                                        scope.launch { state.drag.animateTo(0f, spring()) }
                                    }
                                },
                                onDragCancel = { scope.launch { state.drag.animateTo(0f, spring()) } },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    scope.launch { state.drag.snapTo(state.drag.value + amount) }
                                },
                            )
                        }
                        .pointerInput(Unit) { detectTapGestures(onTap = { advanceOnce(-1f) }) }
                } else {
                    Modifier
                }

                NeumorphicCard(
                    color = deckColor(pose),
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isFront) (if (t < 0.5f) 10f else -1f) else visible - pose)
                        .graphicsLayer {
                            val scale = 1f - SCALE_STEP * pose
                            scaleX = scale
                            scaleY = scale
                            // Pinned at the top edge, so back cards rise above the front one.
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationY = -pose * peekPx + (if (isFront) state.exit.value * exitPx else 0f)
                            if (isFront) {
                                val dragged = state.drag.value * (1f - t)
                                translationX = dragged + swing * widthPx * 1.1f
                                rotationZ = dragged / widthPx * 8f + swing * 8f
                                alpha = 1f - state.exit.value
                            }
                        }
                        .then(gestures),
                ) {
                    Box(
                        Modifier
                            .graphicsLayer { alpha = contentAlpha(depth, state.flight.value) }
                            .then(
                                if (isFront) {
                                    Modifier.semantics {
                                        customActions = listOf(
                                            CustomAccessibilityAction("Next book") {
                                                advanceOnce(-1f)
                                                true
                                            },
                                        )
                                    }
                                } else {
                                    Modifier.clearAndSetSemantics { }
                                },
                            ),
                    ) {
                        content(item)
                    }
                }
            }
        }
    }
}

private fun deckColor(pose: Float): Color =
    if (pose <= 1f) lerp(DeckFront, StackSheetNear, pose) else lerp(StackSheetNear, StackSheetFar, pose - 1f)

/** The leaving card's text is gone before it swings behind, and the next fades in as it lands. */
private fun contentAlpha(depth: Int, t: Float): Float = when (depth) {
    0 -> 1f - ((t - 0.15f) / 0.3f).coerceIn(0f, 1f)
    1 -> ((t - 0.45f) / 0.4f).coerceIn(0f, 1f)
    else -> 0f
}
