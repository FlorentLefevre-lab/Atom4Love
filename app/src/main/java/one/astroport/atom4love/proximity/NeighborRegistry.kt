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

        /**
         * Combien de **personnes** annoncent cette cellule — pas combien
         * d'adresses. Une adresse qui vient de tourner et l'ancienne, pas
         * encore évincée par le TTL, portent le même jeton et ne comptent
         * qu'une fois. Faute de jeton (pair d'une version antérieure), on
         * retombe sur l'adresse : mieux vaut compter quelqu'un deux fois que
         * de le faire disparaître.
         */
        fun countIn(neighbors: List<Neighbor>, cell4d: Long?): Int {
            if (cell4d == null) return 0
            return neighbors
                .filter { it.cell4d == cell4d }
                .distinctBy { it.token ?: it.address }
                .size
        }
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
        /** Dernier RSSI en dBm — la matière première du futur test de portée. */
        val rssi: Int,
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
    private val _neighbors = MutableStateFlow<List<Neighbor>>(emptyList())
    val neighbors: StateFlow<List<Neighbor>> = _neighbors.asStateFlow()

    fun report(
        address: String,
        cell4d: Long?,
        token: Int?,
        rssi: Int,
        signature: ProximityPayload.Signature = ProximityPayload.Signature.Unknown,
    ) {
        val now = clock()
        synchronized(byAddress) {
            val previous = byAddress[address]
            byAddress[address] = Neighbor(
                address = address,
                cell4d = cell4d,
                token = token,
                rssi = rssi,
                signature = signature,
                firstSeenMillis = previous?.firstSeenMillis ?: now,
                lastSeenMillis = now,
            )
            publishLocked(now)
        }
    }


    /** Évince les noyaux plus revus depuis [ttlMillis]. À appeler périodiquement. */
    fun sweep() {
        synchronized(byAddress) { publishLocked(clock()) }
    }

    fun clear() {
        synchronized(byAddress) {
            byAddress.clear()
            _neighbors.value = emptyList()
        }
    }

    private fun publishLocked(now: Long) {
        byAddress.values.removeAll { now - it.lastSeenMillis > ttlMillis }
        _neighbors.value = byAddress.values.toList()
    }
}