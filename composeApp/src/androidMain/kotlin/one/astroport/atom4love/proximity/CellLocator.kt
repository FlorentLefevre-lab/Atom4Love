package one.astroport.atom4love.proximity

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.location.LocationManagerCompat
import com.uber.h3core.H3Core
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.geo.DeviceLocation
import kotlin.math.cos
import kotlin.math.sin

/**
 * Résout la cellule H3 courante (résolution [BuildConfig.H3_RESOLUTION], ~460 m
 * d'arête à la résolution 8).
 *
 * La position ne sert qu'à calculer l'index de cellule, sur l'appareil ; elle ne
 * quitte jamais le device — seule l'adresse 4D (index tourné par [CellRotation])
 * est diffusée.
 */
class CellLocator(private val context: Context) {

    companion object {
        private const val TAG = "Proximity"

        /** Les azimuts échantillonnés autour d'une position — voir [fitsInCell]. */
        private const val BEARINGS = 8

        /**
         * Au-delà, une position ne désigne plus aucun hexagone : le rayon
         * intérieur d'une cellule de résolution 8 vaut ~400 m, et
         * l'« approximative » d'Android floute au kilomètre. Un fix GNSS
         * (5-50 m) comme un fix Wi-Fi (20-100 m) passent largement.
         */
        private const val MAX_ACCURACY_M = 150f

        /**
         * ⚠ **Mémoire de PROCESSUS.** Le portail courant est un fait de
         * l'appareil, pas de l'objet : la balise et l'écran ont chacun leur
         * [CellLocator], et rien ne serait pire que d'annoncer un portail et
         * d'en afficher un autre.
         */
        @Volatile
        private var sticky: Long? = null

        /**
         * Quel portail garder quand le point tombe dans un autre.
         *
         * Se tenir à trente mètres d'un bord est ordinaire — l'A5 y était le
         * 20/08 —, et le bruit GPS fait alors sauter la cellule d'un relevé à
         * l'autre. Chaque saut change l'adresse annoncée et le jeton qui en
         * dérive : le portail clignoterait à l'écran pour quelqu'un
         * d'immobile.
         *
         * On ne déménage donc que sur une preuve : le cercle d'incertitude tout
         * entier dans la nouvelle cellule. Marcher vraiment jusqu'au voisin la
         * fournit en quelques pas ; osciller sur le bord ne la fournit jamais.
         */
        fun settle(previous: Long?, seen: Long, seenFitsEntirely: Boolean): Long = when {
            previous == null -> seen
            previous == seen -> seen
            seenFitsEntirely -> seen
            else -> previous
        }

        private const val EARTH_RADIUS_M = 6_371_000.0

        /**
         * La géométrie de [certain], sans Android autour : de quoi l'épingler
         * par un test. Vrai quand les [BEARINGS] points du cercle de rayon
         * [accuracyM] autour de (lat, lon) tombent tous dans [cell].
         */
        fun fitsInCell(
            h3: H3Core,
            lat: Double,
            lon: Double,
            accuracyM: Double,
            resolution: Int,
            cell: Long = h3.latLngToCell(lat, lon, resolution),
        ): Boolean {
            val latRad = Math.toRadians(lat)
            for (i in 0 until BEARINGS) {
                val bearing = 2 * Math.PI * i / BEARINGS
                // Décalage plan : à l'échelle de quelques centaines de mètres,
                // l'erreur de cette approximation se compte en centimètres.
                val dLat = Math.toDegrees(accuracyM * cos(bearing) / EARTH_RADIUS_M)
                val dLon = Math.toDegrees(
                    accuracyM * sin(bearing) / (EARTH_RADIUS_M * cos(latRad)),
                )
                if (h3.latLngToCell(lat + dLat, lon + dLon, resolution) != cell) return false
            }
            return true
        }
    }

    // newSystemInstance() charge libh3-java.so via System.loadLibrary — le chemin
    // Android (jniLibs de l'AAR). newInstance() est le chemin desktop (extraction
    // du classpath) : il jette UnsatisfiedLinkError sur appareil.
    private val h3: H3Core by lazy { H3Core.newSystemInstance() }

    /**
     * Une résolution complète : la cellule, et ce qu'il faut à l'écran Radar —
     * position, centre géométrique de l'hexagone, distance à ce centre.
     * Comme la cellule, tout reste sur l'appareil.
     */
    data class Fix(
        val lat: Double,
        val lon: Double,
        val cell: Long,
        val centerLat: Double,
        val centerLon: Double,
        val distanceToCenterM: Double,
    )

    /**
     * Ce qui empêche la résolution — deux réglages distincts qu'un écran ne
     * doit pas confondre : la permission accordée à l'app, et l'interrupteur
     * Localisation du téléphone (réglages rapides), qu'un effleurement suffit
     * à couper sans que rien ne le signale.
     */
    enum class Blocker {
        /** Rien n'a été accordé. */
        PERMISSION,

        /**
         * ⚠ **Accordée, mais approximative.** Depuis Android 12 le dialogue
         * offre « Précise » ou « Approximative » ; la seconde ne donne que
         * `ACCESS_COARSE_LOCATION`, une position volontairement floutée à
         * l'échelle du kilomètre. Un hexagone de résolution 8 fait 920 m de
         * large : l'approximative ne peut pas le nommer, et se tromper de
         * portail est pire que ne pas en avoir — le jeton de présence est
         * dérivé de la cellule, deux voisins qui n'ont pas la même ne se
         * reconnaissent plus.
         */
        APPROXIMATE,

        /** L'interrupteur Localisation du téléphone est coupé. */
        SERVICE_OFF,

        /**
         * Tout est accordé et allumé, mais la position reçue est **trop floue
         * pour désigner un hexagone** — voir [precise].
         * ⚠ Ce cas-là ne rend pas le scan aveugle : la radio voit très bien,
         * c'est le lieu qu'on ne sait pas nommer.
         */
        IMPRECISE,
    }

    /**
     * La précision de la dernière position rejetée pour imprécision, en
     * mètres — de quoi l'écrire à l'écran plutôt que de dire « pas de
     * position », ce qui serait faux. null dès qu'une cellule est résolue.
     */
    @Volatile
    var lastImpreciseM: Float? = null
        private set

    fun blocker(): Blocker? {
        if (!DeviceLocation.granted(context)) {
            return if (DeviceLocation.approximateOnly(context)) {
                Blocker.APPROXIMATE
            } else {
                Blocker.PERMISSION
            }
        }
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager != null && !LocationManagerCompat.isLocationEnabled(manager)) {
            return Blocker.SERVICE_OFF
        }
        if (lastImpreciseM != null) return Blocker.IMPRECISE
        return null
    }

    /**
     * Le cercle d'incertitude tient-il **tout entier** dans l'hexagone ?
     *
     * Une position n'est pas un point : c'est un point et un rayon. Indexer le
     * seul point revient à parier que le rayon ne déborde pas — un pari qu'on
     * perd d'autant plus souvent qu'on se tient près d'un bord. On échantillonne
     * donc huit azimuts à la distance de la précision annoncée : si l'un d'eux
     * tombe dans une autre cellule, la nôtre n'est pas certaine, et on préfère
     * n'en nommer aucune.
     *
     * ⚠ Sans précision annoncée, on refuse : une position dont on ne sait rien
     * ne peut pas prouver qu'elle est dans l'hexagone.
     */
    /**
     * Assez précise pour qu'un hexagone veuille dire quelque chose ?
     *
     * ⚠ **Ce n'est PAS « le cercle tient dans l'hexagone ».** C'était la règle
     * du 20/08 au matin, et l'A5 l'a démentie le jour même : posé à 410 m du
     * centre de sa cellule — donc à quelques dizaines de mètres d'un bord —, un
     * fix GNSS honnête à ± 32 m était refusé, et l'appareil n'a plus jamais eu
     * de portail. Trois téléphones sur la même table, deux nommés, un muet, à
     * la seule loterie du bruit GPS. Un lieu où l'on se tient près d'un bord
     * n'est pas un lieu où l'on ne se tient pas.
     *
     * Ce qu'on refuse, c'est ce qui ne peut désigner AUCUN hexagone : le rayon
     * intérieur d'une cellule de résolution 8 vaut ~400 m, et l'approximative
     * d'Android floute au kilomètre. [MAX_ACCURACY_M] laisse passer tout fix
     * GNSS ou Wi-Fi et arrête ceux-là.
     *
     * La stabilité au bord, elle, est traitée ailleurs — par [settle].
     */
    private fun precise(location: Location): Boolean {
        if (!location.hasAccuracy()) {
            Log.i(TAG, "position sans précision annoncée : aucun portail nommé")
            return false
        }
        return location.accuracy <= MAX_ACCURACY_M
    }

    /** null si la permission de localisation manque ou qu'aucune position n'arrive. */
    suspend fun currentCell(): Long? = currentFix()?.cell

    /** null si la permission de localisation manque ou qu'aucune position n'arrive. */
    suspend fun currentFix(): Fix? {
        val location = DeviceLocation.current(context) ?: return null

        Log.d(TAG, "position obtenue (précision ${location.accuracy} m)")
        return runCatching {
            val cell = h3.latLngToCell(location.latitude, location.longitude, BuildConfig.H3_RESOLUTION)
            if (!precise(location)) {
                lastImpreciseM = location.accuracy
                Log.i(
                    TAG,
                    "position à ± ${location.accuracy.toInt()} m : trop floue pour " +
                        "désigner un hexagone, aucun portail nommé",
                )
                return@runCatching null
            }
            lastImpreciseM = null
            // ⚠ Le portail ne change qu'à bon escient — voir [settle].
            val settled = settle(
                previous = sticky,
                seen = cell,
                seenFitsEntirely = fitsInCell(
                    h3, location.latitude, location.longitude,
                    location.accuracy.toDouble(), BuildConfig.H3_RESOLUTION, cell,
                ),
            )
            if (settled != cell) {
                Log.i(
                    TAG,
                    "à cheval sur un bord (± ${location.accuracy.toInt()} m) : on garde " +
                        "${settled.toString(16)} plutôt que ${cell.toString(16)}",
                )
            }
            sticky = settled
            val center = h3.cellToLatLng(settled)
            val distance = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                center.lat, center.lng, distance,
            )
            Fix(
                lat = location.latitude,
                lon = location.longitude,
                cell = settled,
                centerLat = center.lat,
                centerLon = center.lng,
                distanceToCenterM = distance[0].toDouble(),
            )
        }.onFailure { Log.w(TAG, "indexation H3 impossible", it) }.getOrNull()
    }
}