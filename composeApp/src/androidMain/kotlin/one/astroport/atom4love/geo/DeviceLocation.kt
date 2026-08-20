package one.astroport.atom4love.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * La position de l'appareil — le seul chemin de l'app, cellule H3 comme lieu de
 * naissance.
 *
 * ⚠ Mesuré le 17/08 sur le A5 sous LineageOS : demander au client fusionné une
 * position « équilibrée » n'allume jamais le GNSS. La requête part sur le seul
 * provider réseau, celui des services Google, qui ne répond pas sur un appareil
 * où le consentement Google n'a jamais été donné. Dehors, ciel dégagé, l'app
 * restait sans position — elle n'avait rien demandé au ciel.
 *
 * D'où trois puits, du moins cher au plus sûr :
 *  1. la dernière position connue, si elle est fraîche — gratuite ;
 *  2. le client fusionné en HAUTE précision — lui allume le GNSS ;
 *  3. le GPS de l'AOSP en direct — le seul chemin d'un appareil dégooglisé ;
 *  puis, faute de mieux, la dernière position connue quel que soit son âge.
 *
 * ⚠ Ce dernier repli ne décide plus de rien tout seul : c'est [
 * one.astroport.atom4love.proximity.CellLocator] qui juge si la précision
 * annoncée permet de nommer un hexagone, et qui refuse d'en nommer un quand le
 * cercle d'incertitude en déborde.
 *
 * Comme partout ailleurs, la position ne quitte pas l'appareil.
 */
object DeviceLocation {

    private const val TAG = "DeviceLocation"

    /** En deçà, la position connue vaut un fix : on n'allume rien. */
    private const val FRESH_ENOUGH_MS = 2 * 60_000L

    /** Ce qu'on laisse à une puce GNSS froide pour voir le ciel, par puits. */
    private const val FIX_TIMEOUT_MS = 15_000L

    /**
     * En deçà de quoi une position connue dispense d'allumer le ciel.
     *
     * ⚠ **La fraîcheur ne suffit pas.** Une position réseau vieille de dix
     * secondes peut annoncer ± 2 km : fraîche, et hors d'état de nommer un
     * hexagone de 920 m. Le raccourci demande donc les deux — récente **et**
     * précise —, faute de quoi on va chercher un vrai fix.
     */
    private const val PRECISE_ENOUGH_M = 50f

    /**
     * ⚠ **Seule la position PRÉCISE nomme un portail** (règle de Florent, le
     * 20/08). `ACCESS_COARSE_LOCATION` est déclarée au manifeste — Android 12
     * l'exige pour que la demande de `FINE` ne soit pas ignorée —, mais elle
     * n'est jamais suffisante ici : voir [approximateOnly].
     */
    fun granted(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * La personne a dit oui, mais **approximative** : `COARSE` sans `FINE`.
     * Le cas n'existe qu'à partir d'Android 12, où le dialogue offre le choix ;
     * en deçà — Android 10 et 11, notre plancher — accorder, c'est accorder la
     * précise. Il mérite sa propre phrase à l'écran : « rien accordé » et
     * « accordé, mais flouté » ne se corrigent pas du même geste.
     */
    fun approximateOnly(context: Context): Boolean =
        !granted(context) && ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** null si la permission manque ou qu'aucun des trois puits ne donne. */
    suspend fun current(context: Context): Location? {
        if (!granted(context)) return null

        val known = lastKnown(context)
        if (known != null && ageMs(known) < FRESH_ENOUGH_MS && precise(known)) {
            Log.d(TAG, "position connue fraîche (${ageMs(known) / 1000} s, ${known.accuracy} m)")
            return known
        }

        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) { fusedFix(context) }
            ?: withTimeoutOrNull(FIX_TIMEOUT_MS) { gnssFix(context) }
        if (fresh != null) {
            Log.d(TAG, "fix frais (${fresh.provider}, ${fresh.accuracy} m)")
            return fresh
        }

        if (known == null) Log.d(TAG, "aucune position : ni fix frais, ni position connue")
        else Log.d(TAG, "repli sur une position connue de ${ageMs(known) / 1000} s")
        return known
    }

    /**
     * Le client fusionné, en haute précision : c'est cette priorité-là, et
     * elle seule, qui démarre le GNSS. Sans services Google utiles, la tâche
     * échoue ou ne revient jamais — d'où le délai qui l'entoure.
     */
    @SuppressLint("MissingPermission")
    private suspend fun fusedFix(context: Context): Location? {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener {
                    Log.d(TAG, "client fusionné muet", it)
                    if (cont.isActive) cont.resume(null)
                }
            cont.invokeOnCancellation { cancellation.cancel() }
        }
    }

    /** Le GPS de l'AOSP, sans intermédiaire : aucun service tiers là-dedans. */
    @SuppressLint("MissingPermission")
    private suspend fun gnssFix(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null
        val signal = CancellationSignal()
        return suspendCancellableCoroutine { cont ->
            LocationManagerCompat.getCurrentLocation(
                manager,
                LocationManager.GPS_PROVIDER,
                signal,
                ContextCompat.getMainExecutor(context),
            ) { if (cont.isActive) cont.resume(it) }
            cont.invokeOnCancellation { signal.cancel() }
        }
    }

    /** La plus fraîche de toutes les dernières positions connues. */
    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching { manager.allProviders }.getOrDefault(emptyList())
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    /** Assez précise pour se passer du ciel — voir [PRECISE_ENOUGH_M]. */
    private fun precise(location: Location): Boolean =
        location.hasAccuracy() && location.accuracy <= PRECISE_ENOUGH_M

    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
}
