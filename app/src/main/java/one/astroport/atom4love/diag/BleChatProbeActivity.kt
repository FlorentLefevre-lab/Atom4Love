package one.astroport.atom4love.diag

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.chat.ChatSounds
import one.astroport.atom4love.chat.ui.ChatPanel
import one.astroport.atom4love.data.IncarnationStore
import one.astroport.atom4love.nostr.LoveKeyForge
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.Atom4LoveTheme

/**
 * POC — causerie en BLE pur (GATT), sans AP ni relais : texte, images et
 * fichiers fragmentés (chat/wire), chiffrés par Noise XX. Sonde de
 * diagnostic, hors navigation.
 *
 *   adb shell am start -n one.astroport.atom4love.debug/one.astroport.atom4love.diag.BleChatProbeActivity
 */
class BleChatProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Écran maintenu allumé : la mise en veille (ZUI surtout) étrangle le
        // traitement BLE et fait mourir les réceptions en volume — vu sur banc.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                LaunchedEffect(granted) {
                    if (!granted) return@LaunchedEffect
                    // l'identité doit être posée avant l'ouverture des liens :
                    // un handshake déjà engagé garderait la clé de fortune
                    val birth = IncarnationStore(applicationContext).load()?.birth
                    if (birth != null && birth.complete) {
                        probe.bindIdentity(LoveKeyForge.forge(birth))
                    }
                    probe.start()
                }
                DisposableEffect(Unit) { onDispose { probe.stop() } }

                BleChatScreen(probe)
            }
        }
    }
}

@Composable
private fun BleChatScreen(probe: BleChatProbe) {
    val context = LocalContext.current
    val status by probe.status.collectAsState()
    val messages by probe.messages.collectAsState()
    val sounds = remember { ChatSounds() }

    LaunchedEffect(Unit) {
        probe.chimes.collect { chime ->
            when (chime) {
                BleChatProbe.Chime.SENT -> sounds.send()
                BleChatProbe.Chime.RECEIVED -> sounds.receive()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(A4L.Deep)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("CAUSERIE BLE — POC (Noise XX)", style = A4LText.Data, color = A4L.Mint)
        Text(
            buildString {
                append(if (status.advertising) "annonce ✓" else "annonce —")
                append("  ·  ")
                append(if (status.scanning) "scan ✓" else "scan —")
                append("  ·  liens : ${status.links}")
                status.lastError?.let { append("  ·  $it") }
            },
            style = A4LText.Caption,
            color = if (status.links > 0) A4L.Green else A4L.TextMuted,
        )

        ChatPanel(
            messages = messages,
            canSend = status.links > 0,
            placeholder = "message chiffré…",
            emptyHint = "En attente d'un pair… Lancez cette sonde sur les deux appareils, " +
                "Bluetooth activé. La connexion est automatique. Images et fichiers " +
                "jusqu'à ${Attachments.humanSize(BleChatProbe.MAX_TRANSFER_BYTES)} " +
                "(compter ~10 Ko/s).",
            onSendText = { text -> probe.sendText(text) },
            onSendImage = { uri -> probe.sendImage(uri) },
            onSendFile = { uri -> probe.sendFile(uri) },
            onOpen = { message ->
                message.file?.let { file ->
                    runCatching {
                        context.startActivity(Attachments.viewIntent(context, file, message.mime))
                    }
                }
            },
            onDownload = { message ->
                message.file?.let { file ->
                    val ok = Attachments.saveToDownloads(context, file, message.name, message.mime)
                    Toast.makeText(
                        context,
                        if (ok) "Enregistré dans Téléchargements" else "Échec de l'enregistrement",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}
