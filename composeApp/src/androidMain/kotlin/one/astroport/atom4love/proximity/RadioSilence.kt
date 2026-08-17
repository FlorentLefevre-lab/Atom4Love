package one.astroport.atom4love.proximity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Une demande de silence sur l'antenne 2,4 GHz, adressée à la balise.
 *
 * La balise et la cabine ne se connaissent pas et n'ont aucune raison de se
 * connaître — mais elles partagent la même puce. Mesuré au banc le 2026-08-11 :
 * une activité Bluetooth soutenue fait tomber un transfert Wi-Fi de 2,2 Mo/s à
 * 100 Ko/s, l'arbitre de coexistence donnant le temps d'antenne au Bluetooth.
 * La cabine sait déjà endormir SES propres liens ; il lui manquait de quoi
 * demander la même chose à une balise qui, elle, tourne en permanence.
 *
 * D'où ce point de rendez-vous : la cabine [request]e le silence le temps d'un
 * transfert, la balise l'observe et coupe annonce et scan. Personne n'importe
 * l'autre.
 */
object RadioSilence {

    private val _requested = MutableStateFlow(false)

    /** Vrai quand quelqu'un a besoin de l'antenne pour lui seul. */
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    /**
     * Un seul demandeur pour l'instant (la cabine, qui sérialise ses transferts
     * sur un fil unique). Le jour où il y en aura deux, il faudra compter les
     * demandes plutôt que porter un booléen.
     */
    fun request(silence: Boolean) {
        _requested.value = silence
    }
}
