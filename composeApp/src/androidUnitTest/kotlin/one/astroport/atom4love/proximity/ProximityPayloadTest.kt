package one.astroport.atom4love.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** Et un pair resté à la version 2 : cellule et jeton, sans signature. */
    @Test
    fun `la version 2 se lit encore, sans signature`() {
        val v2 = byteArrayOf(2) +
            java.nio.ByteBuffer.allocate(8).putLong(cell).array() +
            java.nio.ByteBuffer.allocate(4).putInt(1234).array()
        val decoded = ProximityPayload.decode(v2)
        assertEquals(cell, decoded?.cell4d)
        assertEquals(1234, decoded?.token)
        assertEquals(ProximityPayload.Signature.Unknown, decoded?.signature)
    }

    @Test
    fun `la signature fait l'aller-retour`() {
        val phase = 4.852425269214   // la fiche du Pixel, calculée par Phi2X
        val signature = ProximityPayload.Signature(sex = 1, glyph = 2, phase = phase)
        val decoded = ProximityPayload.decode(
            ProximityPayload.encode(cell, 1234, signature),
        )!!
        assertEquals(1, decoded.signature.sex)
        assertEquals(2, decoded.signature.glyph)
        // Deux octets pour un tour : la phase revient au pas de quantification près.
        assertEquals(phase, decoded.signature.phase!!, 1e-4)
    }

    @Test
    fun `une signature vide se relit vide`() {
        val decoded = ProximityPayload.decode(
            ProximityPayload.encode(cell, null, ProximityPayload.Signature.Unknown),
        )!!
        assertNull(decoded.signature.sex)
        assertNull(decoded.signature.glyph)
        assertNull(decoded.signature.phase)
    }

    /** Une fiche se remplit par morceaux : chaque champ doit pouvoir partir seul. */
    @Test
    fun `les trois champs sont independants`() {
        val sexeSeul = ProximityPayload.decode(
            ProximityPayload.encode(null, null, ProximityPayload.Signature(0, null, null)),
        )!!.signature
        assertEquals(0, sexeSeul.sex)
        assertNull(sexeSeul.glyph)
        assertNull(sexeSeul.phase)

        val sceauSeul = ProximityPayload.decode(
            ProximityPayload.encode(null, null, ProximityPayload.Signature(null, 19, null)),
        )!!.signature
        assertNull(sceauSeul.sex)
        assertEquals(19, sceauSeul.glyph)
    }

    /** Des valeurs hors domaine ne doivent pas se faire passer pour des vraies. */
    @Test
    fun `une valeur aberrante se diffuse comme inconnue`() {
        val decoded = ProximityPayload.decode(
            ProximityPayload.encode(null, null, ProximityPayload.Signature(7, 42, Double.NaN)),
        )!!.signature
        assertNull(decoded.sex)
        assertNull(decoded.glyph)
        assertNull(decoded.phase)
    }

    /** Le tour boucle : 2π est la même chose que 0, pas une phase inconnue. */
    @Test
    fun `la phase reste dans le tour`() {
        val tau = 2.0 * Math.PI
        for (phase in listOf(0.0, tau - 1e-9, tau, tau + 0.5, -0.5)) {
            val decoded = ProximityPayload.decode(
                ProximityPayload.encode(null, null, ProximityPayload.Signature(null, null, phase)),
            )!!.signature.phase
            assertNotNull("phase $phase perdue", decoded)
            assertTrue("phase $phase relue hors tour : $decoded", decoded!! >= 0.0 && decoded < tau)
        }
    }

    /** 17 octets : il en reste 7 sur les 24 utiles d'une annonce legacy. */
    @Test
    fun `l'annonce tient dans le budget d'une annonce legacy`() {
        assertEquals(17, ProximityPayload.encode(cell, 1234).size)
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
