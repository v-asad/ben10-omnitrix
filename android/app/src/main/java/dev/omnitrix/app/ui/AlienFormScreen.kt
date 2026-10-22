package dev.omnitrix.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.Transformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private val INK = Color(0xFF0D3008)
private val ALIEN_GREEN = Color(0xFF9BFF66)
private const val HOLD_TO_REVERT_MS = 1200

/** Opened while transformed: the white alien badge, a live countdown ring, hold to revert. */
@Composable
fun AlienFormScreen(transformation: Transformation, art: AlienArt, onRevert: () -> Unit, onExpired: () -> Unit) {
    val alien = Aliens.all[transformation.alien]
    LaunchedEffect(transformation.alien) { art.prefetch(transformation.alien, radius = 0) }
    val scope = rememberCoroutineScope()
    val hold = remember { Animatable(0f) }

    // Ticks once per second, aligned to the wall clock. Read only inside the draw lambda, so a
    // tick is a redraw, never a recomposition.
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(transformation) {
        while (now.longValue < transformation.endsAtMillis) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now.longValue = System.currentTimeMillis()
        }
        onExpired()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val charge = scope.launch {
                        hold.animateTo(1f, tween(HOLD_TO_REVERT_MS, easing = LinearEasing))
                        onRevert()
                    }
                    if (tryAwaitRelease() && hold.value < 1f) {
                        charge.cancel()
                        scope.launch { hold.animateTo(0f, tween(200)) }
                    }
                })
            },
    ) {
        val density = LocalDensity.current
        val scale = min(constraints.maxWidth, constraints.maxHeight) / VIRTUAL
        val measurer = rememberTextMeasurer(cacheSize = 8)
        val name = remember(scale) {
            measurer.fitted(alien.name, { orbitron(density, scale, it, FontWeight.Black, INK, 1.2f) }, 15f, 112f, scale)
        }
        val digits = remember(scale) { DigitAtlas(measurer, orbitron(density, scale, 32f, FontWeight.Black, INK)) }
        val clock = remember { CharArray(5) }
        val hint = remember(scale) {
            measurer.measure("HOLD TO REVERT", orbitron(density, scale, 9f, FontWeight.Bold, INK.copy(alpha = 0.7f), 2.5f))
        }
        val kit = remember { BadgeKit() }
        Canvas(Modifier.fillMaxSize()) {
            val remainingMs = (transformation.endsAtMillis - now.longValue).coerceAtLeast(0)
            val progress = remainingMs.toFloat() / transformation.totalMillis.coerceAtLeast(1)
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                // white chrome bezel + countdown arc
                drawCircle(kit.bezel, radius = 226f, center = CENTER, style = Stroke(26f))
                drawArc(ALIEN_GREEN, -90f, 360f * progress, useCenter = false, topLeft = Offset(14f, 14f), size = Size(452f, 452f),
                    style = Stroke(14f, cap = StrokeCap.Round), alpha = 0.3f)
                drawArc(ALIEN_GREEN, -90f, 360f * progress, useCenter = false, topLeft = Offset(14f, 14f), size = Size(452f, 452f),
                    style = Stroke(6f, cap = StrokeCap.Round))
                drawCircle(Color(0xFF0B0D0B), radius = 196f, center = CENTER)
                drawCircle(kit.glow, radius = 190f, center = CENTER)
                // the badge: white core, black wedges
                drawCircle(kit.core, radius = 176f, center = CENTER)
                clipPath(kit.coreClip) { drawPath(WEDGES, Color(0xFF060806)) }
                drawCircle(Color.Black, radius = 176f, center = CENTER, style = Stroke(5f))
                art.version.intValue   // redraw when the artwork arrives
                art.holo(transformation.alien)?.let { holo ->
                    val (o, s) = squareDst(Offset(240f, 112f), 84f)
                    drawImage(holo, srcSize = holo.srcSize(), dstOffset = o, dstSize = s, colorFilter = kit.inkFilter)
                }
                // hold-to-revert charge ring
                if (hold.value > 0f) {
                    drawArc(Color(0xFFFF4A3A), -90f, 360f * hold.value, useCenter = false, topLeft = Offset(58f, 58f), size = Size(364f, 364f),
                        style = Stroke(6f, cap = StrokeCap.Round))
                }
            }
            fun centered(layout: androidx.compose.ui.text.TextLayoutResult, y: Float) =
                drawText(layout, topLeft = Offset(240f * scale - layout.size.width / 2f, y * scale - layout.size.height / 2f))
            centered(name, 162f)
            val secs = (remainingMs / 1000).toInt()
            clock[0] = '0' + secs / 600 % 10; clock[1] = '0' + secs / 60 % 10; clock[2] = ':'
            clock[3] = '0' + secs % 60 / 10; clock[4] = '0' + secs % 10
            digits.draw(this, String(clock), 240f * scale, 340f * scale)
            centered(hint, 378f)
        }
    }
}

private class BadgeKit {
    val bezel = Brush.sweepGradient(listOf(Color.White, Color(0xFF9AA29A), Color.White, Color(0xFF9AA29A), Color.White), CENTER)
    val glow = Brush.radialGradient(listOf(Color(0x66E2FFD8), Color.Transparent), center = CENTER, radius = 196f)
    val core = Brush.radialGradient(listOf(Color.White, Color(0xFFF1FFF0), Color(0xFFB9DCB2)), center = Offset(240f, 180f), radius = 260f)
    val coreClip = circlePath(CENTER, 176f)
    val inkFilter = ColorFilter.tint(INK)
}
