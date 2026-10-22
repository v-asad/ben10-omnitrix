package dev.omnitrix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.omnitrix.app.ui.AlienFormScreen
import dev.omnitrix.app.ui.DossierScreen
import dev.omnitrix.app.ui.SelectorScreen
import dev.omnitrix.app.ui.rememberAlienArt

/** Tapping the Omnitrix face lands here: the dial pops up. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug hook: adb shell am start -n dev.omnitrix.app/.MainActivity --ei transform <alien> [--ei seconds <n>]
        intent.getIntExtra(EXTRA_TRANSFORM, -1).takeIf { it in Aliens.all.indices }?.let { alien ->
            Transformer.transform(this, alien, intent.getIntExtra(EXTRA_SECONDS, 60) * 1000L)
        }
        setContent { OmnitrixApp(onDone = ::finish) }
    }

    private companion object {
        const val EXTRA_TRANSFORM = "transform"
        const val EXTRA_SECONDS = "seconds"
    }
}

@Composable
private fun OmnitrixApp(onDone: () -> Unit) {
    val context = LocalContext.current
    val state = remember { OmnitrixState(context) }
    val energy = remember { Energy.read(context) }
    var transformation by remember { mutableStateOf(state.current()) }
    var cursor by remember { mutableIntStateOf(state.lastAlien) }
    var dossier by remember { mutableStateOf(false) }
    val art = rememberAlienArt()

    LaunchedEffect(Unit) { if (transformation == null) Haptics.dialUp(context) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val current = transformation
        when {
            current != null -> AlienFormScreen(
                transformation = current,
                art = art,
                onRevert = {
                    Transformer.revert(context)
                    Haptics.revert(context)
                    onDone()
                },
                onExpired = { transformation = null },
            )
            dossier -> DossierScreen(cursor, energy, art, onClose = { dossier = false })
            else -> SelectorScreen(
                energy = energy,
                art = art,
                initialAlien = cursor,
                onSelectedChange = { cursor = it },
                onTransform = { alien ->
                    Transformer.transform(context, alien, energy.alienTimeMillis)
                    onDone()
                },
                onOpenDossier = { cursor = it; dossier = true },
            )
        }
    }
}
