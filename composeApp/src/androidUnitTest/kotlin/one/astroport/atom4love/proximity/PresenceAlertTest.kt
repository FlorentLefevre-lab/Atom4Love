package one.astroport.atom4love.proximity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le réveil de présence — et surtout les cas où il doit **se taire**.
 *
 * Un réveil trop bavard serait pire que pas de réveil du tout : dans un bar,
 * un pair au bord des sept mètres traverse la portée plusieurs fois par heure,
 * et une salle qui se remplit ferait sonner à chaque arrivée.
 */
class PresenceAlertTest {

    private val t0 = 1_800_000_000_000L

    @Test
    fun `la premiere carte qui se montre reveille`() {
        assertTrue(PresenceAlert.shouldAnnounce(before = 0, now = 1, lastAnnouncedMs = 0, nowMs = t0))
    }

    @Test
    fun `une salle qui se remplit ne sonne qu'une fois`() {
        assertTrue(PresenceAlert.shouldAnnounce(0, 1, 0, t0))
        // Les suivants arrivent alors qu'il y avait déjà quelqu'un.
        assertFalse(PresenceAlert.shouldAnnounce(1, 2, t0, t0 + 60_000))
        assertFalse(PresenceAlert.shouldAnnounce(2, 5, t0, t0 + 120_000))
    }

    @Test
    fun `un pair qui va et vient ne sonne pas a chaque passage`() {
        // Il sort (1 → 0), revient (0 → 1) deux minutes plus tard : le temps de
        // garde l'absorbe.
        assertFalse(PresenceAlert.shouldAnnounce(0, 1, t0, t0 + 2 * 60_000))
        assertFalse(PresenceAlert.shouldAnnounce(0, 1, t0, t0 + 14 * 60_000))
    }

    @Test
    fun `un autre lieu, plus tard, se dit`() {
        assertTrue(PresenceAlert.shouldAnnounce(0, 1, t0, t0 + PresenceAlert.GUARD_MS))
        assertTrue(PresenceAlert.shouldAnnounce(0, 1, t0, t0 + 60 * 60_000))
    }

    @Test
    fun `une salle vide ne reveille personne`() {
        assertFalse(PresenceAlert.shouldAnnounce(0, 0, 0, t0))
        assertFalse(PresenceAlert.shouldAnnounce(3, 0, 0, t0))
    }

    @Test
    fun `le tout premier reveil part meme sans historique`() {
        // lastAnnouncedMs = 0 : l'écart au temps courant dépasse largement la
        // garde, donc rien ne bloque la toute première fois.
        assertTrue(PresenceAlert.shouldAnnounce(0, 1, 0L, t0))
    }
}
