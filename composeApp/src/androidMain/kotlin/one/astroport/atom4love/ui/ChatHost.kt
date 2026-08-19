package one.astroport.atom4love.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.chat.ChatEngine
import one.astroport.atom4love.chat.ChatMessage
import one.astroport.atom4love.chat.UnreadNotifier
import one.astroport.atom4love.chat.net.P2pGroup
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
    val readAt: StateFlow<Map<String, Long>> = _readAt.asStateFlow()

    /** L'application est-elle sous les yeux ? Posée par l'activité, pas par la
     *  composition — voir [one.astroport.atom4love.MainActivity]. */
    private val _foreground = MutableStateFlow(true)

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
        engine.flatMapLatest { it.messages }
            .combine(_readAt) { messages, reads -> unread(messages, reads) }
            .combine(_foreground) { count, visible -> count to visible }
            .distinctUntilChanged()
            .collect { (count, visible) ->
                when {
                    count == 0 -> notifier.clear()
                    !visible -> notifier.waiting(count)
                }
            }
    }

    /**
     * Ce qui attend d'être lu, tous fils confondus.
     *
     * ⚠ Les messages **sans correspondant** ne comptent pas : ils viennent d'un
     * lien que personne n'a signé, donc d'un appareil qu'on ne sait pas nommer,
     * et [one.astroport.atom4love.chat.Conversations] ne leur donne aucun fil.
     * Les compter ferait une pastille qu'aucun écran ne peut faire retomber.
     */
    private fun unread(messages: List<ChatMessage>, reads: Map<String, Long>): Int =
        messages.count { message ->
            val peer = message.peer
            !message.mine && peer != null && message.atMs > (reads[peer] ?: 0L)
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
    }

    /** L'activité s'en va pour de bon (et non pour se recréer). */
    override fun onCleared() {
        if (open) chat.stop()
    }
}
