package one.astroport.atom4love.proximity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Cœur du module proximité : annonce BLE continue de l'adresse 4D + scan continu
 * des pairs, alimentant [NeighborRegistry].
 *
 * Leçon du POC ([one.astroport.atom4love.diag.BleConcurrencyProbe]) : deux sondes
 * à fenêtres courtes (~12 s) ne se voient que lancées simultanément. D'où le parti
 * pris inverse — annonce et scan permanents en mode BALANCED, portés par un service
 * foreground. Le scan est filtré sur [SERVICE_UUID] : c'est la condition pour
 * qu'Android le laisse tourner écran éteint au lieu de le throttler comme un scan nu.
 */
class ProximityEngine(
    context: Context,
    private val registry: NeighborRegistry,
    private val locator: CellLocator,
    private val rotation: CellRotation = CellRotation.None,
    /**
     * La clé publique du noyau, relue à chaque annonce : elle n'existe pas
     * forcément quand la balise démarre (fiche pas encore forgée), et elle
     * change le jour où une station rend la clé LOVE. Rien d'elle ne part
     * dans l'air — seulement le jeton de [ProximityPayload.token].
     */
    private val nostrKey: () -> ByteArray? = { null },
    /**
     * Ce que le noyau dit de lui sans se nommer — polarité, sceau maya, phase.
     * Relue comme la clé à chaque tour : elle naît avec la fiche, morceau par
     * morceau (le sceau dès la date, la phase seulement avec le lieu).
     */
    private val signature: () -> ProximityPayload.Signature = {
        ProximityPayload.Signature.Unknown
    },
) {

    companion object {
        /**
         * Même UUID 16 bits (plage vendor) que le POC : les anciennes sondes
         * apparaissent comme des payloads non décodables, ignorées proprement.
         */
        val SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

        private const val TAG = "Proximity"

        /** Re-résolution périodique de la cellule (et de l'annonce si elle change). */
        private const val CELL_REFRESH_MS = 5 * 60_000L

        /** Cadence resserrée tant que la cellule n'a pas pu être résolue. */
        private const val CELL_RETRY_MS = 30_000L
        private const val SWEEP_INTERVAL_MS = 10_000L
        private const val ADVERTISE_START_TIMEOUT_MS = 10_000L
    }

    data class State(
        val advertising: Boolean = false,
        /** Adresse 4D actuellement diffusée (null = cellule non résolue ou balise coupée). */
        val advertisedCell4d: Long? = null,
        val scanning: Boolean = false,
        val lastError: String? = null,
    )

    private val adapter: BluetoothAdapter? =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Jeton actuellement dans l'air — il n'a pas à sortir d'ici. */
    private var advertisedToken: Int? = null
    private var advertisedSignature = ProximityPayload.Signature.Unknown

    /**
     * Tourne jusqu'à annulation de la coroutine appelante ; l'annonce et le scan
     * sont toujours coupés (et le registre vidé) en sortie.
     */
    @SuppressLint("MissingPermission")
    suspend fun run() {
        val adapter = this.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update { it.copy(lastError = "Bluetooth indisponible ou désactivé") }
            return
        }
        val advertiser = adapter.bluetoothLeAdvertiser
        val scanner = adapter.bluetoothLeScanner
        if (advertiser == null || scanner == null) {
            _state.update { it.copy(lastError = "ce chipset ne sait pas annoncer ou scanner en BLE") }
            return
        }

        var advertiseCallback: AdvertiseCallback? = null
        var scanCallback: ScanCallback? = null
        try {
            while (currentCoroutineContext().isActive) {
                // Silence demandé : la cabine transfère, et la même puce porte
                // les deux. On se tait entièrement plutôt que de lui disputer
                // l'antenne — la balise n'a rien d'urgent à dire.
                if (RadioSilence.requested.value) {
                    advertiseCallback?.let { runCatching { advertiser.stopAdvertising(it) } }
                    advertiseCallback = null
                    scanCallback?.let { runCatching { scanner.stopScan(it) } }
                    scanCallback = null
                    _state.update { it.copy(advertising = false, scanning = false) }
                    Log.d(TAG, "balise en silence : transfert en cours")
                    RadioSilence.requested.first { !it }
                    Log.d(TAG, "balise relancée : antenne rendue")
                    continue
                }
                if (scanCallback == null) scanCallback = startScan(scanner)
                val h3Cell = locator.currentCell()
                Log.d(TAG, "cellule H3 résolue : ${h3Cell?.toString(16) ?: "échec (pas de position)"}")
                val cell4d = h3Cell?.let { rotation.apply(it, System.currentTimeMillis()) }

                // Le jeton suit la cellule ET le noyau : refaire l'annonce quand
                // l'un des deux bouge, sinon un noyau forgé après le démarrage
                // de la balise n'y figurerait jamais.
                val token = ProximityPayload.token(nostrKey(), cell4d)
                // La signature entre dans la même comparaison : une fiche
                // complétée pendant que la balise tourne doit passer dans
                // l'air, et une fiche inchangée ne doit pas relancer l'annonce.
                val signature = signature()
                if (advertiseCallback == null ||
                    cell4d != _state.value.advertisedCell4d ||
                    token != advertisedToken ||
                    signature != advertisedSignature
                ) {
                    advertiseCallback?.let { runCatching { advertiser.stopAdvertising(it) } }
                    advertisedToken = token
                    advertisedSignature = signature
                    advertiseCallback = startAdvertising(advertiser, cell4d, token, signature)
                }

                var waited = 0L
                val refreshMs = if (cell4d == null) CELL_RETRY_MS else CELL_REFRESH_MS
                // l'attente se rompt sur demande de silence : attendre les 30 s
                // du prochain rafraîchissement laisserait l'antenne occupée
                // pendant tout le transfert
                while (waited < refreshMs && !RadioSilence.requested.value) {
                    delay(SWEEP_INTERVAL_MS)
                    waited += SWEEP_INTERVAL_MS
                    registry.sweep()
                }
            }
        } finally {
            advertiseCallback?.let { runCatching { advertiser.stopAdvertising(it) } }
            scanCallback?.let { runCatching { scanner.stopScan(it) } }
            registry.clear()
            _state.value = State()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startAdvertising(
        advertiser: BluetoothLeAdvertiser,
        cell4d: Long?,
        token: Int?,
        signature: ProximityPayload.Signature,
    ): AdvertiseCallback? {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(SERVICE_UUID)
            .addServiceData(SERVICE_UUID, ProximityPayload.encode(cell4d, token, signature))
            .build()

        var callback: AdvertiseCallback? = null
        val started = withTimeoutOrNull(ADVERTISE_START_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val cb = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onStartFailure(errorCode: Int) {
                        Log.w(TAG, "annonce refusée (errorCode=$errorCode)")
                        _state.update {
                            it.copy(lastError = "annonce refusée (errorCode=$errorCode)")
                        }
                        if (cont.isActive) cont.resume(false)
                    }
                }
                callback = cb
                advertiser.startAdvertising(settings, data, cb)
                cont.invokeOnCancellation { runCatching { advertiser.stopAdvertising(cb) } }
            }
        } ?: false

        if (!started) return null
        _state.update { it.copy(advertising = true, advertisedCell4d = cell4d, lastError = null) }
        Log.d(TAG, "annonce démarrée, cell4d=${cell4d?.toString(16) ?: "inconnue"}")
        return callback
    }

    @SuppressLint("MissingPermission")
    private fun startScan(scanner: android.bluetooth.le.BluetoothLeScanner): ScanCallback {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onPeer(result)

            override fun onBatchScanResults(results: List<ScanResult>) = results.forEach(::onPeer)

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "scan refusé (errorCode=$errorCode)")
                _state.update {
                    it.copy(scanning = false, lastError = "scan refusé (errorCode=$errorCode)")
                }
            }
        }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(),
            callback,
        )
        Log.d(TAG, "scan démarré (filtre $SERVICE_UUID)")
        _state.update { it.copy(scanning = true) }
        return callback
    }

    private fun onPeer(result: ScanResult) {
        val payload = ProximityPayload.decode(
            result.scanRecord?.getServiceData(SERVICE_UUID),
        ) ?: return
        Log.d(TAG, "pair ${result.device.address} rssi=${result.rssi} " +
            "cell4d=${payload.cell4d?.toString(16) ?: "inconnue"}")
        registry.report(result.device.address, payload.cell4d, payload.token, result.rssi)
    }
}
