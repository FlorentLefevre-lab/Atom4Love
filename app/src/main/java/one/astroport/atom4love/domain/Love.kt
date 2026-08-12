package one.astroport.atom4love.domain

import java.text.SimpleDateFormat
import java.time.LocalDate
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
 * Les cinq données d'incarnation. Chaque champ est nul tant qu'il n'a pas été
 * saisi — la station démarre sur une fiche vierge, sans valeur d'exemple qui
 * fausserait une clé. Immuables une fois la clé forgée : c'est ce qui permet à
 * n'importe quelle station de redériver la même clé LOVE, et à vos proches de
 * servir de phrase de récupération.
 */
data class BirthData(
    val year: Int?,          // AAAA
    val month: Int?,         // 1..12
    val day: Int?,           // 1..31
    val hour: Int?,          // 0..23
    val minute: Int?,        // 0..59
    val placeName: String,
    val lat: Double?,
    val lon: Double?,
    val wave: Wave?,
    val weightKg: Float?,
) {
    val dateComplete: Boolean get() = year != null && month != null && day != null
    val timeComplete: Boolean get() = hour != null && minute != null

    /** La date de naissance, ou null tant qu'elle est incomplète ou absurde. */
    val birthDate: LocalDate?
        get() = if (!dateComplete) null
        else runCatching { LocalDate.of(year!!, month!!, day!!) }.getOrNull()

    /**
     * Une date qu'un être humain vivant peut avoir.
     *
     * Deux bornes, et rien de plus : on ne naît pas demain, et l'app ne
     * s'adresse pas aux mineurs. Ce contrôle porte sur la saisie, jamais sur un
     * noyau déjà scellé — une fiche forgée hier reste la sienne.
     *
     * [today] est explicite pour rester testable : une règle qui dépend du jour
     * courant ne se vérifie pas autrement.
     */
    fun isPlausible(today: LocalDate = LocalDate.now()): Boolean {
        val date = birthDate ?: return false
        return !date.isAfter(today.minusYears(MIN_AGE_YEARS.toLong())) &&
            !date.isBefore(today.minusYears(MAX_AGE_YEARS.toLong()))
    }

    /** Ce qui cloche dans la date, pour le dire à l'écran. Null si elle va. */
    fun dateProblem(today: LocalDate = LocalDate.now()): String? {
        val date = birthDate ?: return null
        return when {
            date.isAfter(today) -> "cette date est dans l'avenir"
            date.isAfter(today.minusYears(MIN_AGE_YEARS.toLong())) ->
                "Atom4Love s'adresse aux majeurs — $MIN_AGE_YEARS ans révolus"
            date.isBefore(today.minusYears(MAX_AGE_YEARS.toLong())) ->
                "au-delà de $MAX_AGE_YEARS ans, la date est sans doute une faute de frappe"
            else -> null
        }
    }

    /** Tout ce qu'exige le SALT est là : la clé peut être forgée. */
    val complete: Boolean
        get() = dateComplete && timeComplete && lat != null && lon != null &&
            wave != null && weightKg != null

    companion object {
        /** L'âge minimal pour forger un noyau — décision de produit, pas technique. */
        const val MIN_AGE_YEARS = 18

        /** Au-delà, c'est une faute de frappe bien plus probablement qu'un doyen. */
        const val MAX_AGE_YEARS = 120

        /** La fiche vierge du premier lancement. */
        val Empty = BirthData(
            year = null, month = null, day = null, hour = null, minute = null,
            placeName = "", lat = null, lon = null, wave = null, weightKg = null,
        )

        /** Le jeu d'exemple de la maquette — previews et tests uniquement. */
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

    /** N'appeler que sur une fiche [BirthData.complete] — le SALT n'admet aucun trou. */
    fun salt(b: BirthData): String {
        require(b.complete) { "fiche d'incarnation incomplète" }
        return String.format(
            saltFormat,
            "%04d%02d%02d%02d%02d_%.2f_%.2f_%d_%.1f",
            b.year!!, b.month!!, b.day!!, b.hour!!, b.minute!!,
            b.lat!!, b.lon!!, b.wave!!.sex, b.weightKg!!,
        )
    }

    /** Durée de gestation en jours, dérivée du poids de naissance. */
    fun gestationDays(weightKg: Float): Double = 280.0 + (weightKg - 3.5) * 4.0

    /** PEPPER : l'instant de conception, gestation avant la naissance. */
    fun conception(b: BirthData): Date {
        require(b.dateComplete && b.timeComplete && b.weightKg != null) {
            "instant ou poids de naissance manquant"
        }
        val birth = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(b.year!!, b.month!! - 1, b.day!!, b.hour!!, b.minute!!)
        }
        val millis = (gestationDays(b.weightKg) * 24.0 * 3600.0 * 1000.0).toLong()
        birth.timeInMillis = birth.timeInMillis - millis
        return birth.time
    }

    /**
     * Millisecondes UTC de la naissance — ce qu'attend le sélecteur de date
     * Material. null tant que la date n'est pas saisie (le sélecteur s'ouvre
     * alors sur le mois courant).
     */
    fun birthUtcMillis(b: BirthData): Long? {
        if (!b.dateComplete) return null
        return GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(b.year!!, b.month!! - 1, b.day!!, 0, 0, 0)
        }.timeInMillis
    }

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

    /** « 3,2 kg », ou « — » tant que le poids n'est pas saisi. */
    fun formatWeight(kg: Float?): String =
        if (kg == null) "—" else String.format(Locale.FRANCE, "%.1f kg", kg)

    /** « 48.86 · 2.35 » — les coordonnées telles qu'elles entrent dans le SALT. */
    fun formatCoords(b: BirthData): String =
        if (b.lat == null || b.lon == null) "— · —"
        else String.format(saltFormat, "%.2f · %.2f", b.lat, b.lon)
}
