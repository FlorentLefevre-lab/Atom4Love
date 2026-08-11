package one.astroport.atom4love.diag

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * POC : causerie de texte en BLE pur, sans AP ni relais — la brique GATT
 * bidirectionnelle qui manquait au module proximité, et le socle sur lequel
 * le handshake Noise (portage bitchat) viendra se poser.
 *
 * ⚠ Sonde de diagnostic : l'échange est EN CLAIR dans les airs. Ne sort pas
 * du package diag tant que Noise n'est pas là.
 *
 * Architecture symétrique : chaque appareil est à la fois périphérique
 * (annonce connectable + serveur GATT, characteristic write/notify) et
 * central (scan + connexion aux pairs vus). Un message part sur tous les
 * liens ouverts (écriture côté client, notification côté serveur) avec un
 * id aléatoire de 4 octets ; la réception dédoublonne — à deux appareils
 * reliés deux fois, chacun ne l'affiche qu'une fois.
 */
@SuppressLint("MissingPermission")
class BleChatProbe(context: Context) {

    companion object {
        private const val TAG = "BleChat"

        /** UUID 16 bits vendor, distinct de la balise (fff0). */
        val CHAT_SERVICE: ParcelUuid =
            ParcelUuid.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private val CHAT_CHARACTERISTIC: UUID =
            UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        private val CCCD: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val MAX_TEXT_BYTES = 160
        private const val SEEN_IDS_MAX = 128
    }

    data class Message(val mine: Boolean, val from: String, val text: String)

    data class State(
        val advertising: Boolean = false,
        val scanning: Boolean = false,
        /** Liens utilisables (client prêt à écrire + centraux abonnés au serveur). */
        val links: Int = 0,
        val messages: List<Message> = emptyList(),
        val lastError: String? = null,
    )

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var server: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    /** Centraux abonnés aux notifications de notre serveur. */
    private val subscribers = LinkedHashSet<BluetoothDevice>()

    /** Nos connexions sortantes : adresse → gatt (characteristic prête ou non). */
    private val clients = LinkedHashMap<String, BluetoothGatt>()
    private val writable = LinkedHashMap<String, BluetoothGattCharacteristic>()

    private val seenIds = ArrayDeque<Int>()

    fun start() {
        val adapter = this.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update { it.copy(lastError = "Bluetooth désactivé") }
            return
        }
        startServer()
        startAdvertising()
        startScan()
    }

    fun stop() {
        runCatching { scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) } }
        runCatching { advertiseCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) } }
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        writable.clear()
        subscribers.clear()
        runCatching { server?.close() }
        server = null
        _state.value = State()
    }

    /** Envoie sur tous les liens ouverts. false si aucun lien ou texte vide. */
    fun send(text: String): Boolean {
        val content = text.trim().take(MAX_TEXT_BYTES)
        if (content.isEmpty()) return false
        val payload = encode(Random.nextInt(), content)
        var delivered = 0

        writable.forEach { (address, characteristic) ->
            val gatt = clients[address] ?: return@forEach
            if (writeTo(gatt, characteristic, payload)) delivered++
        }
        val srv = server
        val srvChar = serverCharacteristic
        if (srv != null && srvChar != null) {
            subscribers.forEach { device ->
                if (notifyTo(srv, device, srvChar, payload)) delivered++
            }
        }
        if (delivered > 0) {
            _state.update { it.copy(messages = it.messages + Message(true, "moi", content)) }
        }
        Log.d(TAG, "message émis sur $delivered lien(s)")
        return delivered > 0
    }

    // ── Rôle périphérique : serveur GATT + annonce connectable ────────────

    private fun startServer() {
        val characteristic = BluetoothGattCharacteristic(
            CHAT_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    CCCD,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                ),
            )
        }
        val service = BluetoothGattService(
            CHAT_SERVICE.uuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        ).apply { addCharacteristic(characteristic) }

        server = manager.openGattServer(appContext, serverCallback)?.also {
            it.addService(service)
            serverCharacteristic = characteristic
            Log.d(TAG, "serveur GATT ouvert")
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "serveur : ${device.address} ${if (newState == BluetoothProfile.STATE_CONNECTED) "connecté" else "parti"}")
            if (newState != BluetoothProfile.STATE_CONNECTED) {
                subscribers.remove(device)
                refreshLinks()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            receive(device.address, value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid == CCCD) {
                subscribers.add(device)
                Log.d(TAG, "central ${device.address} abonné aux notifications")
                refreshLinks()
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }

    private fun startAdvertising() {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: run {
            _state.update { it.copy(lastError = "annonce BLE indisponible") }
            return
        }
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _state.update { it.copy(advertising = true, lastError = null) }
                Log.d(TAG, "annonce connectable démarrée")
            }

            override fun onStartFailure(errorCode: Int) {
                _state.update { it.copy(lastError = "annonce refusée ($errorCode)") }
            }
        }
        advertiseCallback = callback
        advertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .build(),
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(CHAT_SERVICE)
                .build(),
            callback,
        )
    }

    // ── Rôle central : scan + connexion sortante ──────────────────────────

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onPeer(result)

            override fun onBatchScanResults(results: List<ScanResult>) = results.forEach(::onPeer)

            override fun onScanFailed(errorCode: Int) {
                _state.update { it.copy(scanning = false, lastError = "scan refusé ($errorCode)") }
            }
        }
        scanCallback = callback
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(CHAT_SERVICE).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        _state.update { it.copy(scanning = true) }
        Log.d(TAG, "scan démarré (filtre $CHAT_SERVICE)")
    }

    private fun onPeer(result: ScanResult) {
        val address = result.device.address
        if (clients.containsKey(address)) return
        Log.d(TAG, "pair de causerie vu : $address rssi=${result.rssi}, connexion…")
        clients[address] = result.device.connectGatt(
            appContext,
            false,
            clientCallback,
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "client : connecté à $address, négociation MTU")
                gatt.requestMtu(185)
            } else {
                Log.d(TAG, "client : $address perdu (status=$status)")
                writable.remove(address)
                clients.remove(address)
                runCatching { gatt.close() }
                refreshLinks()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(CHAT_SERVICE.uuid)
                ?.getCharacteristic(CHAT_CHARACTERISTIC)
                ?: run {
                    Log.w(TAG, "service causerie absent chez ${gatt.device.address}")
                    return
                }
            gatt.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val characteristic = descriptor.characteristic
            if (descriptor.uuid == CCCD && status == BluetoothGatt.GATT_SUCCESS) {
                writable[gatt.device.address] = characteristic
                Log.d(TAG, "lien prêt vers ${gatt.device.address}")
                refreshLinks()
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            receive(gatt.device.address, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            receive(gatt.device.address, value)
        }
    }

    // ── Messages ──────────────────────────────────────────────────────────

    private fun encode(id: Int, text: String): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + body.size).putInt(id).put(body).array()
    }

    private fun receive(fromAddress: String, payload: ByteArray) {
        if (payload.size < 5) return
        val id = ByteBuffer.wrap(payload, 0, 4).int
        synchronized(seenIds) {
            if (seenIds.contains(id)) return
            seenIds.addLast(id)
            if (seenIds.size > SEEN_IDS_MAX) seenIds.removeFirst()
        }
        val text = String(payload, 4, payload.size - 4, Charsets.UTF_8)
        Log.d(TAG, "message reçu de $fromAddress : $text")
        _state.update {
            it.copy(messages = it.messages + Message(false, fromAddress.takeLast(5), text))
        }
    }

    private fun writeTo(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }.getOrDefault(false)

    private fun notifyTo(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, false, payload) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    }.getOrDefault(false)

    private fun refreshLinks() {
        _state.update { it.copy(links = writable.size + subscribers.size) }
    }
}
