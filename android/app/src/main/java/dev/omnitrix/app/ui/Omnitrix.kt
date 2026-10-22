package dev.omnitrix.app.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
val STEP_DEG = 360f / Aliens.size

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

/** Carousel index sitting at 12 o'clock for a ring rotation. */
fun nearestAlien(ringDeg: Float): Int = Math.floorMod((-ringDeg / STEP_DEG).roundToInt(), Aliens.size)

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

class EmblemArt(val emblems: List<ImageBitmap>, val glows: List<ImageBitmap>) {
    companion object {
        /** Source PNG canvas (glyph is 160 px centred in 240). */
        val SRC = IntSize(240, 240)
    }
}

/** Decodes all emblem art once, off the main thread; null until ready. */
@Composable
fun rememberEmblemArt(): State<EmblemArt?> {
    val resources: Resources = LocalContext.current.resources
    return produceState<EmblemArt?>(null) {
        value = withContext(Dispatchers.Default) {
            EmblemArt(
                Aliens.all.map { ImageBitmap.imageResource(resources, it.emblem).also(ImageBitmap::prepareToDraw) },
                Aliens.all.map { ImageBitmap.imageResource(resources, it.glow).also(ImageBitmap::prepareToDraw) },
            )
        }
    }
}

/** Integer destination rect (virtual px) for an emblem centred at [center] with side [size]. */
fun emblemDst(center: Offset, size: Float): Pair<IntOffset, IntSize> {
    val s = size.roundToInt()
    return IntOffset((center.x - size / 2f).roundToInt(), (center.y - size / 2f).roundToInt()) to IntSize(s, s)
}
