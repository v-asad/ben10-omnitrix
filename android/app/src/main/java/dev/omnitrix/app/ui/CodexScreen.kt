package dev.omnitrix.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import dev.omnitrix.app.Chapter
import dev.omnitrix.app.DnaCodex
import dev.omnitrix.app.Energy
import dev.omnitrix.app.Haptics
import dev.omnitrix.app.Mission
import dev.omnitrix.app.Missions
import dev.omnitrix.app.Sounds
import kotlin.math.roundToInt

private val DIM = Color(0xFF6F9A66)
private val FAINT = Color(0xFF1C2A1A)
private val COSMIC = Color(0xFFB79CFF)
private const val PER_ROW = 6

private fun style(color: Color, size: Int, weight: FontWeight = FontWeight.Bold, spacing: Float = 1.5f, align: TextAlign = TextAlign.Center) =
    TextStyle(fontFamily = Orbitron, fontWeight = weight, fontSize = size.sp, color = color, letterSpacing = spacing.sp, textAlign = align)

/** DNA Missions: rank, the active mission (with its mystery hologram) and the chapter map. */
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
        if (allowed) codex.sampleSteps { refresh++ }
    }
    LaunchedEffect(Unit) {
        if (!allowed) request.launch(Manifest.permission.ACTIVITY_RECOGNITION) else codex.sampleSteps { refresh++ }
        codex.markSeen()
    }
    refresh.let { }
    val mission = codex.active
    val main = energy.main

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = rememberScalingLazyListState(initialCenterItemIndex = 1),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { BasicText("DNA MISSIONS", style = style(DIM, 10, spacing = 3f), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) }
        item { RankHeader(codex, main) }
        if (!allowed) item {
            BasicText("TAP TO ALLOW STEPS", style = style(main, 10),
                modifier = Modifier.fillMaxWidth().clickable { request.launch(Manifest.permission.ACTIVITY_RECOGNITION) }.padding(4.dp))
        }
        if (mission != null) item { MissionCard(codex, mission, art, main) }
        else item { BasicText("EVERY MISSION COMPLETE", style = style(main, 12, FontWeight.Black), modifier = Modifier.fillMaxWidth()) }
        if (energy == Energy.MASTER) item {
            BasicText("MASTER CONTROL: ALL DNA ACTIVE", style = style(main, 8), modifier = Modifier.fillMaxWidth())
        }
        Missions.chapters.forEach { chapter -> item(key = "ch${chapter.number}") { ChapterRow(chapter, codex, mission, art, main) } }
        item { BasicText("‹ BACK", style = style(DIM, 11), modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(vertical = 10.dp)) }
    }
}

@Composable
private fun RankHeader(codex: DnaCodex, main: Color) {
    val next = codex.nextRank
    Column(Modifier.fillMaxWidth(0.84f), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(codex.rank.title, style = style(Color(0xFFEAFFE0), 20, FontWeight.Black, 2f), modifier = Modifier.fillMaxWidth())
        val from = codex.rank.xp.coerceAtMost(codex.xp)
        val frac = if (next == null) 1f else (codex.xp - from).toFloat() / (next.xp - from).coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp)) {
            val y = size.height / 2f
            drawLine(FAINT, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
            drawLine(main, Offset(0f, y), Offset(size.width * frac.coerceIn(0f, 1f), y), strokeWidth = size.height, cap = StrokeCap.Round)
        }
        BasicText(
            (if (next == null) "%,d XP".format(codex.xp) else "%,d / %,d XP → ${next.title}".format(codex.xp, next.xp)) +
                "   ·   DNA ${codex.unlockedCount}/${Aliens.size}",
            style = style(DIM, 8, spacing = 1f), modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        )
    }
}

@Composable
private fun MissionCard(codex: DnaCodex, m: Mission, art: AlienArt, main: Color) {
    val final = m.chapter.title == "FINAL MISSION"
    val accent = if (final) COSMIC else main
    LaunchedEffect(m.reward) { art.prefetch(m.reward, radius = 0) }
    val loops = rememberInfiniteTransition(label = "mission")
    val scan = loops.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "scan")
    val holo = remember(accent) { hologramFilter(accent) }
    val dark = remember { ColorFilter.tint(Color(0xFF16201A)) }
    val reveal = codex.missionProgress

    Column(
        Modifier
            .fillMaxWidth(0.9f)
            .border(1.5.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)), RoundedCornerShape(26.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(if (final) "FINAL MISSION" else "MISSION ${m.number} / ${Missions.all.size}" + if (m.isFinale) " · FINALE" else "",
            style = style(accent, 9, spacing = 2f), modifier = Modifier.fillMaxWidth())
        // Mystery hologram: the reward alien's silhouette fills in from the bottom as you progress.
        Box(Modifier.size(96.dp).padding(top = 4.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                art.version.intValue
                val t = scan.value
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent)), radius = size.minDimension / 2f)
                art.holo(m.reward)?.let { img ->
                    val s = size.minDimension.roundToInt()
                    val src = IntSize(img.width, img.height)
                    drawImage(img, srcSize = src, dstSize = IntSize(s, s), colorFilter = dark)
                    val cut = size.height * (1f - reveal)
                    clipRect(top = cut) { drawImage(img, srcSize = src, dstSize = IntSize(s, s), colorFilter = holo) }
                    if (reveal in 0.01f..0.99f) drawLine(accent, Offset(0f, cut), Offset(size.width, cut), strokeWidth = 2f, alpha = 0.9f)
                }
                drawLine(accent, Offset(0f, size.height * t), Offset(size.width, size.height * t), strokeWidth = 1.5f, alpha = 0.35f)
                drawArc(accent, -90f, 360f * reveal, false, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f),
                    style = Stroke(3f, cap = StrokeCap.Round))
            }
            if (reveal < 1f) BasicText("?", style = style(accent.copy(alpha = 0.55f), 30, FontWeight.Black))
        }
        BasicText(m.title, style = style(Color(0xFFEAFFE0), 10, FontWeight.Black, 1f), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        m.goals.forEach { goal ->
            val p = codex.progress(goal)
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText((if (p >= goal.target) "✓ " else "") + goal.type.describe(goal.target),
                        style = style(if (p >= goal.target) accent else DIM, 8, spacing = 0.8f, align = TextAlign.Start), modifier = Modifier.weight(1f))
                }
                Canvas(Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp)) {
                    val y = size.height / 2f
                    drawLine(FAINT, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
                    drawLine(accent, Offset(0f, y), Offset(size.width * p / goal.target, y), strokeWidth = size.height, cap = StrokeCap.Round)
                }
                BasicText("%,d / %,d".format(p, goal.target), style = style(DIM, 7, spacing = 0.5f, align = TextAlign.End), modifier = Modifier.fillMaxWidth())
            }
        }
        BasicText("REWARD  ·  +${m.xp} XP  ·  NEW DNA", style = style(accent, 8, spacing = 1.2f),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, codex: DnaCodex, active: Mission?, art: AlienArt, main: Color) {
    val final = chapter.title == "FINAL MISSION"
    val accent = if (final) COSMIC else main
    val owned = chapter.aliens.count { Missions.unlockOrder.indexOf(it) < codex.unlockedCount }
    Column(Modifier.fillMaxWidth(0.86f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("CH ${chapter.number} · ${chapter.title}", style = style(if (owned > 0) accent else DIM, 8, spacing = 1.2f, align = TextAlign.Start),
                modifier = Modifier.weight(1f))
            BasicText("$owned/${chapter.aliens.size}", style = style(if (owned == chapter.aliens.size) accent else DIM, 8))
        }
        val rows = (chapter.aliens.size + PER_ROW - 1) / PER_ROW
        for (r in 0 until rows) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(PER_ROW.toFloat()).padding(top = 3.dp)) {
                art.version.intValue
                val cell = size.width / PER_ROW
                for (c in 0 until PER_ROW) {
                    val alien = chapter.aliens.getOrNull(r * PER_ROW + c) ?: break
                    val has = Missions.unlockOrder.indexOf(alien) < codex.unlockedCount
                    val target = active?.reward == alien
                    val center = Offset(cell * c + cell / 2f, size.height / 2f)
                    val rad = minOf(cell, size.height) * 0.44f
                    drawCircle(Color.Black, radius = rad, center = center)
                    drawCircle(if (has || target) accent else Color(0xFF223322), radius = rad, center = center, style = Stroke(if (target) 3f else 1.5f))
                    art.icon(alien)?.let { icon ->
                        val s = (rad * 1.5f).roundToInt()
                        drawImage(icon, srcSize = IntSize(icon.width, icon.height),
                            dstOffset = IntOffset((center.x - s / 2f).roundToInt(), (center.y - s / 2f).roundToInt()), dstSize = IntSize(s, s),
                            colorFilter = ColorFilter.tint(when { has -> accent; target -> accent.copy(alpha = 0.45f); else -> Color(0xFF1E2A1E) }))
                    }
                    if (target) drawArc(accent, -90f, 360f * codex.missionProgress, false, center - Offset(rad, rad), Size(rad * 2, rad * 2), style = Stroke(3f))
                }
            }
        }
    }
}

/** "MISSION COMPLETE": shown once per unlock, with the XP earned and your rank. */
@Composable
fun NewDnaSplash(indices: List<Int>, energy: Energy, art: AlienArt, onDone: () -> Unit) {
    val context = LocalContext.current
    val codex = remember { DnaCodex(context) }
    val appear = remember { Animatable(0f) }
    val first = indices.first()
    val earned = Missions.all.filter { it.reward in indices }.sumOf { it.xp }
    val final = Missions.all.firstOrNull { it.reward == first }?.chapter?.title == "FINAL MISSION"
    val accent = if (final) COSMIC else energy.main
    LaunchedEffect(Unit) {
        art.prefetch(first, radius = 0)
        Haptics.ready(context)
        Sounds.play(context, Sounds.Cue.READY)
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 200f))
        kotlinx.coroutines.delay(3000)
        onDone()
    }
    val holo = remember(accent) { hologramFilter(accent) }
    val glow = remember(accent) { ColorFilter.tint(accent) }
    Box(Modifier.fillMaxSize().background(Color(0xEE000000)).clickable(onClick = onDone)) {
        Canvas(Modifier.fillMaxSize()) {
            art.version.intValue
            val k = appear.value.coerceIn(0f, 1.2f)
            val c = Offset(size.width / 2f, size.height * 0.46f)
            val s = (size.minDimension * 0.42f * k).roundToInt().coerceAtLeast(1)
            val dst = IntOffset((c.x - s / 2f).roundToInt(), (c.y - s / 2f).roundToInt())
            art.glow(first)?.let { drawImage(it, srcSize = IntSize(it.width, it.height), dstOffset = dst, dstSize = IntSize(s, s), alpha = 0.6f, colorFilter = glow) }
            art.holo(first)?.let { drawImage(it, srcSize = IntSize(it.width, it.height), dstOffset = dst, dstSize = IntSize(s, s), colorFilter = holo) }
            drawCircle(accent, radius = size.minDimension * 0.3f * k, center = c, style = Stroke(2f), alpha = (1.2f - k).coerceIn(0f, 1f))
        }
        Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 26.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth()) {
                BasicText(if (final) "FINAL MISSION COMPLETE" else "MISSION COMPLETE", style = style(accent, 11, FontWeight.Black, 2f), modifier = Modifier.fillMaxWidth())
                BasicText("+$earned XP  ·  ${codex.rank.title}", style = style(DIM, 9), modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
            Column(Modifier.fillMaxWidth()) {
                BasicText("NEW DNA", style = style(DIM, 9, spacing = 3f), modifier = Modifier.fillMaxWidth())
                BasicText(Aliens.all[first].name + if (indices.size > 1) "  +${indices.size - 1}" else "",
                    style = style(Color.White, 18, FontWeight.Black, 2f), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
