package one.astroport.atom4love.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ω_bio face à la station de référence.
 *
 * Les valeurs attendues ne sont pas recalculées à la main : elles sortent de
 * `tools/phi2x.py` d'Astroport.ONE, exécuté le 2026-08-14 sur les mêmes
 * entrées (`python3 phi2x.py --omega h w sexe`). Un portage qui dérive
 * silencieusement de la station est exactement ce qui est arrivé à la phase —
 * ce test est là pour que ça ne recommence pas.
 */
class OmegaBioTest {

    private fun body(h: Int, w: Float) = BodyMetrics(heightCm = h, weightKg = w)

    @Test
    fun `l'onde Phi suit compute_omega_bio de la station`() {
        // phi2x.py : compute_omega_bio(170, 70, 0) = 225.808272
        assertEquals(
            225.808272,
            Phi2X.omegaBio(body(170, 70f), Wave.Phi)!!,
            1e-6,
        )
    }

    @Test
    fun `l'onde Octave suit compute_omega_bio de la station`() {
        // phi2x.py : compute_omega_bio(165, 60, 1) = 186.789570
        assertEquals(
            186.789570,
            Phi2X.omegaBio(body(165, 60f), Wave.Octave)!!,
            1e-6,
        )
    }

    @Test
    fun `les bornes hautes restent alignees`() {
        // phi2x.py : compute_omega_bio(250, 250, 0) = 649.953686
        assertEquals(
            649.953686,
            Phi2X.omegaBio(body(250, 250f), Wave.Phi)!!,
            1e-6,
        )
    }

    /**
     * Le plancher de la formule d'origine : `max(…, 1.0)`. Il ne peut pas se
     * déclencher depuis l'écran — les rouleaux ne descendent pas si bas — mais
     * il fait partie du portage, et l'omettre rendrait une eau négative le jour
     * où une valeur arriverait d'ailleurs.
     */
    @Test
    fun `l'eau ne descend jamais sous un kilo`() {
        assertEquals(1.0, Phi2X.waterKg(1, 1f, 0), 1e-9)
        assertEquals(Phi2X.F_WATER / 70.0, Phi2X.omegaBio(body(1, 1f), Wave.Phi)!!, 1e-9)
    }

    /**
     * Sans les deux mesures et la polarité, il n'y a pas d'onde biologique.
     * Rendre zéro serait annoncer une fréquence que personne n'a.
     */
    @Test
    fun `une mesure manquante ne donne aucune onde`() {
        assertNull(Phi2X.omegaBio(BodyMetrics.Empty, Wave.Phi))
        assertNull(Phi2X.omegaBio(BodyMetrics(heightCm = 170, weightKg = null), Wave.Phi))
        assertNull(Phi2X.omegaBio(BodyMetrics(heightCm = null, weightKg = 70f), Wave.Phi))
        assertNull(Phi2X.omegaBio(body(170, 70f), wave = null))
    }
}
