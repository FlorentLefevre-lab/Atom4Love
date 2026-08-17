package one.astroport.atom4love.nostr

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.astroport.atom4love.BuildConfig

/**
 * Éclaireur du relais local : quand le téléphone est sur un Wi-Fi (typiquement
 * le hot-spot d'une station Astroport), il sonde la passerelle sur le port
 * [BuildConfig.NOSTR_LOCAL_RELAY_PORT]. Si quelque chose y écoute, c'est le
 * relais NOSTR de la station — zéro configuration, et aucun SSID en dur :
 * n'importe quel hot-spot qui héberge un relais fait l'affaire.
 *
 * Le réseau Wi-Fi est suivi explicitement, et la sonde liée dessus : un
 * hot-spot sans accès Internet n'est jamais le réseau par défaut d'Android,
 * qui garde alors la 4G — sans liaison explicite, la sonde partirait dessus.
 */
class LocalRelayScout(context: Context) {

    companion object {
        private const val TAG = "Nostr"
        private const val PROBE_TIMEOUT_MS = 1_500
    }

    /** Un relais trouvé sur la passerelle, et le réseau par lequel le joindre. */
    data class LocalRelay(val url: String, val network: Network)

    private val connectivity =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var wifi: Network? = null

    /**
     * Suit les allers-retours du Wi-Fi et rappelle [onChange] à chacun (depuis
     * un thread système — relancer une coroutine). Si un Wi-Fi est déjà
     * connecté à l'inscription, son onAvailable est rejoué immédiatement.
     * À fermer avec la valeur retournée.
     */
    fun watchWifi(onChange: () -> Unit): AutoCloseable {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifi = network
                onChange()
            }

            override fun onLost(network: Network) {
                if (wifi == network) wifi = null
                onChange()
            }
        }
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(),
            callback,
        )
        return AutoCloseable { connectivity.unregisterNetworkCallback(callback) }
    }

    /** Sonde la passerelle du Wi-Fi courant. null = pas de Wi-Fi, ou rien sur le port. */
    suspend fun probe(): LocalRelay? = withContext(Dispatchers.IO) {
        val network = wifi ?: return@withContext null
        val gateway = gatewayOf(network) ?: return@withContext null
        val port = BuildConfig.NOSTR_LOCAL_RELAY_PORT
        val listening = runCatching {
            Socket().use { socket ->
                network.bindSocket(socket)
                socket.connect(InetSocketAddress(gateway, port), PROBE_TIMEOUT_MS)
            }
        }.isSuccess
        if (!listening) return@withContext null
        LocalRelay("ws://${gateway.hostAddress}:$port", network)
            .also { Log.d(TAG, "relais local trouvé : ${it.url}") }
    }

    private fun gatewayOf(network: Network): Inet4Address? =
        connectivity.getLinkProperties(network)?.routes
            ?.firstOrNull { it.destination.prefixLength == 0 && it.gateway is Inet4Address }
            ?.gateway as? Inet4Address
}
