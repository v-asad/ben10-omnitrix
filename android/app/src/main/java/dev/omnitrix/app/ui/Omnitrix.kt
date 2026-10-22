package dev.omnitrix.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Shared Omnitrix drawing kit. Every screen draws in a virtual 480×480 space (the Watch8's
 * native resolution) scaled to the real canvas, mirroring design/omnitrix.js.
 */

val Orbitron = FontFamily(
    Font(R.font.orbitron_black, FontWeight.Black),
    Font(R.font.orbitron_bold, FontWeight.Bold),
)

const val VIRTUAL = 480f
val CENTER = Offset(240f, 240f)
/** Angular spacing of the bezel carousel; only a window of the roster is on screen. */
const val SPACING_DEG = 26f

/** Point on a circle around the face centre; 0° = 12 o'clock, clockwise. */
fun polar(radius: Float, degrees: Float, center: Offset = CENTER): Offset {
    val rad = Math.toRadians((degrees - 90f).toDouble())
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

/** Angle of [p] around [c]; 0° = 12 o'clock, clockwise. */
fun angleOf(p: Offset, c: Offset): Float =
    Math.toDegrees(atan2((p.x - c.x).toDouble(), (c.y - p.y).toDouble())).toFloat()

/** Wraps to -180..180. */
fun wrap(deg: Float): Float = ((deg % 360f) + 540f) % 360f - 180f

/** Roster index nearest to carousel position [pos] (position = fractional index at 12 o'clock). */
fun nearestAlien(pos: Float): Int = Math.floorMod(pos.roundToInt(), Aliens.size)

/** Signed distance from [pos] to index [i] around the roster ring, in -n/2..n/2. */
fun ringDistance(i: Int, pos: Float, n: Int = Aliens.size): Float {
    val d = (i - pos) % n
    return when {
        d > n / 2f -> d - n
        d < -n / 2f -> d + n
        else -> d
    }
}

fun circlePath(center: Offset, radius: Float) = Path().apply { addOval(Rect(center, radius)) }

/** The two black side wedges of the hourglass (clip to the core circle when drawing). */
val WEDGES = Path().apply {
    moveTo(226f, 240f); lineTo(104f, 30f); lineTo(40f, 30f); lineTo(40f, 450f); lineTo(104f, 450f); close()
    moveTo(254f, 240f); lineTo(376f, 30f); lineTo(440f, 30f); lineTo(440f, 450f); lineTo(376f, 450f); close()
}

/** Text style sized in virtual pixels for a canvas scaled by [scale]. */
fun orbitron(density: Density, scale: Float, size: Float, weight: FontWeight, color: Color, spacing: Float = 0f) =
    with(density) {
        TextStyle(
            fontFamily = Orbitron,
            fontWeight = weight,
            fontSize = (size * scale).toSp(),
            letterSpacing = (spacing * scale).toSp(),
            color = color,
        )
    }

/** Integer destination rect (virtual px) for a square image centred at [center] with side [size]. */
fun squareDst(center: Offset, size: Float): Pair<IntOffset, IntSize> {
    val s = size.roundToInt()
    return IntOffset((center.x - size / 2f).roundToInt(), (center.y - size / 2f).roundToInt()) to IntSize(s, s)
}

fun ImageBitmap.srcSize() = IntSize(width, height)

/**
 * Hologram colouring: the plates are pre-baked luminance (tools/build_alien_art.py), so tinting
 * is a single multiply, far cheaper per frame than a colour matrix.
 */
fun hologramFilter(tint: Color): ColorFilter = ColorFilter.tint(tint, BlendMode.Modulate)

/** Measures [text], shrinking the font until it fits [maxWidth] virtual px. */
fun TextMeasurer.fitted(text: String, style: (Float) -> TextStyle, size: Float, maxWidth: Float, scale: Float): TextLayoutResult {
    val first = measure(text, style(size))
    val limit = maxWidth * scale
    return if (first.size.width <= limit) first else measure(text, style(size * limit / first.size.width * 0.98f))
}
