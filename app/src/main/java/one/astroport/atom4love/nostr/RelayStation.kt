package one.astroport.atom4love.nostr

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.astroport.atom4love.BuildConfig

/**
 * L'antenne de la station : gère la connexion aux relais NOSTR une fois le
 * noyau forgé, et l'éteint à la dissolution.
 *
 * Un [RelayClient] par relais (la liste n'en compte qu'un pour l'instant —
 * celui de BuildConfig — mais tout est prêt pour en accueillir d'autres).
 * Chaque client se reconnecte tout seul ; l'antenne ne fait qu'agréger leurs
 * états en un compteur « connectés / total » pour l'interface.
 *
 * Aucun événement n'est publié pour l'instant : le premier échange du
 * protocole ATOM4LOVE reste à définir (avec Fred). En attendant, on souscrit
 * à ses propres événements — le tuyau est branché et vérifié de bout en bout
 * (REQ → EOSE), sans rien écrire chez personne.
 */
class RelayStation(
    private val scope: CoroutineScope,
    private val urls: List<String> = listOf(BuildConfig.NOSTR_DEFAULT_RELAY),
) {

    companion object {
        private const val TAG = "Nostr"
        private const val SELF_SUBSCRIPTION = "a4l-self"
    }

    /** Ce que l'interface affiche : « relay · connectés / total ». */
    data class Status(val connected: Int, val total: Int) {
        val online: Boolean get() = connected > 0
        val label: String get() = "relay · $connected / $total"
    }

    private val _status = MutableStateFlow(Status(0, urls.size))
    val status: StateFlow<Status> = _status.asStateFlow()

    private var clients: List<RelayClient> = emptyList()
    private var watchers: List<Job> = emptyList()

    /** Allume l'antenne pour ce noyau. Sans effet si elle émet déjà. */
    fun start(keys: NostrKeys) {
        if (clients.isNotEmpty()) return
        // close() est définitif sur RelayClient : chaque allumage repart
        // sur des clients neufs.
        clients = urls.map { RelayClient(it, scope) }
        watchers = clients.map { client ->
            scope.launch {
                client.state.collect { state ->
                    if (state is RelayClient.State.Retrying) {
                        Log.d(TAG, "${client.url} : nouvel essai ${state.attempt} dans ${state.inMs} ms")
                    }
                    refresh()
                }
            }
        }
        clients.forEach { client ->
            client.subscribe(
                SELF_SUBSCRIPTION,
                NostrFilter(authors = listOf(keys.publicKeyHex), limit = 20),
            )
            client.connect()
        }
        Log.d(TAG, "antenne allumée : ${urls.joinToString()}")
    }

    /** Coupe tout — dissolution du noyau ou fermeture de la station. */
    fun stop() {
        if (clients.isEmpty()) return
        watchers.forEach { it.cancel() }
        clients.forEach { it.close() }
        clients = emptyList()
        watchers = emptyList()
        _status.value = Status(0, urls.size)
        Log.d(TAG, "antenne coupée")
    }

    private fun refresh() {
        _status.value = Status(
            connected = clients.count { it.state.value is RelayClient.State.Connected },
            total = urls.size,
        )
    }
}
