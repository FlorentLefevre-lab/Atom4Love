package one.astroport.atom4love.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le rythme des connexions sortantes — l'horloge est passée, rien n'attend. */
class DialPacingTest {

    private val a = "AA:BB:CC:DD:EE:FF"
    private val b = "11:22:33:44:55:66"

    @Test
    fun `personne en ligne - une composition toutes les cinq secondes`() {
        val pacing = DialPacing()
        assertTrue(pacing.allow(a, now = 0, engaged = false))
        pacing.dialed(0)
        assertFalse(pacing.allow(b, now = 4_999, engaged = false))
        assertTrue(pacing.allow(b, now = 5_000, engaged = false))
    }

    /** Quelqu'un est là : la découverte continue, mais elle cesse d'être pressée. */
    @Test
    fun `quelqu'un est la - une composition par minute`() {
        val pacing = DialPacing()
        pacing.dialed(0)
        assertFalse(pacing.allow(b, now = 30_000, engaged = true))
        assertTrue(pacing.allow(b, now = 60_000, engaged = true))
    }

    /** Le cas mesuré : la même adresse morte rappelée trois fois de suite. */
    @Test
    fun `une adresse qui refuse s'eloigne a chaque refus`() {
        val pacing = DialPacing()
        pacing.failed(a, now = 0)
        assertFalse(pacing.allow(a, now = 29_000, engaged = false))
        assertTrue(pacing.allow(a, now = 30_000, engaged = false))

        pacing.failed(a, now = 30_000)
        assertFalse(pacing.allow(a, now = 89_000, engaged = false))
        assertTrue(pacing.allow(a, now = 90_000, engaged = false))

        pacing.failed(a, now = 90_000)
        assertFalse(pacing.allow(a, now = 209_000, engaged = false))
        assertTrue(pacing.allow(a, now = 210_000, engaged = false))
    }

    @Test
    fun `l'eloignement est plafonne`() {
        val pacing = DialPacing()
        repeat(20) { pacing.failed(a, now = 0) }
        assertFalse(pacing.allow(a, now = 239_000, engaged = false))
        assertTrue(pacing.allow(a, now = 240_000, engaged = false))
    }

    /** Un lien qui marchait et qui tombe n'est pas un refus : on y revient vite. */
    @Test
    fun `un lien perdu se retente en deux secondes`() {
        val pacing = DialPacing()
        pacing.lost(a, now = 0)
        assertFalse(pacing.allow(a, now = 1_999, engaged = false))
        assertTrue(pacing.allow(a, now = 2_000, engaged = false))
    }

    /** Et le succès efface l'ardoise : les refus d'avant ne pèsent plus. */
    @Test
    fun `un lien perdu remet le compteur de refus a zero`() {
        val pacing = DialPacing()
        pacing.failed(a, now = 0)
        pacing.failed(a, now = 0)
        pacing.lost(a, now = 0)
        pacing.failed(a, now = 0)
        assertTrue(pacing.allow(a, now = 30_000, engaged = false))
    }

    /** Une adresse au délai de grâce n'empêche pas de composer vers une autre. */
    @Test
    fun `le delai de grace ne vaut que pour son adresse`() {
        val pacing = DialPacing()
        pacing.failed(a, now = 0)
        assertFalse(pacing.allow(a, now = 10_000, engaged = false))
        assertTrue(pacing.allow(b, now = 10_000, engaged = false))
    }
}
