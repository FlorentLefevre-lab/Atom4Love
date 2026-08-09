package one.astroport.atom4love.diag

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * POC de faisabilité — la question posée : peut-on tenir un point d'accès
 * (SoftAP via LocalOnlyHotspot) ET scanner les réseaux station en même temps,
 * sur un appareil donné ?
 *
 * Classe volontairement isolée dans le package `diag` : aucune dépendance vers
 * le reste d'Atom4Love, supprimable d'un bloc si le concept est abandonné.
 * Elle ne décide rien — elle mesure et rend un verdict factuel.
 *
 * Deux niveaux de réponse :
 *  1. Capacité statique déclarée par le chipset (`isStaApConcurrencySupported`,
 *     API 30+) : le go/no-go théorique.
 *  2. Mesure empirique : on allume réellement l'AP, on lance un scan, on compte
 *     les résultats reçus pendant que l'AP est actif. C'est le go/no-go réel,
 *     car la capacité déclarée ne garantit pas que `startScan()` soit honoré
 *     pendant le tethering.
 */
class WifiConcurrencyProbe(private val context: Context) {

    private val wifi: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private sealed interface HotspotOutcome {
        data class Started(val reservation: WifiManager.LocalOnlyHotspotReservation) : HotspotOutcome
        data class Failed(val reason: Int) : HotspotOutcome
    }

    /**
     * Lance la sonde complète.
     *
     * @param apCode le « code phix2 » — SSID souhaité pour le point d'accès.
     *   Constat : une app tierce ne peut PAS l'imposer (API système requise),
     *   le SSID réellement généré est consigné dans le résultat.
     */
    suspend fun run(apCode: String = "phix2"): ProbeResult {
        val notes = mutableListOf<String>()

        if (!hasLocationPermission()) {
            notes += "Permission de localisation absente : le scan renverra une liste vide. " +
                "Accordez ACCESS_FINE_LOCATION puis relancez."
        }
        if (!wifi.isWifiEnabled) {
            notes += "Wi-Fi désactivé au démarrage de la sonde. LocalOnlyHotspot peut " +
                "l'activer temporairement, mais certains appareils refusent."
        }

        // --- 1. Capacité statique ---
        val staApConcurrency: Boolean? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wifi.isStaApConcurrencySupported
            } else {
                notes += "API < 30 : la concurrence STA+AP n'est pas interrogeable, " +
                    "seule la mesure empirique fait foi."
                null
            }

        // --- 2. Allumage du point d'accès ---
        val outcome = startHotspot(apCode, notes)
        val reservation = (outcome as? HotspotOutcome.Started)?.reservation

        if (reservation == null) {
            val reason = (outcome as? HotspotOutcome.Failed)?.reason
            if (reason == null) {
                notes += "LocalOnlyHotspot n'a ni démarré ni échoué en 15 s : " +
                    "l'appareil ne répond pas à la demande d'AP."
            }
            return ProbeResult(
                staApConcurrencySupported = staApConcurrency,
                hotspotStarted = false,
                hotspotSsid = null,
                hotspotFailReason = reason,
                hotspotFailLabel = reason?.let(::failReasonLabel) ?: "timeout",
                scanTriggerAccepted = false,
                scanResultsWhileApUp = 0,
                scanReceivedFreshBroadcast = false,
                notes = notes,
            )
        }

        try {
            // --- 3. Scan pendant que l'AP est actif ---
            val scan = scanWhileApUp(notes)
            return ProbeResult(
                staApConcurrencySupported = staApConcurrency,
                hotspotStarted = true,
                hotspotSsid = reservationSsid(reservation),
                hotspotFailReason = null,
                hotspotFailLabel = null,
                scanTriggerAccepted = scan.triggerAccepted,
                scanResultsWhileApUp = scan.resultCount,
                scanReceivedFreshBroadcast = scan.freshBroadcast,
                notes = notes,
            )
        } finally {
            reservation.close() // toujours éteindre l'AP, même si le scan lève.
        }
    }

    // --- LocalOnlyHotspot ---

    @SuppressLint("MissingPermission")
    private suspend fun startHotspot(apCode: String, notes: MutableList<String>): HotspotOutcome? =
        withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { cont ->
                val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        if (cont.isActive) cont.resume(HotspotOutcome.Started(reservation))
                    }

                    override fun onFailed(reason: Int) {
                        if (cont.isActive) cont.resume(HotspotOutcome.Failed(reason))
                    }
                }

                // Constat d'API : imposer SSID/clé à un LocalOnlyHotspot
                // (SoftApConfiguration.Builder + overload dédié) est @SystemApi,
                // inaccessible à une app tierce. Le système génère donc un réseau
                // au nom aléatoire — « $apCode » ne peut pas être le SSID sans
                // privilèges système. Le SSID réellement obtenu est consigné
                // dans le résultat.
                notes += "SSID imposé impossible pour une app non-système : " +
                    "« $apCode » ne peut pas nommer l'AP, le système génère un réseau aléatoire."
                wifi.startLocalOnlyHotspot(callback, mainHandler())
            }
        }

    // --- Scan station pendant l'AP ---

    private data class ScanOutcome(
        val triggerAccepted: Boolean,
        val resultCount: Int,
        val freshBroadcast: Boolean,
    )

    @SuppressLint("MissingPermission")
    private suspend fun scanWhileApUp(notes: MutableList<String>): ScanOutcome {
        var scanAccepted = false

        // true = broadcast reçu avec résultats frais, false = broadcast reçu mais
        // cache, null = aucun broadcast dans le délai.
        val broadcast: Boolean? = withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        val fresh = intent
                            ?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                        if (cont.isActive) cont.resume(fresh)
                    }
                }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                cont.invokeOnCancellation {
                    runCatching { context.unregisterReceiver(receiver) }
                }

                @Suppress("DEPRECATION")
                scanAccepted = wifi.startScan()
                if (!scanAccepted) {
                    notes += "startScan() a renvoyé false : scan refusé ou bridé (throttling) " +
                        "pendant que l'AP est actif. Résultats ci-dessous = cache éventuel."
                }
            }
        }
        if (broadcast == null) {
            notes += "Aucun broadcast de résultat de scan reçu en 20 s pendant l'AP : " +
                "forte présomption que le scan station est suspendu tant que l'AP tourne."
        }

        // Lecture des résultats disponibles, AP toujours allumé à cet instant.
        @Suppress("DEPRECATION")
        val results = runCatching { wifi.scanResults }.getOrDefault(emptyList())
        return ScanOutcome(
            triggerAccepted = scanAccepted,
            resultCount = results.size,
            freshBroadcast = broadcast == true,
        )
    }

    // --- Utilitaires ---

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun reservationSsid(reservation: WifiManager.LocalOnlyHotspotReservation): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reservation.softApConfiguration.ssid
        } else {
            @Suppress("DEPRECATION")
            reservation.wifiConfiguration?.SSID
        }

    private fun mainHandler(): Handler = Handler(Looper.getMainLooper())

    private fun failReasonLabel(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL ->
            "ERROR_NO_CHANNEL — pas de canal disponible (souvent : déjà connecté en Wi-Fi)."
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC ->
            "ERROR_GENERIC — échec non spécifié."
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE ->
            "ERROR_INCOMPATIBLE_MODE — mode AP incompatible avec l'état Wi-Fi courant."
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED ->
            "ERROR_TETHERING_DISALLOWED — partage de connexion interdit (politique / opérateur)."
        else -> "reason=$reason (inconnu)"
    }
}

/** Verdict factuel de la sonde. Aucune interprétation « produit », que des mesures. */
data class ProbeResult(
    /** Capacité déclarée par le chipset (API 30+). null = non interrogeable. */
    val staApConcurrencySupported: Boolean?,
    val hotspotStarted: Boolean,
    val hotspotSsid: String?,
    val hotspotFailReason: Int?,
    val hotspotFailLabel: String?,
    /** startScan() honoré pendant l'AP. */
    val scanTriggerAccepted: Boolean,
    /** Nombre de réseaux visibles, AP allumé. */
    val scanResultsWhileApUp: Int,
    /** Un broadcast de résultats frais (non issus du cache) est arrivé pendant l'AP. */
    val scanReceivedFreshBroadcast: Boolean,
    val notes: List<String>,
) {
    /** Le concept « AP + scan simultané » est-il empiriquement démontré ici ? */
    val concurrencyDemonstrated: Boolean
        get() = hotspotStarted && scanReceivedFreshBroadcast && scanResultsWhileApUp > 0

    val headline: String
        get() = when {
            concurrencyDemonstrated ->
                "FAISABLE — AP actif et scan frais : $scanResultsWhileApUp réseau(x) vu(s)."
            hotspotStarted && !scanReceivedFreshBroadcast ->
                "NON FAISABLE (empirique) — l'AP démarre mais le scan station est suspendu."
            else ->
                "INDÉTERMINÉ — l'AP n'a pas démarré (${hotspotFailLabel ?: "cause inconnue"})."
        }
}
