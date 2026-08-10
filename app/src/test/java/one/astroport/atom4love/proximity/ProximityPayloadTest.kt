package one.astroport.atom4love.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le payload est un contrat entre appareils : tout octet mal placé casse la
 * détection croisée sans erreur visible — d'où l'aller-retour verrouillé ici.
 */
class ProximityPayloadTest {

    @Test
    fun `aller-retour d'une cellule H3 réelle`() {
        val cell = 0x8828308281fffffL // res 8, exemple canonique de la doc H3
        val decoded = ProximityPayload.decode(ProximityPayload.encode(cell))
        assertEquals(cell, decoded?.cell4d)
    }

    @Test
    fun `cellule non résolue - la sentinelle revient en null`() {
        val decoded = ProximityPayload.decode(ProximityPayload.encode(null))
        assertNotNull(decoded)
        assertNull(decoded?.cell4d)
    }

    @Test
    fun `payload absent ou de mauvaise taille rejeté`() {
        assertNull(ProximityPayload.decode(null))
        assertNull(ProximityPayload.decode(ByteArray(0)))
        assertNull(ProximityPayload.decode("phix2".toByteArray())) // l'ancienne sonde POC
        assertNull(ProximityPayload.decode(ByteArray(10)))
    }

    @Test
    fun `version inconnue rejetée`() {
        val bytes = ProximityPayload.encode(42L).apply { this[0] = 9 }
        assertNull(ProximityPayload.decode(bytes))
    }
}
