package one.astroport.atom4love.diag

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Écran du banc RFCOMM — tout se pilote à l'adb, l'écran ne fait que rendre le
 * verdict lisible sans brancher un câble de plus.
 *
 * ```
 * # récepteur (celui qui mesure)
 * adb -s <tablette> shell am start -n <appId>/one.astroport.atom4love.diag.RfcommProbeActivity \
 *     --es role serve
 *
 * # émetteur
 * adb -s <pixel> shell am start -n <appId>/one.astroport.atom4love.diag.RfcommProbeActivity \
 *     --es role send --es mac 04:34:F6:42:BF:B2 --ei mb 4
 * ```
 *
 * Le verdict part aussi dans `logcat -s RfcommProbe`, qui reste le plus sûr des
 * deux : sur la tablette, `uiautomator dump` rend le launcher.
 */
class RfcommProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val role = intent.getStringExtra("role") ?: "serve"
        val mac = intent.getStringExtra("mac")
        val megabytes = intent.getIntExtra("mb", 4)

        setContent {
            var verdict by remember { mutableStateOf("…") }
            androidx.compose.runtime.LaunchedEffect(role, mac, megabytes) {
                verdict = when {
                    adapter == null || !adapter.isEnabled -> "Bluetooth coupé"
                    role == "send" && mac != null -> RfcommProbe.send(adapter, mac, megabytes)
                    role == "send" -> "il manque --es mac <adresse>"
                    else -> RfcommProbe.serve(adapter)
                }
            }
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text("Banc RFCOMM · rôle $role", fontSize = 18.sp)
                Text(verdict, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
            }
        }
        lifecycleScope.launch { }
    }
}
