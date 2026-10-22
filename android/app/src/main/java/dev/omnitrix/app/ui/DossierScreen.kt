package dev.omnitrix.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import dev.omnitrix.app.Alien
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.Energy
import kotlin.math.min

/** Full alien profile (swipe up on the hologram). Static: draws once, no animation cost. */
@Composable
fun DossierScreen(alienIndex: Int, energy: Energy, art: AlienArt, onClose: () -> Unit) {
    val alien: Alien = Aliens.all[alienIndex]
    LaunchedEffect(alienIndex) { art.prefetch(alienIndex, radius = 0) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Tap or swipe down closes.
                awaitEachGesture {
                    awaitFirstDown()
                    do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                    onClose()
                }
            },
    ) {
        val density = LocalDensity.current
        val scale = min(constraints.maxWidth, constraints.maxHeight) / VIRTUAL
        val main = energy.main
        val dim = Color(0xFF6F9A66)
        val measurer = rememberTextMeasurer(cacheSize = 16)
        val texts = remember(scale) {
            fun m(text: String, size: Float, weight: FontWeight, color: Color, spacing: Float = 0f, width: Float = 300f) =
                measurer.measure(text, orbitron(density, scale, size, weight, color, spacing).copy(textAlign = TextAlign.Center),
                    constraints = Constraints(maxWidth = (width * scale).toInt()))
            val light = Color(0xFFDFF5D6)
            listOf(
                measurer.fitted(alien.name, { orbitron(density, scale, it, FontWeight.Black, main, 2.5f) }, 26f, 320f, scale) to 216f,
                m("SPECIES", 8f, FontWeight.Bold, dim, 2.5f) to 250f,
                m(alien.species.uppercase(), 11f, FontWeight.Bold, light, 0.5f, 150f) to 262f,
                m("HOME WORLD", 8f, FontWeight.Bold, dim, 2.5f) to 250f,
                m(alien.planet.uppercase(), 11f, FontWeight.Bold, light, 0.5f, 150f) to 262f,
                m("POWERS", 8f, FontWeight.Bold, dim, 2.5f) to 294f,
                m(alien.powers.joinToString(" · "), 11f, FontWeight.Bold, light, 0.3f, 330f) to 307f,
                m("WEAKNESS", 8f, FontWeight.Bold, dim, 2.5f) to 336f,
                m(alien.weakness.uppercase(), 11f, FontWeight.Bold, light, 0.5f) to 349f,
                m("◆ ${alien.eraLabel}", 10f, FontWeight.Bold, main, 2f) to 386f,
            )
        }
        val statTexts = remember(scale) {
            Aliens.STAT_LABELS.mapIndexed { k, label ->
                measurer.measure(label, orbitron(density, scale, 9f, FontWeight.Bold, dim, 1f)) to
                    measurer.measure("${alien.stats[k]}", orbitron(density, scale, 14f, FontWeight.Black, main))
            }
        }
        val tint = remember(energy) { ColorFilter.tint(main) }
        val holoFilter = remember(energy) { hologramFilter(energy.bright) }
        val cone = remember(energy) { Brush.verticalGradient(listOf(main.copy(alpha = 0f), main.copy(alpha = 0.3f)), startY = 60f, endY = 190f) }

        Canvas(Modifier.fillMaxSize()) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                // stat arcs in the four bezel quadrants
                listOf(315f, 45f, 225f, 135f).forEachIndexed { k, c ->
                    val start = c - 28f - 90f
                    drawArc(Color(0xFF16231A), start, 56f, false, Offset(22f, 22f), Size(436f, 436f), style = Stroke(10f, cap = StrokeCap.Round))
                    drawArc(main, start, 56f * alien.stats[k] / 10f, false, Offset(22f, 22f), Size(436f, 436f), style = Stroke(10f, cap = StrokeCap.Round))
                }
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(214f, 190f); lineTo(266f, 190f); lineTo(300f, 60f); lineTo(180f, 60f); close()
                }, cone)
                drawOval(main, topLeft = Offset(212f, 185f), size = Size(56f, 10f), alpha = 0.7f)
                art.version.intValue   // redraw when the artwork arrives
                art.glow(alienIndex)?.let { glow ->
                    val (go, gs) = squareDst(Offset(240f, 122f), 140f)
                    drawImage(glow, srcSize = glow.srcSize(), dstOffset = go, dstSize = gs, alpha = 0.8f, colorFilter = tint)
                }
                art.holo(alienIndex)?.let { holo ->
                    val (eo, es) = squareDst(Offset(240f, 122f), 136f)
                    drawImage(holo, srcSize = holo.srcSize(), dstOffset = eo, dstSize = es, colorFilter = holoFilter)
                }
                drawLine(Color(0xFF1F3320), Offset(240f, 248f), Offset(240f, 276f), strokeWidth = 1f)
            }
            val columns = listOf(240f, 160f, 160f, 320f, 320f, 240f, 240f, 240f, 240f, 240f)
            texts.forEachIndexed { i, (layout, y) -> drawCenteredAt(layout, columns[i], y, scale) }
            listOf(315f, 45f, 225f, 135f).forEachIndexed { k, c ->
                val p = polar(190f, c)
                drawCenteredAt(statTexts[k].first, p.x, p.y - 12f, scale)
                drawCenteredAt(statTexts[k].second, p.x, p.y + 2f, scale)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCenteredAt(layout: TextLayoutResult, x: Float, y: Float, scale: Float) {
    drawText(layout, topLeft = Offset(x * scale - layout.size.width / 2f, y * scale))
}
