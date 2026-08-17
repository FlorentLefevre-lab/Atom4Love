package one.astroport.atom4love.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import one.astroport.atom4love.R

/**
 * La projection des tuiles — Web Mercator (EPSG:3857), celle de tout le monde.
 *
 * Le monde y est un carré de côté 1. Ce n'est pas la projection équirectangulaire
 * qu'on utilisait : là-bas la latitude était proportionnelle à l'ordonnée, ici
 * elle passe par un logarithme, et les pôles partent à l'infini — d'où la coupe
 * à ±85,051°, la limite de tous les fournisseurs de tuiles.
 *
 * Il fallait en changer : une tuile est dessinée en Mercator, la poser sur une
 * carte équirectangulaire ferait glisser les côtes de plusieurs centaines de
 * kilomètres aux latitudes moyennes.
 */
object Mercator {

    /** Au-delà, la projection diverge. C'est la borne de la spécification. */
    const val MAX_LATITUDE = 85.05112877980659

    /** Longitude → abscisse, dans [0, 1]. */
    fun x(lonDeg: Double): Double = (lonDeg + 180.0) / 360.0

    /** Latitude → ordonnée, dans [0, 1], comptée vers le sud. */
    fun y(latDeg: Double): Double {
        val lat = latDeg.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180.0
        return (1.0 - asinh(tan(lat)) / PI) / 2.0
    }

    /** Abscisse → longitude. */
    fun lon(x: Double): Double = x * 360.0 - 180.0

    /** Ordonnée → latitude. */
    fun lat(y: Double): Double = atan(sinh(PI * (1.0 - 2.0 * y))) * 180.0 / PI
}

/**
 * Un fond de carte au choix.
 *
 * Le premier ne demande rien à personne : c'est le trait de côte embarqué, qui
 * marche en jardin fermé et ne dit à aucun serveur ce qu'on regarde. Les autres
 * sont des tuiles, donc un tiers qui voit défiler la fenêtre — c'est le prix de
 * la précision, et il se choisit.
 *
 * ⚠ **L'attribution n'est pas décorative** : la licence ODbL d'OpenStreetMap et
 * les conditions d'Esri l'exigent, et la carte l'affiche. `atomic_map.html` la
 * masque (`attributionControl:false`) — à signaler à Fred.
 */
@Immutable
enum class Basemap(
    @StringRes val labelRes: Int,
    /** null pour le fond vectoriel embarqué. */
    val urlTemplate: String?,
    val attribution: String?,
    val maxTileZoom: Int = 19,
    /** Les tuiles claires se retournent la nuit ; l'imagerie satellite, non. */
    val invertsInDark: Boolean = false,
) {
    Coastline(R.string.basemap_coastline, null, null),

    OpenStreetMap(
        R.string.basemap_osm,
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        "© OpenStreetMap",
        maxTileZoom = 19,
        invertsInDark = true,
    ),

    Satellite(
        R.string.basemap_satellite,
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        "Esri, Maxar, Earthstar Geographics",
        maxTileZoom = 19,
    ),
    ;

    val hasTiles: Boolean get() = urlTemplate != null

    fun url(z: Int, x: Int, y: Int): String? = urlTemplate
        ?.replace("{z}", z.toString())
        ?.replace("{x}", x.toString())
        ?.replace("{y}", y.toString())
}

/** Une tuile, à l'adresse où elle vit. */
data class TileKey(val basemap: Basemap, val z: Int, val x: Int, val y: Int)

/**
 * Va chercher une tuile. Coil tient les caches — mémoire et disque —, donc une
 * tuile déjà vue ne repart pas sur le réseau.
 *
 * null quand elle n'arrive pas : hors ligne, serveur muet, ou tuile inexistante
 * (les fournisseurs rendent 404 sur les zones sans données).
 */
suspend fun loadTile(context: Context, loader: ImageLoader, key: TileKey): ImageBitmap? {
    val url = key.basemap.url(key.z, key.x, key.y) ?: return null
    val result = loader.execute(
        ImageRequest.Builder(context)
            .data(url)
            // Les serveurs de tuiles refusent les agents anonymes, et la
            // politique d'usage d'OSM demande de se nommer.
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("User-Agent", "Atom4Love/0.1 (https://github.com/FlorentLefevre-lab/Atom4Love)")
                    .build(),
            )
            .build(),
    )
    return (result as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
}

/**
 * Le filtre du thème sombre — l'équivalent du `filter:` CSS de sa carte
 * (`brightness(.45) invert(1) contrast(1.1) saturate(.3) hue-rotate(195deg)`).
 *
 * On garde les trois premiers : désaturer, retourner, assombrir. La rotation de
 * teinte est ce qui donne à sa carte son bleu ; ici la couleur de la station
 * vient des points, et une carte teintée leur disputerait le regard.
 */
fun darkTileFilter(brightness: Float = 0.62f, saturation: Float = 0.35f): ColorFilter {
    val s = saturation
    val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
    // Saturation, puis inversion (v → 1 − v), puis luminosité : une seule
    // matrice, obtenue en multipliant les trois.
    fun row(a: Float, b: Float, c: Float) = floatArrayOf(
        -brightness * a, -brightness * b, -brightness * c, 0f, brightness * 255f,
    )
    return ColorFilter.colorMatrix(
        ColorMatrix(
            row(lr + s * (1 - lr), lg - s * lg, lb - s * lb) +
                row(lr - s * lr, lg + s * (1 - lg), lb - s * lb) +
                row(lr - s * lr, lg - s * lg, lb + s * (1 - lb)) +
                floatArrayOf(0f, 0f, 0f, 1f, 0f),
        ),
    )
}

/** log₂, pour passer d'une échelle à un niveau de tuile. */
fun log2(value: Float): Float = (ln(value.toDouble()) / ln(2.0)).toFloat()
