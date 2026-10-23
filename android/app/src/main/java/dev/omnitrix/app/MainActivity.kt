package dev.omnitrix.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.omnitrix.app.ui.AlienFormScreen
import dev.omnitrix.app.ui.BatteryScreen
import dev.omnitrix.app.ui.BioScanScreen
import dev.omnitrix.app.ui.CodexScreen
import dev.omnitrix.app.ui.NewDnaSplash
import dev.omnitrix.app.ui.DossierScreen
import dev.omnitrix.app.ui.FxCache
import dev.omnitrix.app.ui.MenuScreen
import dev.omnitrix.app.ui.SelectorScreen
import dev.omnitrix.app.ui.SettingsScreen
import dev.omnitrix.app.ui.TRANSFORM_MS
import dev.omnitrix.app.ui.TRANSFORM_QUICK_MS
import dev.omnitrix.app.ui.TransformationScreen
import dev.omnitrix.app.ui.rememberAlienArt
import kotlinx.coroutines.delay

/** Tapping the Omnitrix face lands here: the dial pops up. */
class MainActivity : ComponentActivity() {
    private var openedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug hook: adb shell am start -n dev.omnitrix.app/.MainActivity --ei transform <alien> [--ei seconds <n>]
        intent.getIntExtra(EXTRA_TRANSFORM, -1).takeIf { it in Aliens.all.indices }?.let { alien ->
            Transformer.transform(this, alien, intent.getIntExtra(EXTRA_SECONDS, 60) * 1000L)
        }
        // Debug hook: --es preview <slug> plays that alien's transformation without transforming.
        val preview = intent.getStringExtra(EXTRA_PREVIEW)?.let { slug -> Aliens.all.indexOfFirst { it.slug == slug } }?.takeIf { it >= 0 }
        // Cheap insurance: make sure the face holds a live tap target for next time.
        Transformer.refreshFace(this)
        Sounds.preload(this)
        Diagnostics.schedule(this)
        setContent { OmnitrixApp(preview = preview, onDone = ::finish) }
    }

    override fun onStart() {
        super.onStart()
        openedAt = SystemClock.elapsedRealtime()
        Diagnostics.log(this, "app_open")
    }

    override fun onStop() {
        super.onStop()
        Diagnostics.log(this, "app_close", "ms=${SystemClock.elapsedRealtime() - openedAt}")
        // Like the real dial: leaving the Omnitrix closes it, so the next tap always starts at the
        // selector and nothing lingers in the background.
        if (!isChangingConfigurations) finish()
    }

    private companion object {
        const val EXTRA_TRANSFORM = "transform"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_PREVIEW = "preview"
    }
}

private enum class Screen { SELECTOR, DOSSIER, MENU, SETTINGS, BATTERY, BIOSCAN, CODEX }

@Composable
private fun OmnitrixApp(preview: Int?, onDone: () -> Unit) {
    val context = LocalContext.current
    val state = remember { OmnitrixState(context) }
    val settings = remember { Settings(context) }
    val batteryEnergy = remember { Energy.read(context) }
    var transformation by remember { mutableStateOf(state.current()) }
    var cooldownUntil by remember { mutableLongStateOf(state.cooldownUntil) }
    var cursor by remember { mutableIntStateOf(state.lastAlien) }
    var screen by remember { mutableStateOf(Screen.SELECTOR) }
    var transformingInto by remember { mutableStateOf(preview) }
    val art = rememberAlienArt()
    val fx = remember { FxCache() }
    val codex = remember { DnaCodex(context) }
    var newDna by remember { mutableStateOf(codex.newlyUnlocked()) }

    // While recharging, the Omnitrix shows red and won't transform; unlock when it finishes.
    val recharging = cooldownUntil > System.currentTimeMillis()
    val energy = if (recharging) Energy.RECHARGE else batteryEnergy
    LaunchedEffect(cooldownUntil) {
        val wait = cooldownUntil - System.currentTimeMillis()
        if (wait > 0) { delay(wait); cooldownUntil = 0 }
    }
    LaunchedEffect(Unit) {
        if (transformation == null && preview == null) {
            Haptics.dialUp(context)
            Sounds.play(context, Sounds.Cue.DIAL_UP)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val current = transformation
        val into = transformingInto
        when {
            into != null -> TransformationScreen(into, energy, art, fx,
                if (settings.animations == Settings.Animations.QUICK) TRANSFORM_QUICK_MS else TRANSFORM_MS, onDone = onDone)
            current != null -> AlienFormScreen(
                transformation = current,
                art = art,
                onRevert = {
                    Transformer.revert(context)
                    Haptics.revert(context)
                    Sounds.play(context, Sounds.Cue.REVERT)
                    onDone()
                },
                onExpired = { transformation = null; cooldownUntil = state.cooldownUntil },
            )
            screen == Screen.DOSSIER -> {
                BackHandler { screen = Screen.SELECTOR }
                DossierScreen(cursor, energy, art, onClose = { screen = Screen.SELECTOR })
            }
            screen == Screen.MENU -> MenuScreen(energy, onBioScan = { screen = Screen.BIOSCAN }, onCodex = { screen = Screen.CODEX },
                onSettings = { screen = Screen.SETTINGS }, onBattery = { screen = Screen.BATTERY }, onBack = { screen = Screen.SELECTOR })
            screen == Screen.BIOSCAN -> BioScanScreen(energy, onBack = { screen = Screen.MENU })
            screen == Screen.CODEX -> CodexScreen(energy, art, onBack = { newDna = emptyList(); screen = Screen.MENU })
            screen == Screen.SETTINGS -> SettingsScreen(energy, onBack = { screen = Screen.MENU })
            screen == Screen.BATTERY -> BatteryScreen(energy, onBack = { screen = Screen.MENU })
            else -> SelectorScreen(
                energy = energy,
                art = art,
                fx = fx,
                initialAlien = cursor,
                rechargeText = if (recharging) "RECHARGING" else null,
                isUnlocked = { codex.isUnlocked(it, batteryEnergy) },
                onSelectedChange = { cursor = it },
                onTransform = { alien ->
                    // Transform now so the face is already white when the sequence ends.
                    Transformer.transform(context, alien, settings.alienTimeMillis(batteryEnergy))
                    if (settings.animations == Settings.Animations.OFF) onDone() else transformingInto = alien
                },
                onOpenDossier = { cursor = it; screen = Screen.DOSSIER },
                onOpenMenu = { screen = Screen.MENU },
            )
        }
        // "NEW DNA ACQUIRED": shown once per unlock, over the selector
        if (newDna.isNotEmpty() && screen == Screen.SELECTOR && transformation == null && transformingInto == null) {
            NewDnaSplash(newDna, energy, art, onDone = { codex.markSeen(); newDna = emptyList() })
        }
    }
}
