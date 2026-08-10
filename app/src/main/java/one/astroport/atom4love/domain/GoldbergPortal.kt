package one.astroport.atom4love.domain

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Les 12 portails du polyèdre de Goldberg.
 *
 * La grille H3 est un pavage de Goldberg de l'icosaèdre : ses 12 pentagones
 * (résolution 0) en marquent les sommets. Le portail d'un lieu est le sommet
 * le plus proche à vol d'oiseau.
 *
 * Les centres sont figés ici depuis H3 4.x — [GoldbergPortalTest] les
 * revalide contre la vraie grille à chaque build. La projection reste ainsi
 * pure JVM : reproductible sur n'importe quelle station, sans lib native,
 * même avant toute permission de localisation.
 *
 * Chaque portail porte le nom d'une étoile brillante ; l'ordre canonique est
 * le numéro de cellule de base H3 croissant (4 → 117) — même liste sur toutes
 * les stations, à ne jamais réordonner.
 */
object GoldbergPortal {

    data class Portal(
        val index: Int,          // 1..12
        val star: String,
        val latDeg: Double,
        val lonDeg: Double,
    ) {
        val code: String get() = String.format(Locale.US, "a4l:P%02d", index)
        val label: String get() = "$code · $star"
    }

    val All: List<Portal> = listOf(
        Portal(1, "Sirius", 64.700000, 10.536199),         // base 4  — mer de Norvège
        Portal(2, "Canopus", 50.103201, -143.478490),      // base 14 — golfe d'Alaska
        Portal(3, "Vega", 39.100000, 122.300000),          // base 24 — mer de Bohai
        Portal(4, "Arcturus", 23.717925, -67.132326),      // base 38 — Atlantique, Sargasses
        Portal(5, "Rigel", 10.447345, 58.157706),          // base 49 — mer d'Arabie
        Portal(6, "Procyon", 2.300882, -5.245390),         // base 58 — golfe de Guinée
        Portal(7, "Altair", -2.300882, 174.754610),        // base 63 — Pacifique, Gilbert
        Portal(8, "Betelgeuse", -10.447345, -121.842294),  // base 72 — Pacifique sud
        Portal(9, "Aldebaran", -23.717925, 112.867674),    // base 83 — large de l'Australie
        Portal(10, "Antares", -39.100000, -57.700000),     // base 97 — large du Río de la Plata
        Portal(11, "Pollux", -50.103201, 36.521510),       // base 107 — océan Austral
        Portal(12, "Spica", -64.700000, -169.463801),      // base 117 — mer de Ross
    )

    /** Le portail d'un lieu : le sommet le plus proche en distance orthodromique. */
    fun nearest(latDeg: Double, lonDeg: Double): Portal =
        All.minBy { haversineKm(latDeg, lonDeg, it.latDeg, it.lonDeg) }

    private const val EARTH_RADIUS_KM = 6371.0

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }
}
