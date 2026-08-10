package one.astroport.atom4love.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeighborRegistryTest {

    private var now = 0L
    private val registry = NeighborRegistry(ttlMillis = 30_000, clock = { now })

    @Test
    fun `un rapport répété garde firstSeen et rafraîchit le reste`() {
        registry.report("AA:BB", cell4d = 1L, rssi = -40)
        now = 10_000
        registry.report("AA:BB", cell4d = 2L, rssi = -55)

        val neighbor = registry.neighbors.value.single()
        assertEquals(0L, neighbor.firstSeenMillis)
        assertEquals(10_000L, neighbor.lastSeenMillis)
        assertEquals(2L, neighbor.cell4d)
        assertEquals(-55, neighbor.rssi)
    }

    @Test
    fun `le TTL évince les noyaux plus revus`() {
        registry.report("AA:BB", cell4d = 1L, rssi = -40)
        now = 10_000
        registry.report("CC:DD", cell4d = null, rssi = -70)

        now = 31_000 // AA:BB dépasse le TTL, CC:DD non
        registry.sweep()
        assertEquals(listOf("CC:DD"), registry.neighbors.value.map { it.address })

        now = 41_000
        registry.sweep()
        assertTrue(registry.neighbors.value.isEmpty())
    }

    @Test
    fun `clear vide tout, y compris le flux`() {
        registry.report("AA:BB", cell4d = 1L, rssi = -40)
        registry.clear()
        assertTrue(registry.neighbors.value.isEmpty())
    }
}
