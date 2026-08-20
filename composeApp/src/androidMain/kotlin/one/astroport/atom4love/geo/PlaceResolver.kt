package one.astroport.atom4love.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Résolution du lieu de naissance : nom → coordonnées (saisie libre) et
 * position GPS → nom (bouton 📍). Comme pour la cellule H3, tout reste sur
 * l'appareil — les coordonnées n'entrent que dans le SALT de la clé LOVE.
 */
object PlaceResolver {

    private const val TAG = "PlaceResolver"

    data class Place(val name: String, val lat: Double, val lon: Double)

    /** Géocode un nom de lieu saisi librement. null si introuvable ou hors ligne. */
    suspend fun search(context: Context, query: String): Place? = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext null
        val address = runCatching {
            // L'API à écouteur n'existe qu'à partir d'API 33 ; l'appel bloquant
            // reste le seul chemin commun à minSdk 29, d'où l'IO dispatcher.
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocationName(query, 1)?.firstOrNull()
        }.onFailure { Log.w(TAG, "géocodage impossible pour « $query »", it) }.getOrNull()
            ?: return@withContext null
        Place(displayName(address) ?: query.trim(), address.latitude, address.longitude)
    }

    /**
     * Position actuelle de l'appareil + géocodage inverse pour le nom.
     * null si la permission manque ou qu'aucune position n'est disponible.
     */
    suspend fun current(context: Context): Place? {
        val location = DeviceLocation.current(context) ?: return null

        val name = reverseName(context, location.latitude, location.longitude)
            ?: String.format(Locale.US, "%.2f, %.2f", location.latitude, location.longitude)
        return Place(name, location.latitude, location.longitude)
    }

    private suspend fun reverseName(context: Context, lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            }.onFailure { Log.w(TAG, "géocodage inverse impossible", it) }
                .getOrNull()?.let(::displayName)
        }

    /** « Ville, Pays » — la maille de la fiche d'incarnation. */
    private fun displayName(a: Address): String? {
        val locality = a.locality ?: a.subAdminArea ?: a.adminArea
        val parts = listOfNotNull(locality, a.countryName)
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
}
