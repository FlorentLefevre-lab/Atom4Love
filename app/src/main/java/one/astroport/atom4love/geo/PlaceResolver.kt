package one.astroport.atom4love.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

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
            // reste le seul chemin commun à minSdk 26, d'où l'IO dispatcher.
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
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Place? {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val fused = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        val fresh = suspendCancellableCoroutine<Location?> { cont ->
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { cancellation.cancel() }
        }
        val location = fresh
            ?: suspendCancellableCoroutine<Location?> { cont ->
                fused.lastLocation
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
            ?: return null

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
