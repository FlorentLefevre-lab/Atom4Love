package one.astroport.atom4love.proximity

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.location.LocationManagerCompat
import com.uber.h3core.H3Core
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.geo.DeviceLocation

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
    enum class Blocker { PERMISSION, SERVICE_OFF }

    fun blocker(): Blocker? {
        if (!DeviceLocation.granted(context)) return Blocker.PERMISSION
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager != null && !LocationManagerCompat.isLocationEnabled(manager)) {
            return Blocker.SERVICE_OFF
        }
        return null
    }

    /** null si la permission de localisation manque ou qu'aucune position n'arrive. */
    suspend fun currentCell(): Long? = currentFix()?.cell

    /** null si la permission de localisation manque ou qu'aucune position n'arrive. */
    suspend fun currentFix(): Fix? {
        val location = DeviceLocation.current(context) ?: return null

        Log.d(TAG, "position obtenue (précision ${location.accuracy} m)")
        return runCatching {
            val cell = h3.latLngToCell(location.latitude, location.longitude, BuildConfig.H3_RESOLUTION)
            val center = h3.cellToLatLng(cell)
            val distance = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                center.lat, center.lng, distance,
            )
            Fix(
                lat = location.latitude,
                lon = location.longitude,
                cell = cell,
                centerLat = center.lat,
                centerLon = center.lng,
                distanceToCenterM = distance[0].toDouble(),
            )
        }.onFailure { Log.w(TAG, "indexation H3 impossible", it) }.getOrNull()
    }
}