package com.florentlefevre.atom4love

import com.florentlefevre.atom4love.domain.BirthData
import com.florentlefevre.atom4love.domain.LoveKey
import com.florentlefevre.atom4love.domain.Wave
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
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
    fun `la gestation vaut 280 jours corriges de 4 jours par 100 g`() {
        assertEquals(278.8, LoveKey.gestationDays(3.2f), 0.001)
        assertEquals(280.0, LoveKey.gestationDays(3.5f), 0.001)
        assertEquals(282.0, LoveKey.gestationDays(4.0f), 0.001)
    }

    @Test
    fun `la conception est la naissance moins la gestation`() {
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            time = LoveKey.conception(BirthData.Sample)
        }
        // 17 avril 1985 15h30 UTC − 278,8 jours = 12 juillet 1984 vers 20 h.
        // (La maquette affiche « 15 juil. 1984 » : une valeur d'illustration,
        // pas le résultat de la formule.)
        assertEquals(1984, c.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, c.get(Calendar.MONTH))
        assertEquals(12, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `un meme instant produit toujours le meme SALT`() {
        val a = BirthData.Sample
        val b = BirthData.Sample.copy(placeName = "Paname")   // le nom n'entre pas dans le SALT
        assertEquals(LoveKey.salt(a), LoveKey.salt(b))
    }
}
