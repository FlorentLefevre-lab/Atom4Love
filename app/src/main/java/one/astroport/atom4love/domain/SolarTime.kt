package one.astroport.atom4love.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * L'heure d'un acte de naissance, ramenée à l'instant que la station scelle.
 *
 * Portage de `local_solar_to_utc` / `_equation_of_time` — `atom4love_publish.py`
 * d'Astroport.ONE, lui-même port de `zelkova/lib/g1/atomic_keys.dart`.
 *
 * **Pourquoi ce détour.** L'heure écrite sur un acte est celle de l'horloge du
 * lieu. La station n'en garde pas la lettre mais l'instant : elle retire le
 * décalage de longitude (4 minutes par degré) et l'équation du temps, puis
 * scelle le résultat. Tout ce qui compte en dérive — le SALT, le PEPPER, la
 * phase personnelle, le KIN. Cette station lisait jusqu'ici l'heure d'horloge
 * telle quelle : dix minutes d'écart à Paris, et un jour entier de part et
 * d'autre de la ligne de changement de date.
 *
 * ⚠ Ce n'est pas de l'astronomie de précision, et ce n'est pas le sujet :
 * l'équation du temps y est une approximation à un terme, le fuseau civil et
 * l'heure d'été sont ignorés. On porte ce que la station calcule, pas ce que le
 * ciel fait — un écart d'une minute ici donnerait une autre clé LOVE.
 */
object SolarTime {

    /**
     * L'équation du temps, en minutes — l'avance ou le retard du soleil vrai
     * sur le soleil moyen, ce jour-là.
     */
    fun equationOfTimeMinutes(date: LocalDate): Double {
        // Le quantième, à partir de 1 — `(date − 1er janvier) + 1` chez la station.
        val dayOfYear = ChronoUnit.DAYS.between(LocalDate.of(date.year, 1, 1), date) + 1
        val b = (2.0 * PI / 365.0) * (dayOfYear - 81)
        return 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
    }

    /**
     * L'heure d'horloge du lieu → l'instant UTC que la station scelle.
     *
     * Le report est celui de la station : les minutes s'ajoutent à **minuit du
     * jour de naissance**, si bien qu'une naissance juste après minuit à Tokyo
     * recule la veille, et une fin de soirée à Lima passe au lendemain. C'est
     * voulu — c'est cet instant-là qui entre dans la clé.
     */
    fun localSolarToUtc(
        date: LocalDate,
        hour: Int,
        minute: Int,
        lonDeg: Double,
    ): LocalDateTime {
        val offsetMin = lonDeg * 4.0 + equationOfTimeMinutes(date)
        // `Math.rint` et non `Math.round` : Python arrondit les demis vers le
        // pair, et c'est son résultat qui fait foi.
        val utcMin = Math.rint(hour * 60.0 + minute - offsetMin).toLong()
        return date.atStartOfDay().plusMinutes(utcMin)
    }

    /** `YYYYMMDDHHMM` — le tampon qu'attendent le SALT et le PEPPER. */
    fun stamp(instant: LocalDateTime): String = String.format(
        java.util.Locale.US,
        "%04d%02d%02d%02d%02d",
        instant.year, instant.monthValue, instant.dayOfMonth,
        instant.hour, instant.minute,
    )
}
