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
    }

    data class Neighbor(
        /** Adresse radio du moment — instable par construction, cf. doc de classe. */
        val address: String,
        /** Adresse 4D annoncée (null = le pair n'a pas résolu sa propre cellule). */
        val cell4d: Long?,
        /** Dernier RSSI en dBm — la matière première du futur test de portée. */
        val rssi: Int,
        val firstSeenMillis: Long,
        val lastSeenMillis: Long,
    )

    private val byAddress = LinkedHashMap<String, Neighbor>()
    private val _neighbors = MutableStateFlow<List<Neighbor>>(emptyList())
    val neighbors: StateFlow<List<Neighbor>> = _neighbors.asStateFlow()

    fun report(address: String, cell4d: Long?, rssi: Int) {
        val now = clock()
        synchronized(byAddress) {
            val previous = byAddress[address]
            byAddress[address] = Neighbor(
                address = address,
                cell4d = cell4d,
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