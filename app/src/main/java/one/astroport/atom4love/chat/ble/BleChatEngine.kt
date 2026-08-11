package one.astroport.atom4love.chat.ble

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import android.os.SystemClock
import android.util.Log
import java.security.SecureRandom
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
import one.astroport.atom4love.nostr.CabinSalon
import one.astroport.atom4love.noise.NoiseIdentity
import one.astroport.atom4love.noise.NoiseSession
import one.astroport.atom4love.noise.NoiseVouch
import one.astroport.atom4love.nostr.Bech32
import one.astroport.atom4love.nostr.Hex
import one.astroport.atom4love.nostr.NostrKeys

/**
 * Causerie en BLE pur, sans AP ni relais : le canal direct de la cabine.
 *
 * C'est ce qui se dit **ici**, entre gens à portée radio. Rien de ce qui passe
 * par ce moteur ne part sur un relais NOSTR ni ne sort de la portée : la
 * cabine et l'hexagone sont deux mondes étanches, et cette étanchéité est le
 * principe, pas un effet de bord. Le salon d'hexagone ([CabinSalon]) sert
 * l'autre portée, celle qu'on n'atteint pas directement.
 *
 * Le trafic est chiffré par Noise XX dès qu'un lien a mené son handshake :
 * tout ce qui suit — START, DATA, ACK — voyage scellé. Restent en clair le
 * premier message du handshake (il précède l'échange de clés) et les liens
 * dont le MTU ne permet pas de handshake.
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
class BleChatEngine(context: Context) {

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

        /**
         * Ce que l'UI doit demander avant [start]. Aucune localisation : le
         * canal direct n'a pas à savoir où il est, et le scan est déclaré
         * `neverForLocation` au manifeste.
         */
        val RUNTIME_PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                // avant l'API 31, les permissions Bluetooth sont d'installation
                emptyArray()
            }

        fun permissionsGranted(context: Context): Boolean = RUNTIME_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        private const val WRITE_TIMEOUT_MS = 10_000L

        /**
         * Fenêtre de contre-pression : une connexion morte (pair parti,
         * appli relancée en face) retient les tampons du contrôleur jusqu'au
         * timeout de supervision (~20 s) et fait refuser les écritures des
         * liens VIVANTS (« busy »). Observé sur banc le 2026-08-11 : avec
         * ~1 s de tolérance, une image échouait sur les trois liens à la
         * fois. On patiente donc jusqu'à 20 s tant que la pile dit occupée.
         */
        private const val BUSY_DEADLINE_MS = 20_000L
        private const val BUSY_DELAY_MAX_MS = 200L
        private const val PRUNE_PERIOD_MS = 5_000L

        /**
         * Fenêtre de contre-pression de bout en bout : 1 fragment sur
         * [ACK_WINDOW] (et le dernier) part en écriture AVEC réponse ATT —
         * le pair doit digérer la fenêtre avant la suivante. Observé sur
         * banc (2026-08-11) : le serveur GATT de la tablette s'effondre en
         * ~5 s sous un déluge d'écritures sans réponse, sans signal propre.
         * Le dernier fragment acquitté garantit aussi qu'« émis » = intégralement
         * remis à la pile d'en face (fin des queues de transfert perdues).
         */
        private const val ACK_WINDOW = 32
        private const val NOTIFY_WINDOW_PAUSE_MS = 8L

        /**
         * Le débit est entièrement dicté par l'intervalle de connexion, qu'aucune
         * API Android n'expose. Journal HCI du 2026-08-11 : DLE et MTU sont bien
         * négociés (251 o, 517), mais on dépense ~1,4 intervalle par fragment —
         * écriture puis attente du callback, un seul fragment en vol. D'où
         * 62 ms/fragment à 48,75 ms d'intervalle contre 22 ms à 15 ms, soit
         * 8 Ko/s contre 23 Ko/s pour le même code. Une `latence` de 1 autorise
         * en plus le pair à sauter un événement sur deux.
         *
         * Rien de tout cela n'est pilotable : requestConnectionPriority ne
         * produit aucune mise à jour de paramètres quand la pile se croit déjà
         * en HIGH — y compris en rebondissant par BALANCED pour l'y forcer,
         * essayé sans le moindre gain. Le seul vrai levier serait de mettre
         * plusieurs fragments en vol, ce qui écroule le serveur GATT de la
         * tablette (voir [ACK_WINDOW]). On se contente donc de constater.
         *
         * Repère : à 15 ms et latence 0 on tourne autour de 20 ms/fragment ;
         * à latence 1 on mesure ~32 ms.
         */
        private const val SLOW_FRAGMENT_MS = 25L
        private const val PRIORITY_RETRY_AT_FRAGMENT = 64

        /**
         * Délai d'accusé sur le chemin notification. Une écriture confirme sa
         * remise à la pile d'en face, pas une notification : elle part sans
         * contrôle de flux ni signal d'échec. Banc du 2026-08-11 — 550 Ko en
         * 1093 fragments évanouis sans qu'une seule trame arrive en face,
         * l'émetteur rapportant un succès. Faute de retour, seul l'ACK de bout
         * en bout prouve la remise ; sans lui le message est perdu.
         *
         * Le délai suit la taille : un forfait fixe ne peut pas marcher quand
         * l'attente légitime va de 11 s pour 120 Ko à 63 s pour 550 Ko. Un
         * plancher de 4 Ko/s laisse de la marge sous les 8,7 Ko/s les plus
         * lents mesurés au banc. Mieux vaut un échec tardif qu'un faux échec —
         * essayé à 20 s fixes le 2026-08-11 : le message basculait en échec
         * 25 s avant que son accusé n'arrive.
         */
        private const val ACK_WAIT_BASE_MS = 15_000L
        private const val ACK_WAIT_FLOOR_BPS = 4_000L

        private fun ackWaitMs(bytes: Int): Long =
            ACK_WAIT_BASE_MS + bytes * 1000L / ACK_WAIT_FLOOR_BPS

        /**
         * Délais de grâce avant de retenter une connexion sortante — sans
         * eux, un annonceur FFF1 du voisinage qui refuse la connexion
         * (status 133) déclenche une tempête connexion/échec à ~0,5 Hz
         * (observée sur banc le 2026-08-11).
         */
        private const val CONNECT_BACKOFF_FAILED_MS = 30_000L // jamais devenu prêt
        private const val CONNECT_BACKOFF_LOST_MS = 2_000L    // lien prêt perdu
        private const val BACKOFF_MAX_ENTRIES = 64

        /**
         * Espacement minimal entre deux tentatives de connexion sortante,
         * TOUTES adresses confondues : un annonceur voisin qui tourne son
         * adresse toutes les ~3 s (vu sur banc) rend le délai de grâce
         * par adresse inopérant — chaque tentative monopolise la radio
         * ~2 s et fait mourir les transferts en cours.
         */
        private const val CONNECT_SPACING_MS = 5_000L
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

        /**
         * Session Noise du lien. Le client initie, le serveur répond : le lien
         * client de A parle au lien serveur de B, ces deux-là forment le canal.
         * Une session par lien, donc deux par paire d'appareils — le double
         * lien croisé porte deux canaux indépendants.
         */
        var noise: NoiseSession? = null

        /** Évite de re-journaliser un handshake déjà annoncé. */
        var noiseAnnounced = false

        /** Idem pour la première trame scellée du lien. */
        var sealingAnnounced = false

        /** Clé publique NOSTR du pair, une fois son attestation vérifiée. */
        var peerNostrKey: ByteArray? = null

        val ready: Boolean get() = kind == LinkKind.SERVER || characteristic != null

        fun failPending() {
            while (true) {
                (pending.removeFirstOrNull() ?: break).complete(false)
            }
        }
    }

    /**
     * Clés NOSTR du noyau incarné sur cet appareil, si la fiche existe.
     *
     * Sans elles la sonde reste utilisable, avec une identité de fortune : le
     * handshake fonctionne, mais aucune attestation ne circule et rien ne
     * rattache le canal à un noyau.
     */
    private var nostrKeys: NostrKeys? = null

    /**
     * Clé statique Noise. Dérivée de la clé NOSTR quand un noyau est incarné —
     * les deux appareils d'un même noyau présentent alors la même identité — et
     * tirée au sort sinon, le temps d'une exécution.
     */
    private var noiseStaticKey: ByteArray =
        ByteArray(NoiseIdentity.KEY_LENGTH).also { SecureRandom().nextBytes(it) }

    /** Ce que le pair verra de nous — journalisé pour recouper les deux bancs. */
    private var noisePublicKey: ByteArray = NoiseIdentity.staticPublicKey(noiseStaticKey)

    /** Notre attestation npub ↔ clé Noise, absente tant qu'aucun noyau n'est incarné. */
    private var vouch: ByteArray? = null

    /**
     * Rattache la sonde à un noyau. À appeler avant [start] : les liens déjà
     * ouverts garderaient l'identité précédente.
     */
    fun bindIdentity(keys: NostrKeys) {
        nostrKeys = keys
        noiseStaticKey = NoiseIdentity.staticPrivateKey(keys)
        noisePublicKey = NoiseIdentity.staticPublicKey(noiseStaticKey)
        vouch = NoiseVouch.sign(keys, noisePublicKey)
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

    /**
     * Départ et taille des messages sortants, pour chiffrer le débit à
     * l'arrivée de l'accusé. C'est la seule mesure honnête sur le chemin
     * notification : là, writeFrame ne fait qu'empiler dans la pile
     * Bluetooth, qui draine ensuite à son rythme — mesuré au banc le
     * 2026-08-11, 240 notifications « émises » en 417 ms pour ~2,4 s de
     * transmission réelle.
     */
    private val sentAtMs = HashMap<Int, Pair<Long, Int>>()

    /** Guetteurs d'ACK armés sur le chemin notification — voir [ACK_WAIT_MS]. */
    private val ackWatchdogs = HashMap<Int, Job>()

    /** Adresse → horodatage (elapsedRealtime) avant lequel on ne retente pas. */
    private val retryAfterMs = LinkedHashMap<String, Long>()

    /** Fil protocole : transferts en cours d'émission et dernier connect sortant. */
    private var activeOutgoing = 0
    private var lastConnectMs = 0L

    /** Scan et annonce suspendus pendant un transfert — l'antenne au débit. */
    private var radioPaused = false

    private fun key(kind: LinkKind, address: String) =
        if (kind == LinkKind.CLIENT) "c:$address" else "s:$address"

    fun start() {
        val adapter = this.adapter
        if (adapter == null || !adapter.isEnabled) {
            _status.update { it.copy(lastError = "Bluetooth désactivé") }
            return
        }
        // journalisée pour recouper les deux bancs : la clé que le pair
        // annoncera comme « pair … » doit être celle-ci
        Log.i(
            TAG,
            "identité Noise ${Hex.encode(noisePublicKey).take(16)}… — " +
                (nostrKeys?.npubShort ?: "aucun noyau incarné : pas d'attestation"),
        )
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
                maybeResumeRadio()
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
        // Texte : tous les liens (léger). Image/fichier : UN seul lien — les
        // rafales parallèles sur les connexions croisées de la même paire
        // tuent la radio en ~20 s (banc 2026-08-11 : le seul transfert qui a
        // tenu 134 s roulait sur un lien unique), et le récepteur ignore de
        // toute façon le flux jumeau.
        //
        // Préférence : lien CLIENT le plus récent — ses écritures sont
        // acquittées, donc il est plus probablement vivant. Préférer le lien
        // serveur a été essayé au banc le 2026-08-11 pour gagner en débit
        // (la notification n'attend aucun retour du pair) et rejeté : sur
        // 1093 fragments, 550 Ko ont disparu en silence — aucune trame reçue
        // en face — pendant que l'émetteur rapportait un succès. La file de
        // notifications n'a ni contrôle de flux ni signal d'échec. Le gain
        // n'était d'ailleurs pas au rendez-vous : 16 Ko/s de bout en bout,
        // dans la fourchette de l'écriture.
        val targets = if (kind == ChatFrames.KIND_TEXT) {
            perAddress.values.toList()
        } else {
            listOf(
                perAddress.values.lastOrNull { it.kind == LinkKind.CLIENT }
                    ?: perAddress.values.last(),
            )
        }
        var primary = true
        targets.forEach { link ->
            link.transfers.trySend(Outgoing(msgId, kind, name, mime, content, primary))
            primary = false
        }
        Log.i(TAG, "message $msgId (${content.size} o) mis en file vers ${targets.size} lien(s)")
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
        activeOutgoing++
        pauseRadioForTransfer()
        // certains empilements relâchent la priorité au fil du temps :
        // on la redemande au début de chaque transfert
        if (out.primary) sentAtMs[out.msgId] = SystemClock.elapsedRealtime() to out.content.size
        requestHighPriority(link)
        try {
            runTransferInner(link, out)
        } finally {
            activeOutgoing--
            maybeResumeRadio()
        }
    }

    /**
     * Demande l'intervalle court. Réservé au rôle client : côté serveur GATT,
     * Android n'offre aucun moyen de peser sur les paramètres du lien.
     */
    private fun requestHighPriority(link: Link) {
        runCatching { link.gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
    }

    // ── Handshake Noise ───────────────────────────────────────────────────

    /**
     * Fil protocole. Ouvre le handshake sur un lien client fraîchement prêt.
     *
     * Le premier message XX ne porte **aucune charge utile** : il précède tout
     * échange de clés et voyagerait en clair. Le npub attendra le deuxième.
     */
    private fun beginHandshake(link: Link) {
        if (link.kind != LinkKind.CLIENT || link.noise != null) return
        if (!ChatFrames.canSeal(link.mtu)) {
            // MTU refusé par le pair : le premier message XX ne passerait pas
            Log.w(TAG, "handshake impossible vers ${link.address} : mtu=${link.mtu}")
            return
        }
        val session = NoiseSession.initiator(noiseStaticKey)
        link.noise = session
        if (!sendHandshake(link, session, step = 1)) {
            Log.w(TAG, "handshake : premier message impossible vers ${link.address}")
            link.noise = null
            session.destroy()
        }
    }

    /** Fil protocole. Produit et met en file le prochain message du handshake. */
    private fun sendHandshake(link: Link, session: NoiseSession, step: Int): Boolean {
        // le 1er message précède tout échange de clés : sa charge utile
        // voyagerait en clair, notre attestation attend donc le 2e ou le 3e
        val payload = if (step == 1) ByteArray(0) else vouch ?: ByteArray(0)
        val message = runCatching { session.writeHandshake(payload) }.getOrElse { error ->
            Log.w(TAG, "handshake : écriture impossible vers ${link.address} — $error")
            return false
        }
        val frame = ChatFrames.encodeHandshake(step, message, ChatFrames.attPayload(link.mtu))
        if (frame == null) {
            // au MTU plancher (23) le premier message XX ne passe pas : le
            // handshake exige un MTU négocié, il n'y a pas de repli
            Log.w(TAG, "handshake : message $step trop long pour l'ATT de ${link.address}")
            return false
        }
        link.control.trySend(frame)
        return true
    }

    /**
     * Fil protocole. Un message de handshake arrive : le lien client l'a
     * reçu en notification, le lien serveur en écriture du pair.
     */
    private fun onHandshakeFrame(kind: LinkKind, from: String, frame: ChatFrame.Handshake) {
        val link = links[key(kind, from)] ?: return
        val session = link.noise ?: when (kind) {
            // le répondeur ne crée sa session qu'à l'arrivée du premier message
            LinkKind.SERVER -> NoiseSession.responder(noiseStaticKey).also { link.noise = it }
            LinkKind.CLIENT -> {
                Log.w(TAG, "handshake : message ${frame.step} sans session côté client de $from")
                return
            }
        }
        val read = runCatching { session.readHandshake(frame.message) }
        if (read.isFailure) {
            // message hors séquence, altéré, ou pair qui reprend un handshake
            // sur une session déjà engagée : rien de récupérable ici
            Log.w(TAG, "handshake : message ${frame.step} refusé de $from — ${read.exceptionOrNull()}")
            failHandshake(link)
            return
        }
        checkVouch(link, session, read.getOrDefault(ByteArray(0)))
        when (session.step) {
            // écrire le 3e message établit l'initiateur sans qu'il ait à relire
            NoiseSession.Step.WRITE ->
                if (!sendHandshake(link, session, frame.step + 1)) {
                    failHandshake(link)
                    return
                }
            NoiseSession.Step.READ -> Unit // au pair de parler
            NoiseSession.Step.DONE -> Unit
            NoiseSession.Step.FAILED -> {
                failHandshake(link)
                return
            }
        }
        if (session.established) onHandshakeDone(link, session)
    }

    /**
     * Fil protocole. Confronte l'attestation reçue à la clé statique tirée de
     * NOTRE handshake — c'est ce recoupement, et lui seul, qui empêche de
     * rejouer l'attestation d'un autre sur son propre canal.
     *
     * Une charge utile vide est légitime : le pair n'a pas de noyau incarné.
     * Une attestation présente mais fausse ne l'est pas — on jette le lien.
     */
    private fun checkVouch(link: Link, session: NoiseSession, payload: ByteArray) {
        if (payload.isEmpty() || link.peerNostrKey != null) return
        val remote = session.remoteStaticKey ?: return
        val attested = NoiseVouch.verify(payload, remote)
        if (attested == null) {
            Log.w(TAG, "attestation invalide de ${link.address} : lien rejeté")
            failHandshake(link)
            failLink(link)
            return
        }
        link.peerNostrKey = attested
        val npub = Bech32.encode("npub", attested)
        Log.i(TAG, "pair attesté sur ${link.address} : ${npub.take(12)}…${npub.takeLast(4)}")
    }

    private fun onHandshakeDone(link: Link, session: NoiseSession) {
        val remote = session.remoteStaticKey ?: return
        if (!link.noiseAnnounced) {
            link.noiseAnnounced = true
            Log.i(
                TAG,
                "handshake Noise abouti sur ${link.kind} ${link.address} — " +
                    "pair ${Hex.encode(remote).take(16)}…",
            )
            refreshLinks()
        }
    }

    /**
     * Fil protocole. Scelle une trame si la session du lien est établie.
     *
     * Le scellement est **explicite** (trame SEALED) plutôt que déduit de
     * l'état : l'initiateur devient établi en écrivant le 3e message, le
     * répondeur seulement en le lisant. Entre les deux, une trame déjà en vol
     * serait lue avec la mauvaise convention. Le type porté par la trame lève
     * toute ambiguïté, quel que soit l'ordre d'arrivée.
     *
     * Les trames HELLO ne passent pas par ici : elles précèdent la session.
     */
    private fun seal(link: Link, plain: ByteArray): ByteArray? {
        if (ChatFrames.isHandshake(plain)) return plain
        val session = link.noise?.takeIf { it.established } ?: return plain
        return runCatching { ChatFrames.encodeSealed(session.encrypt(plain)) }
            .onSuccess {
                // sans cette trace, un lien resté en clair serait indiscernable
                // d'un lien chiffré : tout fonctionne pareil dans les deux cas
                if (!link.sealingAnnounced) {
                    link.sealingAnnounced = true
                    Log.i(TAG, "trafic scellé sur ${link.kind} ${link.address}")
                }
            }
            .getOrElse { error ->
                Log.w(TAG, "scellement impossible vers ${link.address} — $error")
                null
            }
    }

    /**
     * Fil protocole. Ouvre une trame scellée.
     *
     * Un échec n'est pas récupérable : le compteur de ChaCha20-Poly1305 exige
     * une séquence stricte, donc une trame perdue désaccorde la session pour
     * de bon. On jette le lien plutôt que d'entretenir un canal sourd — le
     * scan en reformera un, avec un handshake neuf.
     */
    private fun unseal(link: Link, frame: ChatFrame.Sealed): ByteArray? {
        val session = link.noise?.takeIf { it.established }
        if (session == null) {
            Log.w(TAG, "trame scellée de ${link.address} sans session établie")
            failLink(link)
            return null
        }
        return runCatching { session.decrypt(frame.ciphertext) }.getOrElse { error ->
            Log.w(TAG, "ouverture impossible depuis ${link.address} — $error")
            failLink(link)
            null
        }
    }

    private fun failHandshake(link: Link) {
        link.noise?.destroy()
        link.noise = null
        link.noiseAnnounced = false
        refreshLinks()
    }

    /** Fil protocole. Coupe scan + annonce le temps des transferts. */
    private fun pauseRadioForTransfer() {
        if (radioPaused) return
        radioPaused = true
        runCatching { scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) } }
        runCatching { advertiseCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) } }
        scanCallback = null
        advertiseCallback = null
        Log.i(TAG, "radio en pause : transfert en cours")
    }

    /** Fil protocole. Relance scan + annonce quand plus rien ne transite. */
    private fun maybeResumeRadio() {
        if (!radioPaused) return
        if (activeOutgoing > 0 || reassembler.activeStreams() > 0) return
        radioPaused = false
        startAdvertising()
        startScan()
        Log.i(TAG, "radio relancée : transferts terminés")
    }

    private suspend fun runTransferInner(link: Link, out: Outgoing) {
        // budget d'une trame ordinaire, scellement Noise déjà déduit
        val att = ChatFrames.framePayload(link.mtu)
        val start = ChatFrames.encodeStart(
            ChatFrame.Start(out.msgId, out.kind, out.content.size, ChatFrames.crc32(out.content), out.name, out.mime),
            att,
        )
        if (start == null || !writeFrame(link, start, withResponse = link.kind == LinkKind.CLIENT)) {
            onTransferFailed(link, out)
            if (start != null) failLink(link)
            return
        }
        val chunk = ChatFrames.dataChunk(link.mtu).coerceAtLeast(1)
        val startedAt = SystemClock.elapsedRealtime()
        var priorityRetried = false
        var offset = 0
        var index = 0
        while (offset < out.content.size) {
            // les acquittements se glissent entre deux fragments
            while (true) {
                val control = link.control.tryReceive().getOrNull() ?: break
                writeFrame(link, control)
            }
            val end = minOf(offset + chunk, out.content.size)
            val windowEdge = end == out.content.size || index % ACK_WINDOW == ACK_WINDOW - 1
            val reliable = windowEdge && link.kind == LinkKind.CLIENT
            if (!writeFrame(link, ChatFrames.encodeData(out.msgId, index, out.content, offset, end), reliable)) {
                onTransferFailed(link, out)
                failLink(link)
                return
            }
            // pas d'équivalent « avec réponse » pour une notification :
            // on relâche la pression d'un souffle à chaque fenêtre
            if (windowEdge && link.kind == LinkKind.SERVER) delay(NOTIFY_WINDOW_PAUSE_MS)
            offset = end
            index++
            // l'intervalle de connexion n'est pas lisible : on l'observe par le
            // temps passé par fragment, seul reflet dont on dispose
            if (!priorityRetried && index == PRIORITY_RETRY_AT_FRAGMENT) {
                priorityRetried = true
                val perFragment = (SystemClock.elapsedRealtime() - startedAt) / index
                if (perFragment > SLOW_FRAGMENT_MS) {
                    // constat seul : reposer la priorité ne sert à rien ici —
                    // essayé au banc, y compris en rebondissant par BALANCED
                    // pour forcer une mise à jour, sans le moindre gain
                    Log.w(TAG, "lien lent ($perFragment ms/fragment)")
                }
            }
            if (out.primary) publishProgress(out.msgId, offset, out.content.size)
        }
        if (out.primary) {
            progressPct.remove(out.msgId)
            updateMessage(out.msgId) {
                // un ACK arrivé pendant les derniers fragments a déjà posé ✓✓
                if (it.status == ChatStatus.SENDING) it.copy(status = ChatStatus.SENT, progress = 1f)
                else it.copy(progress = 1f)
            }
            // ✓ vaut promesse de remise sur le chemin écriture seulement :
            // côté notification, il faut l'ACK pour savoir, ou l'échec
            if (link.kind == LinkKind.SERVER) {
                armAckWatchdog(out.msgId, link.address, out.content.size)
            }
        }
        // ms/fragment journalisés : sans eux, un lien resté à 48 ms ressemble à
        // un lien à 15 ms, en trois fois plus lent. Le chemin notification, lui,
        // ne fait qu'empiler : son vrai débit se lit à l'accusé, pas ici.
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1)
        val verb = if (link.kind == LinkKind.CLIENT) "émis" else "remis à la pile"
        Log.i(
            TAG,
            "message ${out.msgId} $verb (${out.content.size} o, $index fragment(s)) " +
                "vers ${link.address} — $elapsed ms, ${elapsed / maxOf(index, 1)} ms/fragment",
        )
    }

    /**
     * Fil protocole. Bascule le message en échec si aucun ACK ne revient : sur
     * notification, c'est le seul aveu de perte qu'on puisse obtenir.
     */
    private fun armAckWatchdog(msgId: Int, address: String, bytes: Int) {
        ackWatchdogs.remove(msgId)?.cancel()
        val wait = ackWaitMs(bytes)
        ackWatchdogs[msgId] = scope.launch {
            delay(wait)
            ackWatchdogs.remove(msgId)
            sentAtMs.remove(msgId)
            var lost = false
            updateMessage(msgId) { message ->
                // un ACK déjà arrivé a posé ✓✓ : plus rien à guetter
                if (message.status != ChatStatus.SENT) message
                else {
                    lost = true
                    message.copy(status = ChatStatus.FAILED)
                }
            }
            if (lost) {
                Log.w(TAG, "message $msgId sans accusé après ${wait / 1000} s : perdu")
                _status.update { it.copy(lastError = "sans accusé de ${address.takeLast(5)}") }
            }
        }
    }

    private fun onTransferFailed(link: Link, out: Outgoing) {
        Log.w(TAG, "échec d'émission de ${out.msgId} vers ${link.address}")
        if (out.primary) {
            progressPct.remove(out.msgId)
            sentAtMs.remove(out.msgId)
            ackWatchdogs.remove(out.msgId)?.cancel()
            updateMessage(out.msgId) { it.copy(status = ChatStatus.FAILED) }
            _status.update { it.copy(lastError = "échec d'envoi vers ${link.address.takeLast(5)}") }
        }
    }

    /**
     * Fil protocole. Un lien dont les écritures échouent est un lien fantôme :
     * observé sur banc (2026-08-11), l'ACL peut mourir SANS callback de
     * déconnexion côté client — la pile refuse alors tout, indéfiniment. On
     * coupe et on retire : le scan reformera une connexion fraîche (délai de
     * grâce 2 s, le lien avait été prêt).
     */
    private fun failLink(link: Link) {
        Log.w(TAG, "lien ${link.address} déclaré mort après échec d'écriture")
        removeLink(link.kind, link.address)
    }

    /** Sérialise les notifications : une seule en vol par serveur GATT. */
    private val notifyMutex = Mutex()

    private enum class WriteOutcome { OK, FAILED, BUSY }

    /**
     * Une écriture démarrée puis échouée ou expirée n'est JAMAIS réémise à
     * l'aveugle (elle a pu partir : le récepteur verrait un doublon) — seule
     * la pile occupée (écriture non démarrée) se retente, avec repli.
     */
    private suspend fun writeFrame(link: Link, plain: ByteArray, withResponse: Boolean = false): Boolean {
        val frame = seal(link, plain) ?: return false
        val deadline = SystemClock.elapsedRealtime() + BUSY_DEADLINE_MS
        var attempt = 0
        while (true) {
            val outcome =
                if (link.kind == LinkKind.SERVER) notifyMutex.withLock { attemptWrite(link, frame, withResponse) }
                else attemptWrite(link, frame, withResponse)
            when (outcome) {
                WriteOutcome.OK -> return true
                WriteOutcome.FAILED -> return false
                WriteOutcome.BUSY -> {
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        Log.w(TAG, "pile occupée > ${BUSY_DEADLINE_MS / 1000} s vers ${link.address}")
                        return false
                    }
                    attempt++
                    delay(minOf(BUSY_DELAY_MAX_MS, 30L * attempt))
                }
            }
        }
    }

    private suspend fun attemptWrite(link: Link, frame: ByteArray, withResponse: Boolean): WriteOutcome {
        val done = CompletableDeferred<Boolean>()
        link.pending.addLast(done)
        val started = runCatching {
            when (link.kind) {
                LinkKind.CLIENT -> clientWrite(link, frame, withResponse)
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

    private fun clientWrite(link: Link, frame: ByteArray, withResponse: Boolean): Boolean = runCatching {
        val gatt = link.gatt ?: return false
        val characteristic = link.characteristic ?: return false
        val writeType =
            if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = gatt.writeCharacteristic(characteristic, frame, writeType)
            if (code != BluetoothStatusCodes.SUCCESS) {
                Log.w(TAG, "écriture refusée code=$code vers ${link.address}")
            }
            code == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = frame
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }.getOrElse { e ->
        Log.w(TAG, "écriture : exception ${e.javaClass.simpleName} ${e.message} vers ${link.address}")
        false
    }

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

    /**
     * Fil protocole. [kind] désigne le lien qui a reçu : notification pour un
     * lien client, écriture du pair pour un lien serveur. Le handshake en
     * dépend — le lien client de A répond au lien serveur de B, pas l'inverse.
     */
    private fun handleFrame(kind: LinkKind, from: String, bytes: ByteArray) {
        when (val frame = ChatFrames.decode(bytes)) {
            null -> Log.w(TAG, "trame illisible de $from (${bytes.size} o)")
            is ChatFrame.Handshake -> onHandshakeFrame(kind, from, frame)
            is ChatFrame.Sealed -> {
                val link = links[key(kind, from)]
                if (link == null) Log.w(TAG, "trame scellée d'un lien inconnu : $from")
                else unseal(link, frame)?.let { handlePlainFrame(kind, from, it) }
            }
            else -> handlePlainFrame(kind, from, bytes)
        }
    }

    /** Fil protocole. Une trame en clair, scellée à l'origine ou non. */
    private fun handlePlainFrame(kind: LinkKind, from: String, bytes: ByteArray) {
        when (val frame = ChatFrames.decode(bytes)) {
            null -> Log.w(TAG, "trame illisible de $from (${bytes.size} o)")
            // une trame ouverte ne peut pas en contenir une autre
            is ChatFrame.Sealed, is ChatFrame.Handshake ->
                Log.w(TAG, "trame ${frame::class.simpleName} imbriquée de $from : ignorée")
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
        pauseRadioForTransfer()
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
        maybeResumeRadio()
    }

    private fun onIncomingFailed(event: Reassembler.Event.Failed) {
        Log.w(TAG, "réception ${event.msgId} : ${event.reason}")
        progressPct.remove(event.msgId)
        updateMessage(event.msgId) { it.copy(status = ChatStatus.FAILED) }
        event.ackStatus?.let { broadcastControl(ChatFrames.encodeAck(event.msgId, it)) }
        maybeResumeRadio()
    }

    private fun onAck(ack: ChatFrame.Ack) {
        ackWatchdogs.remove(ack.msgId)?.cancel()
        val sent = sentAtMs.remove(ack.msgId)
        if (sent != null) {
            val elapsed = (SystemClock.elapsedRealtime() - sent.first).coerceAtLeast(1)
            Log.i(
                TAG,
                "acquittement ${ack.msgId} statut ${ack.status} — bout en bout $elapsed ms, " +
                    "${sent.second / elapsed} Ko/s",
            )
        } else {
            Log.i(TAG, "acquittement ${ack.msgId} statut ${ack.status}")
        }
        updateMessage(ack.msgId) { message ->
            when {
                !message.mine -> message
                // un accusé prouve la remise quand qu'il arrive : il relève un
                // message que le guetteur avait déjà donné pour perdu
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
                BluetoothGattCharacteristic.PROPERTY_WRITE or
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
            scope.launch { handleFrame(LinkKind.SERVER, device.address, bytes) }
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
        if (kind == LinkKind.CLIENT) {
            backoff(
                address,
                if (link.ready) CONNECT_BACKOFF_LOST_MS else CONNECT_BACKOFF_FAILED_MS,
            )
        }
        // les secrets meurent avec le lien : une session Noise ne se rejoue pas
        link.noise?.destroy()
        link.noise = null
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
                // BALANCED : LOW_LATENCY en continu dispute la radio aux
                // transferts GATT (leçon du module proximité, revue sur banc)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
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
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(),
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
        // la radio reste au transfert : aucune connexion sortante pendant
        // qu'on émet ou réassemble
        if (activeOutgoing > 0 || reassembler.activeStreams() > 0) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastConnectMs < CONNECT_SPACING_MS) return
        val notBefore = retryAfterMs[address]
        if (notBefore != null && now < notBefore) return
        lastConnectMs = now
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
            backoff(address, CONNECT_BACKOFF_FAILED_MS)
            Log.w(TAG, "connectGatt null pour $address")
            return
        }
        link.gatt = gatt
    }

    /** Fil protocole. Pose le délai de grâce d'une adresse, map bornée. */
    private fun backoff(address: String, delayMs: Long) {
        retryAfterMs.remove(address)
        retryAfterMs[address] = SystemClock.elapsedRealtime() + delayMs
        while (retryAfterMs.size > BACKOFF_MAX_ENTRIES) {
            retryAfterMs.remove(retryAfterMs.keys.first())
        }
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
            // intervalle court pendant la causerie : stabilise le lien sous
            // rafale d'écritures et augmente le débit (pratique type DFU)
            runCatching {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
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
                beginHandshake(link)
                refreshLinks()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "callback d'écriture status=$status de ${gatt.device.address}")
            }
            scope.launch {
                links[key(LinkKind.CLIENT, gatt.device.address)]
                    ?.pending?.removeFirstOrNull()
                    ?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value?.copyOf() ?: return
            scope.launch { handleFrame(LinkKind.CLIENT, gatt.device.address, bytes) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val bytes = value.copyOf()
            scope.launch { handleFrame(LinkKind.CLIENT, gatt.device.address, bytes) }
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
