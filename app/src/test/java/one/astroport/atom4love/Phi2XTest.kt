package one.astroport.atom4love

import kotlin.math.abs
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.domain.Wave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le portage de `tools/phi2x.py` vérifié contre la station elle-même.
 *
 * Les valeurs attendues ne sont pas recopiées d'une lecture du code : elles
 * sortent de l'exécution du python d'Astroport.ONE (`tools/phi2x.py` du dépôt
 * `papiche/Astroport.ONE`, relevé le 2026-08-13). Un portage validé contre sa
 * propre relecture ne prouverait rien.
 */
class Phi2XTest {

    /** Le python rend un double ; on se contente de la précision d'affichage. */
    private fun assertClose(expected: Double, actual: Double, eps: Double = 1e-9) {
        assertTrue(
            "attendu $expected, obtenu $actual (écart ${abs(expected - actual)})",
            abs(expected - actual) < eps,
        )
    }

    @Test
    fun `la phase suit la station, naissance apres 1970`() {
        // 18/09/1982 11:54, Laon — la fiche du Pixel.
        assertClose(4.852425269214, Phi2X.personalPhase(401198040L, 49.57, 3.61), 1e-11)
        // 17/04/1985 15:30, Paris — BirthData.Sample.
        assertClose(1.475719788267, Phi2X.personalPhase(482599800L, 48.86, 2.35), 1e-11)
        // 01/01/2000 midi, à l'intersection de l'équateur et de Greenwich.
        assertClose(0.202344854239, Phi2X.personalPhase(946728000L, 0.0, 0.0), 1e-11)
    }

    /**
     * Le cas qui départage les trois implémentations de Fred : avant 1970,
     * l'horodatage est négatif. La station prend le modulo de Python, toujours
     * positif ; `phi2x.js` et le GDScript gardent le signe et tombent sur
     * 4,166. C'est la station qui fait foi — elle dérive la clé LOVE.
     */
    @Test
    fun `la phase suit la station, naissance avant 1970`() {
        // 08/09/1948 midi, Bray-sur-Seine — la fiche de la tablette.
        val phase = Phi2X.personalPhase(-672580800L, 48.41, 3.24)
        assertClose(4.908062711833, phase, 1e-11)
        assertNotEquals(4.165796, phase, 1e-3)
    }

    /**
     * La grille tourne en 14,83 h : deux naissances au même endroit à quelques
     * heures d'écart ne voient pas les pentagones au même endroit. C'est ce
     * point que cabine-33 rate en appelant son offset sans horodatage.
     */
    @Test
    fun `la grille des pentagones tourne avec le temps`() {
        assertClose(2.8563530081354087, Phi2X.pentagonOffset(49.57, 3.61, 401198040.0), 1e-12)
        assertClose(0.9353079567879774, Phi2X.pentagonOffset(49.57, 3.61), 1e-12)
    }

    @Test
    fun `la haversine est celle de la station`() {
        assertClose(4495.61088423951, Phi2X.haversineKm(49.57, 3.61, 90.0, 0.0), 1e-9)
        assertClose(0.0, Phi2X.haversineKm(48.86, 2.35, 48.86, 2.35), 1e-12)
    }

    @Test
    fun `la phase reste dans le tour complet`() {
        for (day in 1..28) {
            val phase = Phi2X.personalPhase(
                BirthData.Sample.copy(day = day),
            )!!
            assertTrue("phase hors [0, 2π[ : $phase", phase >= 0.0 && phase < Phi2X.TAU)
        }
    }

    /**
     * L'heure pèse un tour complet par jour : sans elle, la phase est celle de
     * midi. C'est la même convention que le SALT, et c'est pourquoi le
     * sélecteur dit l'heure « recommandée » plutôt que facultative tout court.
     */
    @Test
    fun `sans heure, la phase est celle de midi`() {
        val sansHeure = BirthData.Sample.copy(hour = null, minute = null)
        val midi = BirthData.Sample.copy(hour = 12, minute = 0)
        assertEquals(Phi2X.personalPhase(midi), Phi2X.personalPhase(sansHeure))
        assertNotEquals(Phi2X.personalPhase(BirthData.Sample), Phi2X.personalPhase(sansHeure))
    }

    @Test
    fun `pas de phase sans date ni lieu`() {
        assertNull(Phi2X.personalPhase(BirthData.Empty))
        assertNull(Phi2X.personalPhase(BirthData.Sample.copy(lat = null)))
        assertNull(Phi2X.personalPhase(BirthData.Sample.copy(year = null)))
    }

    /**
     * k = 1/(1+|sin Δφ|), vérifié contre l'exécution de `compute_resonance_k`
     * de la station. Les deux extrêmes valent 1 — en phase et en opposition —
     * et le creux, en quadrature, ne descend qu'à 0,5.
     */
    @Test
    fun `le taux de resonance suit la station`() {
        // Le Pixel contre BirthData.Sample, avec les phases calculées ci-dessus.
        assertClose(0.811061115065, Phi2X.resonanceK(4.852425269214, 1.475719788267), 1e-11)
        assertClose(1.0, Phi2X.resonanceK(0.0, 0.0), 1e-12)
        assertClose(1.0, Phi2X.resonanceK(0.0, Math.PI), 1e-12)
        assertClose(0.5, Phi2X.resonanceK(1.0, 1.0 + Math.PI / 2), 1e-12)
    }

    @Test
    fun `la resonance est symetrique et bornee`() {
        for (a in 0..12) {
            for (b in 0..12) {
                val pa = a / 12.0 * Phi2X.TAU
                val pb = b / 12.0 * Phi2X.TAU
                val k = Phi2X.resonanceK(pa, pb)
                assertClose(k, Phi2X.resonanceK(pb, pa), 1e-12)
                assertTrue("k hors [0,5 ; 1] : $k", k >= 0.5 - 1e-12 && k <= 1.0 + 1e-12)
            }
        }
    }

    /** En phase ou en opposition, à la tolérance près — et le tour boucle. */
    @Test
    fun `la singularite optique se voit aux deux bouts`() {
        assertTrue(Phi2X.isOpticalSingularity(1.0, 1.0))
        assertTrue(Phi2X.isOpticalSingularity(1.0, 1.0 + Math.PI))
        assertTrue(Phi2X.isOpticalSingularity(0.01, Phi2X.TAU - 0.01))
        assertFalse(Phi2X.isOpticalSingularity(1.0, 1.0 + Math.PI / 2))
    }

    @Test
    fun `le KIN suit la station`() {
        // Mêmes fiches, mêmes valeurs que le python : kin, sceau, ton, couleur.
        KinMaya.of(1982, 9, 18)!!.let {
            assertEquals(83, it.kin); assertEquals(2, it.glyph)
            assertEquals(4, it.tone); assertEquals(1, it.color)
        }
        KinMaya.of(1948, 9, 8)!!.let {
            assertEquals(143, it.kin); assertEquals(2, it.glyph)
            assertEquals(12, it.tone); assertEquals(0, it.color)
        }
        KinMaya.of(1985, 4, 17)!!.let {
            assertEquals(244, it.kin); assertEquals(3, it.glyph)
            assertEquals(9, it.tone); assertEquals(3, it.color)
        }
        KinMaya.of(2000, 1, 1)!!.let {
            assertEquals(153, it.kin); assertEquals(12, it.glyph)
            assertEquals(9, it.tone); assertEquals(1, it.color)
        }
    }

    /**
     * L'anomalie de la station, épinglée pour qu'elle ne se répare pas toute
     * seule : une seule soustraction au lieu d'un modulo laisse quelques dates
     * de fin septembre au-delà des 260 cases du Tzolkin. cabine-33 rend 3 pour
     * la même date. À porter à Fred — d'ici là, nous disons ce que dit la
     * station, parce que c'est son `kin_num` qui reviendra dans le MULTIPASS.
     */
    @Test
    fun `le KIN de fin septembre deborde comme chez la station`() {
        assertEquals(263, KinMaya.of(1944, 9, 28)!!.kin)
    }

    @Test
    fun `le KIN ne demande que la date`() {
        val sansHeure = BirthData.Sample.copy(hour = null, minute = null)
        assertEquals(KinMaya.of(BirthData.Sample), KinMaya.of(sansHeure))
        assertNull(KinMaya.of(BirthData.Empty))
        assertNull(KinMaya.of(1985, 13, 1))
        assertNull(KinMaya.of(1985, 4, 32))
    }

    @Test
    fun `le sceau de Fred et celui du plateau ne different que d'un cran`() {
        val kin = KinMaya.of(1982, 9, 18)!!
        val card = one.astroport.atom4love.domain.AtomCard(kin = kin.kin, wave = Wave.Phi)
        assertEquals(card.seal, kin.glyph + 1)
        assertEquals(card.tone, kin.tone + 1)
    }
}
