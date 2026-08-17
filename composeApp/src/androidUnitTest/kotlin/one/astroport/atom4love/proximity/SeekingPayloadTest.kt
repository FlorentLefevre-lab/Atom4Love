package one.astroport.atom4love.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La déclaration de recherche — et surtout ce qu'elle refuse de lire.
 *
 * Elle voyage sur une annonce ouverte : n'importe qui peut en émettre une
 * malformée, tronquée, ou prétendant chercher trente personnes. Tout ce qui
 * n'est pas exactement au format doit rendre `null` plutôt qu'une valeur à
 * moitié crédible.
 */
class SeekingPayloadTest {

    @Test
    fun `un aller-retour rend ce qu'on a mis`() {
        val bytes = SeekingPayload.encode(0x11223344, listOf(1, 2, 3))!!
        val back = SeekingPayload.decode(bytes)!!
        assertEquals(0x11223344, back.from)
        assertEquals(listOf(1, 2, 3), back.targets)
    }

    @Test
    fun `chercher personne ne s'annonce pas`() {
        assertNull(SeekingPayload.encode(1, emptyList()))
    }

    @Test
    fun `on ne declare jamais plus de cartes que la limite`() {
        val bytes = SeekingPayload.encode(1, listOf(10, 20, 30, 40, 50))!!
        assertEquals(SeekingPayload.MAX_TARGETS, SeekingPayload.decode(bytes)!!.targets.size)
    }

    @Test
    fun `les doublons ne gonflent pas l'annonce`() {
        val bytes = SeekingPayload.encode(1, listOf(7, 7, 7))!!
        assertEquals(listOf(7), SeekingPayload.decode(bytes)!!.targets)
    }

    @Test
    fun `savoir si c'est nous qu'on cherche`() {
        val s = SeekingPayload.decode(SeekingPayload.encode(1, listOf(42, 43))!!)!!
        assertTrue(s.seeks(42))
        assertTrue(s.seeks(43))
        assertFalse(s.seeks(44))
        // Sans jeton à nous, rien ne nous concerne — pas de « peut-être ».
        assertFalse(s.seeks(null))
    }

    @Test
    fun `tout ce qui n'est pas au format est rejete`() {
        assertNull(SeekingPayload.decode(null))
        assertNull(SeekingPayload.decode(ByteArray(0)))
        assertNull(SeekingPayload.decode(ByteArray(3)))
        // Bonne taille, mauvaise version : une autre application, ou une
        // version future qu'on ne sait pas lire.
        val ok = SeekingPayload.encode(1, listOf(2))!!
        val wrongVersion = ok.copyOf().also { it[0] = 9 }
        assertNull(SeekingPayload.decode(wrongVersion))
        // Un compte qui promet plus que la trame ne porte.
        val lying = ok.copyOf().also { it[5] = 3 }
        assertNull(SeekingPayload.decode(lying))
        // Un compte nul, ou au-delà de la limite.
        assertNull(SeekingPayload.decode(ok.copyOf().also { it[5] = 0 }))
        assertNull(SeekingPayload.decode(ok.copyOf().also { it[5] = 99 }))
    }

    @Test
    fun `l'annonce tient dans ce que la puce la plus modeste accepte`() {
        // Relevé sur le banc le 16/08 : 304 octets sur la tablette, 1650 sur le
        // Pixel. On veut rester loin en dessous du plancher.
        val full = SeekingPayload.encode(1, listOf(1, 2, 3))!!
        assertTrue("trame de ${full.size} octets", full.size <= 32)
    }
}
