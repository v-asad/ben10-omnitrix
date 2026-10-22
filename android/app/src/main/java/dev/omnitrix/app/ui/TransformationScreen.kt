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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.Energy
import dev.omnitrix.app.FxSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Full sequence length; QUICK mode (settings) plays a shortened version. */
const val TRANSFORM_MS = 1900
const val TRANSFORM_QUICK_MS = 1000

/**
 * The transformation: slam flash → green DNA surge → the alien's signature beat while its art is
 * revealed → name slam. Tap to skip.
 *
 * Performance: one Canvas driven by a single progress value read at draw time; effects are built
 * off the main thread while you browse the selector ([FxCache]) and the fire shader is pre-warmed
 * there, so the first frames don't stall; particle data is precomputed; paths are reused (reset,
 * never reallocated); no clip masks.
 */
@Composable
fun TransformationScreen(alienIndex: Int, energy: Energy, art: AlienArt, fx: FxCache, durationMs: Int, onDone: () -> Unit) {
    val alien = Aliens.all[alienIndex]
    val progress = remember { Animatable(0f) }
    val effects = remember(alienIndex, energy) { fx.getOrBuild(alienIndex, energy) }

    LaunchedEffect(Unit) {
        art.prefetch(alienIndex, radius = 0)
        art.requestFull(alienIndex)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        onDone()
    }

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDone() } }) {
        val density = LocalDensity.current
        val scale = min(constraints.maxWidth, constraints.maxHeight) / VIRTUAL
        val measurer = rememberTextMeasurer(cacheSize = 2)
        val name = remember(scale) {
            measurer.fitted("${alien.name}!", {
                orbitron(density, scale, it, FontWeight.Black, Color.White, 2f)
                    .copy(shadow = Shadow(alien.fx.primary, blurRadius = 18f * scale))
            }, 34f, 400f, scale)
        }

        Canvas(Modifier.fillMaxSize()) {
            art.version.intValue
            val t = progress.value
            effects.drawBackdrop(this, t)                  // full-res shader layers (outside the 480 scale)
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                effects.draw(this, t, art.full(alienIndex), art.glow(alienIndex))
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

/**
 * Builds [Effects] ahead of time (off the main thread) for the alien under the selector, and
 * keeps the last few, so the transformation starts instantly.
 */
class FxCache {
    private val cache = LinkedHashMap<Pair<Int, Energy>, Effects>(4, 0.75f, true)

    @Synchronized fun get(index: Int, energy: Energy): Effects? = cache[index to energy]

    @Synchronized fun getOrBuild(index: Int, energy: Energy): Effects =
        cache.getOrPut(index to energy) { Effects(Aliens.all[index].fx, energy, index) }.also { trim() }

    /** Call from a background dispatcher. */
    fun warm(index: Int, energy: Energy) {
        if (get(index, energy) == null) {
            val built = Effects(Aliens.all[index].fx, energy, index)
            synchronized(this) { cache.putIfAbsent(index to energy, built); trim() }
        }
    }

    private fun trim() { while (cache.size > 4) cache.remove(cache.keys.first()) }
}

// ---------------------------------------------------------------- timing helpers
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeIn(x: Float) = x * x * x
private fun easeOutBack(x: Float): Float { val c = 1.7f; return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2) }
private fun hash(a: Int, b: Int, c: Int): Float = (((a * 73856093) xor (b * 19349663) xor (c * 83492791)) and 0x7fffffff) % 1000 / 1000f

private val ART_CENTER = Offset(240f, 222f)
private const val ART_SIZE = 300f

/** Precomputed state for one alien's transformation. */
class Effects(spec: FxSpec, energy: Energy, seed: Int) {
    private val family = spec.family
    private val variant = spec.variant
    private val c1 = spec.primary
    private val c2 = spec.secondary
    private val main = energy.main
    private val bright = energy.bright
    private val rnd = Random(seed * 7919 + 13)
    private val burst = Brush.radialGradient(0f to Color.White, 0.35f to bright, 0.75f to main, 1f to Color.Transparent, center = CENTER, radius = 420f)
    private val greenWash = Brush.radialGradient(0f to main.copy(alpha = 0.55f), 1f to Color.Transparent, center = CENTER, radius = 300f)
    private val tintWash = Brush.radialGradient(0f to c1.copy(alpha = 0.5f), 1f to Color.Transparent, center = CENTER, radius = 320f)
    private val glowFilter = ColorFilter.tint(lerp(bright, c1, 0.5f))
    private val reusable = Path()
    private val segment = Path()
    private val measure = PathMeasure()

    // particle seeds (angle°, 0..1, speed 0.5..1.5, size 0..1), allocated once
    private val seeds = Array(48) { floatArrayOf(rnd.nextFloat() * 360f, rnd.nextFloat(), 0.5f + rnd.nextFloat(), rnd.nextFloat()) }

    // ---- family-specific precomputed geometry
    private val fire: RuntimeShader? = if (family == "fire") runCatching { RuntimeShader(FIRE_AGSL) }.getOrNull() else null
    private val fireBrush = fire?.let { ShaderBrush(it) }
    private val shardCount = 16
    private val shards: List<Path> = if (family == "crystal" || family == "ice") List(shardCount) { i ->
        val a = i * 22.5f + rnd.nextFloat() * 8f
        val base1 = polar(250f, a - 7f); val base2 = polar(250f, a + 7f); val tip = polar(40f + rnd.nextFloat() * 50f, a)
        Path().apply { moveTo(base1.x, base1.y); lineTo(tip.x, tip.y); lineTo(base2.x, base2.y); close() }
    } else emptyList()
    private val shardColors = Array(shardCount) { i ->
        if (variant == "prism") Color.hsv(i * 360f / shardCount, 0.6f, 1f) else lerp(c1, c2, (i % 3) / 3f)
    }
    private val circuits: List<Path> = if (family == "circuit") List(10) { i ->
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
    private val vines: List<Path> = if (family == "plant") List(7) { i ->
        Path().apply {
            val x = 40f + i * 66f
            moveTo(x, 490f)
            cubicTo(x + 60f * (if (i % 2 == 0) 1 else -1), 380f, x - 50f * (if (i % 2 == 0) 1 else -1), 260f, 240f + (x - 240f) * 0.35f, 150f + i * 6f)
        }
    } else emptyList()
    private val webRadials = 12
    private val pathLengths: FloatArray = (circuits + vines).let { paths ->
        FloatArray(paths.size).also { lengths -> paths.forEachIndexed { i, p -> measure.setPath(p, false); lengths[i] = measure.length } }
    }
    private val cracks: List<Path> = if (variant == "cracks") List(6) { i ->
        Path().apply {
            var p = Offset(240f, 372f); moveTo(p.x, p.y)
            val dir = -150f + i * 60f + rnd.nextFloat() * 20f
            repeat(5) { p += polar(28f + rnd.nextFloat() * 18f, dir + (rnd.nextFloat() - 0.5f) * 50f, Offset.Zero); lineTo(p.x, p.y) }
        }
    } else emptyList()
    private val waterBrush = Brush.verticalGradient(listOf(lerp(c1, Color.White, 0.2f), c2), startY = 0f, endY = 480f)
    private val smoke = Brush.radialGradient(listOf(c1.copy(alpha = 0.33f), Color.Transparent), center = Offset.Zero, radius = 110f)
    private val dark = Brush.radialGradient(listOf(lerp(c2, Color.Black, 0.3f), Color(0xFF05030A)), center = CENTER, radius = 300f)
    private val orb = Brush.radialGradient(listOf(Color.White, c1, c1.copy(alpha = 0f)), center = CENTER, radius = 1f)
    private val nebula = Brush.radialGradient(listOf(c2.copy(alpha = 0.6f), Color(0xFF06041A), Color.Black), center = CENTER, radius = 320f)

    /** Draws a 1px, near-invisible frame of any shader so the GPU compiles it before it's needed. */
    fun warmUp(scope: DrawScope) = with(scope) {
        if (fire != null && fireBrush != null) {
            fire.setFloatUniform("uRes", size.width, size.height)
            fire.setFloatUniform("uTime", 0f)
            fire.setFloatUniform("uLevel", 0.5f)
            setFireColors(fire)
            drawRect(fireBrush, topLeft = Offset.Zero, size = Size(1f, 1f), alpha = 0.01f)
        }
    }

    private fun setFireColors(shader: RuntimeShader) {
        val body = lerp(c1, Color.Black, 0.65f)
        shader.setFloatUniform("uBody", body.red, body.green, body.blue)
        shader.setFloatUniform("uFlame", c1.red, c1.green, c1.blue)
        shader.setFloatUniform("uTip", c2.red, c2.green, c2.blue)
    }

    /** Layers drawn at full device resolution (shaders). */
    fun drawBackdrop(scope: DrawScope, t: Float) = with(scope) {
        drawRect(Color.Black)
        if (fire != null && fireBrush != null) {
            // rises to engulf, then recedes to an aura around the alien
            val level = 1.0f * easeOut(seg(t, 0.25f, 0.55f)) - 0.68f * seg(t, 0.62f, 0.86f)
            if (level > 0.01f) {
                fire.setFloatUniform("uRes", size.width, size.height)
                fire.setFloatUniform("uTime", t * 4f)
                fire.setFloatUniform("uLevel", level)
                setFireColors(fire)
                // shade only the band the flames can reach (flame top ≈ level * 1.25 + noise margin)
                val top = (size.height * (1f - (level * 1.25f + 0.3f))).coerceAtLeast(0f)
                drawRect(fireBrush, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
            }
        }
    }

    fun draw(scope: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(scope) {
        // ---- shared opening: slam flash + green DNA surge (the Omnitrix's own colours)
        val flash = seg(t, 0f, 0.18f)
        if (t < 0.42f) drawCircle(burst, radius = 30f + easeOut(flash) * 400f, center = CENTER, alpha = 1f - seg(t, 0.18f, 0.42f))
        val dnaIn = seg(t, 0.1f, 0.45f); val dnaOut = seg(t, 0.42f, 0.58f)
        if (dnaIn > 0f && dnaOut < 1f) {
            drawCircle(greenWash, radius = 300f, center = CENTER, alpha = dnaIn * (1f - dnaOut))
            drawHelix(this, dnaIn, t, 1f - dnaOut)
        }
        // ---- signature beat + art reveal
        when (family) {
            "fire" -> flames(this, t, full, glow)
            "feral" -> feral(this, t, full, glow)
            "crystal" -> crystal(this, t, full, glow, snow = false)
            "ice" -> crystal(this, t, full, glow, snow = true)
            "speed" -> speed(this, t, full, glow)
            "shrink" -> shrink(this, t, full, glow)
            "pound" -> pound(this, t, full, glow)
            "grow" -> grow(this, t, full, glow)
            "buzz" -> buzz(this, t, full, glow)
            "water" -> water(this, t, full, glow)
            "circuit" -> circuit(this, t, full, glow)
            "ghost" -> ghost(this, t, full, glow)
            "invisible" -> invisible(this, t, full, glow)
            "electric" -> electric(this, t, full, glow)
            "clone" -> clone(this, t, full, glow)
            "sonic" -> sonic(this, t, full, glow)
            "web" -> web(this, t, full, glow)
            "time" -> time(this, t, full, glow)
            "mummy" -> mummy(this, t, full, glow)
            "plant" -> plant(this, t, full, glow)
            "goo" -> goo(this, t, full, glow)
            "magnet" -> magnet(this, t, full, glow)
            "eye" -> eyes(this, t, full, glow)
            "roll" -> roll(this, t, full, glow)
            "cosmic" -> cosmic(this, t, full, glow)
            "radiation" -> radiation(this, t, full, glow)
            "wind" -> wind(this, t, full, glow)
            else -> drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.45f, 0.75f))
        }
    }

    /** The alien: a bright silhouette that resolves into full colour as [reveal] goes 0→1. */
    private fun drawArt(scope: DrawScope, full: ImageBitmap?, glow: ImageBitmap?, center: Offset, size: Float,
                        reveal: Float, alpha: Float = 1f, sx: Float = 1f, sy: Float = 1f) = with(scope) {
        if (reveal <= 0f || alpha <= 0f || size < 1f) return@with
        val s = size.roundToInt()
        val dst = IntOffset((center.x - size / 2f).roundToInt(), (center.y - size / 2f).roundToInt())
        withTransform({ scale(sx, sy, pivot = center) }) {
            glow?.let { drawImage(it, srcSize = IntSize(it.width, it.height), dstOffset = dst, dstSize = IntSize(s, s),
                alpha = alpha * (1f - 0.5f * reveal), colorFilter = glowFilter) }
            full?.let { drawImage(it, srcSize = IntSize(it.width, it.height), dstOffset = dst, dstSize = IntSize(s, s),
                alpha = alpha * easeOut(reveal)) }
        }
    }

    private fun drawHelix(scope: DrawScope, grow: Float, t: Float, alpha: Float) = with(scope) {
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

    private fun ring(scope: DrawScope, k: Float, center: Offset, from: Float, to: Float, color: Color, width: Float) = with(scope) {
        if (k in 0.001f..0.999f) drawCircle(color, radius = from + k * (to - from), center = center, style = Stroke(width * (1f - k)), alpha = 1f - k)
    }

    // ---------------------------------------------------------------- families
    private fun flames(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.58f, 0.82f))
        if (t < 0.3f) return@with
        for (p in seeds) {                                                 // embers
            val life = (t * 1.4f * p[2] + p[1]) % 1f
            val x = 60f + p[0] + sin(life * 9f + p[3] * 6f) * 14f
            drawCircle(lerp(c1, c2, p[3]), radius = 1.5f + p[3] * 2.5f, center = Offset(x, 470f - life * 440f), alpha = (1f - life) * 0.9f)
        }
    }

    private fun feral(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        for (i in 0 until 3) {                                             // claw slashes
            val k = easeOut(seg(t, 0.3f + i * 0.05f, 0.45f + i * 0.05f))
            val fade = 1f - seg(t, 0.6f, 0.75f)
            if (k <= 0f || fade <= 0f) continue
            val a = Offset(110f + i * 70f, 70f); val b = Offset(230f + i * 70f, 400f)
            drawLine(c1, a, a + (b - a) * k, strokeWidth = 12f - i * 2f, cap = StrokeCap.Round, alpha = fade)
            drawLine(c2, a, a + (b - a) * k, strokeWidth = 3f, cap = StrokeCap.Round, alpha = fade)
        }
        val leap = easeOutBack(seg(t, 0.5f, 0.72f))
        val squash = 1f + 0.12f * sin(seg(t, 0.68f, 0.8f) * PI.toFloat())
        drawArt(this, full, glow, Offset(240f, 222f + (1f - leap) * 320f), ART_SIZE, reveal = seg(t, 0.5f, 0.7f), sx = squash, sy = 2f - squash)
        ring(this, seg(t, 0.7f, 0.9f), Offset(240f, 170f), 60f, 280f, c1, 6f)
    }

    private fun crystal(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?, snow: Boolean) = with(s) {
        if (snow) drawCircle(tintWash, radius = 320f, center = CENTER, alpha = seg(t, 0.25f, 0.45f) * (1f - seg(t, 0.75f, 0.95f)))
        val grow = easeOut(seg(t, 0.28f, 0.52f))
        val shatter = easeOut(seg(t, 0.6f, 0.85f))
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.58f, 0.78f))
        if (grow > 0f && shatter < 1f) shards.forEachIndexed { i, path ->
            val angle = i * 22.5f
            val base = polar(250f, angle)
            val out = polar(120f * shatter, angle, Offset.Zero)
            withTransform({
                translate(out.x, out.y)
                scale(grow, grow, pivot = base)
                rotate(shatter * 60f * (if (i % 2 == 0) 1f else -1f), pivot = base)
            }) {
                drawPath(path, shardColors[i], alpha = 0.85f * (1f - shatter))
                drawPath(path, Color.White, style = Stroke(1.5f), alpha = 1f - shatter)
            }
        }
        if (snow && t > 0.25f) for (p in seeds) {                          // snowfall
            val y = ((t * 0.9f * p[2] + p[1]) % 1f) * 500f - 10f
            drawCircle(c2, radius = 1.2f + p[3] * 2.2f, center = Offset(p[0] * 1.33f + sin(y / 40f + p[3] * 6f) * 10f, y), alpha = 0.85f)
        }
    }

    private fun speed(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        if (t > 0.25f) for (p in seeds) {                                  // radial speed lines rushing inward
            val r = 260f - ((t * 3f * p[2] + p[1]) % 1f) * 230f
            val a = polar(r, p[0]); val b = polar(r + 40f + p[3] * 50f, p[0])
            drawLine(if (p[3] > 0.5f) c1 else c2, a, b, strokeWidth = 1.5f + p[3] * 2f, alpha = 0.7f * (1f - seg(t, 0.75f, 0.9f)))
        }
        val zip = easeOut(seg(t, 0.5f, 0.66f))
        val x = 240f - (1f - zip) * 420f
        for (trail in 3 downTo 1) {                                         // motion trail copies
            drawArt(this, null, glow, Offset(x - trail * 46f * (1f - seg(t, 0.66f, 0.76f)), 222f), ART_SIZE,
                reveal = 1f, alpha = 0.18f * trail * (1f - seg(t, 0.7f, 0.8f)))
        }
        drawArt(this, full, glow, Offset(x, 222f), ART_SIZE, reveal = seg(t, 0.55f, 0.72f))
    }

    private fun shrink(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val collapse = easeIn(seg(t, 0.3f, 0.55f))
        if (t in 0.3f..0.58f) {
            withTransform({ scale(220f * (1f - collapse) + 4f, pivot = CENTER) }) { drawCircle(orb, radius = 1f, center = CENTER) }
        }
        ring(this, seg(t, 0.56f, 0.8f), CENTER, 8f, 128f, c1, 3f)
        val pop = easeOutBack(seg(t, 0.58f, 0.74f))
        drawArt(this, full, glow, Offset(240f, 236f), ART_SIZE * 0.62f * pop.coerceAtLeast(0.01f), reveal = seg(t, 0.6f, 0.75f))
        if (t > 0.6f) for (p in seeds.indices step 4) {                     // sparkles
            val sp = seeds[p]; val k = seg(t, 0.6f + sp[1] * 0.1f, 0.95f)
            if (k > 0f && k < 1f) drawCircle(c2, radius = 2f, center = polar(60f + k * 80f, sp[0], Offset(240f, 236f)), alpha = 1f - k)
        }
    }

    private fun pound(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val impact1 = 0.56f; val impact2 = 0.74f
        val shake = (if (t > impact1) (1f - seg(t, impact1, impact1 + 0.12f)) else 0f) +
            (if (t > impact2) (1f - seg(t, impact2, impact2 + 0.1f)) * 0.7f else 0f)
        translate(sin(t * 160f) * 9f * shake, cos(t * 130f) * 6f * shake) {
            if (t > impact1) cracks.forEach { drawPath(it, c2, style = Stroke(3f), alpha = 1f - seg(t, 0.85f, 1f)) }
            val drop = easeIn(seg(t, 0.4f, impact1))
            drawArt(this, full, glow, Offset(240f, 222f - (1f - drop) * 420f), ART_SIZE, reveal = seg(t, 0.45f, 0.65f))
            for (at in floatArrayOf(impact1, impact2)) {
                val w = seg(t, at, at + 0.2f)
                if (w in 0.001f..0.999f) {
                    drawOval(c1, topLeft = Offset(240f - 40f - w * 220f, 364f - w * 30f), size = Size(80f + w * 440f, 16f + w * 60f),
                        style = Stroke(5f * (1f - w)), alpha = 1f - w)
                }
            }
            val w = seg(t, impact1, impact1 + 0.25f)                       // debris
            if (w in 0.001f..0.999f) for (i in 0 until 16) {
                val p = seeds[i]
                val pos = Offset(240f + cos(p[0]) * 160f * p[2] * w, 372f - 200f * p[2] * w + 300f * w * w)
                drawRect(c2, topLeft = pos, size = Size(4f + p[3] * 5f, 4f + p[3] * 5f), alpha = 1f - w)
            }
        }
    }

    private fun grow(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        // grows from tiny past the screen (feet anchored), then the "camera" pulls back
        val up = easeOut(seg(t, 0.42f, 0.66f))
        val back = easeOut(seg(t, 0.66f, 0.84f))
        val scale = 0.08f + up * 2.4f - back * 1.48f
        val shake = if (t in 0.62f..0.78f) (1f - seg(t, 0.62f, 0.78f)) else 0f
        translate(sin(t * 150f) * 8f * shake, 0f) {
            val size = ART_SIZE * scale
            drawArt(this, full, glow, Offset(240f, 372f - size / 2f), size, reveal = seg(t, 0.45f, 0.7f))
            ring(this, seg(t, 0.62f, 0.85f), Offset(240f, 372f), 40f, 300f, c2, 6f)
        }
    }

    private fun buzz(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val f = easeOut(seg(t, 0.4f, 0.72f))
        val x = 240f + (1f - f) * 260f * cos(f * PI.toFloat())
        val y = 222f + (1f - f) * 260f
        val gust = seg(t, 0.35f, 0.8f)
        if (gust in 0.001f..0.999f) for (i in 0 until 6) {
            drawArc(c1, startAngle = 200f + i * 18f + gust * 120f, sweepAngle = 40f, useCenter = false,
                topLeft = Offset(60f + i * 8f, 60f + i * 8f), size = Size(360f - i * 16f, 360f - i * 16f), style = Stroke(2f), alpha = 0.6f * (1f - gust))
        }
        drawArt(this, full, glow, Offset(x, y), ART_SIZE, reveal = seg(t, 0.45f, 0.72f))
        for (i in 0 until 14) {                                             // slime droplets
            val p = seeds[i]
            val d = seg(t, 0.55f + p[1] * 0.1f, 0.95f)
            if (d <= 0f || d >= 1f) continue
            drawOval(c2, topLeft = Offset(120f + p[0] / 360f * 240f, 150f + d * d * 330f), size = Size(5f + p[3] * 4f, 8f + d * 10f), alpha = 1f - d)
        }
    }

    private fun water(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.58f, 0.78f))
        val level = easeOut(seg(t, 0.25f, 0.5f)) - easeIn(seg(t, 0.6f, 0.85f))
        if (level > 0.001f) {
            val top = 480f - level * 520f
            reusable.reset(); reusable.moveTo(0f, 480f)
            var x = 0f
            while (x <= 480f) { reusable.lineTo(x, top + sin(x / 38f + t * 22f) * 12f); x += 12f }
            reusable.lineTo(480f, 480f); reusable.close()
            drawPath(reusable, waterBrush, alpha = 0.92f)
        }
        if (t in 0.3f..0.9f) for (i in 0 until 20) {                        // bubbles
            val p = seeds[i]
            val life = (t * 1.2f * p[2] + p[1]) % 1f
            drawCircle(Color(0xFFBFF2FF), radius = 2f + p[3] * 5f, center = Offset(40f + p[0] / 360f * 400f, 480f - life * 420f),
                style = Stroke(1.5f), alpha = 1f - life)
        }
    }

    private fun tracePaths(scope: DrawScope, paths: List<Path>, offset: Int, k: Float, color: Color, width: Float, alpha: Float, heads: Boolean) = with(scope) {
        paths.forEachIndexed { i, path ->
            measure.setPath(path, false)
            segment.reset()
            val len = pathLengths[offset + i] * k
            measure.getSegment(0f, len, segment, true)
            drawPath(segment, color, style = Stroke(width, cap = StrokeCap.Round), alpha = alpha)
            if (heads) drawCircle(Color.White, radius = 4f, center = measure.getPosition(len), alpha = alpha)
        }
    }

    private fun sliced(scope: DrawScope, full: ImageBitmap?, settle: Float, offsetFor: (Int) -> Float) = with(scope) {
        if (full == null || settle <= 0f) return@with
        val slices = 12
        val sliceH = full.height / slices
        val dstH = ART_SIZE / slices
        for (i in 0 until slices) {
            drawImage(full, srcOffset = IntOffset(0, i * sliceH), srcSize = IntSize(full.width, sliceH),
                dstOffset = IntOffset((ART_CENTER.x - ART_SIZE / 2f + offsetFor(i)).roundToInt(), (ART_CENTER.y - ART_SIZE / 2f + i * dstH).roundToInt()),
                dstSize = IntSize(ART_SIZE.roundToInt(), dstH.roundToInt() + 1), alpha = settle)
        }
    }

    private fun circuit(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        tracePaths(this, circuits, 0, easeOut(seg(t, 0.25f, 0.55f)), c1, 3f, 1f - seg(t, 0.75f, 0.9f), heads = true)
        val assemble = easeOut(seg(t, 0.5f, 0.78f))
        sliced(this, full, assemble) { i -> (1f - assemble) * (if (i % 2 == 0) 120f else -120f) * (0.4f + (i * 37 % 11) / 11f) }
    }

    private fun ghost(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val dim = seg(t, 0.3f, 0.45f) * (1f - seg(t, 0.9f, 1f))
        if (dim > 0f) drawRect(dark, alpha = dim)
        for (i in 0 until 10) {                                             // drifting smoke
            val p = seeds[i]
            val c = Offset(60f + p[0] + sin(t * 3f + p[3] * 6f) * 30f, 420f - p[1] * 320f - t * 80f * p[2])
            translate(c.x, c.y) { drawCircle(smoke, radius = 110f, center = Offset.Zero, alpha = dim) }
        }
        val appear = seg(t, 0.5f, 0.8f)
        val flick = if (appear < 1f && ((t * 40f).toInt() % 3 == 0)) 0.25f else 1f
        drawArt(this, full, glow, Offset(240f + (1f - appear) * 10f * sin(t * 90f), 222f), ART_SIZE, reveal = appear, alpha = flick)
    }

    private fun invisible(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        // refraction shimmer: the alien ripples into view from transparency
        val settle = seg(t, 0.45f, 0.82f)
        val wobble = (1f - settle) * 26f
        sliced(this, full, easeIn(settle)) { i -> sin(i * 0.9f + t * 30f) * wobble }
        ring(this, seg(t, 0.4f, 0.7f), CENTER, 30f, 260f, c1, 3f)
    }

    private fun electric(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val active = seg(t, 0.28f, 0.4f) * (1f - seg(t, 0.78f, 0.92f))
        val frame = (t * 60f).toInt() / 3                                  // re-roll bolt shapes every 3 frames
        if (active > 0f) {
            if (frame % 4 == 0) drawRect(c1, alpha = 0.12f * active)        // strike flash
            for (b in 0 until 6) {
                reusable.reset()
                var p = polar(250f, b * 60f + hash(b, frame, 0) * 30f)
                reusable.moveTo(p.x, p.y)
                for (k in 1..9) {
                    val target = ART_CENTER + (p - ART_CENTER) * (1f - k / 9f)
                    p = target + Offset((hash(b, frame, k) - 0.5f) * 40f, (hash(b, frame, k + 20) - 0.5f) * 40f)
                    reusable.lineTo(p.x, p.y)
                }
                drawPath(reusable, c1, style = Stroke(4f, cap = StrokeCap.Round), alpha = active * 0.8f)
                drawPath(reusable, c2, style = Stroke(1.5f, cap = StrokeCap.Round), alpha = active)
            }
        }
        val reveal = seg(t, 0.55f, 0.8f)
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = reveal, alpha = if (reveal < 1f && frame % 2 == 0) 0.55f else 1f)
    }

    private fun clone(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val merge = easeOut(seg(t, 0.45f, 0.75f))
        val copies = 5
        for (i in 0 until copies) {                                         // copies converge into one
            val pos = polar(170f * (1f - merge), i * 72f + t * 60f, ART_CENTER)
            drawArt(this, null, glow, pos, ART_SIZE * 0.7f, reveal = 1f, alpha = 0.5f * seg(t, 0.35f, 0.45f) * (1f - merge))
        }
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.6f, 0.8f))
        ring(this, seg(t, 0.72f, 0.92f), ART_CENTER, 40f, 240f, c2, 4f)
    }

    private fun sonic(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        for (i in 0 until 6) ring(this, seg(t, 0.3f + i * 0.08f, 0.55f + i * 0.08f), ART_CENTER, 20f, 300f, if (i % 2 == 0) c1 else c2, 7f)
        val beat = 1f + 0.05f * sin(seg(t, 0.55f, 0.9f) * PI.toFloat() * 6f)
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.5f, 0.75f), sx = beat, sy = beat)
    }

    private fun web(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val spin = easeOut(seg(t, 0.28f, 0.5f))
        val fade = 1f - seg(t, 0.85f, 1f)
        for (i in 0 until webRadials) {                                     // radial strands
            val a = i * 360f / webRadials
            drawLine(c2, CENTER, polar(260f * spin, a), strokeWidth = 1.5f, alpha = 0.8f * fade)
        }
        for (r in 1..5) {                                                   // spiral rings
            val k = seg(t, 0.35f + r * 0.04f, 0.5f + r * 0.04f)
            if (k <= 0f) continue
            reusable.reset()
            for (i in 0..webRadials) {
                val p = polar(r * 46f, i * 360f / webRadials)
                if (i == 0) reusable.moveTo(p.x, p.y) else reusable.lineTo(p.x, p.y)
            }
            drawPath(reusable, c2, style = Stroke(1.2f), alpha = k * 0.7f * fade)
        }
        val drop = easeOutBack(seg(t, 0.52f, 0.72f))
        val y = 222f - (1f - drop) * 300f
        drawLine(c2, Offset(240f, 0f), Offset(240f, y - ART_SIZE * 0.4f), strokeWidth = 2f, alpha = seg(t, 0.52f, 0.56f))
        drawArt(this, full, glow, Offset(240f, y), ART_SIZE, reveal = seg(t, 0.55f, 0.75f))
    }

    private fun time(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val show = seg(t, 0.28f, 0.4f) * (1f - seg(t, 0.8f, 0.95f))
        if (show > 0f) {
            drawCircle(c1, radius = 200f, center = CENTER, style = Stroke(4f), alpha = show)
            for (i in 0 until 12) drawLine(c1, polar(180f, i * 30f), polar(196f, i * 30f), strokeWidth = 4f, alpha = show)
            // hands rewind fast, decelerating to a stop
            val sweep = (1f - easeOut(seg(t, 0.3f, 0.75f))) * 1440f
            drawLine(c1, CENTER, polar(150f, -sweep), strokeWidth = 6f, cap = StrokeCap.Round, alpha = show)
            drawLine(c2, CENTER, polar(110f, -sweep / 12f), strokeWidth = 8f, cap = StrokeCap.Round, alpha = show)
            for ((cx, cy, r) in listOf(Triple(120f, 120f, 34f), Triple(370f, 360f, 44f))) {   // gears
                rotate(t * 400f * (if (r > 40f) -1f else 1f), pivot = Offset(cx, cy)) {
                    drawCircle(c2, radius = r, center = Offset(cx, cy), style = Stroke(6f), alpha = show)
                    for (k in 0 until 8) drawRect(c2, topLeft = polar(r + 2f, k * 45f, Offset(cx, cy)) - Offset(4f, 4f), size = Size(8f, 8f), alpha = show)
                }
            }
        }
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.6f, 0.8f))
    }

    private fun mummy(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.55f, 0.7f))
        val wrap = easeOut(seg(t, 0.28f, 0.5f)); val unwrap = easeIn(seg(t, 0.58f, 0.85f))
        if (wrap <= 0f || unwrap >= 1f) return@with
        for (i in 0 until 8) {                                              // bandage strips wrap, then peel away
            val dir = if (i % 2 == 0) 1f else -1f
            val x = dir * ((1f - wrap) * 520f + unwrap * 520f)
            withTransform({ rotate(-12f, pivot = CENTER); translate(x, 0f) }) {
                drawRect(c1, topLeft = Offset(-40f, i * 64f - 20f), size = Size(560f, 56f), alpha = 0.95f)
                drawLine(c2, Offset(-40f, i * 64f + 10f), Offset(520f, i * 64f + 10f), strokeWidth = 2f, alpha = 0.7f)
            }
        }
    }

    private fun plant(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val grow = easeOut(seg(t, 0.28f, 0.6f))
        val fade = 1f - seg(t, 0.82f, 0.98f)
        tracePaths(this, vines, circuits.size, grow, c1, 7f, fade, heads = false)
        if (grow > 0.3f) for (i in 0 until 24) {                            // leaves along the vines
            val p = seeds[i]
            if (p[1] > grow) continue
            val vine = vines[i % vines.size]
            measure.setPath(vine, false)
            val pos = measure.getPosition(measure.length * p[1])
            withTransform({ rotate(p[0], pivot = pos) }) { drawOval(c2, topLeft = pos - Offset(7f, 4f), size = Size(14f, 8f), alpha = fade) }
        }
        val rise = easeOut(seg(t, 0.55f, 0.78f))
        drawArt(this, full, glow, Offset(240f, 222f + (1f - rise) * 200f), ART_SIZE, reveal = seg(t, 0.55f, 0.78f))
    }

    private fun goo(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val merge = easeOut(seg(t, 0.4f, 0.6f)); val morph = seg(t, 0.6f, 0.8f)
        for (i in 0 until 10) {                                             // blobs splat in and merge
            val p = seeds[i]
            val splat = easeOutBack(seg(t, 0.28f + p[1] * 0.08f, 0.42f + p[1] * 0.08f))
            val pos = polar(170f * (1f - merge), p[0], ART_CENTER)
            drawCircle(lerp(c1, c2, p[3] * 0.5f), radius = (18f + p[3] * 22f) * splat * (1f - morph), center = pos, alpha = 0.95f)
        }
        if (merge > 0f) drawCircle(c1, radius = 90f * merge * (1f - morph), center = ART_CENTER, alpha = 0.9f)
        drawArt(this, full, glow, ART_CENTER, ART_SIZE * (0.6f + 0.4f * easeOutBack(morph)), reveal = morph)
        for (i in 10 until 18) {                                            // drips
            val p = seeds[i]; val d = seg(t, 0.7f + p[1] * 0.1f, 1f)
            if (d in 0.001f..0.999f) drawOval(c1, topLeft = Offset(170f + p[0] / 360f * 140f, 300f + d * d * 160f), size = Size(6f, 10f + d * 8f), alpha = 1f - d)
        }
    }

    private fun magnet(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val field = seg(t, 0.28f, 0.42f) * (1f - seg(t, 0.75f, 0.9f))
        if (field > 0f) for (i in 1..4) {                                   // field lines
            drawOval(c1, topLeft = Offset(240f - i * 50f, 222f - 30f - i * 20f), size = Size(i * 100f, 60f + i * 40f), style = Stroke(1.5f), alpha = field * 0.5f)
        }
        val pull = easeIn(seg(t, 0.35f, 0.62f))
        if (t > 0.3f) for (p in seeds) {                                                  // metal scraps spiral in and snap
            val r = 240f * (1f - pull) * (0.5f + p[1] * 0.5f)
            if (r < 4f) continue
            val pos = polar(r, p[0] + pull * 300f, ART_CENTER)
            withTransform({ rotate(p[0] + t * 500f, pivot = pos) }) {
                drawRect(if (p[3] > 0.6f) c1 else c2, topLeft = pos - Offset(4f, 2f), size = Size(8f + p[3] * 6f, 4f))
            }
        }
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.6f, 0.8f))
        ring(this, seg(t, 0.62f, 0.85f), ART_CENTER, 30f, 260f, c1, 5f)
    }

    private fun eyes(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val fade = 1f - seg(t, 0.68f, 0.8f)
        for (i in 0 until 14) {                                             // eyes blink open around the screen
            val p = seeds[i]
            val open = easeOutBack(seg(t, 0.28f + i * 0.015f, 0.38f + i * 0.015f))
            if (open <= 0f || fade <= 0f) continue
            val pos = polar(130f + p[1] * 90f, p[0])
            drawOval(Color.White, topLeft = pos - Offset(16f, 10f * open), size = Size(32f, 20f * open), alpha = fade)
            drawCircle(c1, radius = 7f * open, center = pos, alpha = fade)
            drawCircle(Color.Black, radius = 3f * open, center = pos, alpha = fade)
            val beam = seg(t, 0.5f, 0.62f)                                  // all fire at the centre
            if (beam in 0.001f..0.999f) drawLine(c2, pos, pos + (ART_CENTER - pos) * beam, strokeWidth = 3f, alpha = fade)
        }
        if (t in 0.6f..0.7f) drawCircle(burst, radius = 200f, center = ART_CENTER, alpha = 1f - seg(t, 0.6f, 0.7f))
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.62f, 0.8f))
    }

    private fun roll(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val k = seg(t, 0.3f, 0.6f)
        val unfold = easeOut(seg(t, 0.6f, 0.78f))
        if (unfold < 1f && k > 0f) {
            // armoured ball rolls in from the left with two bounces
            val x = -60f + easeOut(k) * 300f
            val bounce = kotlin.math.abs(sin(k * PI.toFloat() * 2.5f)) * 80f * (1f - k)
            val c = Offset(x, 290f - bounce)
            val r = 70f * (1f - unfold)
            rotate(k * 900f, pivot = c) {
                drawCircle(c1, radius = r, center = c)
                drawArc(c2, 20f, 80f, useCenter = true, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2))
                drawArc(c2, 200f, 80f, useCenter = true, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2))
                drawCircle(Color.Black, radius = r, center = c, style = Stroke(3f))
            }
        }
        ring(this, seg(t, 0.58f, 0.8f), Offset(240f, 290f), 30f, 220f, c2, 5f)
        drawArt(this, full, glow, ART_CENTER, ART_SIZE * (0.3f + 0.7f * unfold), reveal = unfold)
    }

    private fun cosmic(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val space = seg(t, 0.28f, 0.42f) * (1f - seg(t, 0.92f, 1f))
        if (space > 0f) drawRect(nebula, alpha = space)
        for (p in seeds) {                                                  // warp starfield
            val k = (t * 1.5f * p[2] + p[1]) % 1f
            val a = polar(20f + k * k * 260f, p[0]); val b = polar(20f + k * k * 260f + 6f + k * 30f, p[0])
            drawLine(if (p[3] > 0.8f) c2 else c1, a, b, strokeWidth = 1f + k * 2f, alpha = space * k)
        }
        rotate(t * 120f, pivot = CENTER) {                                  // galaxy arms
            for (arm in 0 until 3) for (i in 0 until 14) {
                val pos = polar(i * 13f, arm * 120f + i * 22f)
                drawCircle(c2, radius = 3f - i * 0.15f, center = pos, alpha = space * (1f - seg(t, 0.6f, 0.75f)))
            }
        }
        if (t in 0.58f..0.68f) drawRect(Color.White, alpha = 1f - seg(t, 0.58f, 0.68f))
        drawArt(this, full, glow, ART_CENTER, ART_SIZE, reveal = seg(t, 0.6f, 0.8f))
    }

    private fun radiation(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val heat = seg(t, 0.28f, 0.42f) * (1f - seg(t, 0.85f, 1f))
        if (heat > 0f) drawCircle(tintWash, radius = 320f, center = CENTER, alpha = heat)
        for (i in 0 until 5) ring(this, seg(t, 0.32f + i * 0.09f, 0.6f + i * 0.09f), ART_CENTER, 10f, 280f, if (i % 2 == 0) c1 else c2, 9f)
        val settle = seg(t, 0.55f, 0.82f)
        sliced(this, full, easeOut(settle)) { i -> sin(i * 1.3f + t * 40f) * (1f - settle) * 12f }   // heat shimmer
        if (t > 0.3f) for (i in 0 until 20) {
            val p = seeds[i]; val life = (t * 1.3f * p[2] + p[1]) % 1f
            drawCircle(c2, radius = 1.5f + p[3] * 2f, center = polar(60f + life * 200f, p[0], ART_CENTER), alpha = (1f - life) * heat)
        }
    }

    private fun wind(s: DrawScope, t: Float, full: ImageBitmap?, glow: ImageBitmap?) = with(s) {
        val vortex = seg(t, 0.28f, 0.4f) * (1f - seg(t, 0.8f, 0.95f))
        if (vortex > 0f) for (i in 0 until 8) {                             // swirling gusts
            val r = 60f + i * 24f
            drawArc(if (i % 2 == 0) c1 else c2, startAngle = t * (900f - i * 60f) + i * 45f, sweepAngle = 70f, useCenter = false,
                topLeft = CENTER - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(3f, cap = StrokeCap.Round), alpha = vortex * 0.8f)
        }
        for (i in 0 until 18) {                                             // leaves caught in the wind
            val p = seeds[i]
            val pos = polar(80f + p[1] * 150f, p[0] + t * 700f * p[2])
            drawOval(c1, topLeft = pos - Offset(5f, 3f), size = Size(10f, 6f), alpha = vortex)
        }
        val spin = easeOutBack(seg(t, 0.5f, 0.75f))
        drawArt(this, full, glow, ART_CENTER, ART_SIZE * spin.coerceAtLeast(0.01f), reveal = seg(t, 0.5f, 0.75f), sx = cos((1f - spin) * 6f))
    }
}

/** Rising fire (AGSL): fractal noise flames, body → flame → tip colours from the alien's spec. */
private const val FIRE_AGSL = """
uniform float2 uRes;
uniform float uTime;
uniform float uLevel;
uniform float3 uBody;
uniform float3 uFlame;
uniform float3 uTip;

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
    half3 col = mix(half3(uBody * 0.5), half3(uBody), smoothstep(0.0, 0.45, i));
    col = mix(col, half3(uFlame), smoothstep(0.45, 0.8, i));
    col = mix(col, half3(uTip), smoothstep(0.88, 1.0, i) * n);
    float a = i * 0.92;
    return half4(col * a, a);
}
"""
