package one.astroport.atom4love.diag

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.Atom4LoveTheme

/**
 * POC — causerie de texte en BLE pur (GATT), sans AP ni relais. En clair :
 * sonde de diagnostic, hors navigation, à faire disparaître derrière Noise.
 *
 *   adb shell am start -n one.astroport.atom4love.debug/one.astroport.atom4love.diag.BleChatProbeActivity
 */
class BleChatProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = BleChatProbe(applicationContext)

        setContent {
            Atom4LoveTheme {
                var granted by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { results -> granted = results.values.all { it } }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_ADVERTISE,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            ),
                        )
                    } else {
                        granted = true
                    }
                }
                LaunchedEffect(granted) { if (granted) probe.start() }
                DisposableEffect(Unit) { onDispose { probe.stop() } }

                BleChatScreen(probe)
            }
        }
    }
}

@Composable
private fun BleChatScreen(probe: BleChatProbe) {
    val state by probe.state.collectAsState()
    var draft by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(A4L.Deep)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("CAUSERIE BLE — POC (en clair)", style = A4LText.Data, color = A4L.Mint)
        Text(
            buildString {
                append(if (state.advertising) "annonce ✓" else "annonce —")
                append("  ·  ")
                append(if (state.scanning) "scan ✓" else "scan —")
                append("  ·  liens : ${state.links}")
                state.lastError?.let { append("  ·  $it") }
            },
            style = A4LText.Caption,
            color = if (state.links > 0) A4L.Green else A4L.TextMuted,
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), reverseScrolling = true),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.messages.isEmpty()) {
                Text(
                    "En attente d'un pair… Lancez cette sonde sur les deux appareils, " +
                        "Bluetooth activé. La connexion est automatique.",
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                )
            }
            state.messages.forEach { message ->
                Column {
                    Text(
                        if (message.mine) "moi" else "pair ${message.from}",
                        style = A4LText.Data,
                        color = if (message.mine) A4L.Mint else A4L.TextDim,
                    )
                    Text(message.text, style = A4LText.Body, color = A4L.TextHigh)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                textStyle = A4LText.Body.copy(color = A4L.TextHigh),
                cursorBrush = SolidColor(A4L.Mint),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text("message en clair…", style = A4LText.Body, color = A4L.TextGhost)
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { if (probe.send(draft)) draft = "" },
                enabled = state.links > 0 && draft.isNotBlank(),
            ) { Text("Envoyer") }
        }
    }
}
