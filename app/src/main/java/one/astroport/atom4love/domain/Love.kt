package one.astroport.atom4love.domain

import android.content.res.Resources
import androidx.annotation.StringRes
import one.astroport.atom4love.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
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

    /**
     * L'heure qui entre dans le SALT — midi quand elle n'est pas connue.
     *
     * Peu de gens savent l'heure de leur naissance ; l'exiger fermait la porte
     * à qui ne peut pas la retrouver. Midi est la convention qu'Astroport.ONE
     * applique déjà à la conception (naissance − 280 jours, à midi solaire) :
     * on ne s'en invente pas une seconde.
     */
    val saltHour: Int get() = hour ?: DEFAULT_HOUR
    val saltMinute: Int get() = minute ?: DEFAULT_MINUTE

    /**
     * Le poids qui entre dans le SALT — 3,5 kg quand il n'est pas connu.
     *
     * Ce n'est pas un choix d'ici : `atom4love_publish.py` retient exactement
     * cette valeur quand son cinquième argument est vide. Une fiche sans poids
     * donne donc la même clé LOVE des deux côtés.
     */
    val saltWeightKg: Float get() = weightKg ?: DEFAULT_WEIGHT_KG

    /** Tout ce qu'exige le SALT est là : la clé peut être forgée. */
    val complete: Boolean
        get() = dateComplete && lat != null && lon != null && wave != null

    /**
     * **L'instant** de naissance, tel que la station le scelle : l'heure
     * d'horloge du lieu ramenée en UTC par la longitude et l'équation du temps
     * ([SolarTime.localSolarToUtc]).
     *
     * C'est la seule date qui compte — SALT, PEPPER, phase personnelle et KIN
     * en dérivent tous. Elle ne vaut null que si la date ou la longitude
     * manque : sans longitude, il n'y a pas d'instant, seulement une heure
     * d'horloge qui ne dit pas où elle a sonné.
     */
    val birthInstantUtc: LocalDateTime?
        get() {
            val date = birthDate ?: return null
            val lon = lon ?: return null
            return SolarTime.localSolarToUtc(date, saltHour, saltMinute, lon)
        }

    /**
     * L'instant de conception que scelle le PEPPER : 280 jours avant la
     * naissance, à **midi de l'horloge locale** ramené en UTC de la même façon.
     *
     * La station prend bien midi civil et non l'heure de naissance
     * (`local_solar_to_utc(…, 12, 0, lon)`), et 280 jours pour tout le monde —
     * `compute_conception_unix` de `phi2x.py` module cette durée par le poids,
     * mais ce n'est pas elle qui fabrique la clé.
     */
    val conceptionInstantUtc: LocalDateTime?
        get() {
            val date = birthDate?.minusDays(LoveKey.GESTATION_DAYS) ?: return null
            val lon = lon ?: return null
            return SolarTime.localSolarToUtc(date, DEFAULT_HOUR, DEFAULT_MINUTE, lon)
        }

    companion object {
        /** L'âge minimal pour forger un noyau — décision de produit, pas technique. */
        const val MIN_AGE_YEARS = 18

        /** Au-delà, c'est une faute de frappe bien plus probablement qu'un doyen. */
        const val MAX_AGE_YEARS = 120

        /** Midi : l'instant retenu quand l'heure de naissance reste inconnue. */
        const val DEFAULT_HOUR = 12
        const val DEFAULT_MINUTE = 0

        /** Le poids retenu quand il reste inconnu — celui de la station. */
        const val DEFAULT_WEIGHT_KG = 3.5f

        /**
         * Les bornes du rouleau du poids de naissance. Un nouveau-né, pas un
         * adulte : c'est ce qui empêche de confondre cette case avec celle du
         * corps d'aujourd'hui, juste en dessous à l'écran.
         */
        val BIRTH_WEIGHT_RANGE_KG = 0.5f..7.0f

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
 * Le SALT, le PEPPER, et ce que la station en calcule.
 *
 * ```
 * SALT   = AAAAMMJJHHmm_lat_lon_sexe_poids_50_170
 * PEPPER = AAAAMMJJHHmm_lat_lon_poids_50
 * ```
 *
 * Les deux tampons sont l'instant UTC de [BirthData.birthInstantUtc] et
 * [BirthData.conceptionInstantUtc], pas l'heure d'horloge.
 */
object LoveKey {

    private val saltFormat = Locale.US   // séparateur décimal « . » dans le SALT

    /**
     * Les deux tailles du SALT, en dur — `BIRTH_HEIGHT_CM_DEFAULT` et
     * `CURRENT_HEIGHT_CM_DEFAULT` d'`atom4love_publish.py`, où elles portent le
     * commentaire « non collectée ».
     *
     * ⚠ **Elles doivent le rester.** Cette station collecte désormais une
     * taille réelle ([BodyMetrics]) : la glisser ici ferait diverger notre clé
     * de celle de la station à la première personne qui ne mesure pas 1,70 m.
     * La taille sert à ω_bio, jamais au SALT.
     */
    private const val BIRTH_HEIGHT_CM = 50
    private const val CURRENT_HEIGHT_CM = 170

    /**
     * N'appeler que sur une fiche [BirthData.complete] — le SALT n'admet aucun
     * trou. L'heure et le poids, eux, restent facultatifs à la saisie : la
     * fiche les remplace par les valeurs par défaut de la station
     * ([BirthData.saltHour], [BirthData.saltWeightKg]) plutôt que de laisser
     * un blanc dans la clé.
     */
    fun salt(b: BirthData): String {
        require(b.complete) { "fiche d'incarnation incomplète" }
        return String.format(
            saltFormat,
            "%s_%.2f_%.2f_%d_%.1f_%d_%d",
            SolarTime.stamp(b.birthInstantUtc!!),
            b.lat!!, b.lon!!, b.wave!!.sex, b.saltWeightKg,
            BIRTH_HEIGHT_CM, CURRENT_HEIGHT_CM,
        )
    }

    /**
     * Le PEPPER — la conception, et le poids qui l'accompagne.
     *
     * La station en dérive la seconde moitié du secret ; ici il complète le
     * SALT dans le noyau provisoire. Même exigence : une fiche complète.
     */
    fun pepper(b: BirthData): String {
        require(b.complete) { "fiche d'incarnation incomplète" }
        return String.format(
            saltFormat,
            "%s_%.2f_%.2f_%.1f_%d",
            SolarTime.stamp(b.conceptionInstantUtc!!),
            b.lat!!, b.lon!!, b.saltWeightKg, BIRTH_HEIGHT_CM,
        )
    }

    /**
     * La gestation que compte le PEPPER d'Astroport.ONE : 280 jours pour tout
     * le monde.
     *
     * L'app calculait autrefois une durée dérivée du poids de naissance
     * (280 + (poids − 3,5) × 4). Ce n'est pas une invention locale — c'est
     * `phi2x.py::compute_conception_unix`, à la ligne près. Mais ce n'est pas
     * elle qui fabrique la clé : `atom4love_publish.py` prend 280 jours secs
     * pour son PEPPER. On suit ce qui fait la clé, pas ce qui l'entoure.
     */
    const val GESTATION_DAYS = 280L

    /**
     * La conception telle qu'elle s'affiche : l'instant du PEPPER.
     *
     * Sans longitude on ne peut pas convertir, et la fiche n'en a pas toujours
     * une quand cette date paraît à l'écran : on retombe alors sur midi UTC,
     * qui donne le même jour à quelques minutes près.
     */
    fun conception(b: BirthData): Date {
        require(b.dateComplete) { "date de naissance manquante" }
        b.conceptionInstantUtc?.let {
            return Date(it.toEpochSecond(ZoneOffset.UTC) * 1000L)
        }
        return GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(b.year!!, b.month!! - 1, b.day!!, 12, 0)
            add(Calendar.DAY_OF_YEAR, -GESTATION_DAYS.toInt())
        }.time
    }

    // `birthUtcMillis` et `utcDateParts` vivaient ici pour le sélecteur de date
    // Material, qui parlait en millisecondes. Les rouleaux prennent et rendent
    // trois nombres : ces deux traductions n'avaient plus d'appelant.

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
