package dev.omnitrix.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.DnaCodex
import dev.omnitrix.app.Energy
import kotlin.math.min
import kotlin.math.roundToInt

private const val PER_ROW = 5

/** The DNA Codex: all 50 aliens, which you have, and how far you are from the next. */
@Composable
fun CodexScreen(energy: Energy, art: AlienArt, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val codex = remember { DnaCodex(context) }
    fun stepsAllowed() = context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(stepsAllowed()) }
    var refresh by remember { mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = stepsAllowed()
        if (allowed) codex.sampleSteps { refresh++ }       // first reading starts the count
    }
    LaunchedEffect(Unit) {
        if (!allowed) request.launch(Manifest.permission.ACTIVITY_RECOGNITION) else codex.sampleSteps { refresh++ }
        codex.markSeen()
    }

    refresh.let { }
    val unlocked = codex.unlockedCount
    val total = Aliens.size
    val dim = Color(0xFF6F9A66)
    fun style(color: Color, size: Int, weight: FontWeight = FontWeight.Bold, spacing: Float = 1.5f) =
        TextStyle(fontFamily = Orbitron, fontWeight = weight, fontSize = size.sp, color = color, letterSpacing = spacing.sp, textAlign = TextAlign.Center)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = rememberScalingLazyListState(initialCenterItemIndex = 1),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { BasicText("DNA CODEX", style = style(energy.main, 14, FontWeight.Black, 3f), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) }
        item {
            BasicText(if (unlocked >= total) "BEN 10,000" else "$unlocked / $total", style = style(Color(0xFFEAFFE0), 24, FontWeight.Black),
                modifier = Modifier.fillMaxWidth())
        }
        item {
            val next = if (unlocked < total) Aliens.all[unlocked].name else null
            val label = when {
                !allowed -> "TAP TO ALLOW STEPS"
                next == null -> "EVERY ALIEN UNLOCKED"
                else -> "NEXT: $next · %,d STEPS".format(codex.stepsToNext)
            }
            BasicText(label, style = style(if (allowed) dim else energy.main, 9),
                modifier = Modifier.fillMaxWidth().then(if (!allowed) Modifier.clickable { request.launch(Manifest.permission.ACTIVITY_RECOGNITION) } else Modifier))
        }
        item {
            Canvas(Modifier.fillMaxWidth(0.7f).height(10.dp)) {
                val y = size.height / 2f
                drawLine(Color(0xFF1C2A1A), Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
                drawLine(energy.main, Offset(0f, y), Offset(size.width * codex.progressToNext, y), strokeWidth = size.height, cap = StrokeCap.Round)
            }
        }
        if (energy == Energy.MASTER) item {
            BasicText("MASTER CONTROL: ALL DNA ACTIVE", style = style(energy.main, 8), modifier = Modifier.fillMaxWidth())
        }
        val rows = (total + PER_ROW - 1) / PER_ROW
        items(rows) { row ->
            Canvas(Modifier.fillMaxWidth(0.86f).aspectRatio(PER_ROW.toFloat())) {
                art.version.intValue
                val cell = size.width / PER_ROW
                for (c in 0 until PER_ROW) {
                    val i = row * PER_ROW + c
                    if (i >= total) break
                    val center = Offset(cell * c + cell / 2f, size.height / 2f)
                    val has = i < unlocked
                    val r = min(cell, size.height) * 0.44f
                    drawCircle(Color.Black, radius = r, center = center)
                    drawCircle(if (has) energy.main else Color(0xFF223322), radius = r, center = center, style = Stroke(if (i == unlocked) 3f else 1.5f))
                    art.icon(i)?.let { icon ->
                        val s = (r * 1.5f).roundToInt()
                        drawImage(icon, srcSize = IntSize(icon.width, icon.height),
                            dstOffset = IntOffset((center.x - s / 2f).roundToInt(), (center.y - s / 2f).roundToInt()), dstSize = IntSize(s, s),
                            colorFilter = ColorFilter.tint(if (has) energy.main else Color(0xFF1E2A1E)))
                    }
                    if (i == unlocked) {   // the next one: a progress arc
                        drawArc(energy.main, -90f, 360f * codex.progressToNext, false, center - Offset(r, r), Size(r * 2, r * 2), style = Stroke(3f))
                    }
                }
            }
        }
        item { BasicText("‹ BACK", style = style(dim, 11), modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(vertical = 10.dp)) }
    }
}

/** Shown once when new aliens were unlocked since you last looked. */
@Composable
fun NewDnaSplash(indices: List<Int>, energy: Energy, art: AlienArt, onDone: () -> Unit) {
    val context = LocalContext.current
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    val first = indices.first()
    LaunchedEffect(Unit) {
        art.prefetch(first, radius = 0)
        dev.omnitrix.app.Haptics.ready(context)
        dev.omnitrix.app.Sounds.play(context, dev.omnitrix.app.Sounds.Cue.READY)
        appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 200f))
        kotlinx.coroutines.delay(2600)
        onDone()
    }
    val holo = remember(energy) { hologramFilter(energy.bright) }
    val glow = remember(energy) { ColorFilter.tint(energy.main) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xE6000000)).clickable(onClick = onDone)) {
        Canvas(Modifier.fillMaxSize()) {
            art.version.intValue
            val k = appear.value.coerceIn(0f, 1.2f)
            val c = Offset(size.width / 2f, size.height * 0.44f)
            val s = (size.minDimension * 0.42f * k).roundToInt().coerceAtLeast(1)
            art.glow(first)?.let { drawImage(it, srcSize = IntSize(it.width, it.height), dstOffset = IntOffset((c.x - s / 2f).roundToInt(), (c.y - s / 2f).roundToInt()), dstSize = IntSize(s, s), alpha = 0.6f, colorFilter = glow) }
            art.holo(first)?.let { drawImage(it, srcSize = IntSize(it.width, it.height), dstOffset = IntOffset((c.x - s / 2f).roundToInt(), (c.y - s / 2f).roundToInt()), dstSize = IntSize(s, s), colorFilter = holo) }
            drawCircle(energy.main, radius = size.minDimension * 0.3f * k, center = c, style = Stroke(2f), alpha = (1.2f - k).coerceIn(0f, 1f))
        }
        val style = { color: Color, sz: Int -> TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Black, fontSize = sz.sp, color = color, letterSpacing = 2.sp, textAlign = TextAlign.Center) }
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(top = 26.dp, bottom = 34.dp), verticalArrangement = Arrangement.SpaceBetween) {
            BasicText("NEW DNA ACQUIRED", style = style(energy.main, 11), modifier = Modifier.fillMaxWidth())
            BasicText(Aliens.all[first].name + if (indices.size > 1) "  +${indices.size - 1}" else "", style = style(Color.White, 18), modifier = Modifier.fillMaxWidth())
        }
    }
}
