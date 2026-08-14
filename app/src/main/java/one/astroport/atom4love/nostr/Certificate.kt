package one.astroport.atom4love.nostr

import android.util.Log
import kotlin.math.pow
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.domain.A4lAddress
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Phi2X

/**
 * Notre propre certificat ATOM4LOVE — le kind 30078 `d=atom4love` que la
 * constellation lit.
 *
 * Jusqu'ici seule la station le publiait, à l'activation d'un MULTIPASS
 * (`atom4love_publish.py`). Elle ne peut pas le faire ici : l'activation refuse
 * toujours, et la clé LOVE de cet appareil est dérivée localement. Le certificat
 * est donc construit et signé ici, à partir de la même fiche et des mêmes
 * formules — [build] suit son script tag pour tag.
 *
 * **Deux choses que nous n'écrivons pas et n'écrirons jamais** :
 * - `email_enc`, l'adresse chiffrée sous `$UPLANETNAME`. Ce secret appartient
 *   aux stations de la constellation ; sans lui on ne peut pas la produire, et
 *   c'est très bien ainsi — c'est la seule donnée du certificat qui désigne la
 *   personne.
 * - les tags de conception (`kin_c`, `glyph_c`, `tone_c`), que la station ne
 *   met que si on lui a donné une date de conception. La nôtre est déduite d'une
 *   gestation de 280 jours, elle n'est pas une donnée saisie.
 *
 * Rien n'est publié sans un geste : [publish] ne part que sur appel explicite,
 * et [existing] est là pour qu'on sache d'abord ce qu'on remplacerait.
 */
class Certificate(
    private val scope: CoroutineScope,
    private val relayUrl: String = BuildConfig.NOSTR_DEFAULT_RELAY,
) {

    companion object {
        private const val TAG = "Nostr"

        /**
         * Le sel du `a4l_proof` — `compute_a4l_proof()`, dont c'est la valeur
         * par défaut. `atomic_demo.html` essaie aussi `ATOM4LOVE_ALPHA` quand le
         * relais refuse le premier ; nous n'en avons qu'un usage, et un seul
         * essai dit franchement si la porte est ouverte ou fermée.
         */
        const val APP_ID = "ATOM4LOVE_v1"

        private const val SUBSCRIPTION = "a4l-mine"
        private const val QUERY_TIMEOUT_MS = 8_000L
        private const val PUBLISH_TIMEOUT_MS = 10_000L
    }

    /** Où en est le geste de publication. */
    sealed interface State {
        /** On n'a pas encore regardé. */
        data object Unknown : State

        data object Checking : State

        /** Rien sur le relais à notre nom : le certificat est à publier. */
        data object Absent : State

        /**
         * Un certificat existe déjà pour notre clé. [fromStation] quand il porte
         * un `email_enc` — donc quand c'est la station qui l'a écrit.
         */
        data class Present(val createdAt: Long, val fromStation: Boolean) : State

        data object Publishing : State

        data class Published(val eventId: String) : State

        /** Le relais a répondu non, ou n'a pas répondu du tout. */
        data class Refused(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Le certificat, tel qu'il partirait — construit sans rien envoyer, pour que
     * l'écran puisse montrer ce qu'il s'apprête à dire de nous.
     *
     * Null tant que la fiche n'a pas de quoi : sans lieu il n'y a ni phase ni
     * adresse, et un certificat sans les deux ne dit rien.
     */
    fun build(keys: NostrKeys, birth: BirthData, createdAt: Long = System.currentTimeMillis() / 1000): NostrEvent? {
        val lat = birth.lat ?: return null
        val lon = birth.lon ?: return null
        val birthUnix = Phi2X.birthUnixUtc(birth) ?: return null
        val phase = Phi2X.personalPhase(birthUnix, lat, lon)

        // L'instant de naissance, et non celui de la publication : la grille
        // pentagonale tourne, et la station ancre l'adresse à la naissance.
        // Republier dix fois rend donc dix fois la même adresse.
        val geo = A4lAddress.encode(lat, lon, birthUnix.toDouble())
        val psi = Phi2X.resonanceField(lat, lon, birthUnix.toDouble())
        val omega = Phi2X.omegaBioAsPublished(birth.weightKg, birth.wave)
        val kin = KinMaya.of(birth)

        val proof = Hex.encode(
            LoveKeyForge.sha256("${keys.publicKeyHex}:$APP_ID".toByteArray(Charsets.UTF_8)),
        )

        // Les mêmes arrondis que la station, et dans son ordre : ce contenu se
        // relit ailleurs, autant qu'il s'y relise à l'identique.
        val content = NostrEvent.json.encodeToString(
            buildJsonObject {
                put("personal_phase", round(phase, 6))
                omega?.let { put("omega_bio", round(it, 4)) }
                put("a5l_amplitude", round(psi, 6))
                birth.wave?.sex?.let { put("biological_sex", it) }
                put("kin_num", kin?.kin ?: 0)
                put("version", 1)
            },
        )

        val tags = buildList {
            add(listOf("d", Constellation.CERTIFICATE_D))
            add(listOf("a4l_proof", proof))
            add(listOf("g", geo.pentagon))
            add(listOf("g", geo.hex))
            add(listOf("a5l", Phi2X.encodeA5lTag(psi)))
            if (kin != null) {
                add(listOf("kin", kin.kin.toString()))
                KinMaya.glyphNameFr(kin.glyph)?.let { add(listOf("glyph", it)) }
                // La tonalité se compte à partir de 1 chez elle, de 0 ici.
                add(listOf("tone", (kin.tone + 1).toString()))
                KinMaya.colorNameFr(kin.color)?.let { add(listOf("color", it)) }
            }
        }

        return NostrEvent.create(
            keys = keys,
            kind = Constellation.CERTIFICATE_KIND,
            content = content,
            tags = tags,
            createdAt = createdAt,
        )
    }

    /** Va voir si un certificat existe déjà à notre nom. */
    fun check(keys: NostrKeys) {
        if (job?.isActive == true) return
        _state.value = State.Checking
        job = scope.launch {
            _state.value = existing(keys)
        }
    }

    private suspend fun existing(keys: NostrKeys): State = withContext(Dispatchers.Default) {
        val client = RelayClient(relayUrl, this)
        var found: NostrEvent? = null
        var answered = false
        try {
            client.subscribe(
                SUBSCRIPTION,
                NostrFilter(
                    kinds = listOf(Constellation.CERTIFICATE_KIND),
                    identifiers = listOf(Constellation.CERTIFICATE_D),
                    authors = listOf(keys.publicKeyHex),
                    limit = 4,
                ),
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
                            val known = found
                            if (known == null || msg.event.createdAt > known.createdAt) {
                                found = msg.event
                            }
                        }
                    }
                true
            } != null
        } finally {
            client.close()
        }
        val mine = found
        when {
            mine != null -> State.Present(
                createdAt = mine.createdAt,
                // Nous ne saurions pas le réécrire : seule une station détient
                // le secret sous lequel il est chiffré.
                fromStation = mine.tags.any { it.isNotEmpty() && it[0] == "email_enc" },
            )
            answered -> State.Absent
            else -> State.Refused("relay")
        }
    }

    /**
     * Publie le certificat et attend l'accusé du relais.
     *
     * Un `OK … false` n'est pas une panne : c'est le relais qui refuse, et son
     * message dit pourquoi — c'est là que se lit l'état de la porte ATOM4LOVE.
     */
    fun publish(keys: NostrKeys, birth: BirthData) {
        if (job?.isActive == true) return
        val event = build(keys, birth) ?: run {
            _state.value = State.Refused("incomplete")
            return
        }
        _state.value = State.Publishing
        job = scope.launch {
            _state.value = send(event)
        }
    }

    private suspend fun send(event: NostrEvent): State = withContext(Dispatchers.Default) {
        val client = RelayClient(relayUrl, this)
        try {
            client.connect()
            val ok = withTimeoutOrNull(PUBLISH_TIMEOUT_MS) {
                // Attendre l'ouverture avant d'émettre : `publishAndWait` envoie
                // sur la socket, et une socket pas encore ouverte avale tout.
                client.state.takeWhile { it !is RelayClient.State.Connected }.collect {}
                client.publishAndWait(event, PUBLISH_TIMEOUT_MS)
            }
            Log.d(TAG, "certificat ${event.id.take(8)} → ${ok?.accepted} ${ok?.message.orEmpty()}")
            when {
                ok == null -> State.Refused("relay")
                ok.accepted -> State.Published(event.id)
                else -> State.Refused(ok.message.ifBlank { "refus" })
            }
        } finally {
            client.close()
        }
    }

    /** `round(x, n)` de Python, pour que le contenu porte les mêmes chiffres. */
    private fun round(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return kotlin.math.round(value * factor) / factor
    }
}
