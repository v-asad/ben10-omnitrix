package dev.omnitrix.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.Energy
import dev.omnitrix.app.Haptics
import dev.omnitrix.app.Settings
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Heart-rate zones by % of max heart rate, each named after an alien. */
private class Zone(val upTo: Float, val alien: String, val tag: String, val color: Color)

private val ZONES = listOf(
    Zone(0.60f, "grey-matter", "CALM", Color(0xFF2F8F2A)),
    Zone(0.70f, "diamondhead", "STEADY", Color(0xFF4FCF2A)),
    Zone(0.80f, "four-arms", "STRENGTH", Color(0xFF9DFF2E)),
    Zone(0.90f, "heatblast", "BURN", Color(0xFFFFB02E)),
    Zone(9.99f, "xlr8", "MAX SPEED", Color(0xFFFF4A2E)),
)

/** Heart-rate permission: Android 16 (Wear OS 6) split BODY_SENSORS into granular health permissions. */
private val HEART_PERMISSIONS = if (Build.VERSION.SDK_INT >= 36)
    arrayOf("android.permission.health.READ_HEART_RATE", Manifest.permission.BODY_SENSORS)
else arrayOf(Manifest.permission.BODY_SENSORS)

/**
 * Bio-scan: live heart rate from the watch's sensor (registered only while this screen is open),
 * a mini Omnitrix core beating at your BPM, a pulse trace paced by it, and alien-named zones.
 * The trace is drawn from your measured rate; watches don't expose a raw ECG to apps.
 */
@Composable
fun BioScanScreen(energy: Energy, art: AlienArt, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val settings = remember { Settings(context) }
    val maxHr = 220f - settings.age

    fun granted() = HEART_PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    var permitted by remember { mutableStateOf(granted()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permitted = granted() }
    LaunchedEffect(Unit) { if (!permitted) request.launch(HEART_PERMISSIONS) }

    var bpm by remember { mutableIntStateOf(0) }
    val session = remember { IntArray(3) }                     // min, max, sum
    var count by remember { mutableIntStateOf(0) }
    var zone by remember { mutableIntStateOf(-1) }

    if (permitted) DisposableEffect(Unit) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values[0].toInt()
                if (v <= 0 || event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) return
                bpm = v
                session[0] = if (count == 0) v else min(session[0], v)
                session[1] = max(session[1], v)
                session[2] += v
                count++
                val z = ZONES.indexOfFirst { v / maxHr <= it.upTo }
                if (z != zone) { if (zone >= 0) Haptics.tick(view); zone = z }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sensors?.unregisterListener(listener) }
    }

    // Frame clock for the beat and the trace (only while this screen is visible).
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { clock.longValue = it } }

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(permitted) {
        detectTapGestures { if (!permitted) request.launch(HEART_PERMISSIONS) }
    }) {
        val density = LocalDensity.current
        val scale = min(constraints.maxWidth, constraints.maxHeight) / VIRTUAL
        val measurer = rememberTextMeasurer(cacheSize = 24)
        val digits = remember(scale) { DigitAtlas(measurer, orbitron(density, scale, 70f, FontWeight.Black, Color(0xFFEAFFE0)), "0123456789-") }
        val dim = Color(0xFF6F9A66)
        val title = remember(scale) { measurer.measure("BIO-SCAN", orbitron(density, scale, 12f, FontWeight.Bold, dim, 5f)) }
        val small = { text: String, color: Color, size: Float -> measurer.measure(text, orbitron(density, scale, size, FontWeight.Bold, color, 2f)) }
        val z = zone.coerceAtLeast(0)
        val zoneLabel = remember(zone, scale) { small(if (zone < 0) "SCANNING…" else "ZONE ${z + 1} · ${ZONES[z].tag}", dim, 11f) }
        val zoneName = remember(zone, scale) { if (zone < 0) null else measurer.measure(Aliens.all.first { it.slug == ZONES[z].alien }.name,
            orbitron(density, scale, 15f, FontWeight.Black, ZONES[z].color, 2f)) }
        val footer = remember(count / 5, scale) {
            val avg = if (count > 0) session[2] / count else 0
            listOf("MIN" to session[0], "AVG" to avg, "MAX" to session[1]).map { (k, v) ->
                small(k, Color(0xFF4F7A45), 9f) to small(if (count > 0) "$v" else "--", Color(0xFF9DFF7A), 14f)
            }
        }
        val prompt = remember(scale) { small("TAP TO ALLOW HEART RATE", energy.main, 10f) }
        val zoneIcon = remember(zone) { if (zone < 0) -1 else Aliens.all.indexOfFirst { it.slug == ZONES[z].alien } }
        val kit = remember(energy) { BioKit(energy) }

        Canvas(Modifier.fillMaxSize()) {
            val now = clock.longValue
            val rate = if (bpm > 0) bpm else 60
            val period = 60_000f / rate
            val phase = (now % period.toLong()) / period                    // 0..1 within a beat
            art.version.intValue

            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                // zone gauge: 40 bpm … max HR + 10, over -130°…130°
                val lo = 40f; val hi = maxHr + 10f
                fun ang(v: Float) = -130f + ((v.coerceIn(lo, hi) - lo) / (hi - lo)) * 260f
                var from = lo
                ZONES.forEachIndexed { i, zz ->
                    val to = min(hi, zz.upTo * maxHr)
                    val active = i == zone
                    drawArc(zz.color, ang(from) - 90f + 1f, ang(to) - ang(from) - 2f, false, Offset(28f, 28f), Size(424f, 424f),
                        style = Stroke(if (active) 16f else 9f, cap = StrokeCap.Butt), alpha = if (active) 1f else 0.4f)
                    from = to
                }
                if (bpm > 0) {
                    val needle = polar(212f, ang(bpm.toFloat()))
                    drawCircle(Color.White, radius = 11f, center = needle)
                    drawCircle(Color.Black, radius = 11f, center = needle, style = Stroke(3f))
                }
                // DNA helix backdrop
                for (i in 0 until 22) {
                    val y = 40f + i * 19f
                    val w = kotlin.math.sin(i * 0.6f + now / 900f) * 90f
                    drawLine(energy.main, Offset(240f - w, y), Offset(240f + w, y), strokeWidth = 2f, alpha = 0.12f)
                }
                // mini Omnitrix core, beating at your pulse
                val beat = 1f + 0.14f * exp(-phase * 9f)
                withTransform({ scale(beat, beat, pivot = Offset(240f, 118f)) }) {
                    drawCircle(kit.coreGlow, radius = 36f, center = Offset(240f, 118f))
                    drawCircle(energy.main, radius = 28f, center = Offset(240f, 118f))
                    drawPath(kit.miniWedges, Color(0xFF050805))   // overhang lands on the black background: no clip needed
                    drawCircle(Color.Black, radius = 28f, center = Offset(240f, 118f), style = Stroke(3f))
                }
                // pulse trace, scrolling, paced by the measured rate
                kit.trace.reset()
                var x = 72f                                                    // stays inside the circle: no clip needed
                val speed = 0.12f                                              // px per ms
                while (x <= 408f) {
                    val t = ((now * speed - x) / (period * speed)).let { it - kotlin.math.floor(it) }
                    val y = 262f - pqrst(t) * 46f
                    if (x == 72f) kit.trace.moveTo(x, y) else kit.trace.lineTo(x, y)
                    x += 3f
                }
                drawPath(kit.trace, energy.main, style = Stroke(6f, join = StrokeJoin.Round), alpha = 0.25f)
                drawPath(kit.trace, energy.main, style = Stroke(2.5f, join = StrokeJoin.Round))
                if (zoneIcon >= 0) art.icon(zoneIcon)?.let { icon ->
                    val (o, s) = squareDst(Offset(178f, 352f), 30f)
                    drawImage(icon, srcSize = icon.srcSize(), dstOffset = o, dstSize = s, colorFilter = ColorFilter.tint(ZONES[z].color))
                }
            }
            fun centered(layout: androidx.compose.ui.text.TextLayoutResult, cx: Float, y: Float) =
                drawText(layout, topLeft = Offset(cx * scale - layout.size.width / 2f, y * scale))
            centered(title, 240f, 162f)
            if (!permitted) centered(prompt, 240f, 214f)
            else digits.draw(this, if (bpm > 0) "$bpm" else "--", 240f * scale, 214f * scale)
            centered(zoneLabel, 240f, 306f)
            zoneName?.let { drawText(it, topLeft = Offset(198f * scale, 343f * scale)) }
            footer.forEachIndexed { i, (k, v) ->
                val cx = 150f + i * 90f
                centered(k, cx, 388f); centered(v, cx, 402f)
            }
        }
    }
}

/** One heartbeat, 0..1 → amplitude (P wave, QRS spike, T wave). */
private fun pqrst(t: Float): Float = when {
    t < 0.10f -> 0f
    t < 0.18f -> 0.12f * kotlin.math.sin((t - 0.10f) / 0.08f * Math.PI.toFloat())
    t < 0.24f -> 0f
    t < 0.26f -> -0.15f
    t < 0.29f -> 1f
    t < 0.32f -> -0.35f
    t < 0.42f -> 0f
    t < 0.56f -> 0.22f * kotlin.math.sin((t - 0.42f) / 0.14f * Math.PI.toFloat())
    else -> 0f
}

private class BioKit(energy: Energy) {
    val coreGlow = Brush.radialGradient(listOf(energy.main.copy(alpha = 0.45f), Color.Transparent), center = Offset(240f, 118f), radius = 40f)
    val miniWedges = Path().apply {
        moveTo(237f, 118f); lineTo(224f, 93f); lineTo(214f, 104f); lineTo(212f, 118f); lineTo(214f, 132f); lineTo(224f, 143f); close()
        moveTo(243f, 118f); lineTo(256f, 93f); lineTo(266f, 104f); lineTo(268f, 118f); lineTo(266f, 132f); lineTo(256f, 143f); close()
    }
    val trace = Path()
}
