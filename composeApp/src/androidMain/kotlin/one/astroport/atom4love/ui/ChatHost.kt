package one.astroport.atom4love.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.chat.ChatEngine
import one.astroport.atom4love.chat.ChatKind
import one.astroport.atom4love.chat.ChatMessage
import one.astroport.atom4love.chat.Conversations
import one.astroport.atom4love.chat.UnreadNotifier
import one.astroport.atom4love.chat.net.P2pGroup
import one.astroport.atom4love.R
import one.astroport.atom4love.nostr.NostrKeys

/**
 * La cabine, tenue hors de la composition.
 *
 * Elle y vivait, et Compose la fermait sans que personne ne l'ait demandé :
 * toute recréation de l'activité — une rotation, un changement de thème, de
 * langue, de taille de police, un passage en fenêtres partagées — détruit la
 * composition, donc appelait `stop()`. La conversation disparaissait avec, et
 * « fermer = effacer » cessait d'être un geste pour devenir un accident.
 *
 * Ici, l'ouverture et la fermeture sont les seuls chemins vers `start()` et
 * `stop()`. Un ViewModel traverse les changements de configuration et n'est
 * vidé que lorsque l'activité s'en va pour de bon — c'est exactement la durée
 * de vie que la cabine devait avoir.
 */
class ChatHost(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    /** Une instance neuve à chaque ouverture : un moteur arrêté ne rallume pas
     *  (son scope est annulé), et ce qui s'est dit en cabine n'a pas à
     *  survivre à la sortie. L'instance existe même fermée : l'indicateur du
     *  haut lit ses flux en permanence.
     *
     *  ⚠ **Un flux, et non un état de composition.** Ce qui suit les messages
     *  pour prévenir doit continuer de tourner quand l'application n'est plus à
     *  l'écran — et là, précisément, Compose ne recompose plus. Un
     *  `mutableStateOf` ne serait plus observable au seul moment qui compte. */
    private val _engine = MutableStateFlow(ChatEngine(context))
    val engine: StateFlow<ChatEngine> = _engine.asStateFlow()
    val chat: ChatEngine get() = _engine.value

    /**
     * ⚠ **Ce qui prévient ne peut pas vivre dans la composition, et c'est le
     * défaut que l'appareil a montré.**
     *
     * Le compte des non-lus se faisait à l'écran, et la notification tombait
     * dans un effet qui le surveillait. Elle n'est **jamais partie** : quand
     * l'activité s'arrête, le recomposeur se met en pause, donc le compte ne
     * change plus, donc l'effet ne se rejoue pas — et le seul moment où une
     * notification a un sens est exactement celui-là. Le message arrivait bien
     * (le moteur, lui, ne s'arrête pas), la pastille était juste au retour, et
     * la barre d'état était restée muette. Vu sur le Pixel le 19/08, tablette
     * en main.
     *
     * Tout ce qui décide de prévenir vit donc ici, sur des flux, dans le scope
     * du ViewModel : il survit à l'arrêt de l'activité, comme le moteur.
     */
    private val notifier = UnreadNotifier(context)

    /**
     * Quand chaque fil a été regardé pour la dernière fois.
     *
     * ⚠ Ces marques vivaient elles aussi à l'écran. Elles descendent ici pour
     * la même raison : sans elles, le compte des non-lus n'est pas calculable
     * hors composition. Elles ne se gardent pas d'une session à l'autre — ce
     * qui ne survit pas n'a pas de non-lus à survivre.
     */
    private val _readAt = MutableStateFlow(emptyMap<String, Long>())

    /** L'application est-elle sous les yeux ? Posée par l'activité, pas par la
     *  composition — voir [one.astroport.atom4love.MainActivity]. */
    private val _foreground = MutableStateFlow(true)

    /**
     * Ce qui attend d'être lu, **et de qui**.
     *
     * ⚠ Le compte seul ne suffit plus. La barre d'état disait « n messages vous
     * attendent » et rien d'autre : c'était une position de prudence — une
     * notification se lit par-dessus une épaule — et Florent l'a tranchée
     * autrement le 19/08. Un bandeau qui ne nomme personne oblige à ouvrir
     * l'application pour savoir s'il faut ouvrir l'application.
     *
     * Le nom est celui de [Conversations], donc **la règle des homonymes est
     * déjà appliquée** : deux « Marie » à portée reparaissent chacune avec la
     * queue de sa clé, ici comme dans la liste.
     */
    private val _unread = MutableStateFlow(Unread())
    val unread: StateFlow<Unread> = _unread.asStateFlow()

    private val _unreadByPeer = MutableStateFlow(emptyMap<String, Int>())

    /**
     * Le même compte, **fil par fil** — par clé publique hexadécimale.
     *
     * ⚠ La pastille de l'onglet disait « 3 » et aucune ligne de la liste ne
     * disait lesquelles : il fallait ouvrir les conversations une à une pour
     * retrouver les trois messages. Il vient de la **même** lecture que
     * [unread], à la même émission ([Conversations.unreadByPeer]) : deux
     * comptes calculés séparément finiraient par se contredire, et c'est
     * toujours la pastille qu'on croirait.
     *
     * Il vit ici pour la raison qui a fait descendre tout le reste :
     * l'écran ne recompose pas quand il n'est pas à l'écran.
     */
    val unreadByPeer: StateFlow<Map<String, Int>> = _unreadByPeer.asStateFlow()

    /**
     * **Les arrivées, une par une.**
     *
     * Un état ne suffit pas pour ça : deux messages du même correspondant
     * portent le même compte une fois le premier lu, et un bandeau qui écoute
     * un état ne saurait pas qu'il s'est passé quelque chose une deuxième fois.
     * Un flux d'évènements dit « il vient d'arriver ceci », ce qui est
     * exactement ce qu'un bandeau montre.
     */
    private val _arrivals = MutableSharedFlow<Unread>(extraBufferCapacity = 8)
    val arrivals: SharedFlow<Unread> = _arrivals

    /**
     * Ce qu'un bandeau a besoin de savoir : combien, de qui, quoi, et où aller.
     *
     * [from] est null quand la personne ne s'est pas nommée — c'est à l'écran
     * de choisir le mot, dans sa langue.
     */
    data class Unread(
        val count: Int = 0,
        val from: String? = null,
        val extract: String = "",
        val peerHex: String? = null,
    )

    var open by mutableStateOf(false)
        private set

    init {
        // Un groupe Wi-Fi Direct survit à l'application qui l'a formé : fermer
        // la cabine le referme, mais un balayage dans les récents, un crash ou
        // une mort par mémoire ne passent par aucun stop(). On le ramasse une
        // fois, ici, avant qu'une cabine puisse rouvrir.
        viewModelScope.launch { P2pGroup(context).reclaim() }
        // Les pièces jointes sortent par les mêmes portes dérobées : `stop()`
        // les efface, et aucune de ces trois morts-là ne l'appelle. Même
        // ramassage, même endroit — rien n'est encore ouvert à cet instant,
        // donc il n'y a aucun transfert en cours à emporter.
        viewModelScope.launch(Dispatchers.IO) { Attachments.wipe(context) }
        notifier.ensureChannel()
        watchUnread()
    }

    /** Le fil est regardé maintenant : tout ce qu'il porte est lu. */
    fun markRead(peerHex: String, atMs: Long = System.currentTimeMillis()) {
        _readAt.value = _readAt.value + (peerHex to atMs)
    }

    /** L'activité entre ou sort de l'écran. */
    fun foreground(visible: Boolean) {
        _foreground.value = visible
    }

    /**
     * ⚠ La notification **suit la pastille**, elle ne la double pas : tant
     * qu'il reste quelque chose à lire elle tient, et elle tombe à la lecture.
     * Revenir à l'application sans lire ne l'efface donc pas — le message
     * attend toujours, et c'est ce qu'elle dit. Elle ne se pose en revanche
     * jamais pendant qu'on regarde l'écran : un bandeau y dit déjà la même
     * chose, et faire sonner un téléphone qu'on tient en main est une faute.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchUnread() = viewModelScope.launch {
        // `flatMapLatest` et non un simple `chat.messages` : fermer installe un
        // moteur NEUF, et un collecteur accroché à l'ancien ne verrait plus rien.
        // Les pairs viennent avec, parce que c'est d'eux que sortent les pseudos.
        engine.flatMapLatest { engine ->
            combine(engine.peers, engine.messages) { peers, messages -> peers to messages }
        }
            .combine(_readAt) { (peers, messages), reads -> summarise(peers, messages, reads) }
            .distinctUntilChanged()
            .combine(_foreground) { tally, visible -> tally to visible }
            .collect { (tally, visible) ->
                val unread = tally.unread
                announce(unread)
                _unread.value = unread
                _unreadByPeer.value = tally.byPeer
                when {
                    unread.count == 0 -> notifier.clear()
                    !visible -> notifier.waiting(unread)
                }
            }
    }

    /**
     * Une lecture des messages en attente, sous les deux formes dont l'écran a
     * besoin : ce qu'un bandeau montre, et ce que chaque ligne de la liste
     * porte. Une seule lecture, donc jamais deux comptes qui divergent.
     */
    private data class Tally(val unread: Unread, val byPeer: Map<String, Int>)

    /**
     * L'identifiant du dernier message entrant déjà annoncé.
     *
     * ⚠ Sans cette mémoire, revenir de l'arrière-plan ou marquer un fil comme
     * lu ferait resurgir un bandeau pour un message vieux de dix minutes : le
     * flux réémet à chaque changement, et seul un identifiant dit ce qui est
     * vraiment neuf. `-1` et non `0` : le moteur numérote à partir de zéro.
     */
    private var announced = -1

    /**
     * ⚠ **L'ensemencement se fait à la première ÉMISSION, pas au premier
     * message.** Écrit d'abord comme « le premier identifiant vu ne crie pas »,
     * il avalait la première arrivée réelle : au démarrage il n'y a aucun
     * message entrant, donc rien à ensemencer, et c'est le message suivant —
     * le premier vrai — qui héritait du silence. Vu sur le Pixel le 19/08.
     */
    private var seeded = false

    private fun announce(unread: Unread) {
        val id = lastIncomingId
        // Le premier passage porte l'état trouvé au démarrage : des messages
        // déjà là, arrivés avant que quiconque regarde. Même règle que le
        // journal de bord — la première observation ensemence en silence.
        if (!seeded) {
            seeded = true
            announced = id
            return
        }
        if (id <= announced) return
        // ⚠ **Un message entrant naît vide.** Il est rangé dès l'entête, puis
        // complété quand son texte a fini d'arriver : le flux émet donc DEUX
        // fois, et la première n'a rien à montrer. Le bandeau affichait un nom
        // suivi d'une ligne blanche — vu sur le Pixel le 19/08. On laisse
        // passer l'émission creuse sans marquer l'identifiant : la suivante,
        // qui porte le texte, sera bien la première annoncée.
        if (unread.extract.isEmpty()) return
        announced = id
        if (unread.count > 0) _arrivals.tryEmit(unread)
    }

    private var lastIncomingId = -1

    /**
     * Ce qui attend d'être lu — le total et **de qui vient le plus récent** pour
     * le bandeau, le détail **fil par fil** pour la liste.
     *
     * La règle de ce qui compte (et surtout de ce qui ne compte pas : les
     * messages sans correspondant) vit dans [Conversations.unread], avec les
     * fils eux-mêmes, et s'éprouve donc sans appareil.
     */
    private fun summarise(
        peers: List<ChatEngine.Peer>,
        messages: List<ChatMessage>,
        reads: Map<String, Long>,
    ): Tally {
        val waiting = Conversations.unread(messages, reads)
        lastIncomingId = messages.lastOrNull { !it.mine && it.peer != null }?.id ?: -1
        // Le détail vient de la même définition que le total, et non d'un
        // regroupement refait ici : c'est ce qui garantit que la somme des
        // lignes est exactement ce que dit la pastille.
        val byPeer = Conversations.unreadByPeer(messages, reads)
        val last = waiting.lastOrNull() ?: return Tally(Unread(), emptyMap())
        // Le nom passe par les fils : c'est là que vit la règle des homonymes.
        val name = Conversations.of(peers, messages)
            .firstOrNull { it.peerHex == last.peer }?.name
        return Tally(Unread(waiting.size, name, extract(last), last.peer), byPeer)
    }

    /** Ce qu'on montre d'un message dans un bandeau — jamais une pièce jointe. */
    private fun extract(message: ChatMessage): String = when (message.kind) {
        ChatKind.TEXT -> message.text
        ChatKind.IMAGE -> context.getString(R.string.notify_image)
        ChatKind.FILE -> message.name.ifEmpty { context.getString(R.string.notify_file) }
    }

    fun open(keys: NostrKeys?) {
        if (open) return
        // l'identité avant l'ouverture des liens : un handshake déjà engagé
        // garderait la clé de fortune
        keys?.let { chat.bindIdentity(it) }
        chat.start()
        open = true
    }

    fun close() {
        if (!open) return
        open = false
        chat.stop()
        _engine.value = ChatEngine(context)
        // Plus un message, donc plus rien à avoir lu : garder les marques
        // ferait vieillir des dates sans objet, et le prochain qui parle
        // arriverait « déjà lu » s'il tombait dans la même milliseconde.
        _readAt.value = emptyMap()
        _unreadByPeer.value = emptyMap()
    }

    /** L'activité s'en va pour de bon (et non pour se recréer). */
    override fun onCleared() {
        if (open) chat.stop()
    }
}
