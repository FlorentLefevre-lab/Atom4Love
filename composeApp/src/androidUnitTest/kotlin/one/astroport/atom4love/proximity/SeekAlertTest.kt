package one.astroport.atom4love.proximity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les trois conditions de [SeekAlert], épinglées. Chacune a coûté une phrase de
 * KDoc parce que chacune protège quelqu'un : celui qu'on réveillerait pour
 * rien, celui qu'on ferait sonner en main, et celui qu'on harcèlerait.
 */
class SeekAlertTest {

    private val now = 1_000_000L

    @Test
    fun `écran éteint, lanterne fermée, jamais prévenu — on réveille`() {
        assertTrue(SeekAlert.shouldWake(seekingBack = false, onScreen = false, lastWokeMs = 0L, nowMs = now))
    }

    @Test
    fun `on cherche déjà en face — rien à dire`() {
        // Les deux lanternes sont ouvertes : les écrans battent, la notification
        // n'apprendrait rien et couvrirait le rythme.
        assertFalse(SeekAlert.shouldWake(seekingBack = true, onScreen = false, lastWokeMs = 0L, nowMs = now))
    }

    @Test
    fun `le téléphone est en main — on ne sonne pas`() {
        // Le Plateau porte déjà « vous cherche » sur la carte, et il le dit mieux.
        assertFalse(SeekAlert.shouldWake(seekingBack = false, onScreen = true, lastWokeMs = 0L, nowMs = now))
    }

    @Test
    fun `le temps de garde tient, puis rend la main`() {
        val juste = now - SeekAlert.GUARD_MS + 1
        assertFalse(
            "une lanterne ouverte et refermée trois fois ne sonne pas trois fois",
            SeekAlert.shouldWake(seekingBack = false, onScreen = false, lastWokeMs = juste, nowMs = now),
        )
        assertTrue(
            SeekAlert.shouldWake(
                seekingBack = false,
                onScreen = false,
                lastWokeMs = now - SeekAlert.GUARD_MS,
                nowMs = now,
            ),
        )
    }
}
