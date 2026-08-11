package one.astroport.atom4love.diag

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.chat.ChatKind
import one.astroport.atom4love.chat.ChatMessage
import one.astroport.atom4love.chat.ChatStatus
import one.astroport.atom4love.chat.wire.ChatFrame
import one.astroport.atom4love.chat.wire.ChatFrames
import one.astroport.atom4love.chat.wire.Reassembler

/**
 * POC : causerie en BLE pur, sans AP ni relais — la brique GATT
 * bidirectionnelle sur laquelle le handshake Noise (portage bitchat)
 * viendra se poser.
 *
 * ⚠ Sonde de diagnostic : l'échange est EN CLAIR dans les airs. Ne sort pas
 * du package diag tant que Noise n'est pas là.
 *
 * Architecture symétrique : chaque appareil est à la fois périphérique
 * (annonce connectable + serveur GATT) et central (scan + connexion aux
 * pairs vus). Tout contenu — texte, image, fichier — passe par les trames
 * fragmentées de chat/wire : une trame START annonce l'id, le genre, la
 * taille et le CRC ; les fragments DATA suivent, cadencés par les callbacks
 * d'écriture ; le récepteur renvoie un ACK de bout en bout (✓✓). Un message
 * part une fois par adresse vue (lien client préféré au lien serveur) et le
 * réassembleur élit le premier flux : le double lien croisé n'affiche rien
 * en double.
 *
 * Toute la machinerie protocolaire vit sur un fil unique ([dispatcher]) ;
 * les callbacks Binder n'y déposent que des `scope.launch`.
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

        /** MTU demandé ; l'effectif revient par onMtuChanged (23 si refus). */
        private const val REQUESTED_MTU = 517
        private const val DEFAULT_MTU = 23

        private const val MAX_TEXT_BYTES = 4096
        const val MAX_TRANSFER_BYTES = 2_000_000

        private const val WRITE_TIMEOUT_MS = 10_000L
        private const val WRITE_RETRIES = 8
        private const val PRUNE_PERIOD_MS = 5_000L
    }

    enum class Chime { SENT, RECEIVED }

    data class Status(
        val advertising: Boolean = false,
        val scanning: Boolean = false,
        /** Liens utilisables (clients prêts à écrire + centraux abonnés). */
        val links: Int = 0,
        val lastError: String? = null,
    )

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chimes = MutableSharedFlow<Chime>(extraBufferCapacity = 8)
    val chimes: SharedFlow<Chime> = _chimes.asSharedFlow()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "BleChat") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var server: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    private var stateReceiver: BroadcastReceiver? = null

    private enum class LinkKind { CLIENT, SERVER }

    /** Un transfert complet, rejoué séquentiellement par le fil du lien. */
    private class Outgoing(
        val msgId: Int,
        val kind: Int,
        val name: String,
        val mime: String,
        val content: ByteArray,
        /** Ce lien pilote la barre de progression et le statut du message. */
        val primary: Boolean,
    )

    private class Link(val kind: LinkKind, val address: String) {
        var mtu = DEFAULT_MTU
        var gatt: BluetoothGatt? = null                              // CLIENT
        var characteristic: BluetoothGattCharacteristic? = null      // CLIENT
        var device: BluetoothDevice? = null                          // SERVER

        /**
         * Écritures en vol, complétées par les callbacks dans l'ordre GATT
         * (FIFO) : un callback tardif après timeout consomme SON deferred,
         * jamais celui de l'écriture suivante.
         */
        val pending = ArrayDeque<CompletableDeferred<Boolean>>()
        val transfers = Channel<Outgoing>(Channel.UNLIMITED)
        val control = Channel<ByteArray>(Channel.UNLIMITED)
        var job: Job? = null

        val ready: Boolean get() = kind == LinkKind.SERVER || characteristic != null

        fun failPending() {
            while (true) {
                (pending.removeFirstOrNull() ?: break).complete(false)
            }
        }
    }

    /** clé = "c:<adresse>" (client sortant) ou "s:<adresse>" (central abonné). */
    private val links = LinkedHashMap<String, Link>()

    /** MTU annoncés côté serveur, parfois avant l'abonnement CCCD. */
    private val serverMtus = HashMap<String, Int>()

    /**
     * Adresses des centraux abonnés — miroir des liens serveur, consultable
     * depuis le fil Binder (lecture synchrone du CCCD) sous synchronized.
     */
    private val subscribedAddresses = mutableSetOf<String>()

    private val reassembler = Reassembler(MAX_TRANSFER_BYTES)

    /** Dernier pourcentage publié par transfert — étrangle les recompositions. */
    private val progressPct = HashMap<Int, Int>()

    private fun key(kind: LinkKind, address: String) =
        if (kind == LinkKind.CLIENT) "c:$address" else "s:$address"

    fun start() {
        val adapter = this.adapter
        if (adapter == null || !adapter.isEnabled) {
            _status.update { it.copy(lastError = "Bluetooth désactivé") }
            return
        }
        registerStateReceiver()
        startRadio()
        scope.launch {
            while (isActive) {
                delay(PRUNE_PERIOD_MS)
                reassembler.prune().forEach { failed ->
                    Log.w(TAG, "réception ${failed.msgId} : ${failed.reason}")
                    progressPct.remove(failed.msgId)
                    updateMessage(failed.msgId) { it.copy(status = ChatStatus.FAILED) }
                }
            }
        }
    }

    fun stop() {
        runCatching { stateReceiver?.let { appContext.unregisterReceiver(it) } }
        stateReceiver = null
        runCatching { scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) } }
        runCatching { advertiseCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) } }
        scope.cancel()
        // le démontage des liens passe par le fil protocole : l'executor FIFO
        // le sérialise derrière tout corps de coroutine encore en cours
        runCatching {
            executor.submit {
                links.values.forEach { link ->
                    link.failPending()
                    runCatching { link.gatt?.close() }
                }
                links.clear()
                serverMtus.clear()
                synchronized(subscribedAddresses) { subscribedAddresses.clear() }
            }.get()
        }
        runCatching { server?.close() }
        server = null
        dispatcher.close()
        _status.value = Status()
    }

    private fun startRadio() {
        startServer()
        startAdvertising()
        startScan()
    }

    /** Suit les cycles du Bluetooth : coupé → liens fermés ; revenu → tout repart. */
    private fun registerStateReceiver() {
        if (stateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> scope.launch {
                        Log.w(TAG, "Bluetooth coupé : liens fermés")
                        links.keys.toList().forEach { k ->
                            val link = links[k] ?: return@forEach
                            removeLink(link.kind, link.address)
                        }
                        runCatching { server?.close() }
                        server = null
                        _status.update {
                            it.copy(advertising = false, scanning = false, lastError = "Bluetooth coupé")
                        }
                    }
                    BluetoothAdapter.STATE_ON -> scope.launch {
                        Log.w(TAG, "Bluetooth revenu : redémarrage annonce + scan")
                        startRadio()
                    }
                }
            }
        }
        stateReceiver = receiver
        appContext.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    // ── Envoi ─────────────────────────────────────────────────────────────

    /** Envoie un texte. false si vide, trop long ou aucun lien. */
    fun sendText(text: String): Boolean {
        val content = text.trim()
        if (content.isEmpty() || status.value.links == 0) return false
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_TEXT_BYTES) {
            _status.update { it.copy(lastError = "texte trop long (max $MAX_TEXT_BYTES o)") }
            return false
        }
        val msgId = Random.nextInt()
        addMessage(
            ChatMessage(
                id = msgId, mine = true, from = "moi",
                kind = ChatKind.TEXT, status = ChatStatus.SENDING,
                text = content, sizeBytes = bytes.size,
            ),
        )
        _chimes.tryEmit(Chime.SENT)
        scope.launch { dispatch(msgId, ChatFrames.KIND_TEXT, "", "", bytes) }
        return true
    }

    /** Prépare (recompression) puis envoie une image. */
    fun sendImage(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            val read = Attachments.prepareImage(appContext, uri)
            val copy = saveLocalCopy(read)
            withContext(dispatcher) { dispatchAttachment(ChatKind.IMAGE, ChatFrames.KIND_IMAGE, read, copy) }
        }
    }

    /** Envoie un fichier tel quel, plafonné à [MAX_TRANSFER_BYTES]. */
    fun sendFile(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            val read = Attachments.read(appContext, uri, MAX_TRANSFER_BYTES)
            val copy = saveLocalCopy(read)
            withContext(dispatcher) { dispatchAttachment(ChatKind.FILE, ChatFrames.KIND_FILE, read, copy) }
        }
    }

    private fun saveLocalCopy(read: Attachments.Read) = (read as? Attachments.Read.Ok)
        ?.let { runCatching { Attachments.saveCopy(appContext, it.name, it.bytes) }.getOrNull() }

    private fun dispatchAttachment(
        kind: ChatKind,
        wireKind: Int,
        read: Attachments.Read,
        copy: java.io.File?,
    ) {
        when (read) {
            is Attachments.Read.TooBig -> _status.update {
                it.copy(lastError = "pièce trop lourde (max ${Attachments.humanSize(MAX_TRANSFER_BYTES)})")
            }
            is Attachments.Read.Unreadable -> _status.update {
                it.copy(lastError = "pièce illisible")
            }
            is Attachments.Read.Ok -> {
                if (read.bytes.size > MAX_TRANSFER_BYTES) {
                    _status.update {
                        it.copy(lastError = "pièce trop lourde (max ${Attachments.humanSize(MAX_TRANSFER_BYTES)})")
                    }
                    return
                }
                val msgId = Random.nextInt()
                addMessage(
                    ChatMessage(
                        id = msgId, mine = true, from = "moi",
                        kind = kind, status = ChatStatus.SENDING,
                        file = copy, name = read.name, mime = read.mime,
                        sizeBytes = read.bytes.size,
                    ),
                )
                _chimes.tryEmit(Chime.SENT)
                dispatch(msgId, wireKind, read.name, read.mime, read.bytes)
            }
        }
    }

    /** Fil protocole. Une émission par adresse vue, lien client préféré. */
    private fun dispatch(msgId: Int, kind: Int, name: String, mime: String, content: ByteArray) {
        val perAddress = LinkedHashMap<String, Link>()
        links.values.forEach { link ->
            if (!link.ready) return@forEach
            val current = perAddress[link.address]
            if (current == null || (current.kind == LinkKind.SERVER && link.kind == LinkKind.CLIENT)) {
                perAddress[link.address] = link
            }
        }
        if (perAddress.isEmpty()) {
            updateMessage(msgId) { it.copy(status = ChatStatus.FAILED) }
            _status.update { it.copy(lastError = "aucun lien pour émettre") }
            return
        }
        var primary = true
        perAddress.values.forEach { link ->
            link.transfers.trySend(Outgoing(msgId, kind, name, mime, content, primary))
            primary = false
        }
        Log.i(TAG, "message $msgId (${content.size} o) mis en file vers ${perAddress.size} pair(s)")
    }

    // ── Fil d'émission d'un lien ──────────────────────────────────────────

    private fun startLinkJob(link: Link) {
        if (link.job != null) return
        link.job = scope.launch {
            while (isActive) {
                // priorité aux trames de contrôle (acquittements)
                val control = link.control.tryReceive().getOrNull()
                if (control != null) {
                    writeFrame(link, control)
                    continue
                }
                select<Unit> {
                    link.control.onReceive { writeFrame(link, it) }
                    link.transfers.onReceive { out ->
                        try {
                            runTransfer(link, out)
                        } catch (e: CancellationException) {
                            // lien retiré en plein transfert : le message ne
                            // doit pas rester figé en SENDING
                            onTransferFailed(link, out)
                            throw e
                        }
                    }
                }
            }
        }
    }

    private suspend fun runTransfer(link: Link, out: Outgoing) {
        val att = ChatFrames.attPayload(link.mtu)
        val start = ChatFrames.encodeStart(
            ChatFrame.Start(out.msgId, out.kind, out.content.size, ChatFrames.crc32(out.content), out.name, out.mime),
            att,
        )
        if (start == null || !writeFrame(link, start)) {
            onTransferFailed(link, out)
            return
        }
        val chunk = ChatFrames.dataChunk(link.mtu).coerceAtLeast(1)
        var offset = 0
        var index = 0
        while (offset < out.content.size) {
            // les acquittements se glissent entre deux fragments
            while (true) {
                val control = link.control.tryReceive().getOrNull() ?: break
                writeFrame(link, control)
            }
            val end = minOf(offset + chunk, out.content.size)
            if (!writeFrame(link, ChatFrames.encodeData(out.msgId, index, out.content, offset, end))) {
                onTransferFailed(link, out)
                return
            }
            offset = end
            index++
            if (out.primary) publishProgress(out.msgId, offset, out.content.size)
        }
        if (out.primary) {
            progressPct.remove(out.msgId)
            updateMessage(out.msgId) {
                // un ACK arrivé pendant les derniers fragments a déjà posé ✓✓
                if (it.status == ChatStatus.SENDING) it.copy(status = ChatStatus.SENT, progress = 1f)
                else it.copy(progress = 1f)
            }
        }
        Log.i(TAG, "message ${out.msgId} émis (${out.content.size} o, $index fragment(s)) vers ${link.address}")
    }

    private fun onTransferFailed(link: Link, out: Outgoing) {
        Log.w(TAG, "échec d'émission de ${out.msgId} vers ${link.address}")
        if (out.primary) {
            progressPct.remove(out.msgId)
            updateMessage(out.msgId) { it.copy(status = ChatStatus.FAILED) }
            _status.update { it.copy(lastError = "échec d'envoi vers ${link.address.takeLast(5)}") }
        }
    }

    /** Sérialise les notifications : une seule en vol par serveur GATT. */
    private val notifyMutex = Mutex()

    private enum class WriteOutcome { OK, FAILED, BUSY }

    /**
     * Une écriture démarrée puis échouée ou expirée n'est JAMAIS réémise à
     * l'aveugle (elle a pu partir : le récepteur verrait un doublon) — seule
     * la pile occupée (écriture non démarrée) se retente, avec repli.
     */
    private suspend fun writeFrame(link: Link, frame: ByteArray): Boolean {
        repeat(WRITE_RETRIES) { attempt ->
            val outcome =
                if (link.kind == LinkKind.SERVER) notifyMutex.withLock { attemptWrite(link, frame) }
                else attemptWrite(link, frame)
            when (outcome) {
                WriteOutcome.OK -> return true
                WriteOutcome.FAILED -> return false
                WriteOutcome.BUSY -> delay(30L * (attempt + 1))
            }
        }
        return false
    }

    private suspend fun attemptWrite(link: Link, frame: ByteArray): WriteOutcome {
        val done = CompletableDeferred<Boolean>()
        link.pending.addLast(done)
        val started = runCatching {
            when (link.kind) {
                LinkKind.CLIENT -> clientWrite(link, frame)
                LinkKind.SERVER -> serverNotify(link, frame)
            }
        }.getOrDefault(false)
        if (!started) {
            link.pending.remove(done)
            return WriteOutcome.BUSY
        }
        return when (withTimeoutOrNull(WRITE_TIMEOUT_MS) { done.await() }) {
            true -> WriteOutcome.OK
            false -> WriteOutcome.FAILED
            // deferred laissé dans pending : le callback tardif consommera le
            // sien, pas celui de l'écriture suivante
            null -> {
                Log.w(TAG, "écriture expirée vers ${link.address}")
                WriteOutcome.FAILED
            }
        }
    }

    private fun clientWrite(link: Link, frame: ByteArray): Boolean = runCatching {
        val gatt = link.gatt ?: return false
        val characteristic = link.characteristic ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                frame,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = frame
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }.getOrDefault(false)

    private fun serverNotify(link: Link, frame: ByteArray): Boolean = runCatching {
        val server = this.server ?: return false
        val characteristic = serverCharacteristic ?: return false
        val device = link.device ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, false, frame) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = frame
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    }.getOrDefault(false)

    // ── Réception ─────────────────────────────────────────────────────────

    /** Fil protocole. */
    private fun handleFrame(from: String, bytes: ByteArray) {
        when (val frame = ChatFrames.decode(bytes)) {
            null -> Log.w(TAG, "trame illisible de $from (${bytes.size} o)")
            is ChatFrame.Ack -> onAck(frame)
            else -> when (val event = reassembler.onFrame(from, frame)) {
                is Reassembler.Event.Started -> onIncomingStarted(event)
                is Reassembler.Event.Progress ->
                    publishProgress(event.msgId, event.receivedBytes, event.totalBytes)
                is Reassembler.Event.Completed -> onIncomingCompleted(event)
                is Reassembler.Event.Failed -> onIncomingFailed(event)
                null -> Unit
            }
        }
    }

    private fun onIncomingStarted(event: Reassembler.Event.Started) {
        val start = event.start
        Log.i(TAG, "réception de ${start.totalBytes} o (genre ${start.kind}) depuis ${event.from}")
        addMessage(
            ChatMessage(
                id = start.msgId, mine = false, from = event.from.takeLast(5),
                kind = kindOf(start.kind), status = ChatStatus.RECEIVING,
                name = start.name, mime = start.mime, sizeBytes = start.totalBytes,
            ),
        )
    }

    private fun onIncomingCompleted(event: Reassembler.Event.Completed) {
        val start = event.start
        progressPct.remove(start.msgId)
        when (kindOf(start.kind)) {
            ChatKind.TEXT -> updateMessage(start.msgId) {
                it.copy(
                    status = ChatStatus.RECEIVED, progress = 1f,
                    text = String(event.bytes, Charsets.UTF_8),
                )
            }
            else -> {
                val file = runCatching {
                    Attachments.saveCopy(appContext, start.name, event.bytes)
                }.getOrNull()
                updateMessage(start.msgId) {
                    it.copy(
                        status = if (file != null) ChatStatus.RECEIVED else ChatStatus.FAILED,
                        progress = 1f, file = file,
                    )
                }
            }
        }
        Log.i(TAG, "message ${start.msgId} reçu au complet (${event.bytes.size} o)")
        _chimes.tryEmit(Chime.RECEIVED)
        broadcastControl(ChatFrames.encodeAck(start.msgId, ChatFrames.ACK_OK))
    }

    private fun onIncomingFailed(event: Reassembler.Event.Failed) {
        Log.w(TAG, "réception ${event.msgId} : ${event.reason}")
        progressPct.remove(event.msgId)
        updateMessage(event.msgId) { it.copy(status = ChatStatus.FAILED) }
        event.ackStatus?.let { broadcastControl(ChatFrames.encodeAck(event.msgId, it)) }
    }

    private fun onAck(ack: ChatFrame.Ack) {
        Log.i(TAG, "acquittement ${ack.msgId} statut ${ack.status}")
        updateMessage(ack.msgId) { message ->
            when {
                !message.mine -> message
                ack.status == ChatFrames.ACK_OK ->
                    message.copy(status = ChatStatus.DELIVERED, progress = 1f)
                else -> message.copy(status = ChatStatus.FAILED)
            }
        }
    }

    /** Diffuse une trame de contrôle sur un lien par adresse (client préféré). */
    private fun broadcastControl(frame: ByteArray) {
        val perAddress = LinkedHashMap<String, Link>()
        links.values.forEach { link ->
            if (!link.ready) return@forEach
            val current = perAddress[link.address]
            if (current == null || (current.kind == LinkKind.SERVER && link.kind == LinkKind.CLIENT)) {
                perAddress[link.address] = link
            }
        }
        perAddress.values.forEach { it.control.trySend(frame) }
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
                scope.launch { removeLink(LinkKind.SERVER, device.address) }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            scope.launch {
                serverMtus[device.address] = mtu
                links[key(LinkKind.SERVER, device.address)]?.mtu = mtu
                Log.d(TAG, "serveur : MTU $mtu pour ${device.address}")
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
            val bytes = value.copyOf()
            scope.launch { handleFrame(device.address, bytes) }
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
                val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentEquals(value)
                scope.launch {
                    if (enable) addServerLink(device) else removeLink(LinkKind.SERVER, device.address)
                }
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            if (descriptor.uuid == CCCD) {
                // certaines piles lisent le CCCD avant de s'abonner : sans
                // réponse, transaction ATT en rade puis déconnexion
                val subscribed = synchronized(subscribedAddresses) {
                    device.address in subscribedAddresses
                }
                server?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    if (subscribed) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
                )
            } else {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            scope.launch {
                links[key(LinkKind.SERVER, device.address)]
                    ?.pending?.removeFirstOrNull()
                    ?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    /** Fil protocole. */
    private fun addServerLink(device: BluetoothDevice) {
        val k = key(LinkKind.SERVER, device.address)
        if (links.containsKey(k)) return
        val link = Link(LinkKind.SERVER, device.address).apply {
            this.device = device
            mtu = serverMtus[device.address] ?: DEFAULT_MTU
        }
        links[k] = link
        synchronized(subscribedAddresses) { subscribedAddresses.add(device.address) }
        startLinkJob(link)
        Log.i(TAG, "central ${device.address} abonné — lien serveur prêt (mtu=${link.mtu})")
        refreshLinks()
    }

    /** Fil protocole. */
    private fun removeLink(kind: LinkKind, address: String) {
        // avant l'early-return : un central peut négocier le MTU sans jamais s'abonner
        if (kind == LinkKind.SERVER) {
            serverMtus.remove(address)
            synchronized(subscribedAddresses) { subscribedAddresses.remove(address) }
        }
        val link = links.remove(key(kind, address)) ?: return
        link.job?.cancel()
        link.failPending()
        // les transferts encore en file ne partiront jamais par ce lien
        link.transfers.close()
        while (true) {
            val out = link.transfers.tryReceive().getOrNull() ?: break
            onTransferFailed(link, out)
        }
        runCatching { link.gatt?.close() }
        refreshLinks()
    }

    private fun startAdvertising() {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: run {
            _status.update { it.copy(lastError = "annonce BLE indisponible") }
            return
        }
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _status.update { it.copy(advertising = true, lastError = null) }
                Log.d(TAG, "annonce connectable démarrée")
            }

            override fun onStartFailure(errorCode: Int) {
                _status.update { it.copy(lastError = "annonce refusée ($errorCode)") }
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
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch { onPeer(result) }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                scope.launch { results.forEach(::onPeer) }
            }

            override fun onScanFailed(errorCode: Int) {
                _status.update { it.copy(scanning = false, lastError = "scan refusé ($errorCode)") }
            }
        }
        scanCallback = callback
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(CHAT_SERVICE).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        _status.update { it.copy(scanning = true) }
        Log.d(TAG, "scan démarré (filtre $CHAT_SERVICE)")
    }

    /** Fil protocole. */
    private fun onPeer(result: ScanResult) {
        val address = result.device.address
        val k = key(LinkKind.CLIENT, address)
        if (links.containsKey(k)) return
        Log.i(TAG, "pair de causerie vu : $address rssi=${result.rssi}, connexion…")
        val link = Link(LinkKind.CLIENT, address)
        links[k] = link
        val gatt = result.device.connectGatt(
            appContext,
            false,
            clientCallback,
            BluetoothDevice.TRANSPORT_LE,
        )
        if (gatt == null) {
            // Bluetooth en train de tomber ou interfaces saturées : sans ce
            // retrait, l'adresse resterait bloquée par le dédoublonnage
            links.remove(k)
            Log.w(TAG, "connectGatt null pour $address")
            return
        }
        link.gatt = gatt
    }

    /** Lien client irrécupérable : on coupe pour que le scan retente. */
    private fun dropClient(gatt: BluetoothGatt, reason: String) {
        Log.w(TAG, "lien client ${gatt.device.address} abandonné : $reason")
        runCatching { gatt.disconnect() }
        scope.launch { removeLink(LinkKind.CLIENT, gatt.device.address) }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "client : connecté à $address, négociation MTU")
                if (!gatt.requestMtu(REQUESTED_MTU)) gatt.discoverServices()
            } else {
                Log.d(TAG, "client : $address perdu (status=$status)")
                scope.launch { removeLink(LinkKind.CLIENT, address) }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            scope.launch {
                val link = links[key(LinkKind.CLIENT, gatt.device.address)] ?: return@launch
                link.mtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
                Log.d(TAG, "client : MTU ${link.mtu} vers ${gatt.device.address}")
            }
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                dropClient(gatt, "découverte échouée (status=$status)")
                return
            }
            val characteristic = gatt.getService(CHAT_SERVICE.uuid)
                ?.getCharacteristic(CHAT_CHARACTERISTIC)
                ?: run {
                    dropClient(gatt, "service causerie absent")
                    return
                }
            gatt.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD) ?: run {
                dropClient(gatt, "CCCD absent")
                return
            }
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
            if (descriptor.uuid != CCCD) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                dropClient(gatt, "abonnement refusé (status=$status)")
                return
            }
            val characteristic = descriptor.characteristic
            scope.launch {
                val link = links[key(LinkKind.CLIENT, gatt.device.address)] ?: return@launch
                link.characteristic = characteristic
                startLinkJob(link)
                Log.i(TAG, "lien client prêt vers ${gatt.device.address} (mtu=${link.mtu})")
                refreshLinks()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            scope.launch {
                links[key(LinkKind.CLIENT, gatt.device.address)]
                    ?.pending?.removeFirstOrNull()
                    ?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value?.copyOf() ?: return
            scope.launch { handleFrame(gatt.device.address, bytes) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val bytes = value.copyOf()
            scope.launch { handleFrame(gatt.device.address, bytes) }
        }
    }

    // ── État partagé ──────────────────────────────────────────────────────

    private fun kindOf(wireKind: Int): ChatKind = when (wireKind) {
        ChatFrames.KIND_IMAGE -> ChatKind.IMAGE
        ChatFrames.KIND_FILE -> ChatKind.FILE
        else -> ChatKind.TEXT
    }

    private fun publishProgress(msgId: Int, done: Int, total: Int) {
        val pct = (done * 100L / total).toInt()
        if (progressPct[msgId] == pct) return
        progressPct[msgId] = pct
        updateMessage(msgId) { it.copy(progress = done / total.toFloat()) }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.update { it + message }
    }

    private fun updateMessage(msgId: Int, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list ->
            list.map { if (it.id == msgId) transform(it) else it }
        }
    }

    private fun refreshLinks() {
        _status.update { status -> status.copy(links = links.values.count { it.ready }) }
    }
}
