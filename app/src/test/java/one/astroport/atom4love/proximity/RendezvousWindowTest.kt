package one.astroport.atom4love.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Les fenêtres de recherche multiple — et **la seule propriété qui compte** :
 * deux appareils qui se cherchent doivent jouer leur paire commune pendant
 * exactement les mêmes secondes.
 *
 * Si elle tombe, deux personnes peuvent se chercher toute la soirée sans jamais
 * afficher leur figure au même moment. C'est le genre de panne qui ne se voit
 * pas au débogage et qui rend le jeu injouable en salle.
 */
class RendezvousWindowTest {

    @Test
    fun `la fenetre d'une paire est la meme des deux cotes`() {
        val rng = Random(20260816)
        repeat(2_000) {
            val a = rng.nextDouble(0.0, Phi2X_TAU)
            val b = rng.nextDouble(0.0, Phi2X_TAU)
            assertEquals(
                "fenêtre asymétrique pour ($a, $b)",
                Rendezvous.windowOf(a, b),
                Rendezvous.windowOf(b, a),
            )
        }
    }

    @Test
    fun `elle ne bouge pas avec le temps`() {
        // La fenêtre appartient à la paire ; c'est l'horloge qui défile devant,
        // pas l'attribution qui change.
        val w = Rendezvous.windowOf(1.0, 2.0)
        repeat(50) { assertEquals(w, Rendezvous.windowOf(1.0, 2.0)) }
    }

    @Test
    fun `sans phase, pas de fenetre`() {
        assertNull(Rendezvous.windowOf(null, 1.0))
        assertNull(Rendezvous.windowOf(1.0, null))
        assertNull(Rendezvous.windowOf(null, null))
    }

    @Test
    fun `la fenetre courante avance d'un cran par cycle et boucle`() {
        val t0 = 1_800_000_000_000L
        val base = Rendezvous.windowAt(t0)
        assertEquals((base + 1) % Rendezvous.WINDOWS, Rendezvous.windowAt(t0 + Rendezvous.CYCLE_MS))
        assertEquals(base, Rendezvous.windowAt(t0 + Rendezvous.CYCLE_MS * Rendezvous.WINDOWS))
        // Elle ne change pas AU MILIEU d'un cycle : la figure se voit entière.
        assertEquals(base, Rendezvous.windowAt(t0 + Rendezvous.CYCLE_MS - 1))
    }

    @Test
    fun `les fenetres se repartissent, aucune n'est desertee`() {
        val rng = Random(7)
        val counts = IntArray(Rendezvous.WINDOWS)
        repeat(3_000) {
            val w = Rendezvous.windowOf(
                rng.nextDouble(0.0, Phi2X_TAU),
                rng.nextDouble(0.0, Phi2X_TAU),
            )!!
            counts[w]++
        }
        counts.forEach { assertTrue("fenêtre désertée : ${counts.toList()}", it > 500) }
    }

    @Test
    fun `deux appareils qui se cherchent tombent sur la meme seconde`() {
        // Le scénario réel : A cherche {B, C, D}, B cherche {A, E, F}. Sur un
        // tour complet, il doit exister des instants où A joue B ET B joue A.
        val a = 1.0
        val b = 2.5
        val others = listOf(0.3, 4.1, 5.5, 3.3)
        val t0 = 1_800_000_000_000L
        var together = 0
        for (step in 0 until Rendezvous.WINDOWS * 4) {
            val t = t0 + step * Rendezvous.CYCLE_MS
            val w = Rendezvous.windowAt(t)
            val aPlaysB = Rendezvous.windowOf(a, b) == w
            val bPlaysA = Rendezvous.windowOf(b, a) == w
            // La symétrie garantit que les deux sont vrais en même temps.
            assertEquals("désaccord au pas $step", aPlaysB, bPlaysA)
            if (aPlaysB) together++
        }
        assertTrue("jamais ensemble sur quatre tours", together >= 4)
    }

    private companion object {
        const val Phi2X_TAU = 2.0 * Math.PI
    }
}
