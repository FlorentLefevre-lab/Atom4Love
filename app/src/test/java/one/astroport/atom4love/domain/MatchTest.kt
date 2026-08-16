package one.astroport.atom4love.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux échelons du jeu, épinglés sur les seuils de Fred — et surtout sur ce
 * qui les sépare : un match tient à **un** plan, un super match aux **deux**.
 *
 * Le dernier test compte les échelons sur toute la grille : c'est le seul moyen
 * de savoir si le mot « super » en vaut la peine. Un super match trop fréquent
 * ne serait plus un événement, et le vocabulaire mentirait.
 */
class MatchTest {

    /** Deux phases distantes de [delta] radians, prises loin des bords. */
    private fun pair(delta: Double) = 1.0 to (1.0 + delta)

    @Test
    fun `sans phase ni sceau, rien n'est conclu`() {
        val r = Match.read(null, null, null, null)
        assertEquals(Match.Level.None, r.level)
        assertNull(r.k)
        assertNull(r.bond)
    }

    @Test
    fun `une phase manquante n'empeche pas le lien de sceau`() {
        // Le sceau arrive avec la date, la phase seulement avec le lieu : une
        // fiche à moitié remplie doit quand même pouvoir faire un match.
        val r = Match.read(myPhase = null, myGlyph = 0, theirPhase = null, theirGlyph = 10)
        assertEquals(Match.Level.Match, r.level)
        assertEquals(Oracle.Bond.Challenge, r.bond)
        assertNull(r.k)
    }

    @Test
    fun `la phase seule suffit au match quand elle passe le seuil de Fred`() {
        val (a, b) = pair(0.01)
        // Sceaux sans lien : 0 et 1 ne sont ni le défi (10) ni l'occulte (19).
        val r = Match.read(a, 0, b, 1)
        assertEquals(Match.Level.Match, r.level)
        assertTrue(r.quantum)
        assertNull(r.bond)
        assertTrue(r.k!! >= Phi2X.SUPER_COHERENCE_K)
    }

    @Test
    fun `l'opposition de phase vaut la coincidence — c'est un match aussi`() {
        // Le point qui compte : Δφ ≈ π donne k ≈ 1 comme Δφ ≈ 0. Un défi n'est
        // pas moins qu'une union.
        val (a, b) = pair(Math.PI - 0.01)
        val r = Match.read(a, 0, b, 1)
        assertEquals(Match.Level.Match, r.level)
        assertTrue(r.quantum)
    }

    @Test
    fun `le quart de tour ne fait aucun match`() {
        val (a, b) = pair(Math.PI / 2)
        val r = Match.read(a, 0, b, 1)
        assertEquals(Match.Level.None, r.level)
        // k y touche son minimum : c'est le vrai fond, pas l'opposition.
        assertEquals(0.5, r.k!!, 1e-9)
    }

    @Test
    fun `les deux plans ensemble font un super match`() {
        val (a, b) = pair(0.01)
        // Sceau 10 = le défi du sceau 0.
        val r = Match.read(a, 0, b, 10)
        assertEquals(Match.Level.Super, r.level)
        assertTrue(r.quantum)
        assertEquals(Oracle.Bond.Challenge, r.bond)
    }

    @Test
    fun `l'occulte compte comme lien au meme titre que le defi`() {
        val r = Match.read(myPhase = null, myGlyph = 0, theirPhase = null, theirGlyph = 19)
        assertEquals(Match.Level.Match, r.level)
        assertEquals(Oracle.Bond.Hidden, r.bond)
    }

    @Test
    fun `un super match reste rare, et un match reste atteignable`() {
        // Balayage de toutes les paires de sceaux × un tour de phase découpé
        // fin. Les phases réelles ne sont pas uniformes, mais l'ordre de
        // grandeur suffit à dire si le mot « super » est mérité.
        val steps = 720
        var match = 0
        var superb = 0
        var total = 0
        for (mine in 0..19) {
            for (theirs in 0..19) {
                for (i in 0 until steps) {
                    val delta = i * Phi2X.TAU / steps
                    total++
                    when (Match.read(0.0, mine, delta, theirs).level) {
                        Match.Level.Super -> superb++
                        Match.Level.Match -> match++
                        Match.Level.None -> Unit
                    }
                }
            }
        }
        val matchPct = 100.0 * (match + superb) / total
        val superPct = 100.0 * superb / total
        // Un match sur ~8 rencontres : assez pour que le jeu vive.
        assertTrue("match trop rare : $matchPct %", matchPct > 8.0)
        assertTrue("match trop courant : $matchPct %", matchPct < 20.0)
        // Un super match sur ~300 : un événement, pas une habitude.
        assertTrue("super match trop courant : $superPct %", superPct < 1.0)
        assertTrue("super match impossible : $superPct %", superPct > 0.1)
    }
}
