package one.astroport.atom4love.domain

import android.content.res.Resources
import androidx.annotation.StringRes
import one.astroport.atom4love.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Onde biologique. Le sexe entre tel quel dans le SALT : 0 = Onde Φ, 1 = Onde
 * Octave. **Seul [sex] entre dans la dérivation** — le nom s'affiche, il suit
 * donc la langue ; le symbole est un glyphe et n'appartient à aucune.
 */
enum class Wave(val sex: Int, val symbol: String, @StringRes val labelRes: Int) {
    Phi(0, "Φ", R.string.wave_phi),
    Octave(1, "8", R.string.wave_octave),
}

/**
 * Ce qui peut clocher dans une date de naissance saisie. Le domaine dit
 * *lequel* des trois cas ; l'écran dit *comment* on l'annonce, dans la langue
 * de l'appareil. [arg] est l'âge qui borne le cas, ou null quand il n'en faut
 * aucun.
 */
enum class DateProblem(@StringRes val messageRes: Int, val arg: Int?) {
    Future(R.string.date_problem_future, null),
    Minor(R.string.date_problem_minor, BirthData.MIN_AGE_YEARS),
    TooOld(R.string.date_problem_too_old, BirthData.MAX_AGE_YEARS),
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

    /**
     * Ce qui cloche dans la date. Une valeur, pas une phrase : ce contrôle vit
     * dans le domaine, qui n'a pas de `Context` — l'écran rend le texte.
     */
    fun dateProblem(today: LocalDate = LocalDate.now()): DateProblem? {
        val date = birthDate ?: return null
        return when {
            date.isAfter(today) -> DateProblem.Future
            date.isAfter(today.minusYears(MIN_AGE_YEARS.toLong())) -> DateProblem.Minor
            date.isBefore(today.minusYears(MAX_AGE_YEARS.toLong())) -> DateProblem.TooOld
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
 * Le SALT et ce que la station en calcule.
 *
 * SALT = `AAAAMMJJHHmn_lat_lon_sexe_poids`
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

    /**
     * La gestation, telle qu'Astroport.ONE la compte : 280 jours pour tout le
     * monde.
     *
     * L'app calculait autrefois une durée dérivée du poids de naissance
     * (280 + (poids − 3,5) × 4). C'était une invention locale, et la station
     * ne la connaît pas : son PEPPER prend la date de naissance moins 280 jours,
     * à midi solaire local. Afficher autre chose que ce qui fait la clé serait
     * mentir à qui relit sa fiche.
     */
    const val GESTATION_DAYS = 280L

    /**
     * La conception : [GESTATION_DAYS] jours avant la naissance, à midi.
     *
     * La station affine ce midi en heure solaire du lieu avant de le convertir
     * en UTC — quelques minutes d'écart sous nos longitudes, sans effet sur le
     * jour affiché ici.
     */
    fun conception(b: BirthData): Date {
        require(b.dateComplete) { "date de naissance manquante" }
        return GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(b.year!!, b.month!! - 1, b.day!!, 12, 0)
            add(Calendar.DAY_OF_YEAR, -GESTATION_DAYS.toInt())
        }.time
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

    /**
     * « 11 juil. 1984 » en français, « 11 Jul 1984 » en anglais. Le format se
     * construit à chaque appel plutôt qu'une fois pour toutes : la langue peut
     * changer sous l'application, et un `SimpleDateFormat` est de toute façon
     * mutable — le partager entre fils était déjà douteux.
     */
    fun formatDate(d: Date, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("d MMM yyyy", locale)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(d)

    /** « 280 jours » — la même durée pour tout le monde. */
    fun formatGestation(res: Resources): String =
        res.getString(R.string.format_gestation, GESTATION_DAYS)

    /** « 3,2 kg », ou « — » tant que le poids n'est pas saisi. */
    fun formatWeight(res: Resources, kg: Float?): String =
        if (kg == null) "—" else res.getString(R.string.format_weight, kg)

    /** « 48.86 · 2.35 » — les coordonnées telles qu'elles entrent dans le SALT. */
    fun formatCoords(b: BirthData): String =
        if (b.lat == null || b.lon == null) "— · —"
        else String.format(saltFormat, "%.2f · %.2f", b.lat, b.lon)
}
