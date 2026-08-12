package one.astroport.atom4love.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L'annonce de proximité : ce qu'elle porte, et ce qu'elle refuse de porter. */
class ProximityPayloadTest {

    private val cell = 0x881FB5BB15L
    private val alice = ByteArray(32) { 1 }
    private val bob = ByteArray(32) { 2 }

    @Test
    fun `cellule et jeton font l'aller-retour`() {
        val decoded = ProximityPayload.decode(ProximityPayload.encode(cell, 1234))
        assertEquals(cell, decoded?.cell4d)
        assertEquals(1234, decoded?.token)
    }

    @Test
    fun `cellule inconnue et noyau absent se relisent en null`() {
        val decoded = ProximityPayload.decode(ProximityPayload.encode(null, null))
        assertNull(decoded?.cell4d)
        assertNull(decoded?.token)
    }

    /** Un pair resté à la version 1 doit continuer d'exister à nos yeux. */
    @Test
    fun `l'ancien format se lit encore, sans jeton`() {
        val v1 = byteArrayOf(1) + java.nio.ByteBuffer.allocate(8).putLong(cell).array()
        val decoded = ProximityPayload.decode(v1)
        assertEquals(cell, decoded?.cell4d)
        assertNull(decoded?.token)
    }

    @Test
    fun `un payload d'une autre application est ignore`() {
        assertNull(ProximityPayload.decode("phix2".toByteArray()))
        assertNull(ProximityPayload.decode(null))
        assertNull(ProximityPayload.decode(ByteArray(13) { 9 }))
    }

    @Test
    fun `le jeton est stable pour un meme noyau dans une meme cellule`() {
        assertEquals(ProximityPayload.token(alice, cell), ProximityPayload.token(alice, cell))
    }

    @Test
    fun `deux noyaux ne partagent pas leur jeton`() {
        assertNotEquals(ProximityPayload.token(alice, cell), ProximityPayload.token(bob, cell))
    }

    /** Le jour où D2 tournera, le jeton tournera avec la cellule diffusée. */
    @Test
    fun `changer de cellule change le jeton`() {
        assertNotEquals(ProximityPayload.token(alice, cell), ProximityPayload.token(alice, cell + 1))
    }

    @Test
    fun `sans noyau ni cellule, aucun jeton`() {
        assertNull(ProximityPayload.token(null, cell))
        assertNull(ProximityPayload.token(alice, null))
        assertNull(ProximityPayload.token(ByteArray(0), cell))
    }

    /** Le jeton ne doit pas révéler le noyau : rien de la clé n'apparaît. */
    @Test
    fun `le jeton n'est pas un morceau de la cle`() {
        val token = ProximityPayload.token(alice, cell)!!
        val head = java.nio.ByteBuffer.wrap(alice).int
        assertNotEquals(head, token)
    }
}
