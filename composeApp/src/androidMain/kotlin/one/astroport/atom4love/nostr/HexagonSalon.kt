package one.astroport.atom4love.nostr

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Le salon de cabine : des pensées éphémères échangées entre les noyaux
 * physiquement réunis autour d'une même station.
 *
 * Deux garde-fous de conception, en attendant l'alignement du protocole
 * avec Fred (note de protocole, question 19) :
 *  - le salon n'existe QUE sur le relais local ([RelayStation.localRelay]) —
 *    rien ne part jamais sur un relais public ;
 *  - kind éphémère ([KIND_PENSEE], plage 20000–29999 du NIP-01) : les relais
 *    ne stockent pas ces événements, le salon ne laisse aucune archive. À la
 *    déconnexion du relais local, les pensées s'effacent aussi de l'écran :
 *    le salon appartient au lieu, pas à l'appareil.
 *
 * Le tag `["h", cellule]` porte la cellule H3 du salon, dans l'encodage de
 * l'adresse affichée par le Radar.
 */
class HexagonSalon(
    private val scope: CoroutineScope,
    private val localRelay: StateFlow<RelayClient?>,
) {

    companion object {
        /** Kind éphémère réservé v0 aux pensées de cabine — à valider avec Fred. */
        const val KIND_PENSEE = 24242
        private const val SUBSCRIPTION = "a4l-cabine"
        private const val MAX_PENSEES = 100
        private const val TAG = "Nostr"
    }

    /** Une pensée affichable. [author] est le npub raccourci de l'émetteur. */
    data class Pensee(
        val id: String,
        val author: String,
        val mine: Boolean,
        val text: String,
        val atSeconds: Long,
    )

    private var keys: NostrKeys? = null
    private var watcher: Job? = null
    private val cell = MutableStateFlow<String?>(null)

    private val _pensees = MutableStateFlow<List<Pensee>>(emptyList())
    val pensees: StateFlow<List<Pensee>> = _pensees.asStateFlow()

    /** Ouvre le salon pour ce noyau. Sans effet s'il est déjà ouvert. */
    fun start(keys: NostrKeys) {
        if (this.keys != null) return
        this.keys = keys
        watcher = scope.launch {
            combine(localRelay, cell) { client, cellId -> client to cellId }
                .collectLatest { (client, cellId) ->
                    _pensees.value = emptyList()
                    if (client == null || cellId == null) return@collectLatest
                    client.subscribe(
                        SUBSCRIPTION,
                        NostrFilter(kinds = listOf(KIND_PENSEE), hexagons = listOf(cellId)),
                    )
                    client.inbound
                        .filterIsInstance<RelayMessage.Event>()
                        .collect { inbound ->
                            if (inbound.subscriptionId == SUBSCRIPTION) admit(inbound.event)
                        }
                }
        }
    }

    /** Ferme le salon — dissolution du noyau ou fermeture de la station. */
    fun stop() {
        keys = null
        watcher?.cancel()
        watcher = null
        _pensees.value = emptyList()
    }

    /** La cellule H3 du lieu (encodage de l'adresse Radar). Change → salon vidé. */
    fun setCell(cellId: String) {
        cell.value = cellId
    }

    /**
     * Publie une pensée sur le relais local. false si le salon est fermé,
     * le texte vide, ou le relais muet — l'appelant peut alors garder le
     * texte dans le champ de saisie.
     */
    suspend fun send(text: String): Boolean {
        val forgedKeys = keys ?: return false
        val cellId = cell.value ?: return false
        val client = localRelay.value ?: return false
        val content = text.trim()
        if (content.isEmpty()) return false

        val event = NostrEvent.create(
            keys = forgedKeys,
            kind = KIND_PENSEE,
            content = content,
            tags = listOf(listOf("h", cellId)),
        )
        val ok = client.publishAndWait(event)
        val accepted = ok?.accepted == true
        if (accepted) {
            // Écho local immédiat : les kinds éphémères ne sont pas toujours
            // renvoyés à leur émetteur ; le dédoublonnage absorbe l'écho relais.
            admit(event)
        } else {
            Log.w(TAG, "pensée refusée par le relais : ${ok?.message ?: "pas de réponse"}")
        }
        return accepted
    }

    private fun admit(event: NostrEvent) {
        if (event.kind != KIND_PENSEE || !event.hasValidId()) return
        _pensees.value = _pensees.value
            .let { existing -> if (existing.any { it.id == event.id }) existing else existing + toPensee(event) }
            .sortedBy { it.atSeconds }
            .takeLast(MAX_PENSEES)
    }

    private fun toPensee(event: NostrEvent): Pensee {
        val npub = runCatching { Bech32.encode("npub", Hex.decode(event.pubkey)) }
            .getOrDefault(event.pubkey)
        return Pensee(
            id = event.id,
            author = "${npub.take(8)}…${npub.takeLast(4)}",
            mine = event.pubkey == keys?.publicKeyHex,
            text = event.content,
            atSeconds = event.createdAt,
        )
    }
}
