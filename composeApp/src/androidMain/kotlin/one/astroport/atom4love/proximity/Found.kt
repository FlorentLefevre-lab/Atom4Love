package one.astroport.atom4love.proximity

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * **Les rencontres abouties** — qui a trouvé qui, dans cette salle.
 *
 * Demandé par Florent le 20/08 : un bouton « J'ai trouvé la personne ! » qui
 * dise l'évènement **aux autres**, dans leur journal et sur l'état de leurs
 * cartes.
 *
 * ## Pourquoi ça traverse la maille
 *
 * Le reste du jeu est privé par construction : une lanterne ne se voit qu'à
 * deux, une conversation ne concerne que deux. Une **rencontre**, elle, est un
 * fait de la salle — c'est ce que tout le monde voit quand deux personnes se
 * lèvent et se serrent la main. La dire est donc légitime, et elle est même la
 * seule bonne nouvelle que ce jeu produise.
 *
 * ⚠ **Celui qui touche le bouton se déclare, et déclare l'autre.** C'est un pas
 * de plus que la carte « vous cherche » du 19/08, qui n'engageait que celui qui
 * cherchait. On l'assume parce que le geste vient APRÈS la rencontre : les deux
 * se sont vus, et l'un raconte ce que la salle a déjà vu.
 *
 * ## Ce qu'elle garde
 *
 * Des **jetons de présence**, pas des noms : qui sait rapprocher un jeton d'une
 * carte nomme la personne lui-même, les autres voient passer une rencontre sans
 * savoir qui. Et rien ne descend sur le disque — mémoire de processus, comme
 * les visages.
 */
object Found {

    /** Une rencontre, telle qu'elle arrive. */
    data class Meeting(val finder: Int, val found: Int, val atMs: Long = System.currentTimeMillis())

    private val _pairs = MutableStateFlow<Set<Meeting>>(emptySet())

    /** Toutes les rencontres connues de la session. */
    val pairs: StateFlow<Set<Meeting>> = _pairs.asStateFlow()

    private val _arrivals = MutableSharedFlow<Meeting>(extraBufferCapacity = 16)

    /** Chaque rencontre **neuve**, une fois — de quoi écrire une ligne de journal. */
    val arrivals: SharedFlow<Meeting> = _arrivals.asSharedFlow()

    /** Les jetons impliqués dans une rencontre, pour l'état des cartes. */
    fun involves(token: Int?): Boolean =
        token != null && _pairs.value.any { it.finder == token || it.found == token }

    /** Rend `true` si la rencontre est neuve — sinon on ne redit rien. */
    fun record(finder: Int, found: Int): Boolean {
        val known = _pairs.value.any {
            (it.finder == finder && it.found == found) || (it.finder == found && it.found == finder)
        }
        if (known) return false
        val meeting = Meeting(finder, found)
        _pairs.update { it + meeting }
        _arrivals.tryEmit(meeting)
        return true
    }

    /** La station oublie tout. */
    fun clear() {
        _pairs.value = emptySet()
    }
}
