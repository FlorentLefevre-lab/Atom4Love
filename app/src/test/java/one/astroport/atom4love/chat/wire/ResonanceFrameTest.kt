package one.astroport.atom4love.chat.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La trame qui porte l'onde biologique d'un pair.
 *
 * Elle est arrivée après que des appareils étaient déjà en service : le point
 * qui compte autant que l'aller-retour, c'est qu'une station qui ne la connaît
 * pas ne s'en trouve pas mal.
 */
class ResonanceFrameTest {

    @Test
    fun `l'onde fait l'aller-retour`() {
        val frame = ChatFrames.decode(ChatFrames.encodeResonance(225.81f))
        assertEquals(225.81f, (frame as ChatFrame.Resonance).omegaBio, 1e-4f)
    }

    /** Cinq octets : un type, un flottant. Rien à fragmenter, même en BLE. */
    @Test
    fun `la trame tient en cinq octets`() {
        assertEquals(5, ChatFrames.encodeResonance(186.79f).size)
    }

    /**
     * Ce qui n'est pas une onde ne doit pas traverser : NaN et infini se
     * propageraient jusqu'au synthétiseur, qui rendrait un silence ou pire.
     */
    @Test
    fun `une valeur qui n'est pas une onde est refusee`() {
        assertNull(ChatFrames.decode(ChatFrames.encodeResonance(Float.NaN)))
        assertNull(ChatFrames.decode(ChatFrames.encodeResonance(Float.POSITIVE_INFINITY)))
        assertNull(ChatFrames.decode(ChatFrames.encodeResonance(0f)))
        assertNull(ChatFrames.decode(ChatFrames.encodeResonance(-12f)))
    }

    /** Tronquée par un lien, elle ne devient pas une autre trame. */
    @Test
    fun `une trame tronquee est refusee`() {
        val full = ChatFrames.encodeResonance(225.81f)
        assertNull(ChatFrames.decode(full.copyOfRange(0, 3)))
    }

    /**
     * Le contrat de compatibilité : une version d'avant cette trame la lit
     * comme un type inconnu et rend `null` — le lien continue, elle n'entend
     * simplement pas le battement. C'est ce que fait `decode` de tout octet de
     * tête qu'il ne connaît pas, et ce test épingle la règle plutôt que le cas.
     */
    @Test
    fun `un type inconnu ne casse rien`() {
        assertNull(ChatFrames.decode(byteArrayOf(0x7F, 1, 2, 3, 4)))
        assertTrue(ChatFrames.decode(ChatFrames.encodeResonance(225.81f)) is ChatFrame.Resonance)
    }
}
