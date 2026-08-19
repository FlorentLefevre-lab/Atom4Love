package one.astroport.atom4love.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import one.astroport.atom4love.chat.ChatEngine
import one.astroport.atom4love.data.Pseudo
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.nostr.Hex
import one.astroport.atom4love.proximity.NeighborRegistry
import one.astroport.atom4love.proximity.ProximityPayload
import one.astroport.atom4love.proximity.ProximityService
import one.astroport.atom4love.proximity.SeekingBeacon

/**
 * Ce qui remplit le [Journal] — **en observant, jamais en instrumentant**.
 *
 * ⚠ C'est la décision de conception de tout ce fichier, et elle mérite d'être
 * défendue. La façon évidente d'écrire un journal est d'appeler `record()` là où
 * les choses arrivent : dans le moteur de la balise, dans celui du chat, dans
 * l'antenne. C'est ce qu'on ne fait pas. Ces trois moteurs sont éprouvés, l'un
 * d'eux tourne sur trois fils et a coûté des soirées de banc — y semer des
 * appels d'affichage revient à faire dépendre du code radio d'une fenêtre qu'on
 * peut fermer, et à devoir relire ce code entier le jour où le journal change
 * d'avis sur ce qu'il montre.
 *
 * Tout ce dont le journal a besoin est **déjà publié** : la balise expose son
 * état, ses voisins et son adresse 4D, le chat ses pairs, l'antenne son statut.
 * Il ne manquait que quelqu'un pour regarder. Ce quelqu'un est ici, il ne
 * connaît aucun des moteurs, et le supprimer n'enlèverait pas une ligne de radio.
 *
 * ⚠ **Il ne retient rien lui-même.** La mémoire de ce qui a déjà été inscrit vit
 * dans [Journal], pas en `remember` : une rotation détruit la composition, et
 * une mémoire qui repartirait vide réinscrirait toutes les cartes à portée comme
 * si elles venaient d'arriver. Ce fichier ne fait que répéter ce qu'il voit ;
 * c'est le journal qui sait ce qu'il a déjà entendu.
 */
@Composable
fun JournalRecorder(chat: ChatEngine?, relayOnline: Boolean) {
    val beaconRunning by ProximityService.running.collectAsStateWithLifecycle()
    val cell4d by ProximityService.advertisedCell4d.collectAsStateWithLifecycle()
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
    val own by ProximityService.signature.collectAsStateWithLifecycle()
    val seekers by ProximityService.seekers.collectAsStateWithLifecycle()
    val sought by SeekingBeacon.targets.collectAsStateWithLifecycle()
    val peers by (chat?.peers ?: remember { MutableStateFlow(emptyList()) })
        .collectAsStateWithLifecycle()

    LaunchedEffect(beaconRunning) { Journal.noteBeacon(beaconRunning) }

    // L'adresse 4D passe de rien à une cellule quand la localisation est
    // accordée, et change quand on se déplace assez pour changer d'hexagone.
    // Les deux valent d'être vus : le premier explique le portail, le second dit
    // qu'on a bougé.
    LaunchedEffect(cell4d) { Journal.noteCell(cell4d) }

    LaunchedEffect(relayOnline) { Journal.noteRelay(relayOnline) }

    // ── Les cartes qui vont et viennent ───────────────────────────────────
    //
    // ⚠ **Seules les cartes signées.** Un voisin sans signature est une radio
    // qui passe : il compte dans le portail, il n'a rien montré, et l'inscrire
    // remplirait le journal de lignes qui ne disent rien de plus que « il y a du
    // monde ». Ce qui entre ici est ce qui se joue au Plateau.
    //
    // ⚠ Rangées par **jeton de présence** et non par adresse : une adresse BLE
    // tourne toutes les trente secondes, et suivre les adresses ferait paraître
    // puis disparaître la même personne six fois par minute.
    val signed = remember(neighbors, own) {
        neighbors
            .filter { it.signature != ProximityPayload.Signature.Unknown }
            .associate { card ->
                card.identity to Journal.Card(
                    glyph = card.signature.glyph,
                    percent = percentOf(own, card),
                )
            }
    }
    LaunchedEffect(signed) { Journal.noteCards(signed) }

    // ── Les rencontres ────────────────────────────────────────────────────
    //
    // ⚠ **Mutuelles, et rien d'autre.** `sought` est ce que NOUS cherchons,
    // `seekers` ce que d'autres déclarent chercher. L'intersection est le seul
    // ensemble où les deux consentements se rejoignent — et c'est exactement là
    // que les deux lanternes se mettent à battre du même rythme. Écrire l'un des
    // deux ensembles seul reviendrait à inscrire une intention à sens unique, ce
    // que ce projet refuse partout ailleurs.
    val mutual = remember(sought, seekers) { sought.toSet() intersect seekers }
    LaunchedEffect(mutual, neighbors) {
        Journal.noteMeetings(mutual) { token ->
            neighbors.firstOrNull { it.token == token }?.signature?.glyph
        }
    }

    // ── Les liens attestés ────────────────────────────────────────────────
    // ⚠ Les homonymes se séparent ici aussi, et par la même règle : deux
    // « Marie » qui rejoignent la radio à une minute d'intervalle produiraient
    // deux lignes identiques, et le journal deviendrait faux — il dirait que la
    // même personne est entrée deux fois.
    val present = remember(peers) {
        val labels = Pseudo.labels(peers.associate { it.npub to it.display })
        peers.associate { Hex.encode(it.nostrKey) to labels[it.npub] }
    }
    LaunchedEffect(present) { Journal.notePeers(present) }
}

/**
 * La part de résonance d'une carte, quand les deux phases sont connues.
 *
 * Le journal la porte parce qu'une carte sans son pourcentage n'est qu'un sceau
 * de plus dans une liste : c'est le nombre qui fait lever les yeux.
 */
private fun percentOf(
    own: ProximityPayload.Signature,
    card: NeighborRegistry.Neighbor,
): Int? {
    val mine = own.phase ?: return null
    val theirs = card.signature.phase ?: return null
    return Phi2X.classifyResonance(mine, theirs).percent
}
