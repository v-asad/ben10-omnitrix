package dev.omnitrix.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/** Fraction of the screen width, from the left edge, where a swipe counts as "back". */
private const val EDGE_FRACTION = 0.07f
/** How far (fraction of width) you drag before letting go goes back. */
private const val COMMIT_FRACTION = 0.3f

/**
 * Wear-style swipe back from the left edge, for every Omnitrix screen.
 *
 * System swipe-to-dismiss is off because it steals the selector's carousel drags; this restores
 * the gesture but only from the edge. A touch that starts in the edge strip is claimed before the
 * screen sees it, the content follows the finger (the watch face shows through the translucent
 * window) and releasing past [COMMIT_FRACTION] runs [onBack].
 */
@Composable
fun EdgeSwipeBack(onBack: () -> Unit, content: @Composable () -> Unit) {
    val back by rememberUpdatedState(onBack)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val width = size.width.toFloat()
                awaitEachGesture {
                    // Initial pass: we see the touch before the screen does and claim edge touches.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.position.x > width * EDGE_FRACTION) return@awaitEachGesture
                    down.consume()
                    var dx = 0f
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        dx = (change.position.x - down.position.x).coerceAtLeast(0f)
                        scope.launch { offset.snapTo(dx) }
                    }
                    scope.launch {
                        if (dx > width * COMMIT_FRACTION) {
                            offset.animateTo(width, tween(140))
                            back()
                            offset.snapTo(0f)
                        } else {
                            offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                        }
                    }
                }
            }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (offset.value / size.width.coerceAtLeast(1f)) * 0.7f
            },
    ) { content() }
}
