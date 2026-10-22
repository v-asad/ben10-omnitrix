package dev.omnitrix.app.ui

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.Energy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val DURATION_MS = 1900

/** Signature beat of a transformation. Aliens without one get the shared DNA sequence. */
enum class Signature { FIRE, FERAL, CRYSTAL, SPEED, SHRINK, POUND, BUZZ, WATER, CIRCUIT, GHOST, DNA }

fun signatureFor(slug: String) = when (slug) {
    "heatblast" -> Signature.FIRE
    "wildmutt" -> Signature.FERAL
    "diamondhead" -> Signature.CRYSTAL
    "xlr8" -> Signature.SPEED
    "grey-matter" -> Signature.SHRINK
    "four-arms" -> Signature.POUND
    "stinkfly" -> Signature.BUZZ
    "ripjaws" -> Signature.WATER
    "upgrade" -> Signature.CIRCUIT
    "ghostfreak" -> Signature.GHOST
    else -> Signature.DNA
}

/**
 * The transformation: slam flash → green DNA surge → the alien's signature beat while its art is
 * revealed → name slam. ~1.9 s, tap to skip.
 *
 * Performance: one Canvas driven by a single progress value read at draw time; particle data is
 * precomputed; the fire is one AGSL shader pass (uniforms only change per frame); paths are
 * reused (reset, never reallocated); no clip masks.
 */
@Composable
fun TransformationScreen(alienIndex: Int, energy: Energy, art: AlienArt, onDone: () -> Unit) {
    val alien = Aliens.all[alienIndex]
    val progress = remember { Animatable(0f) }
    val fx = remember(alienIndex, energy) { Effects(signatureFor(alien.slug), energy) }

    LaunchedEffect(Unit) {
        art.prefetch(alienIndex, radius = 0)
        art.requestFull(alienIndex)
        progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        onDone()
    }

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDone() } }) {
        val density = LocalDensity.current
        val scale = min(constraints.maxWidth, constraints.maxHeight) / VIRTUAL
        val measurer = rememberTextMeasurer(cacheSize = 2)
        val name = remember(scale) {
            measurer.fitted("${alien.name}!", {
                orbitron(density, scale, it, FontWeight.Black, Color.White, 2f)
                    .copy(shadow = Shadow(energy.main, blurRadius = 18f * scale))
            }, 34f, 400f, scale)
        }

        Canvas(Modifier.fillMaxSize()) {
            art.version.intValue
            val t = progress.value
            fx.drawBackdrop(this, t)                        // full-res shader layers (outside the 480 scale)
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                fx.draw(this, t, art.full(alienIndex), art.glow(alienIndex))
            }
            val k = seg(t, 0.8f, 0.9f)
            if (k > 0f) {
                val s = 1.6f - 0.6f * easeOutBack(k)
                withTransform({ scale(s, s, pivot = Offset(size.width / 2f, 400f * scale)) }) {
                    drawText(name, topLeft = Offset(size.width / 2f - name.size.width / 2f, 388f * scale), alpha = k)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- timing helpers
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeIn(x: Float) = x * x * x
private fun easeOutBack(x: Float): Float { val c = 1.7f; return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2) }

private val ART_CENTER = Offset(240f, 222f)
private const val ART_SIZE = 300f

/** Precomputed state for one transformation. */
private class Effects(val signature: Signature, energy: Energy) {
    val main = energy.main
    val bright = energy.bright
    val rnd = Random(signature.ordinal * 7919 + 13)
    val burst = Brush.radialGradient(0f to Color.White, 0.35f to bright, 0.75f to main, 1f to Color.Transparent, center = CENTER, radius = 420f)
    val greenWash = Brush.radialGradient(0f to main.copy(alpha = 0.55f), 1f to Color.Transparent, center = CENTER, radius = 300f)
    val glowFilter = ColorFilter.tint(bright)
    val reusable = Path()
    val segment = Path()
    val measure = PathMeasure()

    // particle seeds (angle, radius, speed, size), allocated once
    val seeds = Array(48) { floatArrayOf(rnd.nextFloat() * 360f, rnd.nextFloat(), 0.5f + rnd.nextFloat(), rnd.nextFloat()) }

    // ----- signature-specific precomputed geometry
    val fire: RuntimeShader? = if (signature == Signature.FIRE) runCatching { RuntimeShader(FIRE_AGSL) }.getOrNull() else null
    val fireBrush = fire?.let { ShaderBrush(it) }
    val shards: List<Path> = if (signature == Signature.CRYSTAL) List(16) { i ->
        val a = i * 22.5f + rnd.nextFloat() * 8f
        val base1 = polar(250f, a - 7f); val base2 = polar(250f, a + 7f); val tip = polar(40f + rnd.nextFloat() * 50f, a)
        Path().apply { moveTo(base1.x, base1.y); lineTo(tip.x, tip.y); lineTo(base2.x, base2.y); close() }
    } else emptyList()
    val shardAngles = FloatArray(16) { it * 22.5f }
    val circuits: List<Path> = if (signature == Signature.CIRCUIT) List(10) { i ->
        Path().apply {
            var p = polar(250f, i * 36f + 10f)
            moveTo(p.x, p.y)
            repeat(4) { step ->
                val toward = Offset(240f - p.x, 222f - p.y)
                p = if (step % 2 == 0) Offset(p.x + toward.x * 0.45f, p.y) else Offset(p.x, p.y + toward.y * 0.45f)
                lineTo(p.x, p.y)
            }
        }
    } else emptyList()
    val circuitLengths = FloatArray(circuits.size).also { lengths ->
        circuits.forEachIndexed { i, path -> measure.setPath(path, false); lengths[i] = measure.length }
    }
    val waterBrush = Brush.verticalGradient(listOf(Color(0xFF5FD4FF), Color(0xFF0B4F8C)), startY = 0f, endY = 480f)
    val smoke = Brush.radialGradient(listOf(Color(0x55B79CFF), Color.Transparent), center = Offset.Zero, radius = 110f)
    val violet = Brush.radialGradient(listOf(Color(0xFF2A1240), Color(0xFF07030C)), center = CENTER, radius = 300f)

    /** Layers drawn at full device resolution (shaders). */
    fun drawBackdrop(scope: DrawScope, t: Float) = with(scope) {
        drawRect(Color.Black)
        if (signature == Signature.FIRE && fire != null && fireBrush != null) {
            // rises to engulf, then recedes to an aura around Heatblast
            val level = 1.0f * easeOut(seg(t, 0.25f, 0.55f)) - 0.68f * seg(t, 0.62f, 0.86f)
            if (level > 0.01f) {
                fire.setFloatUniform("uRes", size.width, size.height)
                fire.setFloatUniform("uTime", t * 4f)
                fire.setFloatUniform("uLevel", level)
                // shade only the band the flames can reach (flame top ≈ level * 1.25 + noise margin)
                val top = (size.height * (1f - (level * 1.25f + 0.3f))).coerceAtLeast(0f)
                drawRect(fireBrush, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
            }
        }
    }

    fun draw(scope: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(scope) {
        // ---- shared opening: slam flash + green DNA surge
        val flash = seg(t, 0f, 0.18f)
        if (t < 0.42f) drawCircle(burst, radius = 30f + easeOut(flash) * 400f, center = CENTER, alpha = 1f - seg(t, 0.18f, 0.42f))
        val dnaIn = seg(t, 0.1f, 0.45f); val dnaOut = seg(t, 0.42f, 0.58f)
        if (dnaIn > 0f && dnaOut < 1f) {
            drawCircle(greenWash, radius = 300f, center = CENTER, alpha = dnaIn * (1f - dnaOut))
            drawHelix(this, dnaIn, t, 1f - dnaOut)
        }
        // ---- signature beat + art reveal
        when (signature) {
            Signature.FIRE -> flames(this, t, full, glow)
            Signature.FERAL -> feral(this, t, full, glow)
            Signature.CRYSTAL -> crystal(this, t, full, glow)
            Signature.SPEED -> speed(this, t, full, glow)
            Signature.SHRINK -> shrink(this, t, full, glow)
            Signature.POUND -> pound(this, t, full, glow)
            Signature.BUZZ -> buzz(this, t, full, glow)
            Signature.WATER -> water(this, t, full, glow)
            Signature.CIRCUIT -> circuit(this, t, full, glow)
            Signature.GHOST -> ghost(this, t, full, glow)
            Signature.DNA -> drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.45f, 0.75f))
        }
    }

    /** The alien: a bright silhouette that resolves into full colour as [reveal] goes 0→1. */
    fun drawArt(scope: DrawScope, full: ImageBitmap?, glow: ImageBitmap?, center: Offset, size: Float,
                reveal: Float, alpha: Float = 1f, sx: Float = 1f, sy: Float = 1f) = with(scope) {
        if (reveal <= 0f || alpha <= 0f) return@with
        val s = (size).roundToInt()
        val dst = IntOffset((center.x - size / 2f).roundToInt(), (center.y - size / 2f).roundToInt())
        withTransform({ scale(sx, sy, pivot = center) }) {
            glow?.let { drawImage(it, srcSize = IntSize(it.width, it.height), dstOffset = dst, dstSize = IntSize(s, s),
                alpha = alpha * (1f - 0.5f * reveal), colorFilter = glowFilter) }
            full?.let { drawImage(it, srcSize = IntSize(it.width, it.height), dstOffset = dst, dstSize = IntSize(s, s),
                alpha = alpha * easeOut(reveal)) }
        }
    }

    fun drawHelix(scope: DrawScope, grow: Float, t: Float, alpha: Float) = with(scope) {
        val half = 200f * easeOut(grow)
        val phase = t * 14f
        for (k in 0..36) {
            val y = 240f - half + (2f * half) * k / 36f
            val w = sin(k * 0.45f + phase)
            val depth = cos(k * 0.45f + phase)
            val a = Offset(240f + 46f * w, y); val b = Offset(240f - 46f * w, y)
            if (k % 3 == 0) drawLine(main, a, b, strokeWidth = 2f, alpha = 0.5f * alpha)
            drawCircle(bright, radius = 3.5f + depth, center = a, alpha = alpha)
            drawCircle(main, radius = 3.5f - depth, center = b, alpha = alpha)
        }
    }

    // ---------------------------------------------------------------- signatures
    fun flames(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.58f, 0.82f))
        for (p in seeds) {                                               // embers
            val life = (t * 1.4f * p[2] + p[1]) % 1f
            if (t < 0.3f) continue
            val x = 60f + p[0] / 360f * 360f + sin(life * 9f + p[3] * 6f) * 14f
            drawCircle(Color(0xFFFFB347), radius = 1.5f + p[3] * 2.5f, center = Offset(x, 470f - life * 440f), alpha = (1f - life) * 0.9f)
        }
    }

    fun feral(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        for (i in 0 until 3) {                                           // claw slashes
            val k = easeOut(seg(t, 0.3f + i * 0.05f, 0.45f + i * 0.05f))
            val fade = 1f - seg(t, 0.6f, 0.75f)
            if (k <= 0f || fade <= 0f) continue
            val a = Offset(110f + i * 70f, 70f); val b = Offset(230f + i * 70f, 400f)
            drawLine(Color(0xFFFF9A2E), a, a + (b - a) * k, strokeWidth = 12f - i * 2f, cap = StrokeCap.Round, alpha = fade)
            drawLine(Color.White, a, a + (b - a) * k, strokeWidth = 3f, cap = StrokeCap.Round, alpha = fade)
        }
        val leap = easeOutBack(seg(t, 0.5f, 0.72f))
        val squash = 1f + 0.12f * sin(seg(t, 0.68f, 0.8f) * PI.toFloat())
        drawArt(this, full, glow, Offset(240f, 222f + (1f - leap) * 320f), ART_SIZE, reveal = seg(t, 0.5f, 0.7f), sx = squash, sy = 2f - squash)
        val roar = seg(t, 0.7f, 0.9f)
        if (roar in 0.001f..0.999f) drawCircle(Color(0xFFFF9A2E), radius = 60f + roar * 220f, center = Offset(240f, 170f), style = Stroke(6f * (1f - roar)), alpha = 1f - roar)
    }

    fun crystal(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val grow = easeOut(seg(t, 0.28f, 0.52f))
        val shatter = easeOut(seg(t, 0.6f, 0.85f))
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.58f, 0.78f))
        shards.forEachIndexed { i, path ->
            if (grow <= 0f || shatter >= 1f) return@forEachIndexed
            val base = polar(250f, shardAngles[i])
            val out = polar(120f * shatter, shardAngles[i], Offset.Zero)
            withTransform({
                translate(out.x, out.y)
                scale(grow, grow, pivot = base)
                rotate(shatter * 60f * (if (i % 2 == 0) 1f else -1f), pivot = base)
            }) {
                drawPath(path, Color(0xFF7FF5DD), alpha = 0.85f * (1f - shatter))
                drawPath(path, Color.White, style = Stroke(1.5f), alpha = 1f - shatter)
            }
        }
    }

    fun speed(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val lines = seg(t, 0.25f, 0.85f)
        if (lines > 0f) for (p in seeds) {                               // radial speed lines rushing inward
            val r = 260f - ((t * 3f * p[2] + p[1]) % 1f) * 230f
            val a = polar(r, p[0]); val b = polar(r + 40f + p[3] * 50f, p[0])
            drawLine(if (p[3] > 0.5f) Color(0xFF6FE7FF) else Color.White, a, b, strokeWidth = 1.5f + p[3] * 2f, alpha = 0.7f * (1f - seg(t, 0.75f, 0.9f)))
        }
        val zip = easeOut(seg(t, 0.5f, 0.66f))
        val x = 240f - (1f - zip) * 420f
        for (trail in 3 downTo 1) {                                       // motion trail copies
            drawArt(this, null, glow, Offset(x - trail * 46f * (1f - seg(t, 0.66f, 0.76f)), 222f), ART_SIZE,
                reveal = 1f, alpha = 0.18f * trail * (1f - seg(t, 0.7f, 0.8f)))
        }
        drawArt(this, full, glow, Offset(x, 222f), ART_SIZE, reveal = seg(t, 0.55f, 0.72f))
    }

    fun shrink(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val collapse = easeIn(seg(t, 0.3f, 0.55f))
        if (t in 0.3f..0.58f) drawCircle(greenWash, radius = 220f * (1f - collapse) + 4f, center = CENTER, alpha = 1f)
        if (t in 0.3f..0.58f) drawCircle(bright, radius = 140f * (1f - collapse) + 3f, center = CENTER, alpha = 0.35f + collapse * 0.6f)
        val ping = seg(t, 0.56f, 0.8f)
        if (ping in 0.001f..0.999f) drawCircle(bright, radius = 8f + ping * 120f, center = CENTER, style = Stroke(3f * (1f - ping)), alpha = 1f - ping)
        val pop = easeOutBack(seg(t, 0.58f, 0.74f))
        drawArt(this, full, glow, Offset(240f, 236f), ART_SIZE * 0.62f * pop.coerceAtLeast(0.01f), reveal = seg(t, 0.6f, 0.75f))
    }

    fun pound(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val impact1 = 0.56f; val impact2 = 0.74f
        val shake = (if (t > impact1) (1f - seg(t, impact1, impact1 + 0.12f)) else 0f) +
            (if (t > impact2) (1f - seg(t, impact2, impact2 + 0.1f)) * 0.7f else 0f)
        translate(sin(t * 160f) * 9f * shake, cos(t * 130f) * 6f * shake) {
            val drop = easeIn(seg(t, 0.4f, impact1))
            drawArt(this, full, glow, Offset(240f, 222f - (1f - drop) * 420f), ART_SIZE, reveal = seg(t, 0.45f, 0.65f))
            for ((i, at) in floatArrayOf(impact1, impact2).withIndex()) {
                val w = seg(t, at, at + 0.2f)
                if (w in 0.001f..0.999f) {
                    drawOval(Color(0xFFFFC46B), topLeft = Offset(240f - 40f - w * 220f, 372f - 8f - w * 30f), size = Size(80f + w * 440f, 16f + w * 60f),
                        style = Stroke(5f * (1f - w)), alpha = 1f - w)
                    if (i == 0) for (p in seeds.take(16)) {                // debris
                        val v = Offset(cos(p[0]) * 160f * p[2], -200f * p[2])
                        val pos = Offset(240f, 372f) + v * w + Offset(0f, 300f * w * w)
                        drawRect(Color(0xFFB08A5A), topLeft = pos, size = Size(4f + p[3] * 5f, 4f + p[3] * 5f), alpha = 1f - w)
                    }
                }
            }
        }
    }

    fun buzz(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val f = easeOut(seg(t, 0.4f, 0.72f))
        // swoop along a curve from bottom-right
        val x = 240f + (1f - f) * 260f * cos(f * PI.toFloat())
        val y = 222f + (1f - f) * 260f
        val gust = seg(t, 0.35f, 0.8f)
        if (gust in 0.001f..0.999f) for (i in 0 until 6) {
            drawArc(Color(0xFFD7FF8A), startAngle = 200f + i * 18f + gust * 120f, sweepAngle = 40f, useCenter = false,
                topLeft = Offset(60f + i * 8f, 60f + i * 8f), size = Size(360f - i * 16f, 360f - i * 16f), style = Stroke(2f), alpha = 0.6f * (1f - gust))
        }
        drawArt(this, full, glow, Offset(x, y), ART_SIZE, reveal = seg(t, 0.45f, 0.72f))
        for (p in seeds.take(14)) {                                      // slime droplets
            val d = seg(t, 0.55f + p[1] * 0.1f, 0.95f)
            if (d <= 0f || d >= 1f) continue
            val px = 120f + p[0] / 360f * 240f
            val py = 150f + d * d * 330f
            drawOval(Color(0xFF9BFF3A), topLeft = Offset(px, py), size = Size(5f + p[3] * 4f, 8f + d * 10f), alpha = 1f - d)
        }
    }

    fun water(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.58f, 0.78f))
        val level = easeOut(seg(t, 0.25f, 0.5f)) - easeIn(seg(t, 0.6f, 0.85f))   // fraction of screen covered
        if (level > 0.001f) {
            val top = 480f - level * 520f
            reusable.reset()
            reusable.moveTo(0f, 480f)
            var x = 0f
            while (x <= 480f) { reusable.lineTo(x, top + sin(x / 38f + t * 22f) * 12f); x += 12f }
            reusable.lineTo(480f, 480f); reusable.close()
            drawPath(reusable, waterBrush, alpha = 0.92f)
        }
        for (p in seeds.take(20)) {                                      // bubbles
            val life = (t * 1.2f * p[2] + p[1]) % 1f
            if (t < 0.3f || t > 0.9f) continue
            drawCircle(Color(0xFFBFF2FF), radius = 2f + p[3] * 5f, center = Offset(40f + p[0] / 360f * 400f, 480f - life * 420f),
                style = Stroke(1.5f), alpha = 1f - life)
        }
    }

    fun circuit(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val trace = easeOut(seg(t, 0.25f, 0.55f))
        val fade = 1f - seg(t, 0.75f, 0.9f)
        circuits.forEachIndexed { i, path ->
            measure.setPath(path, false)
            segment.reset()
            measure.getSegment(0f, circuitLengths[i] * trace, segment, true)
            drawPath(segment, main, style = Stroke(3f, cap = StrokeCap.Round), alpha = fade)
            val head = measure.getPosition(circuitLengths[i] * trace)
            drawCircle(Color.White, radius = 4f, center = head, alpha = fade)
        }
        // assemble from glitching horizontal slices
        val assemble = easeOut(seg(t, 0.5f, 0.78f))
        if (full != null && assemble > 0f) {
            val slices = 12
            val sliceH = full.height / slices
            val dstH = ART_SIZE / slices
            for (i in 0 until slices) {
                val offset = (1f - assemble) * (if (i % 2 == 0) 120f else -120f) * (0.4f + (i * 37 % 11) / 11f)
                drawImage(full, srcOffset = IntOffset(0, i * sliceH), srcSize = IntSize(full.width, sliceH),
                    dstOffset = IntOffset((ART_CENTER.x - ART_SIZE / 2f + offset).roundToInt(), (ART_CENTER.y - ART_SIZE / 2f + i * dstH).roundToInt()),
                    dstSize = IntSize(ART_SIZE.roundToInt(), dstH.roundToInt() + 1), alpha = assemble)
            }
        }
    }

    fun ghost(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val dark = seg(t, 0.3f, 0.45f) * (1f - seg(t, 0.9f, 1f))
        if (dark > 0f) drawRect(violet, alpha = dark)
        for (p in seeds.take(10)) {                                      // drifting smoke
            val c = Offset(60f + p[0] / 360f * 360f + sin(t * 3f + p[3] * 6f) * 30f, 420f - p[1] * 320f - t * 80f * p[2])
            translate(c.x, c.y) { drawCircle(smoke, radius = 110f, center = Offset.Zero, alpha = dark) }
        }
        val appear = seg(t, 0.5f, 0.8f)
        val flick = if (appear < 1f && ((t * 40f).toInt() % 3 == 0)) 0.25f else 1f
        val jitter = (1f - appear) * 10f * sin(t * 90f)
        drawArt(this, full, glow, Offset(240f + jitter, 222f), ART_SIZE, reveal = appear, alpha = flick)
    }
}

/** Rising fire (AGSL): fractal noise flames, black → red → orange → yellow-white. */
private const val FIRE_AGSL = """
uniform float2 uRes;
uniform float uTime;
uniform float uLevel;

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) { v += a * noise(p); p *= 2.0; a *= 0.5; }
    return v;
}
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uRes;
    float h = 1.0 - uv.y;
    float n = fbm(float2(uv.x * 6.0, h * 4.0 - uTime * 3.0));
    float flame = uLevel * 1.25 - h + (n - 0.5) * 0.55;
    float i = clamp(flame * 1.5, 0.0, 1.0);
    // deep red body, orange flames, yellow-white only at the hottest tips
    half3 col = mix(half3(0.35, 0.02, 0.0), half3(0.95, 0.18, 0.0), smoothstep(0.0, 0.45, i));
    col = mix(col, half3(1.0, 0.5, 0.05), smoothstep(0.45, 0.8, i));
    col = mix(col, half3(1.0, 0.88, 0.45), smoothstep(0.88, 1.0, i) * n);
    float a = i * 0.92;
    return half4(col * a, a);
}
"""
