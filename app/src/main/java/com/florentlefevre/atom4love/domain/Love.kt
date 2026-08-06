package com.florentlefevre.atom4love.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Onde biologique. Le sexe entre tel quel dans le SALT : 0 = Onde Φ, 1 = Onde Octave. */
enum class Wave(val sex: Int, val symbol: String, val label: String) {
    Phi(0, "Φ", "Onde Φ"),
    Octave(1, "8", "Onde Octave"),
}

/**
 * Les cinq données d'incarnation. Immuables une fois la clé forgée : c'est ce qui
 * permet à n'importe quelle station de redériver la même clé LOVE, et à vos proches
 * de servir de phrase de récupération.
 */
data class BirthData(
    val year: Int,
    val month: Int,          // 1..12
    val day: Int,            // 1..31
    val hour: Int,           // 0..23
    val minute: Int,         // 0..59
    val placeName: String,
    val lat: Double,
    val lon: Double,
    val wave: Wave,
    val weightKg: Float,
) {
    companion object {
        /** Le jeu d'exemple de la maquette. */
        val Sample = BirthData(
            year = 1985, month = 4, day = 17, hour = 15, minute = 30,
            placeName = "Paris, France", lat = 48.86, lon = 2.35,
            wave = Wave.Phi, weightKg = 3.2f,
        )
    }
}

/**
 * Dérivation de la clé LOVE.
 *
 * SALT   = `AAAAMMJJHHmn_lat_lon_sexe_poids`
 * PEPPER = date de conception, calculée depuis le poids de naissance :
 *          gestation = 280 + (poids − 3,5) × 4 jours.
 */
object LoveKey {

    private val saltFormat = Locale.US   // séparateur décimal « . » dans le SALT

    fun salt(b: BirthData): String = String.format(
        saltFormat,
        "%04d%02d%02d%02d%02d_%.2f_%.2f_%d_%.1f",
        b.year, b.month, b.day, b.hour, b.minute,
        b.lat, b.lon, b.wave.sex, b.weightKg,
    )

    /** Durée de gestation en jours, dérivée du poids de naissance. */
    fun gestationDays(weightKg: Float): Double = 280.0 + (weightKg - 3.5) * 4.0

    /** PEPPER : l'instant de conception, gestation avant la naissance. */
    fun conception(b: BirthData): Date {
        val birth = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(b.year, b.month - 1, b.day, b.hour, b.minute)
        }
        val millis = (gestationDays(b.weightKg) * 24.0 * 3600.0 * 1000.0).toLong()
        birth.timeInMillis = birth.timeInMillis - millis
        return birth.time
    }

    /** Millisecondes UTC de la naissance — ce qu'attend le sélecteur de date Material. */
    fun birthUtcMillis(b: BirthData): Long =
        GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(b.year, b.month - 1, b.day, 0, 0, 0)
        }.timeInMillis

    /** Décompose des millisecondes UTC en (année, mois 1..12, jour). */
    fun utcDateParts(millis: Long): Triple<Int, Int, Int> {
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private val shortDate = SimpleDateFormat("d MMM yyyy", Locale.FRENCH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun formatDate(d: Date): String = shortDate.format(d)

    /** « 278,8 jours » — virgule décimale française. */
    fun formatDays(days: Double): String = String.format(Locale.FRANCE, "%.1f jours", days)

    /** « 3,2 kg ». */
    fun formatWeight(kg: Float): String = String.format(Locale.FRANCE, "%.1f kg", kg)

    /** « 48.86 · 2.35 » — les coordonnées telles qu'elles entrent dans le SALT. */
    fun formatCoords(b: BirthData): String =
        String.format(saltFormat, "%.2f · %.2f", b.lat, b.lon)
}
