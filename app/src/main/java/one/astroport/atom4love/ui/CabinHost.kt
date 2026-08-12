package one.astroport.atom4love.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import one.astroport.atom4love.chat.CabinChat
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
class CabinHost(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    /** Une instance neuve à chaque ouverture : un moteur arrêté ne rallume pas
     *  (son scope est annulé), et ce qui s'est dit en cabine n'a pas à
     *  survivre à la sortie. L'instance existe même fermée : l'indicateur du
     *  haut lit ses flux en permanence. */
    var chat by mutableStateOf(CabinChat(context))
        private set

    var open by mutableStateOf(false)
        private set

    init {
        // Un groupe Wi-Fi Direct survit à l'application qui l'a formé : fermer
        // la cabine le referme, mais un balayage dans les récents, un crash ou
        // une mort par mémoire ne passent par aucun stop(). On le ramasse une
        // fois, ici, avant qu'une cabine puisse rouvrir.
        viewModelScope.launch { P2pGroup(context).reclaim() }
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
        chat = CabinChat(context)
    }

    /** L'activité s'en va pour de bon (et non pour se recréer). */
    override fun onCleared() {
        if (open) chat.stop()
    }
}
