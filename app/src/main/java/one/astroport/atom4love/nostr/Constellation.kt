package one.astroport.atom4love.nostr

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.domain.A4lAddress
import one.astroport.atom4love.domain.KinMaya

/**
 * La constellation : tous les noyaux qui ont activé leur clé LOVE, lus sur le
 * relais public.
 *
 * Un certificat ATOM4LOVE est un **kind 30078** (NIP-78, remplaçable paramétré)
 * d'identifiant `d=atom4love`. Il est publié par la station au moment de
 * l'activation — `atom4love_publish.py`, appelé depuis `/atom4love/activate` —
 * et c'est exactement ce que lit `atomic_map.html` pour poser ses marqueurs.
 *
 * Ce qu'il porte :
 * - tag `g` : l'adresse [A4lAddress] du lieu de **naissance**, à la maille du
 *   kilomètre. C'est l'ancre du SALT, **pas la résidence**.
 * - tags `kin` et `phase` : le sceau du Tzolkin et la phase personnelle φ.
 * - contenu JSON : `personal_phase`, parfois `picture`.
 *
 * ⚠ La résidence vit chez Fred dans un `d=atom4love-home` séparé, en **degrés
 * bruts**. Sa carte en fait un calque ; nous l'avons lu jusqu'au 15/08, puis
 * retiré — voir le mot plus bas.
 *
 * **Écoute seule, relais public.** L'antenne de la station ([RelayStation])
 * s'accroche au relais local quand il y en a un — la constellation, elle, n'est
 * nulle part ailleurs que sur le relais public : la requête a donc son propre
 * client, ouvert le temps d'une lecture et refermé aussitôt.
 *
 * Un point sur lequel nous divergeons de sa carte, sciemment : elle écarte les
 * certificats sans φ lisible (`if (isNaN(phi)) return`). Nous les gardons, sans
 * couleur de phase. La question posée est « qui a activé sa clé », et un
 * certificat sans φ y répond oui.
 */
class Constellation(
    private val scope: CoroutineScope,
    private val relayUrl: String = BuildConfig.NOSTR_DEFAULT_RELAY,
) {

    companion object {
        private const val TAG = "Nostr"

        /** L'identifiant du certificat de clé LOVE — `d=atom4love`. */
        const val CERTIFICATE_D = "atom4love"

        /** NIP-78 : données d'application, remplaçables et paramétrées. */
        const val CERTIFICATE_KIND = 30078

        private const val SUBSCRIPTION = "a4l-constellation"

        /** Le plafond de `atomic_map.html`, à la lettre. */
        private const val QUERY_LIMIT = 2_000

        /**
         * Sa carte laisse 14 s à la requête. Un relais qui n'a pas envoyé son
         * EOSE dans ce délai a quand même livré des événements : on garde ce
         * qui est arrivé plutôt que de rendre une erreur sur une lecture
         * partielle.
         */
        private const val QUERY_TIMEOUT_MS = 14_000L

        /**
         * Sept jours : la fenêtre pendant laquelle un noyau fraîchement scellé
         * passe devant tout le monde sur la carte.
         *
         * Une semaine parce que la carte n'est pas un fil qu'on déroule tous
         * les matins — quelqu'un qui ouvre l'application le dimanche doit
         * encore voir celui qui s'est inscrit le mardi. Plus court, l'honneur
         * ne serait rendu qu'aux assidus ; plus long, « nouveau » ne voudrait
         * plus rien dire.
         */
        const val NEWCOMER_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L
    }

    /** Un noyau de la constellation, tel que son certificat le donne. */
    data class Atom(
        val pubkey: String,
        val place: A4lAddress.Place,
        /** φ — null si le certificat ne la porte ni en tag ni dans son contenu. */
        val phase: Double?,
        val kin: KinMaya.Kin?,
        /** Quand la station a scellé ce certificat (epoch, secondes). */
        val createdAt: Long,
    ) {
        /** De quoi le nommer tant qu'on ne lit pas son profil (kind 0). */
        val shortKey: String get() = pubkey.take(8)

        /**
         * Un noyau qui vient d'arriver dans la constellation — celui à qui la
         * carte fait honneur, [NEWCOMER_WINDOW_MS] durant.
         *
         * **L'honneur est automatique, et son déclencheur est une preuve, pas
         * une déclaration.** Ce certificat est publié par la station elle-même
         * au moment où elle active la clé LOVE : il n'existe pas sans MULTIPASS
         * actif. Le lire récent, c'est donc *savoir* que quelqu'un vient de
         * s'inscrire sur Astroport — personne n'a eu à l'annoncer, et personne
         * ne peut se l'attribuer sans passer par la station.
         *
         * ⚠ **Ce que ça mesure exactement** : la date à laquelle la station a
         * scellé ce certificat, pas celle d'une première inscription. Un 30078
         * est remplaçable (NIP-78) ; quelqu'un qui réactive sa clé LOVE
         * repasserait donc « nouveau ». C'est assumé — la fête est pour l'entrée
         * dans la constellation, et une réactivation en est une aussi. Ce qu'il
         * ne faut surtout pas en conclure, c'est un âge de compte.
         *
         * ⚠ Un certificat daté du futur compte comme nouveau. Les horloges de
         * deux machines ne s'accordent jamais à la seconde, et rater l'honneur
         * de quelqu'un qui vient tout juste de s'inscrire — le cas même qu'on
         * veut servir — serait pire qu'en honorer un de trop.
         */
        fun isNewcomer(nowMs: Long = System.currentTimeMillis()): Boolean =
            nowMs - createdAt * 1000L < NEWCOMER_WINDOW_MS
    }

    sealed interface State {
        /** Rien n'a encore été demandé. */
        data object Idle : State

        data object Loading : State

        /** [atoms] peut être vide : le relais a répondu, il n'y a personne. */
        data class Loaded(val atoms: List<Atom>, val readAtMs: Long) : State

        /** Le relais n'a rien dit du tout — hors ligne, ou injoignable. */
        data object Unreachable : State
    }

    // ⚠ **Le calque des résidences vivait ici** — `data class Home`, le flux
    // `homes`, et `loadHomes()` qui lisait `d=atom4love-home` sur le relais
    // public. Retiré le 15/08, sur demande de Florent.
    //
    // C'était la donnée la plus exposée de tout le protocole : le certificat
    // ordinaire ne porte le lieu de naissance qu'à la maille du kilomètre, via
    // l'adresse `a4l:`, quand la résidence partait en **degrés bruts** — non
    // pas où l'on est né, mais où l'on habite. Nous n'en publiions aucune, et
    // le relais public n'en comptait aucune non plus : ce calque allumait une
    // carte vide au prix d'une requête sur la donnée la plus sensible du jeu.

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /** Relit la constellation. Un appel pendant une lecture en cours ne fait rien. */
    fun refresh() {
        if (job?.isActive == true) return
        _state.value = State.Loading
        job = scope.launch {
            val events = read(CERTIFICATE_D)
            _state.value = if (events == null) {
                State.Unreachable
            } else {
                State.Loaded(atoms = events.toAtoms(), readAtMs = System.currentTimeMillis())
            }
        }
    }

    /**
     * Une requête, un EOSE, on referme. null si la socket n'a jamais rien rendu.
     *
     * La collecte part sur [Dispatchers.Default] et non sur le fil principal :
     * [RelayClient] émet ses messages en `tryEmit` avec un tampon fini, et deux
     * mille événements arrivant en rafale pendant que l'interface se recompose
     * seraient perdus sans bruit.
     */
    private suspend fun read(identifier: String): List<NostrEvent>? = withContext(Dispatchers.Default) {
        val client = RelayClient(relayUrl, this)
        val events = mutableListOf<NostrEvent>()
        val subscription = "$SUBSCRIPTION-$identifier"
        val answered: Boolean
        try {
            // Souscrire avant de connecter : le filtre est mémorisé et rejoué à
            // l'ouverture, donc aucun événement ne passe avant qu'on écoute.
            client.subscribe(
                subscription,
                NostrFilter(
                    kinds = listOf(CERTIFICATE_KIND),
                    identifiers = listOf(identifier),
                    limit = QUERY_LIMIT,
                ),
            )
            answered = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                client.inbound
                    .onSubscription { client.connect() }
                    .takeWhile { msg ->
                        when (msg) {
                            is RelayMessage.Eose -> msg.subscriptionId != subscription
                            is RelayMessage.Closed -> msg.subscriptionId != subscription
                            else -> true
                        }
                    }
                    .collect { msg ->
                        if (msg is RelayMessage.Event && msg.subscriptionId == subscription) {
                            events += msg.event
                        }
                    }
                true
            } != null
        } finally {
            client.close()
        }
        Log.d(TAG, "$identifier : ${events.size} événements lus sur $relayUrl (EOSE : $answered)")
        // Le relais a fini sa phrase, ou il en a dit assez : dans les deux cas
        // ce qu'on tient est une constellation. Silence complet, en revanche,
        // ne veut pas dire « personne » — il ne veut rien dire du tout.
        events.takeIf { answered || it.isNotEmpty() }
    }

    /**
     * Un noyau par clé publique. Le 30078 est remplaçable, mais un relais peut
     * détenir plusieurs versions : le plus récent gagne, comme chez Fred.
     */
    private fun List<NostrEvent>.toAtoms(): List<Atom> =
        groupBy { it.pubkey }
            .values
            .mapNotNull { versions -> versions.maxBy { it.createdAt }.toAtom() }
            .sortedByDescending { it.createdAt }

    private fun NostrEvent.toAtom(): Atom? {
        val place = A4lAddress.fromTags(tags) ?: return null
        val body = runCatching {
            NostrEvent.json.parseToJsonElement(content).jsonObject
        }.getOrNull()

        fun tag(name: String): String? =
            tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

        fun number(field: String): Double? =
            body?.get(field)?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }

        /**
         * Le KIN se dit de trois façons selon l'âge du certificat : le tag
         * `kin` (que lit son projecteur), le champ `kin_num`, ou l'objet
         * `kin_birth` des certificats les plus anciens. Les trois disent le
         * même nombre — son projecteur, lui, écarte purement ceux qui n'ont
         * pas le tag, et perd donc la moitié de ce relais.
         */
        fun kinNumber(): Int? {
            tag("kin")?.toIntOrNull()?.let { return it }
            number("kin_num")?.let { return it.toInt() }
            return runCatching {
                body?.get("kin_birth")?.jsonObject?.get("kin")?.jsonPrimitive?.int
            }.getOrNull()
        }

        return Atom(
            pubkey = pubkey,
            place = place,
            // Le tag d'abord — c'est celui que lit son projecteur ; le contenu
            // ensuite, c'est celui que lit sa carte. Les deux portent la même φ.
            phase = tag("phase")?.toDoubleOrNull() ?: number("personal_phase"),
            kin = kinNumber()?.let { KinMaya.ofNumber(it) },
            // ⚠ `omega_bio` était lu ici. Retiré le 15/08 avec le reste de
            // Watson : le champ existe toujours dans les certificats de la
            // station, on ne le regarde simplement plus.
            createdAt = createdAt,
        )
    }
}
