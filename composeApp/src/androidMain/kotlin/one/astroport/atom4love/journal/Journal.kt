package one.astroport.atom4love.journal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Le journal de la radio — ce que la station fait, pendant qu'elle le fait.
 *
 * ## Ce qu'il remplace
 *
 * **La conversation commune.** La cabine ouvrait une salle où tous les pairs à
 * portée parlaient ensemble, et c'était le seul endroit où l'on pouvait écrire.
 * Elle avait un défaut qu'aucun réglage ne corrigeait : on n'écrit pas à une
 * salle. On écrit à quelqu'un. Les conversations sont donc devenues privées
 * ([one.astroport.atom4love.chat.Conversations]), et la fenêtre qu'elles
 * laissent vide est rendue à ce qu'on n'avait jamais montré — **le travail de
 * la radio**.
 *
 * Ça n'est pas un lot de consolation. Cette application fait, en permanence et
 * sans qu'on le voie, des choses que personne ne peut deviner : elle annonce une
 * adresse 4D, elle balaie, elle entend des sceaux, elle noue des liens chiffrés.
 * Tout ça était invisible, et son invisibilité fabriquait la question qui revient
 * le plus — *« il ne se passe rien, est-ce que ça marche ? »*. Le journal y
 * répond sans une ligne d'explication : il se passe quelque chose, et ça défile.
 *
 * ## La règle qui décide de ce qui entre, et elle est stricte
 *
 * ⚠ **Le journal dit ce que la radio fait, jamais ce que son porteur décide.**
 *
 * Y entrent : le balayage, l'annonce de la balise, une carte qui paraît ou
 * s'éloigne, un lien qui se noue, une rencontre **mutuelle** — parce que dans
 * chacun de ces cas, l'évènement s'est produit tout seul ou des deux côtés.
 *
 * N'y entrent **jamais** : partir chercher quelqu'un depuis le Plateau, ouvrir
 * une conversation privée, en recevoir une. Ce sont des initiatives, et une
 * initiative qui s'inscrit quelque part cesse d'être un geste pour devenir une
 * trace. C'est la même règle qui fait taire la déclaration de recherche à sens
 * unique ([one.astroport.atom4love.proximity.Rendezvous]) et qui interdit à la
 * bannière de présence d'annoncer une recherche : **le consentement d'un seul ne
 * s'affiche pas.** Un journal qui note « vous avez ouvert une conversation avec
 * X » serait, sur un téléphone qu'on prête ou qu'on pose, exactement ce que tout
 * le reste du projet s'applique à ne pas produire.
 *
 * ## Pourquoi la mémoire des transitions vit ICI
 *
 * ⚠ Elle vivait dans le composable qui observe, en `remember`. C'était un défaut
 * réel : une rotation, un changement de thème ou de langue détruit la
 * composition, la mémoire repartait vide, et **toutes les cartes à portée
 * étaient réinscrites comme si elles venaient d'arriver**. Trois voisins dans un
 * bar suffisaient à noyer la fenêtre à chaque fois qu'on tournait le téléphone.
 *
 * Les `note…` ci-dessous portent donc leur propre valeur précédente, et ne
 * s'écrivent que sur un vrai changement. L'observateur redevient ce qu'il doit
 * être : quelqu'un qui répète ce qu'il voit, sans savoir ce qui a déjà été dit.
 *
 * ## Ce qu'il ne garde pas
 *
 * Rien, au-delà de [CAPACITY] lignes et de la vie du processus. Il n'y a pas de
 * fichier, pas de base, pas de reprise au démarrage suivant : le journal est une
 * **fenêtre sur le présent**, pas un registre. Ça le met du bon côté de la
 * promesse que la cabine tenait déjà — ce qui s'est passé ici ne s'emporte pas.
 */
object Journal {

    /**
     * Assez pour remonter le fil d'une soirée, trop peu pour faire un registre.
     * Au-delà, on ne relit plus, on cherche — et chercher dans ses propres
     * allées et venues est exactement l'usage qu'on ne veut pas rendre possible.
     */
    const val CAPACITY = 200

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    /** Le plus récent en tête : c'est là que l'œil arrive. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /**
     * ⚠ **Le numéro d'ordre n'est pas décoratif.** La liste paresseuse de
     * l'écran veut une clé unique par ligne, et l'horodatage n'en est pas une :
     * deux cartes qui paraissent dans le même balayage sont inscrites à la même
     * milliseconde, avec le même sceau et le même pourcentage — donc deux
     * entrées rigoureusement égales. Compose lève sur une clé déjà employée, et
     * ça se produirait précisément quand la salle se remplit.
     */
    private val sequence = AtomicLong(0)

    private fun next(): Long = sequence.incrementAndGet()

    // ── Les transitions, retenues ici et non dans la composition ───────────
    //
    // ⚠ **La première observation ensemence, elle n'écrit pas.** Vu à l'écran le
    // 19/08 : au lancement, le journal s'ouvrait sur « Balise éteinte », « Relais
    // perdu », « Présence annoncée sans position » — puis, deux secondes plus
    // tard, les trois lignes inverses. Six lignes pour dire « l'application
    // démarre », et les trois premières étaient les valeurs par défaut des flux,
    // pas des évènements.
    //
    // Une valeur initiale n'est pas une transition. On la retient en silence, et
    // seul ce qui change ensuite s'inscrit. Ce que ça ne cache pas : une balise
    // qui reste éteinte se lit sur le Plateau, en tête, dans la ligne qui porte
    // le geste pour l'allumer — c'est là que ça doit se dire, pas dans une
    // fenêtre qui raconte ce qui se passe.
    private var lastBeacon: Boolean? = null
    private var lastCell: Long? = null
    private var lastCellKnown = false
    private var lastRelay: Boolean? = null
    private var lastCards: Map<String, Int?> = emptyMap()
    private var lastMeetings: Set<Int> = emptySet()
    private var lastPeers: Set<String> = emptySet()

    fun noteBeacon(on: Boolean) {
        if (lastBeacon == on) return
        val first = lastBeacon == null
        lastBeacon = on
        if (!first) record(Entry.Beacon(on))
    }

    fun noteCell(cell4d: Long?) {
        if (lastCellKnown && lastCell == cell4d) return
        val first = !lastCellKnown
        lastCellKnown = true
        lastCell = cell4d
        // ⚠ Une exception à l'ensemencement muet, et une seule : une cellule
        // **connue** dès la première observation mérite sa ligne. C'est une
        // information — voilà l'hexagone où vous êtes —, là où son absence n'est
        // que l'état de départ de tout le monde.
        if (!first || cell4d != null) record(Entry.Cell(cell4d))
    }

    fun noteRelay(online: Boolean) {
        if (lastRelay == online) return
        val first = lastRelay == null
        lastRelay = online
        if (!first) record(Entry.Relay(online))
    }

    /**
     * Les cartes signées à portée, par jeton de présence.
     *
     * ⚠ Par **jeton** et non par adresse : une adresse BLE tourne toutes les
     * trente secondes, et suivre les adresses ferait paraître puis disparaître
     * la même personne six fois par minute.
     */
    fun noteCards(seen: Map<String, Card>) {
        val before = lastCards
        seen.forEach { (id, card) ->
            if (id !in before) record(Entry.CardSeen(card.glyph, card.percent))
        }
        before.keys.forEach { id ->
            if (id !in seen) record(Entry.CardGone(lastGlyphs[id]))
        }
        lastGlyphs = seen.mapValues { it.value.glyph }
        lastCards = seen.mapValues { it.value.percent }
    }

    private var lastGlyphs: Map<String, Int?> = emptyMap()

    /** Ce qu'il faut savoir d'une carte pour l'inscrire. */
    data class Card(val glyph: Int?, val percent: Int?)

    /**
     * Les rencontres **mutuelles** : les jetons que l'on cherche et qui nous
     * cherchent. Un seul consentement ne produit rien, ici comme partout.
     */
    fun noteMeetings(mutual: Set<Int>, glyphOf: (Int) -> Int?) {
        (mutual - lastMeetings).forEach { token -> record(Entry.Meeting(glyphOf(token))) }
        lastMeetings = mutual
    }

    /** Les noyaux attestés, par clé publique hexadécimale. */
    fun notePeers(present: Map<String, String?>) {
        val before = lastPeers
        present.forEach { (hex, name) ->
            if (hex !in before) record(Entry.Peer(name, joined = true))
        }
        before.forEach { hex ->
            if (hex !in present) record(Entry.Peer(lastPeerNames[hex], joined = false))
        }
        lastPeerNames = present
        lastPeers = present.keys
    }

    private var lastPeerNames: Map<String, String?> = emptyMap()

    fun record(entry: Entry) {
        _entries.update { current -> (listOf(entry) + current).take(CAPACITY) }
    }

    /**
     * La station oublie tout : le journal part avec le reste, **et sa mémoire
     * des transitions avec lui**. Sans ce second effacement, la balise resterait
     * « déjà allumée » pour un noyau tout neuf, et sa première ligne manquerait.
     */
    fun clear() {
        _entries.value = emptyList()
        lastBeacon = null
        lastCell = null
        lastCellKnown = false
        lastRelay = null
        lastCards = emptyMap()
        lastGlyphs = emptyMap()
        lastMeetings = emptySet()
        lastPeers = emptySet()
        lastPeerNames = emptyMap()
    }

    /**
     * Une ligne du journal — **une valeur, jamais une phrase**.
     *
     * Même règle que [one.astroport.atom4love.chat.ChatError] et que
     * [one.astroport.atom4love.proximity.ProximityPayload.Signature] : ce qui
     * enregistre n'a pas de `Context` et ne saurait pas dans quelle langue
     * écrire. L'écran rend le texte, et changer de langue relit tout le journal
     * dans la nouvelle sans qu'une seule ligne ait été réécrite.
     */
    sealed interface Entry {
        val atMs: Long

        /** Identifiant de ligne — voir [sequence]. */
        val seq: Long

        /** La balise s'allume ou s'éteint — le socle de tout le reste. */
        data class Beacon(
            val on: Boolean,
            override val atMs: Long = now(),
            override val seq: Long = next(),
        ) : Entry

        /**
         * L'adresse 4D que la balise annonce, ou son absence.
         *
         * ⚠ Sans permission de localisation la balise annonce une **présence**
         * et rien de plus : c'est la nuance que cette ligne existe pour dire.
         * Elle explique, sans un mot d'aide, pourquoi le portail compte zéro
         * alors que la balise tourne.
         */
        data class Cell(
            val cell4d: Long?,
            override val atMs: Long = now(),
            override val seq: Long = next(),
        ) : Entry

        /**
         * Une carte paraît dans l'air — son sceau, et ce qu'elle vaut rapportée
         * à la nôtre.
         *
         * Elle ne nomme personne, et ne le peut pas : l'annonce de proximité ne
         * porte aucun npub. C'est bien ce qui rend cette ligne publiable dans
         * une fenêtre qu'on laisse ouverte sur une table.
         */
        data class CardSeen(
            val glyph: Int?,
            val percent: Int?,
            override val atMs: Long = now(),
            override val seq: Long = next(),
        ) : Entry

        /** Elle s'éloigne. La salle bouge, et ça se voit. */
        data class CardGone(
            val glyph: Int?,
            override val atMs: Long = now(),
            override val seq: Long = next(),
        ) : Entry

        /**
         * **Une rencontre**, et le seul évènement du journal qui engage deux
         * personnes.
         *
         * Il n'est inscrit que lorsque les deux se cherchent : un seul qui
         * cherche ne produit rien, ici comme partout ailleurs. C'est « la
         * tentative de rencontre entre pairs participants et volontaires » —
         * les deux mots comptent, et le second est la condition d'écriture.
         */
        data class Meeting(
            val glyph: Int?,
            override val atMs: Long = now(),
            override val seq: Long = next(),
        ) : Entry

        /**
         * Un noyau attesté entre dans la radio, ou en sort. [name] est le nom
         * qu'il s'est donné, null s'il n'en a pas.
         */
        data class Peer(
            val name: String?,
            val joined: Boolean,
            override val atMs: Long = now(),
            override val seq: Long = next(),
        ) : Entry

        /** L'antenne trouve un relais, ou le perd. */
        data class Relay(
            val online: Boolean,
            override val atMs: Long = now(),
            override val seq: Long = next(),
        ) : Entry
    }

    private fun now(): Long = System.currentTimeMillis()
}
