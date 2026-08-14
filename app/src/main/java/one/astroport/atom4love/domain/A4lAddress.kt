package one.astroport.atom4love.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * L'adresse hexagonale `a4l:` — le seul lieu que porte un certificat ATOM4LOVE.
 *
 * Format `a4l:P<pp>H<qqqq><rrrr>` : le pentagone le plus proche, puis les
 * coordonnées axiales de la maille hexagonale de 1 km, chacune encodée en
 * `hex(valeur + 32768)` sur quatre chiffres. C'est ce que `geoTagA4L()` écrit
 * dans `phi2x.js`, et ce que la carte de Fred (`atomic_map.html`) redécode pour
 * poser ses marqueurs.
 *
 * **Aucune coordonnée en clair n'est publiée** : la maille de 1 km est tout ce
 * que le relais voit, et tout ce dont la carte a besoin. Le tag `g` du kind
 * 30078 `d=atom4love` porte le lieu de **naissance** — l'ancre du SALT, pas la
 * résidence ; celle-ci fait l'objet d'un événement séparé, `d=atom4love-home`,
 * que l'on ne publie que si on l'a choisi.
 *
 * On ne sait ici que **décoder**. Écrire une adresse demanderait la grille des
 * pentagones de Fred, qui tourne avec le temps et n'est pas celle des douze
 * portails de [GoldbergPortal] — ça viendra avec la publication de notre propre
 * certificat, pas avant.
 */
object A4lAddress {

    /** Arête de la maille, en kilomètres — `HEX_SIZE_KM` de `phi2x.js`. */
    const val HEX_SIZE_KM = 1.0

    private const val EARTH_RADIUS_KM = 6371.0

    /** `P` puis le pentagone, `H` puis q et r sur quatre chiffres hexadécimaux chacun. */
    private val PATTERN = Regex("""a4l:P(\d+)H([0-9a-fA-F]{4})([0-9a-fA-F]{4})""")

    /** Un lieu tel que la maille le rend : au kilomètre, jamais plus fin. */
    data class Place(
        val latDeg: Double,
        val lonDeg: Double,
        val pentagonId: Int,
        val q: Int,
        val r: Int,
    )

    /**
     * Décode une adresse. Inverse exact de `geoTagA4L()` : `r` donne l'est,
     * `q` et `r` ensemble donnent le nord.
     *
     * ```
     * q = (√3/3·x − y/3) / taille      →   x = √3·(q·taille + y/3)
     * r = (2/3·y) / taille             →   y = 1,5·r·taille
     * ```
     *
     * null si la chaîne n'est pas une adresse, ou si elle sort du globe — un
     * `q`/`r` arbitraire produit des degrés qui ne veulent rien dire.
     */
    fun decode(text: String): Place? {
        val m = PATTERN.find(text) ?: return null
        val pentagonId = m.groupValues[1].toIntOrNull() ?: return null
        val q = m.groupValues[2].toInt(16) - 32768
        val r = m.groupValues[3].toInt(16) - 32768

        val yKm = 1.5 * r * HEX_SIZE_KM
        val xKm = sqrt(3.0) * (q * HEX_SIZE_KM + yKm / 3.0)

        val degPerKm = EARTH_RADIUS_KM * PI / 180.0
        val lat = xKm / degPerKm
        val cosLat = cos(lat * PI / 180.0)
        // Aux pôles le parallèle se referme : la longitude n'y porte plus rien.
        val lon = if (abs(cosLat) > 0.001) yKm / (degPerKm * cosLat) else 0.0

        if (lat.isNaN() || lon.isNaN()) return null
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
        return Place(lat, lon, pentagonId, q, r)
    }

    /** La première adresse trouvée dans les tags `g` d'un événement, s'il y en a une. */
    fun fromTags(tags: List<List<String>>): Place? = tags.firstNotNullOfOrNull { tag ->
        if (tag.size >= 2 && tag[0] == "g") decode(tag[1]) else null
    }
}
