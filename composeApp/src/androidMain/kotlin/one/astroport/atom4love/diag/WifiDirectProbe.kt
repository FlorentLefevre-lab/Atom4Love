package one.astroport.atom4love.diag

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import one.astroport.atom4love.nostr.LocalRelayScout

/**
 * POC de faisabilité — la question posée : ouvrir un groupe Wi-Fi Direct
 * coupe-t-il l'accès à la station à laquelle on est connecté ?
 *
 * L'enjeu n'est pas théorique. En Wi-Fi Direct, l'un des deux appareils devient
 * *group owner*, c'est-à-dire un point d'accès — et on a mesuré le 2026-08-11
 * que le Pixel 10 Pro ne sait plus scanner le Wi-Fi pendant qu'il tient un AP.
 * Si tenir un groupe P2P coupait aussi la liaison station, alors passer la
 * cabine en direct coûterait le salon d'hexagone : le choix offert à
 * l'utilisateur ne serait plus « rapide ou privé » mais « la cabine ou
 * l'hexagone ». Cette sonde tranche avant qu'on ne promette quoi que ce soit.
 *
 * Elle ne décide rien : elle ouvre un groupe, mesure, et le referme.
 */
class WifiDirectProbe(private val context: Context) {

    data class ProbeResult(
        val supported: Boolean,
        val groupCreated: Boolean,
        /** SSID du groupe P2P créé, tel que le système l'a nommé. */
        val groupSsid: String?,
        val stationSsidBefore: String?,
        val stationSsidDuring: String?,
        /** Le relais local répondait-il avant d'ouvrir le groupe ? */
        val relayBefore: Boolean,
        /** Et pendant que le groupe tourne ? C'est la question. */
        val relayDuring: Boolean,
        val relayAfter: Boolean,
        val notes: List<String>,
    ) {
        /** Verdict lisible, sans interprétation au-delà des faits mesurés. */
        val verdict: String
            get() = when {
                !supported -> "NON SUPPORTÉ — cet appareil n'annonce pas Wi-Fi Direct."
                !groupCreated -> "INDÉTERMINÉ — le groupe P2P n'a pas pu s'ouvrir."
                !relayBefore ->
                    "INDÉTERMINÉ — le relais local ne répondait déjà pas avant le groupe."
                relayDuring -> "COMPATIBLE — la station reste jointe pendant le groupe P2P."
                else -> "EXCLUSIF — la station est perdue tant que le groupe P2P tourne."
            }
    }

    private val wifi: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val scout = LocalRelayScout(context.applicationContext)

    suspend fun run(): ProbeResult {
        val notes = mutableListOf<String>()
        val manager = context.applicationContext
            .getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager == null) {
            return empty(notes + "Aucun service Wi-Fi P2P sur cet appareil.")
        }

        // LocalRelayScout.probe() rend null tant que son rappel réseau n'a pas
        // désigné le Wi-Fi courant : sans cette veille, la sonde conclurait
        // « relais injoignable » sans avoir ouvert une seule socket.
        val watcher = scout.watchWifi { }
        delay(WIFI_WATCH_MS)

        val ssidBefore = stationSsid()
        val relayBefore = scout.probe() != null
        if (!relayBefore) {
            notes += "Aucun relais local joignable avant l'essai : sans point de " +
                "comparaison, le résultat ne prouverait rien."
        }

        val channel = manager.initialize(context.applicationContext, Looper.getMainLooper(), null)
            ?: return empty(notes + "Canal Wi-Fi P2P indisponible.")

        val created = createGroup(manager, channel, notes)
        if (!created) {
            runCatching { removeGroup(manager, channel) }
            runCatching { watcher.close() }
            return ProbeResult(
                supported = true, groupCreated = false, groupSsid = null,
                stationSsidBefore = ssidBefore, stationSsidDuring = null,
                relayBefore = relayBefore, relayDuring = false, relayAfter = relayBefore,
                notes = notes,
            )
        }

        // le temps que la bascule d'interface se pose
        delay(SETTLE_MS)
        val groupSsid = groupSsid(manager, channel)
        val ssidDuring = stationSsid()
        val relayDuring = scout.probe() != null
        if (relayBefore && !relayDuring) {
            notes += "Le relais local ne répond plus tant que le groupe P2P est ouvert."
        }

        removeGroup(manager, channel)
        delay(SETTLE_MS)
        val relayAfter = scout.probe() != null
        runCatching { watcher.close() }
        if (relayBefore && !relayAfter) {
            notes += "Le relais n'était toujours pas revenu ${SETTLE_MS / 1000} s après " +
                "la fermeture du groupe : la reprise peut être plus lente que la sonde."
        }

        return ProbeResult(
            supported = true, groupCreated = true, groupSsid = groupSsid,
            stationSsidBefore = ssidBefore, stationSsidDuring = ssidDuring,
            relayBefore = relayBefore, relayDuring = relayDuring, relayAfter = relayAfter,
            notes = notes,
        )
    }

    private fun empty(notes: List<String>) = ProbeResult(
        supported = false, groupCreated = false, groupSsid = null,
        stationSsidBefore = null, stationSsidDuring = null,
        relayBefore = false, relayDuring = false, relayAfter = false, notes = notes,
    )

    @Suppress("DEPRECATION", "MissingPermission")
    private fun stationSsid(): String? = runCatching {
        wifi.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()

    @Suppress("MissingPermission")
    private suspend fun createGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        notes: MutableList<String>,
    ): Boolean = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            manager.createGroup(
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onFailure(reason: Int) {
                        notes += "createGroup a échoué (${failureName(reason)})."
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
        }
    } ?: run {
        notes += "createGroup n'a ni réussi ni échoué en ${ACTION_TIMEOUT_MS / 1000} s."
        false
    }

    @Suppress("MissingPermission")
    private suspend fun removeGroup(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        withTimeoutOrNull(ACTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                manager.removeGroup(
                    channel,
                    object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onFailure(reason: Int) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    },
                )
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun groupSsid(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
    ): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(ACTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                manager.requestGroupInfo(channel) { group ->
                    if (cont.isActive) cont.resume(group?.networkName)
                }
            }
        }
    }

    private fun failureName(reason: Int) = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P non supporté"
        WifiP2pManager.BUSY -> "occupé"
        WifiP2pManager.ERROR -> "erreur interne"
        else -> "code $reason"
    }

    private companion object {
        /** Le temps que l'interface P2P monte et que le routage se stabilise. */
        /** Le temps que le rappel réseau désigne le Wi-Fi courant. */
        const val WIFI_WATCH_MS = 1_500L

        const val SETTLE_MS = 4_000L
        const val ACTION_TIMEOUT_MS = 15_000L
    }
}
