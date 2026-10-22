package dev.omnitrix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** Bridge spike: a plain list to transform, so we can prove the face reacts. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug hook: adb shell am start -n dev.omnitrix.app/.MainActivity --ei transform <alien> [--ei seconds <n>]
        intent.getIntExtra(EXTRA_TRANSFORM, -1).takeIf { it in Aliens.names.indices }?.let { alien ->
            Transformer.transform(this, alien, intent.getIntExtra(EXTRA_SECONDS, 60) * 1000L)
        }
        setContent { MaterialTheme { SpikeScreen() } }
    }
}

private const val SPIKE_ALIEN_TIME_MS = 60_000L
private const val EXTRA_TRANSFORM = "transform"
private const val EXTRA_SECONDS = "seconds"

@Composable
private fun SpikeScreen() {
    val context = LocalContext.current
    val state = remember { OmnitrixState(context) }
    var current by remember { mutableStateOf(state.current()) }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = current?.let { Aliens.names[it.alien] } ?: "HUMAN",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (current != null) {
            item {
                Button(onClick = { Transformer.revert(context); current = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("REVERT")
                }
            }
        }
        items(Aliens.names.size) { i ->
            Button(
                onClick = {
                    Transformer.transform(context, i, SPIKE_ALIEN_TIME_MS)
                    current = state.current()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(Aliens.names[i]) }
        }
    }
}
