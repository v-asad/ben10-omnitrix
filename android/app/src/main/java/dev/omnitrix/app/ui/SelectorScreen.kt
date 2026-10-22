package dev.omnitrix.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.Energy
import dev.omnitrix.app.Haptics
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val ROTARY_STEP_PX = 48f
private val HOLO_CENTER = Offset(240f, 196f)
private const val HOLO_RADIUS = 96f
private const val HOLO_SIZE = 196f

/**
 * The dial is up: a DNA carousel around the bezel, the selected alien projected as a hologram.
 * Bezel / crown rotation or dragging around the edge spins the carousel; tap the hologram to
 * transform; swipe up for the dossier.
 *
 * The roster is longer than a full circle, so the carousel is a rolling window: [SPACING_DEG]
 * apart, fading toward the bottom. Performance: one Canvas; animated values are read only in the
 * draw lambda (redraw without recomposition); paths, brushes, filters and text layouts are built
 * once; art decodes off the main thread with neighbours prefetched.
 */
@Composable
fun SelectorScreen(
    energy: Energy,
    art: AlienArt,
    initialAlien: Int,
    onSelectedChange: (Int) -> Unit,
    onTransform: (Int) -> Unit,
    onOpenDossier: (Int) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val count = Aliens.size

    val position = remember { Animatable(initialAlien.toFloat()) }   // fractional index at 12 o'clock
    val target = remember { floatArrayOf(initialAlien.toFloat()) }      // plain holder: no recomposition
    var selected by remember { mutableIntStateOf(initialAlien) }
    val materialize = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    var transforming by remember { mutableStateOf(false) }

    val loops = rememberInfiniteTransition(label = "selector")
    val scan = loops.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "scan")
    val spin = loops.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "spin")

    LaunchedEffect(Unit) { materialize.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
    LaunchedEffect(selected) { art.prefetch(selected) }

    fun settle(index: Int) {
        if (index == selected) return
        selected = index
        onSelectedChange(index)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        scope.launch {
            materialize.snapTo(0f)
            materialize.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f))
        }
    }

    fun step(direction: Int) {
        if (transforming) return
        target[0] = target[0].roundToInt() + direction.toFloat()
        settle(nearestAlien(target[0]))
        scope.launch { position.animateTo(target[0], spring(dampingRatio = 0.8f, stiffness = 380f)) }
    }

    fun slam() {
        if (transforming) return
        if (energy.locked) {
            Haptics.denied(context)
            scope.launch {
                repeat(3) { shake.animateTo(9f, tween(35)); shake.animateTo(-9f, tween(35)) }
                shake.animateTo(0f, tween(35))
            }
            return
        }
        transforming = true
        Haptics.transform(context)
        scope.launch {
            materialize.animateTo(0f, tween(150))     // collapse into the projector, then the transformation plays
            onTransform(selected)
        }
    }

    val focus = remember { FocusRequester() }
    val rotary = remember { floatArrayOf(0f) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                rotary[0] += event.verticalScrollPixels
                while (rotary[0] >= ROTARY_STEP_PX) { step(1); rotary[0] -= ROTARY_STEP_PX }
                while (rotary[0] <= -ROTARY_STEP_PX) { step(-1); rotary[0] += ROTARY_STEP_PX }
                true
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(energy) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    val onRing = (down.position - c).getDistance() > radius * 0.7f
                    var lastAngle = angleOf(down.position, c)
                    var moved = false
                    var dy = 0f
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (!moved || transforming) continue
                        if (onRing) {
                            // the ring follows the finger: clockwise drag brings earlier aliens up
                            val angle = angleOf(change.position, c)
                            target[0] -= wrap(angle - lastAngle) / SPACING_DEG
                            lastAngle = angle
                            scope.launch { position.snapTo(target[0]) }
                            settle(nearestAlien(target[0]))
                            change.consume()
                        } else {
                            dy = change.position.y - down.position.y
                        }
                    }
                    when {
                        !moved && !onRing -> slam()
                        moved && onRing -> {
                            target[0] = target[0].roundToInt().toFloat()
                            scope.launch { position.animateTo(target[0], spring(dampingRatio = 0.8f, stiffness = 380f)) }
                        }
                        moved && dy < -radius * 0.25f -> onOpenDossier(selected)
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val scale = min(constraints.maxWidth, constraints.maxHeight) / VIRTUAL
        val alien = Aliens.all[selected]
        val main = energy.main
        val measurer = rememberTextMeasurer(cacheSize = 16)

        // Text: measured when the selection changes, never per frame.
        val name = remember(selected, scale, energy) {
            measurer.fitted(alien.name, { orbitron(density, scale, it, FontWeight.Black, main, 2.5f) }, 25f, 300f, scale)
        }
        val origin = remember(selected, scale, energy) {
            measurer.fitted("${alien.species} · ${alien.planet}".uppercase(),
                { orbitron(density, scale, it, FontWeight.Bold, energy.bright.copy(alpha = 0.75f), 1f) }, 11f, 300f, scale)
        }
        val index = remember(selected, scale) {
            measurer.measure("DNA %02d / %02d · %s".format(selected + 1, count, alien.era),
                orbitron(density, scale, 10f, FontWeight.Bold, Color(0xFF6F9A66), 1.5f))
        }
        val cta = remember(scale, energy) {
            measurer.measure(if (energy.locked) "NO ENERGY · CHARGE" else "TAP TO TRANSFORM",
                orbitron(density, scale, 10f, FontWeight.Bold, main, 3f))
        }
        val statLabels = remember(scale) {
            Aliens.STAT_LABELS.map { measurer.measure(it, orbitron(density, scale, 8f, FontWeight.Bold, Color(0xFF6F9A66), 1f)) }
        }
        val kit = remember(energy) { SelectorKit(energy) }

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shake.value * scale },
        ) {
            art.version.intValue   // redraw when artwork finishes loading
            val pos = position.value
            val t = scan.value
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                drawBezel(kit, art, pos, count)
                drawCore(kit, spin.value)
                drawHologram(kit, art, selected, materialize.value, t, energy.locked)
                drawStats(kit, alien.stats)
            }
            drawCentered(index, 72f, scale)
            drawCentered(name, 300f, scale)
            drawCentered(origin, 331f, scale)
            Aliens.STAT_LABELS.indices.forEach { k ->
                val layout = statLabels[k]
                drawText(layout, topLeft = Offset((169f + k * 47f) * scale - layout.size.width / 2f, 349f * scale))
            }
            drawCentered(cta, 382f, scale, alpha = 0.55f + 0.45f * abs(sin(t * PI.toFloat())))

        }
    }
}

/** Everything that depends only on the energy state, allocated once. */
private class SelectorKit(val energy: Energy) {
    val main = energy.main
    val mainFilter = ColorFilter.tint(energy.main)
    val holoFilter = hologramFilter(energy.bright)
    val dimFilter = ColorFilter.tint(if (energy.locked) Color(0xFF5A2A24) else Color(0xFF4C6E44))
    val ringBrush = Brush.sweepGradient(listOf(energy.ring, Color(0xFF15181A), energy.ring, Color(0xFF15181A), energy.ring), CENTER)
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 6f))
    val notch = Path().apply { moveTo(231f, 0f); lineTo(249f, 0f); lineTo(240f, 8f); close() }
    val cone = Path().apply { moveTo(198f, 292f); lineTo(282f, 292f); lineTo(334f, 104f); lineTo(146f, 104f); close() }
    val coneBrush = Brush.verticalGradient(listOf(energy.main.copy(alpha = 0f), energy.main.copy(alpha = 0.32f)), startY = 104f, endY = 292f)
    val padBrush = Brush.radialGradient(listOf(energy.main.copy(alpha = 0.55f), Color.Transparent), center = Offset(240f, 292f), radius = 56f)
    val scanlines = Brush.verticalGradient(
        0f to Color(0x73000000), 0.5f to Color(0x73000000), 0.5f to Color.Transparent, 1f to Color.Transparent,
        startY = 0f, endY = 4f, tileMode = TileMode.Repeated,
    )
    val edgeFade = Brush.radialGradient(0.62f to Color.Transparent, 1f to Color.Black, center = HOLO_CENTER, radius = HOLO_RADIUS)
    val ticks = Path().apply {
        for (i in 0 until 60) {
            val a = polar(if (i % 5 == 0) 186f else 189f, i * 6f)
            val b = polar(193f, i * 6f)
            moveTo(a.x, a.y); lineTo(b.x, b.y)
        }
    }
}

private fun DrawScope.drawBezel(kit: SelectorKit, art: AlienArt, pos: Float, count: Int) {
    drawCircle(kit.ringBrush, radius = 226f, center = CENTER, style = Stroke(26f), alpha = 0.5f)
    // the chamber at 12 o'clock glows
    drawArc(kit.main, -90f - 13f, 26f, useCenter = false, topLeft = Offset(14f, 14f), size = Size(452f, 452f),
        style = Stroke(26f), alpha = 0.18f)
    val top = nearestAlien(pos)
    for (i in 0 until count) {
        val a = ringDistance(i, pos, count) * SPACING_DEG
        if (abs(a) > 172f) continue                         // rolling window: only what fits on the bezel
        val sel = i == top
        val p = polar(206f, a)
        val alpha = if (sel) 1f else max(0.18f, 1f - abs(a) / 180f)
        drawCircle(Color.Black, radius = if (sel) 25f else 17f, center = p)
        drawCircle(if (sel) kit.main else Color(0xFF2A3A28), radius = if (sel) 25f else 17f, center = p,
            style = Stroke(if (sel) 3f else 2f), alpha = alpha)
        art.icon(i)?.let { icon ->
            val (offset, size) = squareDst(p, if (sel) 38f else 26f)
            drawImage(icon, srcSize = icon.srcSize(), dstOffset = offset, dstSize = size,
                alpha = alpha, colorFilter = if (sel) kit.mainFilter else kit.dimFilter)
        }
    }
    drawPath(kit.notch, kit.main)
}

private fun DrawScope.drawCore(kit: SelectorKit, spinDeg: Float) {
    drawCircle(Color.Black, radius = 176f, center = CENTER)
    drawCircle(kit.main, radius = 176f, center = CENTER, style = Stroke(2f), alpha = 0.9f)
    drawPath(kit.ticks, Color(0xFF1F2E1E), style = Stroke(1.5f))
    rotate(spinDeg, pivot = CENTER) {
        drawCircle(kit.main, radius = 168f, center = CENTER, style = Stroke(1f, pathEffect = kit.dash), alpha = 0.45f)
    }
}

/**
 * Hologram, projector beam, orbit ring and sparks. No clipping (stencil clips are the costliest
 * thing on a watch GPU): the art fits inside the circle, scanlines are drawn as a circle and the
 * sweep line is cut to the chord at its height.
 */
private fun DrawScope.drawHologram(kit: SelectorKit, art: AlienArt, selected: Int, materialize: Float, t: Float, locked: Boolean) {
    val tau = 2f * PI.toFloat()
    val pulse = 0.5f + 0.5f * sin(t * tau * 2f)
    drawCircle(kit.padBrush, radius = 50f + 8f * pulse, center = Offset(240f, 292f))
    drawOval(kit.main, topLeft = Offset(196f, 285f), size = Size(88f, 14f), alpha = 0.6f + 0.25f * pulse)

    if (materialize > 0.01f) {
        val m = materialize
        val glitch = if (t in 0.61f..0.625f || t in 0.87f..0.88f) 5f else 0f
        val flicker = (0.86f + 0.14f * sin(t * tau * 3f)) * (if (locked) 0.45f else 1f)
        withTransform({
            translate(glitch, 0f)
            scale(scaleX = 0.6f + 0.4f * m.coerceAtMost(1f), scaleY = m, pivot = Offset(240f, 292f))
        }) {
            art.glow(selected)?.let { glow ->
                val (o, s) = squareDst(HOLO_CENTER, HOLO_SIZE * 1.04f)
                drawImage(glow, srcSize = glow.srcSize(), dstOffset = o, dstSize = s, alpha = 0.4f * flicker, colorFilter = kit.mainFilter)
            }
            art.holo(selected)?.let { holo ->
                val (o, s) = squareDst(HOLO_CENTER, HOLO_SIZE)
                drawImage(holo, srcSize = holo.srcSize(), dstOffset = o, dstSize = s, alpha = 0.95f * flicker, colorFilter = kit.holoFilter)
            }
            drawCircle(kit.scanlines, radius = HOLO_RADIUS, center = HOLO_CENTER)
            val dy = (t * 2f - 1f) * HOLO_RADIUS
            val half = sqrt(HOLO_RADIUS * HOLO_RADIUS - dy * dy)
            drawRect(kit.main, topLeft = Offset(HOLO_CENTER.x - half, HOLO_CENTER.y + dy), size = Size(half * 2f, 2.5f), alpha = 0.6f * flicker)
            drawCircle(kit.edgeFade, radius = HOLO_RADIUS + 1f, center = HOLO_CENTER)
            withTransform({ rotate(-12f, pivot = HOLO_CENTER) }) {
                drawOval(kit.main, topLeft = Offset(HOLO_CENTER.x - 104f, HOLO_CENTER.y + 18f), size = Size(208f, 34f),
                    style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), phase = -t * 170f)),
                    alpha = 0.55f * flicker)
            }
        }
    }
    // beam on top so the hologram's edge fade doesn't darken it
    drawPath(kit.cone, kit.coneBrush)
    // DNA sparks rising through the beam
    for (n in 0 until 7) {
        val phase = (t * 1.6f + n / 7f) % 1f
        val y = 290f - phase * 180f
        val spread = 22f + (290f - y) * 0.26f
        val x = 240f + spread * sin(n * 2.4f + phase * 5f)
        drawCircle(kit.main, radius = 1.8f, center = Offset(x, y), alpha = (1f - phase) * 0.9f)
    }
}

private fun DrawScope.drawStats(kit: SelectorKit, stats: IntArray) {
    for (k in 0 until 4) {
        val x0 = 150f + k * 47f
        for (n in 0 until 10) {
            drawRect(if (n < stats[k]) kit.main else Color(0xFF1C2A1A), topLeft = Offset(x0 + n * 4f, 362f), size = Size(3f, 6f))
        }
    }
}

private fun DrawScope.drawCentered(layout: TextLayoutResult, y: Float, scale: Float, alpha: Float = 1f) {
    drawText(layout, topLeft = Offset(240f * scale - layout.size.width / 2f, y * scale), alpha = alpha)
}
