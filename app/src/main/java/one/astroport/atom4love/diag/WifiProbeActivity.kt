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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.Atom4LoveTheme

/**
 * POC — écran de diagnostic « émettre + scanner en même temps », sur les deux
 * vecteurs comparés : hotspot Wi-Fi local et annonce BLE.
 *
 * Activité volontairement autonome, hors de la navigation de l'app :
 *
 *   adb shell am start -n one.astroport.atom4love.debug/one.astroport.atom4love.diag.WifiProbeActivity
 *
 * À supprimer avec le package `diag` une fois la question tranchée.
 */
class WifiProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wifiProbe = WifiConcurrencyProbe(applicationContext)
        val bleProbe = BleConcurrencyProbe(applicationContext)

        setContent {
            Atom4LoveTheme {
                var wifiState by remember { mutableStateOf<ProbeUiState<ProbeResult>>(ProbeUiState.Idle()) }
                var bleState by remember { mutableStateOf<ProbeUiState<BleProbeResult>>(ProbeUiState.Idle()) }
                val scope = rememberCoroutineScope()

                // Les deux sondes partagent la même demande de permissions ;
                // l'action à lancer après la réponse est mémorisée ici.
                var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { _ ->
                    pending?.invoke() // les sondes notent elles-mêmes les refus.
                    pending = null
                }

                fun withPermissions(action: () -> Unit) {
                    pending = action
                    permissionLauncher.launch(requiredPermissions())
                }

                ProbeScreen(
                    wifiState = wifiState,
                    bleState = bleState,
                    onRunWifi = {
                        withPermissions {
                            wifiState = ProbeUiState.Running()
                            scope.launch {
                                wifiState = runCatching { wifiProbe.run(apCode = "phix2") }
                                    .fold({ ProbeUiState.Done(it) }, { ProbeUiState.Error(it.toString()) })
                            }
                        }
                    },
                    onRunBle = {
                        withPermissions {
                            bleState = ProbeUiState.Running()
                            scope.launch {
                                bleState = runCatching { bleProbe.run(code = "phix2") }
                                    .fold({ ProbeUiState.Done(it) }, { ProbeUiState.Error(it.toString()) })
                            }
                        }
                    },
                )
            }
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, // scan Wi-Fi
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            // Avant API 31, les permissions Bluetooth sont d'installation ;
            // seule la localisation est à demander (elle couvre aussi le scan BLE).
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}

private sealed interface ProbeUiState<out R> {
    class Idle<R> : ProbeUiState<R>
    class Running<R> : ProbeUiState<R>
    data class Done<R>(val result: R) : ProbeUiState<R>
    data class Error<R>(val message: String) : ProbeUiState<R>
}

@Composable
private fun ProbeScreen(
    wifiState: ProbeUiState<ProbeResult>,
    bleState: ProbeUiState<BleProbeResult>,
    onRunWifi: () -> Unit,
    onRunBle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(A4L.Deep)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Sondes « phix2 » — émettre + scanner",
            style = MaterialTheme.typography.headlineSmall,
            color = A4L.Cyan,
        )
        Text(
            "Deux vecteurs comparés : hotspot Wi-Fi local (SSID imposé par le système) " +
                "et annonce BLE (code « phix2 » embarqué dans le payload). Chaque sonde " +
                "émet puis scanne pendant l'émission.",
            style = MaterialTheme.typography.bodyMedium,
            color = A4L.TextBody,
        )

        ProbeSection(
            title = "Wi-Fi — AP + scan",
            buttonLabel = "Lancer la sonde Wi-Fi",
            state = wifiState,
            onRun = onRunWifi,
        ) { WifiResultBlock(it) }

        HorizontalDivider(color = A4L.StrokeSoft)

        ProbeSection(
            title = "BLE — annonce + scan",
            buttonLabel = "Lancer la sonde BLE",
            state = bleState,
            onRun = onRunBle,
        ) { BleResultBlock(it) }
    }
}

@Composable
private fun <R> ProbeSection(
    title: String,
    buttonLabel: String,
    state: ProbeUiState<R>,
    onRun: () -> Unit,
    resultBlock: @Composable (R) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = A4L.TextStrong)
        Button(
            onClick = onRun,
            enabled = state !is ProbeUiState.Running,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = A4L.Gold, contentColor = A4L.Void),
        ) {
            Text(if (state is ProbeUiState.Running) "Sonde en cours…" else buttonLabel)
        }
        when (state) {
            is ProbeUiState.Idle -> Unit
            is ProbeUiState.Running -> CircularProgressIndicator(color = A4L.Cyan)
            is ProbeUiState.Error -> Text(state.message, color = A4L.Red)
            is ProbeUiState.Done -> resultBlock(state.result)
        }
    }
}

@Composable
private fun WifiResultBlock(r: ProbeResult) {
    ResultColumn(headline = r.headline, ok = r.concurrencyDemonstrated, notes = r.notes) {
        Fact("Concurrence STA+AP déclarée (chipset)", when (r.staApConcurrencySupported) {
            true -> "oui"
            false -> "non"
            null -> "non interrogeable (API < 30)"
        })
        Fact("Point d'accès démarré", if (r.hotspotStarted) "oui" else "non — ${r.hotspotFailLabel}")
        r.hotspotSsid?.let { Fact("SSID effectif", it) }
        Fact("startScan() accepté pendant l'AP", if (r.scanTriggerAccepted) "oui" else "non")
        Fact("Résultats frais reçus pendant l'AP", if (r.scanReceivedFreshBroadcast) "oui" else "non")
        Fact("Réseaux visibles, AP allumé", r.scanResultsWhileApUp.toString())
    }
}

@Composable
private fun BleResultBlock(r: BleProbeResult) {
    ResultColumn(headline = r.headline, ok = r.concurrencyDemonstrated, notes = r.notes) {
        Fact("Annonce multiple déclarée (chipset)", when (r.multipleAdvertisementSupported) {
            true -> "oui"
            false -> "non"
            null -> "adaptateur indisponible"
        })
        Fact(
            "Annonce démarrée",
            if (r.advertisingStarted) "oui" else "non — ${r.advertiseFailLabel}",
        )
        Fact("« phix2 » réellement diffusé dans le payload", if (r.payloadCarriesCode) "oui" else "non")
        Fact("Scan actif pendant l'annonce", if (r.scanStarted) "oui" else "non")
        Fact("Appareils BLE vus pendant l'annonce", r.devicesSeenWhileAdvertising.toString())
        Fact("Pairs diffusant « phix2 »", r.peersWithCode.toString())
    }
}

@Composable
private fun ResultColumn(
    headline: String,
    ok: Boolean,
    notes: List<String>,
    facts: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            headline,
            style = MaterialTheme.typography.titleMedium,
            color = if (ok) A4L.Green else A4L.Orange,
        )
        facts()
        if (notes.isNotEmpty()) {
            Text("Notes", style = MaterialTheme.typography.titleSmall, color = A4L.TextStrong)
            notes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall, color = A4L.TextMuted)
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, valueColor: Color = A4L.TextHigh) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = A4L.TextDim)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
