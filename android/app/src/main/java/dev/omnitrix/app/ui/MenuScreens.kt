package dev.omnitrix.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import dev.omnitrix.app.BuildConfig
import dev.omnitrix.app.Diagnostics
import dev.omnitrix.app.Energy
import dev.omnitrix.app.Settings

private val DIM = Color(0xFF6F9A66)

private fun style(color: Color, size: Int, weight: FontWeight = FontWeight.Bold, spacing: Float = 1.5f) =
    TextStyle(fontFamily = Orbitron, fontWeight = weight, fontSize = size.sp, color = color, letterSpacing = spacing.sp)

/** Black, round-friendly list with rotary scrolling (the bezel / crown scrolls it). */
@Composable
private fun OmnitrixList(title: String, energy: Energy, onBack: () -> Unit, content: ScalingLazyListScope.() -> Unit) {
    BackHandler(onBack = onBack)
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = rememberScalingLazyListState(initialCenterItemIndex = 1),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            BasicText(title, style = style(energy.main, 14, FontWeight.Black, 3f).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp))
        }
        content()
        item { Row(onClick = onBack, label = "‹ BACK", energy = energy, subtle = true) }
    }
}

@Composable
private fun Row(onClick: (() -> Unit)?, label: String, energy: Energy, value: String? = null, note: String? = null, subtle: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth(0.92f)
            .border(1.5.dp, if (subtle) Color(0xFF223322) else energy.main.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Column {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(label, style = style(if (subtle) DIM else Color(0xFFDFF5D6), 11), modifier = Modifier.weight(1f))
                if (value != null) BasicText(value, style = style(energy.main, 12, FontWeight.Black))
            }
            if (note != null) BasicText(note, style = style(DIM, 8, spacing = 1f), modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
fun MenuScreen(energy: Energy, onSettings: () -> Unit, onBattery: () -> Unit, onBack: () -> Unit) {
    OmnitrixList("OMNITRIX", energy, onBack) {
        item { Row(onSettings, "SETTINGS", energy, value = "›") }
        item { Row(onBattery, "BATTERY", energy, value = "›", note = "DIAGNOSTICS & DRAIN") }
        item {
            BasicText("OMNITRIX OS v${BuildConfig.VERSION_NAME}", style = style(DIM, 8).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Composable
fun SettingsScreen(energy: Energy, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var version by remember { mutableIntStateOf(0) }       // bump to re-read after a change
    fun change(block: () -> Unit) { block(); version++ }
    version.let { }                                          // read so changes recompose

    fun <T> next(list: List<T>, current: T) = list[(list.indexOf(current) + 1) % list.size]

    OmnitrixList("SETTINGS", energy, onBack) {
        item(key = "time$version") {
            Row({ change { settings.alienMinutes = next(Settings.ALIEN_MINUTES, settings.alienMinutes) } }, "ALIEN TIME", energy,
                value = "${settings.alienMinutes} MIN", note = "×3 IN MASTER CONTROL")
        }
        item(key = "cool$version") {
            Row({ change { settings.cooldownMinutes = next(Settings.COOLDOWN_MINUTES, settings.cooldownMinutes) } }, "RECHARGE", energy,
                value = if (settings.cooldownMinutes == 0) "OFF" else "${settings.cooldownMinutes} MIN", note = "AFTER A TIMEOUT")
        }
        item(key = "anim$version") {
            Row({ change { settings.animations = next(Settings.Animations.entries, settings.animations) } }, "ANIMATIONS", energy,
                value = settings.animations.label)
        }
        item(key = "hap$version") {
            Row({ change { settings.haptics = next(Settings.Haptics.entries, settings.haptics) } }, "HAPTICS", energy,
                value = settings.haptics.label)
        }
        item(key = "snd$version") {
            Row({ change { settings.sound = !settings.sound } }, "SOUND", energy,
                value = if (settings.sound) "ON" else "OFF", note = "ONLY IN SOUND MODE · USES WATCH VOLUME")
        }
        item(key = "diag$version") {
            Row({ change { settings.diagnostics = !settings.diagnostics; Diagnostics.schedule(context) } }, "DIAGNOSTICS", energy,
                value = if (settings.diagnostics) "ON" else "OFF", note = "BATTERY LOGGING")
        }
    }
}

@Composable
fun BatteryScreen(energy: Energy, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Diagnostics.summary(context) }
    OmnitrixList("BATTERY", energy, onBack) {
        item { Row(null, "NOW", energy, value = "${s.batteryPct}%" + if (s.charging) " ⚡" else "") }
        item {
            Row(null, "DRAIN", energy, value = s.drainPerHour?.let { "%.1f%%/H".format(it) } ?: "—",
                note = if (s.drainPerHour != null && s.sinceUnplugHours != null) "OVER %.1f H OFF CHARGER".format(s.sinceUnplugHours) else "NEEDS ~30 MIN OF SAMPLES")
        }
        item { Row(null, "APP TIME TODAY", energy, value = "${s.appMinutesToday} MIN") }
        item { Row(null, "TRANSFORMS TODAY", energy, value = "${s.transformationsToday}") }
        item { Row(null, "SAMPLES", energy, value = "${s.samples}", note = "FULL REPORT: tools/battery_report.py") }
    }
}
