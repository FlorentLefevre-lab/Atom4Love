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
        val firstSeenMillis: Long,
        val lastSeenMillis: Long,
    )

    private val byAddress = LinkedHashMap<String, Neighbor>()
    private val _neighbors = MutableStateFlow<List<Neighbor>>(emptyList())
    val neighbors: StateFlow<List<Neighbor>> = _neighbors.asStateFlow()

    fun report(address: String, cell4d: Long?, token: Int?, rssi: Int) {
        val now = clock()
        synchronized(byAddress) {
            val previous = byAddress[address]
            byAddress[address] = Neighbor(
                address = address,
                cell4d = cell4d,
                token = token,
                rssi = rssi,
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