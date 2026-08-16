package one.astroport.atom4love.nostr

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import one.astroport.atom4love.BuildConfig

/**
 * Le carnet NOSTR de la clé LOVE — NIP-02, kind 3.
 *
 * Un seul geste : ajouter à la liste le pair qu'on vient d'attester en cabine
 * ([CabinChat.Peer.nostrKey], connu dès le handshake — voir la note à Fred du
 * 16 août, pas besoin de QR). Ni retrait, ni import, ni relais tiers : juste ce
 * qu'une rencontre réelle donne d'elle-même, avec la même clé LOVE et le même
 * relais que [Certificate].
 *
 * Un kind 3 est un remplaçable simple (NIP-01) : le relais ne garde que le
 * plus récent par clé. Ajouter un pair suppose donc de republier la liste
 * entière, un membre de plus — jamais un de moins —, ce qui demande de relire
 * d'abord celle qui existe pour ne pas effacer les suivis antérieurs. Le
 * contenu ne porte aucune métadonnée de relais (`content = ""`) : la seule
 * clé qui écrit ici est la nôtre, et elle n'en a pas à donner.
 */
class Contacts(
    private val scope: CoroutineScope,
    private val relayUrl: String = BuildConfig.NOSTR_DEFAULT_RELAY,
) {

    companion object {
        private const val TAG = "Nostr"

        /** NIP-02. */
        const val FOLLOW_KIND = 3

        private const val SUBSCRIPTION = "a4l-contacts"
        private const val QUERY_TIMEOUT_MS = 8_000L
        private const val PUBLISH_TIMEOUT_MS = 10_000L

        /**
         * La liste `p` fusionnée : [newPubkeyHex] en plus, à la fin, s'il n'y
         * était pas déjà (comparaison insensible à la casse — l'hexadécimal
         * d'un relais n'est pas garanti bas de casse). Fonction pure, testée
         * sans rien publier.
         */
        fun merge(existingTags: List<List<String>>, newPubkeyHex: String): List<List<String>> {
            val already = existingTags.any {
                it.size >= 2 && it[0] == "p" && it[1].equals(newPubkeyHex, ignoreCase = true)
            }
            return if (already) existingTags else existingTags + listOf(listOf("p", newPubkeyHex))
        }
    }

    /** Où en est le geste de suivi, par clé de pair suivi (hex). */
    sealed interface State {
        data object Publishing : State
        data object Published : State
        data class Refused(val reason: String) : State
    }

    private val _state = MutableStateFlow<Map<String, State>>(emptyMap())
    val state: StateFlow<Map<String, State>> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Ajoute [peerPubkeyHex] au carnet signé par [keys]. Ne part que sur appel
     * explicite — un bouton, jamais une conséquence d'une simple rencontre.
     */
    fun follow(keys: NostrKeys, peerPubkeyHex: String) {
        if (job?.isActive == true) return
        _state.value = _state.value + (peerPubkeyHex to State.Publishing)
        job = scope.launch {
            _state.value = _state.value + (peerPubkeyHex to publish(keys, peerPubkeyHex))
        }
    }

    private suspend fun publish(keys: NostrKeys, peerPubkeyHex: String): State =
        withContext(Dispatchers.Default) {
            val existing = existingTags(keys.publicKeyHex) ?: return@withContext State.Refused("relay")
            val event = NostrEvent.create(
                keys = keys,
                kind = FOLLOW_KIND,
                content = "",
                tags = merge(existing, peerPubkeyHex),
            )
            val client = RelayClient(relayUrl, this)
            try {
                client.connect()
                val ok = withTimeoutOrNull(PUBLISH_TIMEOUT_MS) {
                    // Attendre l'ouverture avant d'émettre, comme le certificat :
                    // une socket pas encore ouverte avale tout.
                    client.state.takeWhile { it !is RelayClient.State.Connected }.collect {}
                    client.publishAndWait(event, PUBLISH_TIMEOUT_MS)
                }
                Log.d(TAG, "suivi de ${peerPubkeyHex.take(8)} → ${ok?.accepted} ${ok?.message.orEmpty()}")
                when {
                    ok == null -> State.Refused("relay")
                    ok.accepted -> State.Published
                    else -> State.Refused(ok.message.ifBlank { "refus" })
                }
            } finally {
                client.close()
            }
        }

    /**
     * Les tags `p` du dernier kind 3 publié sous [pubkeyHex] — vide s'il n'y
     * en a jamais eu, `null` si le relais n'a pas répondu (à ne pas confondre
     * l'un avec l'autre : le second dit de réessayer, pas de republier à vide).
     */
    private suspend fun existingTags(pubkeyHex: String): List<List<String>>? =
        withContext(Dispatchers.Default) {
            val client = RelayClient(relayUrl, this)
            var latest: NostrEvent? = null
            var answered = false
            try {
                client.subscribe(
                    SUBSCRIPTION,
                    NostrFilter(kinds = listOf(FOLLOW_KIND), authors = listOf(pubkeyHex), limit = 4),
                )
                answered = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                    client.inbound
                        .onSubscription { client.connect() }
                        .takeWhile { msg ->
                            when (msg) {
                                is RelayMessage.Eose -> msg.subscriptionId != SUBSCRIPTION
                                is RelayMessage.Closed -> msg.subscriptionId != SUBSCRIPTION
                                else -> true
                            }
                        }
                        .collect { msg ->
                            if (msg is RelayMessage.Event && msg.subscriptionId == SUBSCRIPTION) {
                                val known = latest
                                if (known == null || msg.event.createdAt > known.createdAt) latest = msg.event
                            }
                        }
                    true
                } != null
            } finally {
                client.close()
            }
            if (!answered && latest == null) null else (latest?.tags ?: emptyList())
        }
}
