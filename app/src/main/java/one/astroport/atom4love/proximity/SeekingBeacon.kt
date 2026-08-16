package one.astroport.atom4love.proximity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ce que la balise déclare chercher — le point de rendez-vous entre le Plateau
 * et la radio.
 *
 * Le Plateau ne connaît pas la radio et n'a aucune raison de la connaître : il
 * pose ici les jetons des cartes qu'on est parti chercher, et
 * [ProximityEngine] s'en sert pour allumer ou éteindre son annonce étendue.
 * Même arrangement que [RadioSilence], pour la même raison.
 *
 * ⚠ **Ça ne s'allume que par un geste.** Personne ne déclare chercher sans
 * avoir touché une carte : fermer la lanterne rend [targets] vide, et l'annonce
 * s'éteint au balayage suivant. Une déclaration qui survivrait au geste
 * continuerait de parler pour quelqu'un qui a rangé son téléphone.
 */
object SeekingBeacon {

    private val _targets = MutableStateFlow<List<Int>>(emptyList())

    /** Les jetons cherchés, au plus [SeekingPayload.MAX_TARGETS]. */
    val targets: StateFlow<List<Int>> = _targets.asStateFlow()

    fun seek(tokens: List<Int>) {
        _targets.value = tokens.distinct().take(SeekingPayload.MAX_TARGETS)
    }

    fun stop() {
        _targets.value = emptyList()
    }
}
