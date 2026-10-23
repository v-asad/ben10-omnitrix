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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import dev.omnitrix.app.Energy
import dev.omnitrix.app.Haptics
import dev.omnitrix.app.Settings
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min

/** Heart-rate zones by % of max heart rate. */
private class Zone(val upTo: Float, val word: String, val color: Color)

private val ZONES = listOf(
    Zone(0.60f, "CALM", Color(0xFF2F8F2A)),
    Zone(0.70f, "STEADY", Color(0xFF4FCF2A)),
    Zone(0.80f, "STRONG", Color(0xFF9DFF2E)),
    Zone(0.90f, "BURN", Color(0xFFFFB02E)),
    Zone(9.99f, "MAX", Color(0xFFFF4A2E)),
)

/** Heart-rate permission: Android 16 (Wear OS 6) split BODY_SENSORS into granular health permissions. */
private val HEART_PERMISSIONS = if (Build.VERSION.SDK_INT >= 36)
    arrayOf("android.permission.health.READ_HEART_RATE", Manifest.permission.BODY_SENSORS)
else arrayOf(Manifest.permission.BODY_SENSORS)

/**
 * Bio-scan, minimal: the zone gauge around the edge (its needle pulses with each beat), your BPM,
 * a pulse trace paced by it and one zone word. The sensor is registered only while this screen is
 * open. The trace follows your measured rate; watches don't expose a raw ECG to apps.
 */
@Composable
fun BioScanScreen(energy: Energy, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val maxHr = remember { 220f - Settings(context).age }

    fun granted() = HEART_PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    var permitted by remember { mutableStateOf(granted()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permitted = granted() }
    LaunchedEffect(Unit) { if (!permitted) request.launch(HEART_PERMISSIONS) }

    var bpm by remember { mutableIntStateOf(0) }
    var zone by remember { mutableIntStateOf(-1) }

    if (permitted) DisposableEffect(Unit) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values[0].toInt()
                if (v <= 0 || event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) return
                bpm = v
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
        val measurer = rememberTextMeasurer(cacheSize = 12)
        val digits = remember(scale) { DigitAtlas(measurer, orbitron(density, scale, 96f, FontWeight.Black, Color(0xFFEAFFE0)), "0123456789-") }
        val unit = remember(scale) { measurer.measure("BPM", orbitron(density, scale, 12f, FontWeight.Bold, Color(0xFF6F9A66), 5f)) }
        val zoneColor = if (zone >= 0) ZONES[zone].color else Color(0xFF4F7A45)
        val word = remember(zone, permitted, scale) {
            measurer.measure(when { !permitted -> "TAP TO ALLOW"; zone < 0 -> "SCANNING"; else -> ZONES[zone].word },
                orbitron(density, scale, 12f, FontWeight.Black, if (permitted) zoneColor else energy.main, 4f))
        }
        val trace = remember { Path() }

        Canvas(Modifier.fillMaxSize()) {
            val now = clock.longValue
            val period = 60_000f / (if (bpm > 0) bpm else 60)
            val phase = (now % period.toLong()) / period                    // 0..1 within a beat

            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                // zone gauge: 40 bpm … max HR + 10, over -130°…130°
                val lo = 40f; val hi = maxHr + 10f
                fun ang(v: Float) = -130f + ((v.coerceIn(lo, hi) - lo) / (hi - lo)) * 260f
                var from = lo
                ZONES.forEachIndexed { i, z ->
                    val to = min(hi, z.upTo * maxHr)
                    val active = i == zone
                    drawArc(z.color, ang(from) - 90f + 1f, ang(to) - ang(from) - 2f, false, Offset(24f, 24f), Size(432f, 432f),
                        style = Stroke(if (active) 14f else 7f, cap = StrokeCap.Butt), alpha = if (active) 1f else 0.3f)
                    from = to
                }
                if (bpm > 0) {                                                // needle pulses with each beat
                    val needle = polar(216f, ang(bpm.toFloat()))
                    drawCircle(zoneColor, radius = 10f + 7f * exp(-phase * 8f), center = needle, alpha = 0.35f)
                    drawCircle(Color.White, radius = 8f, center = needle)
                }
                // pulse trace, scrolling, paced by the measured rate (stays inside the circle: no clip)
                trace.reset()
                var x = 96f
                val speed = 0.12f                                              // px per ms
                while (x <= 384f) {
                    val t = ((now * speed - x) / (period * speed)).let { it - floor(it) }
                    val y = 330f - pqrst(t) * 38f
                    if (x == 96f) trace.moveTo(x, y) else trace.lineTo(x, y)
                    x += 3f
                }
                drawPath(trace, zoneColor, style = Stroke(2.5f, join = StrokeJoin.Round), alpha = if (bpm > 0) 1f else 0.35f)
            }
            fun centered(layout: TextLayoutResult, y: Float) =
                drawText(layout, topLeft = Offset(size.width / 2f - layout.size.width / 2f, y * scale))
            digits.draw(this, if (bpm > 0) "$bpm" else "--", size.width / 2f, 205f * scale)
            centered(unit, 262f)
            centered(word, 384f)
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
