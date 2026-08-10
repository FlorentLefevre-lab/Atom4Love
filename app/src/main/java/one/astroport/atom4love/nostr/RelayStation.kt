package one.astroport.atom4love.nostr

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import one.astroport.atom4love.BuildConfig

/**
 * L'antenne de la station : gère la connexion aux relais NOSTR une fois le
 * noyau forgé, et l'éteint à la dissolution.
 *
 * Deux modes d'écoute. Si un [LocalRelayScout] est fourni et qu'un relais
 * répond sur la passerelle du Wi-Fi courant (le hot-spot d'une station
 * Astroport), l'antenne s'accroche à lui — sockets liées au réseau Wi-Fi,
 * puisqu'un hot-spot sans Internet n'est jamais le réseau par défaut
 * d'Android. Sinon, relais par défaut de BuildConfig. À chaque aller-retour
 * du Wi-Fi, l'antenne se re-règle toute seule ([retune]).
 *
 * Aucun événement n'est publié pour l'instant : le premier échange du
 * protocole ATOM4LOVE reste à définir (avec Fred). En attendant, on souscrit
 * à ses propres événements — le tuyau est branché et vérifié de bout en bout
 * (REQ → EOSE), sans rien écrire chez personne.
 *
 * Les mutations se font sur le thread du [scope] (Main dans l'app) ; seule la
 * sonde de la passerelle part sur IO.
 */
class RelayStation(
    private val scope: CoroutineScope,
    private val defaultUrls: List<String> = listOf(BuildConfig.NOSTR_DEFAULT_RELAY),
    private val scout: LocalRelayScout? = null,
) {

    companion object {
        private const val TAG = "Nostr"
        private const val SELF_SUBSCRIPTION = "a4l-self"
    }

    /** Ce que l'interface affiche : « relay · connectés / total ». */
    data class Status(val connected: Int, val total: Int, val local: Boolean = false) {
        val online: Boolean get() = connected > 0
        val label: String get() = (if (local) "relais local" else "relay") + " · $connected / $total"
    }

    private val _status = MutableStateFlow(Status(0, defaultUrls.size))
    val status: StateFlow<Status> = _status.asStateFlow()

    /**
     * Le client du relais local quand l'antenne y est accrochée, null sinon.
     * C'est la porte du salon de cabine : tout ce qui se publie passe par
     * lui, jamais par les relais par défaut.
     */
    private val _localRelay = MutableStateFlow<RelayClient?>(null)
    val localRelay: StateFlow<RelayClient?> = _localRelay.asStateFlow()

    private var clients: List<RelayClient> = emptyList()
    private var watchers: List<Job> = emptyList()
    private var currentUrls: List<String> = emptyList()
    private var usingLocal = false
    private var keys: NostrKeys? = null
    private var wifiWatch: AutoCloseable? = null
    private var generation = 0

    /** Allume l'antenne pour ce noyau. Sans effet si elle émet déjà. */
    fun start(keys: NostrKeys) {
        if (this.keys != null) return
        this.keys = keys
        generation++
        // L'éclaireur d'abord : si un Wi-Fi est déjà connecté, son onAvailable
        // est rejoué à l'inscription et déclenche le premier réglage.
        wifiWatch = scout?.watchWifi { scope.launch { retune() } }
        scope.launch { retune() }
        Log.d(TAG, "antenne allumée")
    }

    /** Coupe tout — dissolution du noyau ou fermeture de la station. */
    fun stop() {
        if (keys == null) return
        keys = null
        generation++
        wifiWatch?.close()
        wifiWatch = null
        teardown()
        currentUrls = emptyList()
        usingLocal = false
        _status.value = Status(0, defaultUrls.size)
        Log.d(TAG, "antenne coupée")
    }

    /**
     * (Re)règle l'antenne : relais local si la passerelle du Wi-Fi en héberge
     * un, relais par défaut sinon. Sans effet si rien n'a changé.
     */
    private suspend fun retune() {
        val forgedKeys = keys ?: return
        val gen = generation
        val local = scout?.probe() // jusqu'à ~1,5 s, sans toucher à l'état
        if (gen != generation) return // antenne coupée ou reforgée entre-temps

        val urls = local?.let { listOf(it.url) } ?: defaultUrls
        if (urls == currentUrls && clients.isNotEmpty()) return

        teardown()
        currentUrls = urls
        usingLocal = local != null
        val httpClient = local?.let {
            OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .socketFactory(it.network.socketFactory)
                .build()
        }
        // close() est définitif sur RelayClient : chaque réglage repart
        // sur des clients neufs.
        clients = urls.map { RelayClient(it, scope, httpClient) }
        watchers = clients.map { client ->
            scope.launch {
                client.state.collect { state ->
                    if (state is RelayClient.State.Retrying) {
                        Log.d(TAG, "${client.url} : nouvel essai ${state.attempt} dans ${state.inMs} ms")
                        // Un relais local qui meurt (station redémarrée) ne
                        // produit aucun événement réseau : on re-sonde après
                        // quelques échecs pour rebasculer sur le défaut.
                        if (usingLocal && state.attempt >= 3) scope.launch { retune() }
                    }
                    refresh()
                }
            }
        }
        clients.forEach { client ->
            client.subscribe(
                SELF_SUBSCRIPTION,
                NostrFilter(authors = listOf(forgedKeys.publicKeyHex), limit = 20),
            )
            client.connect()
        }
        _localRelay.value = if (usingLocal) clients.firstOrNull() else null
        refresh()
        Log.d(TAG, "à l'écoute : ${urls.joinToString()}" + if (usingLocal) " (relais local)" else "")
    }

    private fun teardown() {
        _localRelay.value = null
        watchers.forEach { it.cancel() }
        clients.forEach { it.close() }
        clients = emptyList()
        watchers = emptyList()
    }

    private fun refresh() {
        if (keys == null) return // antenne coupée : ne pas écraser le statut final
        _status.value = Status(
            connected = clients.count { it.state.value is RelayClient.State.Connected },
            total = currentUrls.size,
            local = usingLocal,
        )
    }
}
