package one.astroport.atom4love.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La conversion solaire→UTC face à la station.
 *
 * Toutes les valeurs attendues sortent de `local_solar_to_utc` /
 * `build_salt_raw` d'`atom4love_publish.py`, exécutés le 2026-08-14 sur les
 * mêmes entrées. C'est cet instant-là qui entre dans le SALT, dans le PEPPER,
 * dans la phase et dans le KIN : un écart d'une minute ici donne une autre clé.
 */
class SolarTimeTest {

    private fun fiche(
        year: Int, month: Int, day: Int, hour: Int, minute: Int,
        lat: Double, lon: Double, wave: Wave = Wave.Phi, weight: Float = 3.2f,
    ) = BirthData(
        year = year, month = month, day = day, hour = hour, minute = minute,
        placeName = "", lat = lat, lon = lon, wave = wave, weightKg = weight,
    )

    @Test
    fun `l'equation du temps suit celle de la station`() {
        // phi2x/publish : _equation_of_time(1985, 4, 17) = 0.264048980
        assertEquals(
            0.264048980,
            SolarTime.equationOfTimeMinutes(LocalDate.of(1985, 4, 17)),
            1e-9,
        )
    }

    /**
     * Paris : dix minutes de moins que l'heure d'horloge. C'est tout l'écart
     * qui séparait cette station de la sienne — invisible à l'œil, décisif
     * pour une clé.
     */
    @Test
    fun `Paris recule de dix minutes`() {
        assertEquals(
            "198504171520_48.86_2.35_0_3.2_50_170",
            LoveKey.salt(fiche(1985, 4, 17, 15, 30, 48.86, 2.35)),
        )
    }

    /**
     * Tokyo, cinq minutes après minuit : la conversion recule d'un jour entier.
     * Le SALT, la phase et le KIN basculent tous les trois — c'est le cas qui
     * prouve qu'on ne pouvait pas se contenter de l'heure d'horloge.
     */
    @Test
    fun `une naissance juste apres minuit a l'est recule d'un jour`() {
        val tokyo = fiche(1985, 4, 17, 0, 5, 35.68, 139.69)
        assertEquals(
            "198504161446_35.68_139.69_0_3.2_50_170",
            LoveKey.salt(tokyo),
        )
        assertEquals(16, tokyo.birthInstantUtc!!.dayOfMonth)
        assertEquals(KinMaya.of(1985, 4, 16), KinMaya.of(tokyo))
    }

    /** Lima, fin de soirée : la conversion avance d'un jour, symétriquement. */
    @Test
    fun `une naissance en fin de soiree a l'ouest avance d'un jour`() {
        val lima = fiche(1985, 4, 17, 23, 50, -12.05, -77.04, wave = Wave.Octave)
        assertEquals(
            "198504180458_-12.05_-77.04_1_3.2_50_170",
            LoveKey.salt(lima),
        )
        assertEquals(KinMaya.of(1985, 4, 18), KinMaya.of(lima))
    }

    /** Le PEPPER : 280 jours plus tôt, à midi d'horloge, converti pareil. */
    @Test
    fun `le PEPPER suit build_pepper_raw`() {
        assertEquals(
            "198407111156_48.86_2.35_3.2_50",
            LoveKey.pepper(BirthData.Sample),
        )
    }

    /** L'instant que la phase reçoit — `birth_dt_utc.timestamp()` chez elle. */
    @Test
    fun `la phase part de l'instant converti`() {
        assertEquals(482_599_200L, Phi2X.birthUnixUtc(BirthData.Sample))
    }

    /**
     * Le vecteur de bout en bout : la fiche d'exemple passée à la station,
     * et tout ce qu'elle en dit. Chaque valeur vient de son python, sur
     * l'instant converti — c'est le seul test qui vérifie que les quatre
     * calculs partent bien du même endroit.
     *
     * Manque le npub : la station le tire de son binaire `keygen`, qu'on n'a
     * pas. C'est la dernière pièce qui nous sépare d'une redérivation complète.
     */
    @Test
    fun `la fiche d'exemple, de bout en bout`() {
        val b = BirthData.Sample
        assertEquals("198504171520_48.86_2.35_0_3.2_50_170", LoveKey.salt(b))
        assertEquals("198407111156_48.86_2.35_3.2_50", LoveKey.pepper(b))
        // phi2x.py : compute_personal_phase(482599200, 48.86, 2.35)
        assertEquals(1.435898643, Phi2X.personalPhase(b)!!, 1e-9)
        // phi2x.py : calc_kin_unix(482599200) → 244, Graine, ton 10, Jaune
        assertEquals(244, KinMaya.of(b)!!.kin)
    }
}
