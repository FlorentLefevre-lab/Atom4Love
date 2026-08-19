package one.astroport.atom4love.trial

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle qui décide **quand** le MULTIPASS se propose.
 *
 * Ce qui est épinglé ici n'est pas un calcul mais une promesse : que
 * l'application ne réclame rien tant qu'elle n'a pas été essayée, et qu'elle ne
 * transforme jamais un refus de localisation en réclamation de compte.
 */
class TrialTest {

    private val hour = 3_600_000L
    private val forgedAt = 1_755_000_000_000L

    /** Le bar de la première soirée. */
    private val origin = Trial.Origin(lat = 43.6047, lon = 1.4442, atMs = forgedAt)

    /** Deux kilomètres et demi plus loin, en ligne droite. */
    private val homeLat = 43.6270
    private val homeLon = 1.4442

    @Test
    fun `parti et revenu plus tard, la proposition est due`() {
        assertTrue(
            Trial.isDue(origin, homeLat, homeLon, forgedAt + 5 * hour),
        )
    }

    @Test
    fun `rester sur place ne finit pas l'essai, même des jours plus tard`() {
        assertFalse(
            Trial.isDue(origin, origin.lat, origin.lon, forgedAt + 72 * hour),
        )
    }

    @Test
    fun `traverser la ville en vingt minutes ne finit pas l'essai`() {
        assertFalse(
            Trial.isDue(origin, homeLat, homeLon, forgedAt + hour / 3),
        )
    }

    /**
     * ⚠ Le cas qui compte le plus. Sans position — permission refusée, service
     * coupé —, on ne réclame **jamais** : faire l'inverse ferait d'un refus de
     * localisation une demande de compte, et ce marché-là n'est pas passé.
     */
    @Test
    fun `sans position courante, jamais de proposition`() {
        assertFalse(Trial.isDue(origin, null, null, forgedAt + 100 * hour))
    }

    @Test
    fun `sans lieu de départ, jamais de proposition`() {
        val blind = Trial.Origin(lat = null, lon = null, atMs = forgedAt)
        assertFalse(Trial.isDue(blind, homeLat, homeLon, forgedAt + 100 * hour))
    }

    @Test
    fun `sans noyau forgé, il n'y a pas de départ et donc rien à proposer`() {
        assertFalse(Trial.isDue(null, homeLat, homeLon, forgedAt + 100 * hour))
    }

    /**
     * Le bruit du GPS ne doit pas suffire. La maille du certificat fait un
     * kilomètre : en dessous du seuil, on mesurerait une dérive d'antenne
     * plutôt qu'un trajet.
     */
    @Test
    fun `un déplacement sous le seuil ne compte pas, même le lendemain`() {
        // ~1,1 km au nord : au-dessus du bruit, sous les deux kilomètres.
        assertFalse(
            Trial.isDue(origin, origin.lat!! + 0.010, origin.lon, forgedAt + 24 * hour),
        )
    }
}
