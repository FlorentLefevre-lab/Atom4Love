package one.astroport.atom4love.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le rythme partagé, vérifié là où il peut échouer sans qu'on s'en aperçoive.
 *
 * Rien ne circule : chaque appareil calcule seul. Il n'y a donc aucun protocole
 * pour rattraper un désaccord, et la seule garantie est arithmétique — les deux
 * dérivations doivent tomber sur le même entier, sinon deux personnes se
 * cherchent à deux mètres l'une de l'autre en regardant deux écrans qui ne
 * battent pas ensemble.
 */
class RendezvousTest {

    /** Quelques phases plausibles, dont les bords du tour. */
    private val phases = listOf(
        0.0, 0.0001, 1.0, 2.7182818, 3.1415926, 4.651994, 5.9, 6.283, 6.2831852,
    )

    @Test
    fun `la dérivation est symétrique — personne n'est l'appelant`() {
        for (a in phases) {
            for (b in phases) {
                assertEquals("φ=$a et φ=$b", Rendezvous.of(a, b), Rendezvous.of(b, a))
            }
        }
    }

    /**
     * **Le défaut qui ne se verrait que dans un bar.** Notre φ existe ici en
     * pleine précision ; le voisin ne le reçoit qu'arrondi aux 65535ᵉ de tour.
     * Si la dérivation ne ramenait pas les deux à ce qui passe dans l'air, les
     * deux appareils tireraient deux motifs différents — sans erreur, sans
     * message, juste deux écrans qui refusent de se synchroniser.
     *
     * On fait donc passer la phase par les **vrais octets** de l'annonce.
     */
    @Test
    fun `notre phase pleine et celle qu'entend le voisin donnent le même motif`() {
        val theirs = 2.0
        for (mine in phases) {
            val heard = ProximityPayload
                .decode(
                    ProximityPayload.encode(
                        cell4d = null,
                        token = null,
                        signature = ProximityPayload.Signature(sex = 0, glyph = 3, phase = mine),
                    ),
                )!!
                .signature.phase

            assertEquals(
                "φ=$mine entendu $heard",
                // ce que calcule l'autre appareil : notre phase telle qu'il l'a reçue
                Rendezvous.of(theirs, heard),
                // ce que nous calculons : notre phase telle que nous la connaissons
                Rendezvous.of(mine, theirs),
            )
        }
    }

    @Test
    fun `sans phase des deux côtés il n'y a pas de rendez-vous`() {
        assertNull(Rendezvous.of(null, 1.0))
        assertNull(Rendezvous.of(1.0, null))
        assertNull(Rendezvous.of(null, null))
    }

    @Test
    fun `chaque motif porte le premier pas et exactement LIT éclairs`() {
        for (a in phases) {
            for (b in phases) {
                val beat = Rendezvous.of(a, b)!!
                assertTrue("$beat démarre éteint", beat.isLit(0))
                assertEquals("$beat", Rendezvous.LIT, Integer.bitCount(beat.mask))
                assertEquals(
                    "$beat déborde du cycle",
                    0,
                    beat.mask ushr Rendezvous.SLOTS,
                )
            }
        }
    }

    /**
     * Deux rendez-vous simultanés dans la même salle ne doivent pas se
     * confondre. Sur mille paires tirées régulièrement dans le tour, on veut
     * une très large majorité de figures distinctes — le calcul théorique donne
     * 1365 motifs possibles, donc quelques doublons sont attendus et normaux.
     */
    @Test
    fun `des paires différentes donnent des figures différentes`() {
        val seen = mutableSetOf<Int>()
        var drawn = 0
        for (i in 0 until 40) {
            for (j in i + 1 until 40) {
                seen += Rendezvous.of(i * 0.157, j * 0.157)!!.mask
                drawn++
            }
        }
        assertTrue("$drawn paires n'ont donné que ${seen.size} figures", seen.size > 500)
    }

    @Test
    fun `le pas avance avec l'horloge et reboucle sur le cycle`() {
        val beat = Rendezvous.of(1.0, 2.0)!!
        assertEquals(0, beat.slotAt(0L))
        assertEquals(1, beat.slotAt(Rendezvous.SLOT_MS))
        assertEquals(0, beat.slotAt(Rendezvous.CYCLE_MS))
        assertEquals(3, beat.slotAt(Rendezvous.CYCLE_MS * 7 + Rendezvous.SLOT_MS * 3 + 40))
    }

    /**
     * Un éclair est franc puis s'éteint dans son pas ; un pas éteint le reste
     * du début à la fin. C'est ce qui donne une figure comparable de loin.
     */
    @Test
    fun `l'éclat tombe à zéro avant le pas suivant`() {
        val beat = Rendezvous.of(1.0, 2.0)!!
        assertEquals(1f, beat.glowAt(0L), 0.001f)
        assertTrue(beat.glowAt(Rendezvous.SLOT_MS / 2) < 0.3f)
        assertTrue(beat.glowAt(Rendezvous.SLOT_MS - 1) < 0.001f)

        val dark = (1 until Rendezvous.SLOTS).first { !beat.isLit(it) }
        assertEquals(0f, beat.glowAt(dark * Rendezvous.SLOT_MS), 0f)
        assertEquals(0f, beat.glowAt(dark * Rendezvous.SLOT_MS + 70), 0f)
    }

    /** Un cycle plus tard, tout est exactement pareil : le motif est absolu. */
    @Test
    fun `le motif est calé sur le temps absolu, pas sur un départ`() {
        val beat = Rendezvous.of(4.651994, 1.234)!!
        val t = 1_755_000_000_000L
        assertEquals(beat.slotAt(t), beat.slotAt(t + Rendezvous.CYCLE_MS))
        assertEquals(beat.glowAt(t), beat.glowAt(t + Rendezvous.CYCLE_MS * 3), 0.0001f)
    }

    @Test
    fun `deux personnes distinctes ne partagent pas le rythme d'un tiers`() {
        val withAlice = Rendezvous.of(4.651994, 1.0)!!
        val withBob = Rendezvous.of(4.651994, 5.0)!!
        assertNotEquals(withAlice, withBob)
    }
}
