package one.astroport.atom4love.nostr

import one.astroport.atom4love.domain.A4lAddress
import one.astroport.atom4love.domain.KinMaya
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'honneur au nouveau venu : la fenêtre, ses deux bords, et le cas limite qui
 * compte le plus — celui de la personne qui vient tout juste de s'inscrire.
 */
class NewcomerTest {

    private val now = 1_800_000_000_000L

    private fun atomSealedAt(epochSeconds: Long) = Constellation.Atom(
        pubkey = "0".repeat(64),
        place = A4lAddress.Place(latDeg = 48.85, lonDeg = 2.35, pentagonId = 0, q = 0, r = 0),
        phase = 1.0,
        kin = KinMaya.ofNumber(119),
        createdAt = epochSeconds,
    )

    @Test
    fun `un certificat scelle a l'instant est un nouveau venu`() {
        assertTrue(atomSealedAt(now / 1000).isNewcomer(now))
    }

    @Test
    fun `il le reste jusqu'au dernier moment de la semaine`() {
        val presqueSept = now - Constellation.NEWCOMER_WINDOW_MS + 1000
        assertTrue(atomSealedAt(presqueSept / 1000).isNewcomer(now))
    }

    @Test
    fun `passe une semaine, il rentre dans le rang`() {
        val huitJours = now - Constellation.NEWCOMER_WINDOW_MS - 1000
        assertFalse(atomSealedAt(huitJours / 1000).isNewcomer(now))
    }

    @Test
    fun `une horloge en avance ne fait pas rater l'honneur`() {
        // Le cas qui compte : quelqu'un vient de créer son MULTIPASS, et la
        // station a quelques secondes d'avance sur le téléphone qui lit. Le
        // certificat paraît venir du futur — il doit être honoré quand même,
        // sinon on manque précisément la personne qu'on voulait fêter.
        val futur = now + 5 * 60 * 1000L
        assertTrue(atomSealedAt(futur / 1000).isNewcomer(now))
    }

    @Test
    fun `un certificat ancien de plusieurs mois n'est pas nouveau`() {
        val troisMois = now - 90L * 24 * 60 * 60 * 1000
        assertFalse(atomSealedAt(troisMois / 1000).isNewcomer(now))
    }
}
