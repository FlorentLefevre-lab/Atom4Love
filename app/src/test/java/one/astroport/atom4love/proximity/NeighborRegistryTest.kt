package one.astroport.atom4love.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeighborRegistryTest {

    private var now = 0L
    private val registry = NeighborRegistry(ttlMillis = 30_000, clock = { now })

    @Test
    fun `un rapport répété garde firstSeen et rafraîchit le reste`() {
        registry.report("AA:BB", cell4d = 1L, token = null, rssi = -40)
        now = 10_000
        registry.report("AA:BB", cell4d = 2L, token = null, rssi = -55)

        val neighbor = registry.neighbors.value.single()
        assertEquals(0L, neighbor.firstSeenMillis)
        assertEquals(10_000L, neighbor.lastSeenMillis)
        assertEquals(2L, neighbor.cell4d)
        assertEquals(-55, neighbor.rssi)
    }

    @Test
    fun `le TTL évince les noyaux plus revus`() {
        registry.report("AA:BB", cell4d = 1L, token = null, rssi = -40)
        now = 10_000
        registry.report("CC:DD", cell4d = null, token = null, rssi = -70)

        now = 31_000 // AA:BB dépasse le TTL, CC:DD non
        registry.sweep()
        assertEquals(listOf("CC:DD"), registry.neighbors.value.map { it.address })

        now = 41_000
        registry.sweep()
        assertTrue(registry.neighbors.value.isEmpty())
    }

    @Test
    fun `clear vide tout, y compris le flux`() {
        registry.report("AA:BB", cell4d = 1L, token = null, rssi = -40)
        registry.clear()
        assertTrue(registry.neighbors.value.isEmpty())
    }

    /** Le cas mesuré le 2026-08-12 : un appareil dont l'adresse tourne pendant
     *  que l'ancienne survit à son TTL comptait pour deux. */
    @Test
    fun `deux adresses d'un meme jeton ne comptent qu'une personne`() {
        registry.report("AA:BB", cell4d = 7L, token = 42, rssi = -60)
        now = 5_000
        registry.report("CC:DD", cell4d = 7L, token = 42, rssi = -62)

        assertEquals(2, registry.neighbors.value.size)
        assertEquals(1, NeighborRegistry.countIn(registry.neighbors.value, 7L))
    }

    @Test
    fun `deux jetons distincts comptent deux personnes`() {
        registry.report("AA:BB", cell4d = 7L, token = 42, rssi = -60)
        registry.report("CC:DD", cell4d = 7L, token = 43, rssi = -62)
        assertEquals(2, NeighborRegistry.countIn(registry.neighbors.value, 7L))
    }

    /** Un pair sans jeton (version antérieure) compte pour lui-même : mieux
     *  vaut le compter deux fois que le faire disparaître. */
    @Test
    fun `sans jeton on retombe sur l'adresse`() {
        registry.report("AA:BB", cell4d = 7L, token = null, rssi = -60)
        registry.report("CC:DD", cell4d = 7L, token = null, rssi = -62)
        assertEquals(2, NeighborRegistry.countIn(registry.neighbors.value, 7L))
    }

    /** La signature traverse le registre : c'est elle que le Radar affiche. */
    @Test
    fun `la signature du pair arrive jusqu'au registre`() {
        val signature = ProximityPayload.Signature(sex = 1, glyph = 2, phase = 4.85)
        registry.report("AA:BB", cell4d = 7L, token = 42, rssi = -60, signature = signature)
        assertEquals(signature, registry.neighbors.value.single().signature)
    }

    /**
     * Le cas mesuré le 13/08 : un pair sans localisation n'a pas de cellule,
     * donc pas de jeton. Ses adresses tournent et il s'affichait trois fois.
     * La signature suffit à le reconnaître.
     */
    @Test
    fun `sans jeton, la signature regroupe les adresses d'un meme voisin`() {
        val signature = ProximityPayload.Signature(sex = 0, glyph = 2, phase = 4.852)
        registry.report("AA:BB", cell4d = null, token = null, rssi = -60, signature = signature)
        registry.report("CC:DD", cell4d = null, token = null, rssi = -62, signature = signature)
        registry.report("EE:FF", cell4d = null, token = null, rssi = -58, signature = signature)
        assertEquals(3, registry.neighbors.value.size)
        assertEquals(1, registry.neighbors.value.distinctBy { it.identity }.size)
    }

    @Test
    fun `deux signatures distinctes restent deux voisins`() {
        registry.report(
            "AA:BB", null, null, -60, ProximityPayload.Signature(0, 2, 4.852),
        )
        registry.report(
            "CC:DD", null, null, -62, ProximityPayload.Signature(1, 11, 1.220),
        )
        assertEquals(2, registry.neighbors.value.distinctBy { it.identity }.size)
    }

    /** Le jeton passe avant la signature : c'est lui qui tient quand elle bouge. */
    @Test
    fun `le jeton prime sur la signature`() {
        registry.report("AA:BB", 7L, 42, -60, ProximityPayload.Signature(0, 2, 4.852))
        registry.report("CC:DD", 7L, 42, -62, ProximityPayload.Signature(1, 11, 1.220))
        assertEquals(1, registry.neighbors.value.distinctBy { it.identity }.size)
    }

    /** Sans jeton ni signature, on retombe sur l'adresse — deux voisins comptés. */
    @Test
    fun `sans rien, l'adresse fait foi`() {
        registry.report("AA:BB", cell4d = null, token = null, rssi = -60)
        registry.report("CC:DD", cell4d = null, token = null, rssi = -62)
        assertEquals(2, registry.neighbors.value.distinctBy { it.identity }.size)
    }

    /** Un pair d'une version antérieure reste un voisin, simplement sans signature. */
    @Test
    fun `sans signature le voisin existe quand meme`() {
        registry.report("AA:BB", cell4d = 7L, token = 42, rssi = -60)
        val neighbor = registry.neighbors.value.single()
        assertEquals(ProximityPayload.Signature.Unknown, neighbor.signature)
        assertEquals(1, NeighborRegistry.countIn(registry.neighbors.value, 7L))
    }

    @Test
    fun `une autre cellule ne compte pas, et la cellule inconnue non plus`() {
        registry.report("AA:BB", cell4d = 7L, token = 42, rssi = -60)
        registry.report("CC:DD", cell4d = 8L, token = 43, rssi = -62)
        registry.report("EE:FF", cell4d = null, token = null, rssi = -70)
        assertEquals(1, NeighborRegistry.countIn(registry.neighbors.value, 7L))
        assertEquals(0, NeighborRegistry.countIn(registry.neighbors.value, null))
    }
}
