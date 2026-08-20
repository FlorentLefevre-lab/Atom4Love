package one.astroport.atom4love.proximity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les noyaux voisins vus au scan BLE, avec éviction par TTL.
 *
 * Android randomise l'adresse MAC d'annonce (rotation ~15 min) : un même appareil
 * réapparaît donc périodiquement sous une nouvelle identité radio. Le TTL court
 * fait le ménage de ces fantômes ; l'identité stable viendra du payload (npub…),
 * jamais de l'adresse.
 */
class NeighborRegistry(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    companion object {
        /** ~3 intervalles d'annonce en mode BALANCED manqués avant d'évincer. */
        const val DEFAULT_TTL_MILLIS = 30_000L

        // ⚠ **`countIn` a vécu ici et n'y est plus.** Il comptait les voisins
        // annonçant notre cellule, pour le carreau ⛩️ du Plateau. Retiré le
        // 20/08 avec ce carreau : un portail fait 900 m de large et le BLE
        // porte à 7 m — le compte voyait deux dix-millièmes du lieu qu'il
        // prétendait mesurer, différait d'un écran à l'autre (la détection
        // n'est pas symétrique) et tombait à zéro dès que les liens de
        // causerie tenaient. Le carreau dit désormais QUEL portail.
        //
        // Ne pas le réécrire : compter les gens d'un lieu suppose que chacun
        // s'y déclare, ce qui est le travail du relais, pas du scan.

    }

    data class Neighbor(
        /** Adresse radio du moment — instable par construction, cf. doc de classe. */
        val address: String,
        /** Adresse 4D annoncée (null = le pair n'a pas résolu sa propre cellule). */
        val cell4d: Long?,
        /**
         * Jeton de présence ([ProximityPayload.token]) : ce qui reste le même
         * quand l'adresse change. null quand le pair ne l'annonce pas encore —
         * il compte alors pour lui-même, faute de savoir le regrouper.
         */
        val token: Int?,
        /** Dernier RSSI **brut** en dBm, tel que la radio l'a rendu. */
        val rssi: Int,
        /**
         * Le RSSI **lissé**, et c'est celui qu'il faut lire pour dire une
         * distance ou une chaleur.
         *
         * Mesuré le 15/08, deux appareils immobiles sur une table : 43 relevés,
         * de −79 à −61 dBm — **18 dB d'amplitude sans que rien ne bouge**, pour
         * un écart-type de 4,2 dB. Traduit en distance (n = 2,7), le même couple
         * immobile « se déplaçait » d'un facteur 4,6. Avec les quatre seuils de
         * chaleur d'alors, l'état changeait 20 fois sur 42 relevés.
         *
         * Une médiane sur cinq relevés tue les évanouissements profonds — ce
         * −79 isolé, un trajet multiple qui s'annule — que la moyenne se
         * contenterait d'étaler. Une exponentielle par-dessus rend la marche
         * lisible : α = 0,3 à un relevé par seconde, soit environ trois
         * secondes de mémoire.
         */
        val rssiSmoothed: Int,
        /**
         * La puissance d'émission que **le pair** annonce, en dBm — le champ
         * normalisé du Bluetooth (AD 0x0A), lu par `ScanResult.getTxPower()`.
         *
         * Null quand il ne l'annonce pas : un appareil plus ancien, ou une pile
         * qui n'a pas eu la place de l'ajouter. La distance retombe alors sur
         * l'étalonnage d'un couple connu, ce qui vaut pour ce couple-là et pour
         * lui seul.
         *
         * ⚠ C'est **la moitié du problème**, pas sa solution. Elle corrige ce
         * que l'émetteur a mis dans l'air ; ce que le récepteur en entend
         * dépend encore de son antenne et de sa sensibilité, et rien dans le
         * BLE ne les annonce.
         */
        val txPowerDbm: Int?,
        /**
         * Ce que le pair dit de lui sans se nommer — polarité, sceau, phase.
         * Vide pour un pair resté à une version antérieure de l'annonce : il
         * reste un voisin, simplement sans résonance calculable.
         */
        val signature: ProximityPayload.Signature = ProximityPayload.Signature.Unknown,
        val firstSeenMillis: Long,
        val lastSeenMillis: Long,
    ) {
        /**
         * De quoi reconnaître deux annonces d'une même personne, du plus sûr au
         * plus faible.
         *
         * Le jeton d'abord — mais il se dérive du noyau **et** de la cellule :
         * localisation coupée, pas de cellule, pas de jeton. Un appareil dans ce
         * cas se présentait alors sous chacune de ses adresses, qui tournent
         * toutes les 20 à 40 s pendant que les anciennes finissent leur TTL :
         * trois lignes de résonance pour un seul voisin, mesuré le 2026-08-13.
         *
         * La signature prend le relais. Elle ne dit pas qui, et ne révèle rien
         * de plus que ce qui est déjà dans l'air ; mais une phase sur 16 bits
         * croisée d'un sceau et d'une polarité ne se confond pas entre deux
         * inconnus tenant dans la même portée BLE.
         *
         * Faute des deux, l'adresse : compter quelqu'un deux fois vaut mieux que
         * de le faire disparaître.
         */
        val identity: String
            get() = when {
                token != null -> "t:$token"
                signature != ProximityPayload.Signature.Unknown ->
                    "s:${signature.sex}/${signature.glyph}/${signature.phase}"
                else -> "a:$address"
            }
    }

    private val byAddress = LinkedHashMap<String, Neighbor>()

    /** Les cinq derniers relevés bruts par adresse — la fenêtre de la médiane. */
    private val window = HashMap<String, ArrayDeque<Int>>()

    /** Facteur de l'exponentielle : trois secondes de mémoire à 1 Hz. */
    private val alpha = 0.3

    /** Nombre de relevés dans la fenêtre de la médiane. Impair, pour trancher. */
    private val windowSize = 5
    private val _neighbors = MutableStateFlow<List<Neighbor>>(emptyList())
    val neighbors: StateFlow<List<Neighbor>> = _neighbors.asStateFlow()

    /**
     * Les jetons de ceux qui **nous** cherchent — cf. [SeekingPayload].
     *
     * Un ensemble de jetons et rien d'autre : de quoi marquer une carte dans la
     * main, jamais de quoi savoir qui cherche qui d'autre dans la salle.
     *
     * ⚠ Ils se périment comme les voisins. Quelqu'un qui a fermé sa lanterne
     * arrête d'annoncer, et sa marque doit disparaître de l'écran d'en face —
     * sinon on irait chercher quelqu'un qui a rangé son téléphone.
     */
    private val seekersSeen = HashMap<Int, Long>()
    private val _seekers = MutableStateFlow<Set<Int>>(emptySet())
    val seekers: StateFlow<Set<Int>> = _seekers.asStateFlow()

    fun reportSeeker(token: Int) {
        synchronized(byAddress) {
            seekersSeen[token] = clock()
            publishSeekersLocked(clock())
        }
    }

    fun report(
        address: String,
        cell4d: Long?,
        token: Int?,
        rssi: Int,
        signature: ProximityPayload.Signature = ProximityPayload.Signature.Unknown,
        txPowerDbm: Int? = null,
    ) {
        val now = clock()
        synchronized(byAddress) {
            val previous = byAddress[address]
            // Médiane des cinq derniers, puis exponentielle sur le résultat.
            val recent = window.getOrPut(address) { ArrayDeque() }
            recent.addLast(rssi)
            while (recent.size > windowSize) recent.removeFirst()
            val median = recent.sorted()[recent.size / 2]
            val smoothed = previous?.rssiSmoothed
                ?.let { it + alpha * (median - it) }
                ?: median.toDouble()
            byAddress[address] = Neighbor(
                address = address,
                cell4d = cell4d,
                token = token,
                rssi = rssi,
                rssiSmoothed = Math.round(smoothed).toInt(),
                // Elle ne change pas d'un relevé à l'autre ; on garde la
                // dernière connue plutôt que de retomber à null sur une trame
                // où la pile ne l'aurait pas remontée.
                txPowerDbm = txPowerDbm ?: previous?.txPowerDbm,
                signature = signature,
                firstSeenMillis = previous?.firstSeenMillis ?: now,
                lastSeenMillis = now,
            )
            publishLocked(now)
        }
    }


    /** Évince les noyaux plus revus depuis [ttlMillis]. À appeler périodiquement. */
    fun sweep() {
        synchronized(byAddress) {
            val now = clock()
            publishLocked(now)
            publishSeekersLocked(now)
        }
    }

    fun clear() {
        synchronized(byAddress) {
            byAddress.clear()
            window.clear()
            seekersSeen.clear()
            _neighbors.value = emptyList()
            _seekers.value = emptySet()
        }
    }

    private fun publishLocked(now: Long) {
        byAddress.values.removeAll { now - it.lastSeenMillis > ttlMillis }
        _neighbors.value = byAddress.values.toList()
    }

    private fun publishSeekersLocked(now: Long) {
        seekersSeen.values.removeAll { now - it > ttlMillis }
        _seekers.value = seekersSeen.keys.toSet()
    }
}