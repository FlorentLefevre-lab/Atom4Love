package one.astroport.atom4love

import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.LoveKey
import one.astroport.atom4love.domain.Wave
import java.time.LocalDate
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La dérivation de la clé LOVE doit être reproductible à l'identique sur toute
 * station : c'est la seule chose que ces écrans calculent vraiment.
 */
class LoveKeyTest {

    @Test
    fun `le SALT suit le format AAAAMMJJHHmn_lat_lon_sexe_poids`() {
        assertEquals("198504171530_48.86_2.35_0_3.2", LoveKey.salt(BirthData.Sample))
    }

    @Test
    fun `l'onde Octave bascule le sexe a 1 dans le SALT`() {
        val octave = BirthData.Sample.copy(wave = Wave.Octave)
        assertEquals("198504171530_48.86_2.35_1_3.2", LoveKey.salt(octave))
    }

    @Test
    fun `la conception est la naissance moins 280 jours, convention Astroport`() {
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            time = LoveKey.conception(BirthData.Sample)
        }
        // 17 avril 1985 − 280 jours = 11 juillet 1984. La station applique la
        // même soustraction avant de placer l'instant à midi solaire local.
        assertEquals(1984, c.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, c.get(Calendar.MONTH))
        assertEquals(11, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `le poids de naissance ne deplace plus la conception`() {
        val leger = LoveKey.conception(BirthData.Sample.copy(weightKg = 2.5f))
        val lourd = LoveKey.conception(BirthData.Sample.copy(weightKg = 4.5f))
        assertEquals(leger, lourd)
    }

    // ── Plausibilité de la date de naissance ─────────────────────────────
    // Le jour de référence est fixé : une règle qui dépend de « aujourd'hui »
    // ne se teste pas autrement, et ces cas doivent tenir dans dix ans.
    private val today: LocalDate = LocalDate.of(2026, 8, 12)

    private fun born(y: Int, m: Int, d: Int) =
        BirthData.Sample.copy(year = y, month = m, day = d)

    @Test
    fun `on ne nait pas demain`() {
        assertFalse(born(2026, 8, 14).isPlausible(today))
        assertEquals("cette date est dans l'avenir", born(2026, 8, 14).dateProblem(today))
    }

    @Test
    fun `un mineur ne forge pas de noyau`() {
        // La veille des 18 ans : refusé. Le jour même : accepté.
        assertFalse(born(2008, 8, 13).isPlausible(today))
        assertTrue(born(2008, 8, 12).isPlausible(today))
    }

    @Test
    fun `au-dela de 120 ans, c'est une faute de frappe`() {
        assertFalse(born(1906, 8, 11).isPlausible(today))
        assertTrue(born(1906, 8, 12).isPlausible(today))
    }

    @Test
    fun `une date impossible au calendrier n'est jamais plausible`() {
        // 31 février : le sélecteur ne le propose pas, une fiche restaurée le peut.
        assertFalse(born(1985, 2, 31).isPlausible(today))
    }

    @Test
    fun `une date tenable ne signale aucun probleme`() {
        assertTrue(BirthData.Sample.isPlausible(today))
        assertNull(BirthData.Sample.dateProblem(today))
    }

    @Test
    fun `un meme instant produit toujours le meme SALT`() {
        val a = BirthData.Sample
        val b = BirthData.Sample.copy(placeName = "Paname")   // le nom n'entre pas dans le SALT
        assertEquals(LoveKey.salt(a), LoveKey.salt(b))
    }
}
