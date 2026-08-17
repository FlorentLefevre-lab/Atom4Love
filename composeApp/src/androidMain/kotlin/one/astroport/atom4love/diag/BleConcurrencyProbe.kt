package one.astroport.atom4love.diag

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * POC — pendant BLE de [WifiConcurrencyProbe] : peut-on émettre une annonce BLE
 * portant le code « phix2 » ET scanner les annonces voisines en même temps ?
 *
 * Différence structurelle avec le Wi-Fi : ici le code est embarqué tel quel
 * dans le payload d'annonce (service data), là où le SSID d'un hotspot tiers
 * est imposé par le système. C'est la mesure clé de la comparaison.
 *
 * Limite assumée : un appareil ne reçoit pas ses propres annonces, donc un
 * seul téléphone ne peut pas prouver la détection d'un pair « phix2 » — le
 * compteur [BleProbeResult.peersWithCode] ne devient significatif qu'avec un
 * second appareil qui fait tourner la même sonde.
 */
class BleConcurrencyProbe(private val context: Context) {

    companion object {
        /** UUID 16 bits de test (plage vendor) portant le service data « phix2 ». */
        val SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

        private const val ADVERTISE_TIMEOUT_MS = 10_000L
        private const val SCAN_WINDOW_MS = 10_000L
    }

    private val adapter: BluetoothAdapter? =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter

    @SuppressLint("MissingPermission")
    suspend fun run(code: String = "phix2"): BleProbeResult {
        val notes = mutableListOf<String>()

        val adapter = this.adapter
        if (adapter == null || !adapter.isEnabled) {
            notes += if (adapter == null) {
                "Pas d'adaptateur Bluetooth sur cet appareil."
            } else {
                "Bluetooth désactivé — l'activer puis relancer (une app ne peut plus " +
                    "l'activer elle-même depuis Android 13)."
            }
            return BleProbeResult(
                multipleAdvertisementSupported = null,
                advertisingStarted = false,
                advertiseFailLabel = "adaptateur indisponible",
                payloadCarriesCode = false,
                scanStarted = false,
                devicesSeenWhileAdvertising = 0,
                peersWithCode = 0,
                notes = notes,
            )
        }

        // --- 1. Capacités statiques ---
        val multiAdv = adapter.isMultipleAdvertisementSupported
        if (!multiAdv) {
            notes += "isMultipleAdvertisementSupported = false : le chipset ne déclare pas " +
                "l'annonce matérielle multiple — l'émission peut échouer ou monopoliser le slot."
        }

        // --- 2. Émission de l'annonce portant le code ---
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            notes += "bluetoothLeAdvertiser = null : ce chipset ne sait pas émettre d'annonce BLE."
            return BleProbeResult(
                multipleAdvertisementSupported = multiAdv,
                advertisingStarted = false,
                advertiseFailLabel = "advertiser indisponible",
                payloadCarriesCode = false,
                scanStarted = false,
                devicesSeenWhileAdvertising = 0,
                peersWithCode = 0,
                notes = notes,
            )
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        // Annonce legacy : 31 octets utiles. UUID 16 bits + « phix2 » (5 octets)
        // tiennent largement — c'est précisément ce que le SSID Wi-Fi interdisait.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(SERVICE_UUID)
            .addServiceData(SERVICE_UUID, code.toByteArray(Charsets.UTF_8))
            .build()

        var advertiseFailLabel: String? = null
        val started = withTimeoutOrNull(ADVERTISE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val cb = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onStartFailure(errorCode: Int) {
                        advertiseFailLabel = advertiseFailLabel(errorCode)
                        if (cont.isActive) cont.resume(false)
                    }
                }
                advertiser.startAdvertising(settings, data, cb)
                cont.invokeOnCancellation { runCatching { advertiser.stopAdvertising(cb) } }
                // stopAdvertising exige le même callback : on garde `cb` accessible
                // via la fermeture du finally plus bas en le rejouant ici.
                stopHandle = { runCatching { advertiser.stopAdvertising(cb) } }
            }
        } ?: false
        if (!started && advertiseFailLabel == null) {
            advertiseFailLabel = "timeout — pas de réponse de la pile BLE en 10 s"
        }

        try {
            if (!started) {
                return BleProbeResult(
                    multipleAdvertisementSupported = multiAdv,
                    advertisingStarted = false,
                    advertiseFailLabel = advertiseFailLabel,
                    payloadCarriesCode = false,
                    scanStarted = false,
                    devicesSeenWhileAdvertising = 0,
                    peersWithCode = 0,
                    notes = notes,
                )
            }

            // --- 3. Scan pendant l'émission ---
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                notes += "bluetoothLeScanner = null malgré un adaptateur actif."
                return BleProbeResult(
                    multipleAdvertisementSupported = multiAdv,
                    advertisingStarted = true,
                    advertiseFailLabel = null,
                    payloadCarriesCode = true,
                    scanStarted = false,
                    devicesSeenWhileAdvertising = 0,
                    peersWithCode = 0,
                    notes = notes,
                )
            }

            val seen = mutableSetOf<String>()
            val peers = mutableSetOf<String>()
            var scanFail: Int? = null
            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    seen += result.device.address
                    val record = result.scanRecord ?: return
                    android.util.Log.d(
                        "BleProbe",
                        "scan ${result.device.address} rssi=${result.rssi} " +
                            "uuids=${record.serviceUuids} " +
                            "svcData=${record.serviceData?.mapValues { it.value.toString(Charsets.UTF_8) }} " +
                            "raw=${record.bytes?.joinToString("") { "%02x".format(it) }}",
                    )
                    val carriesCode = record.serviceUuids?.contains(SERVICE_UUID) == true ||
                        record.getServiceData(SERVICE_UUID) != null
                    if (carriesCode) peers += result.device.address
                }

                override fun onScanFailed(errorCode: Int) {
                    scanFail = errorCode
                }
            }
            scanner.startScan(
                emptyList(),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            )
            delay(SCAN_WINDOW_MS) // fenêtre d'écoute, annonce toujours active.
            runCatching { scanner.stopScan(scanCallback) }

            if (scanFail != null) {
                notes += "onScanFailed(code=$scanFail) pendant l'émission : le scan a été " +
                    "refusé alors que l'annonce tournait."
            }
            if (peers.isEmpty()) {
                notes += "0 pair « phix2 » vu : normal avec un seul appareil (on ne reçoit " +
                    "pas ses propres annonces). Lancer la sonde sur un second téléphone " +
                    "pour valider la détection croisée."
            }

            return BleProbeResult(
                multipleAdvertisementSupported = multiAdv,
                advertisingStarted = true,
                advertiseFailLabel = null,
                payloadCarriesCode = true,
                scanStarted = scanFail == null,
                devicesSeenWhileAdvertising = seen.size,
                peersWithCode = peers.size,
                notes = notes,
            )
        } finally {
            stopHandle?.invoke() // toujours couper l'annonce.
            stopHandle = null
        }
    }

    /** Poignée d'arrêt de l'annonce en cours (le stop exige le même callback). */
    private var stopHandle: (() -> Unit)? = null

    private fun advertiseFailLabel(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ->
            "ADVERTISE_FAILED_DATA_TOO_LARGE — payload > 31 octets."
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
            "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS — plus de slot d'annonce disponible."
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED ->
            "ADVERTISE_FAILED_ALREADY_STARTED — annonce déjà en cours."
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR ->
            "ADVERTISE_FAILED_INTERNAL_ERROR — erreur interne de la pile."
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
            "ADVERTISE_FAILED_FEATURE_UNSUPPORTED — annonce non supportée par ce chipset."
        else -> "errorCode=$code (inconnu)"
    }
}

/** Verdict factuel de la sonde BLE — miroir de [ProbeResult] côté Wi-Fi. */
data class BleProbeResult(
    /** Chipset déclarant l'annonce multiple (null = adaptateur indisponible). */
    val multipleAdvertisementSupported: Boolean?,
    val advertisingStarted: Boolean,
    val advertiseFailLabel: String?,
    /** Le code « phix2 » est réellement diffusé dans le payload (vs SSID Wi-Fi imposé). */
    val payloadCarriesCode: Boolean,
    val scanStarted: Boolean,
    /** Appareils BLE distincts vus pendant que l'annonce tournait. */
    val devicesSeenWhileAdvertising: Int,
    /** Appareils diffusant eux aussi le service « phix2 » (0 attendu, seul). */
    val peersWithCode: Int,
    val notes: List<String>,
) {
    val concurrencyDemonstrated: Boolean
        get() = advertisingStarted && scanStarted && devicesSeenWhileAdvertising > 0

    val headline: String
        get() = when {
            concurrencyDemonstrated ->
                "FAISABLE — annonce « phix2 » active et $devicesSeenWhileAdvertising " +
                    "appareil(s) vu(s) au scan."
            advertisingStarted && scanStarted ->
                "PARTIEL — émission + scan actifs mais 0 appareil vu (environnement vide ?)."
            advertisingStarted ->
                "NON FAISABLE (empirique) — l'annonce tourne mais le scan a été refusé."
            else ->
                "INDÉTERMINÉ — l'annonce n'a pas démarré (${advertiseFailLabel ?: "cause inconnue"})."
        }
}
