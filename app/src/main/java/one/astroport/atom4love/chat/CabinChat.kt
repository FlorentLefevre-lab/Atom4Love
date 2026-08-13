package one.astroport.atom4love.chat

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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
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
import one.astroport.atom4love.chat.net.FramedSocket
import one.astroport.atom4love.chat.net.P2pGroup
import one.astroport.atom4love.chat.wire.BytesSource
import one.astroport.atom4love.chat.wire.ChatFrame
import one.astroport.atom4love.chat.wire.ChatFrames
import one.astroport.atom4love.chat.wire.FileSink
import one.astroport.atom4love.chat.wire.FileSource
import one.astroport.atom4love.chat.wire.MemorySink
import one.astroport.atom4love.chat.wire.Payload
import one.astroport.atom4love.chat.wire.Reassembler
import one.astroport.atom4love.chat.wire.Source
import one.astroport.atom4love.nostr.CabinSalon
import one.astroport.atom4love.proximity.RadioSilence
import one.astroport.atom4love.noise.NoiseIdentity
import one.astroport.atom4love.noise.NoiseSession
import one.astroport.atom4love.noise.NoiseVouch
import one.astroport.atom4love.nostr.Bech32
import one.astroport.atom4love.nostr.Hex
import one.astroport.atom4love.nostr.NostrKeys
import java.util.zip.CRC32

/**
 * Le canal direct de la cabine : ce qui se dit **ici**, entre gens à portée.
 *
 * Rien de ce qui passe par ce moteur ne part sur un relais NOSTR ni ne sort de
 * la portée : la cabine et l'hexagone sont deux mondes étanches, et cette
 * étanchéité est le principe, pas un effet de bord. Le salon d'hexagone
 * ([CabinSalon]) sert l'autre portée, celle qu'on n'atteint pas directement.
 *
 * Le trafic est chiffré par Noise XX dès qu'un lien a mené son handshake :
 * tout ce qui suit — START, DATA, ACK, ADDR — voyage scellé. Restent en clair
 * le premier message du handshake (il précède l'échange de clés) et les liens
 * dont le MTU ne permet pas de handshake.
 *
 * ## Trois médiums, une seule porte
 *
 * Le **BLE est la seule porte d'entrée** ([Medium]) : lui seul découvre un
 * inconnu et l'atteste. Architecture symétrique — chaque appareil est à la fois
 * périphérique (annonce connectable + serveur GATT) et central (scan +
 * connexion aux pairs vus). Une fois le pair attesté, chacun lui annonce dans
 * le canal scellé par où il est joignable en Wi-Fi (trame `ADDR`) : c'est tout
 * ce qui remplace une découverte réseau, et personne ne peut énumérer les
 * cabines d'un LAN. Le passage effectif au Wi-Fi n'a lieu que si l'utilisateur
 * l'accepte ([enable]) — la cabine s'établit toujours d'elle-même en BLE.
 *
 * Ce qui est commun aux trois médiums : les trames de `chat/wire`, la session
 * Noise, l'attestation, le réassemblage, la progression et l'accusé de bout en
 * bout. Ce qui diffère tient dans [Link] — comment on écrit un paquet d'octets,
 * et combien il en tient. Le routage choisit **par personne** (npub attesté) le
 * lien du meilleur médium accepté : un pair joignable des deux côtés ne reçoit
 * jamais deux fois le même message.
 *
 * Toute la machinerie protocolaire vit sur un fil unique ([dispatcher]) ;
 * les callbacks Binder et les fils de socket n'y déposent que des
 * `scope.launch`.
 */
@SuppressLint("MissingPermission")
class CabinChat(context: Context) {

    companion object {
        private const val TAG = "CabinChat"

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
        /**
         * Plafond d'une pièce jointe **par médium**, parce que ce n'est pas la
         * même expérience.
         *
         * En BLE, 14 Ko/s : 2 Mo tiennent déjà 2 min 20, et 10 Mo prendraient
         * un quart d'heure — personne n'attend ça, et le moindre incident de
         * lien perd tout. Par la station ou en pair à pair, le même fichier
         * passe en quelques secondes (mesuré : 1,4 Mo en 1,3 s), donc 10 Mo
         * sont un plafond honnête.
         *
         * Le plafond retenu est celui du **plus lent des liens visés** : si un
         * pair n'est joignable qu'en radio, c'est lui qui décide, sinon la
         * pièce partirait vers lui pour un quart d'heure.
         *
         * **Le plafond du flux est passé de 10 à 200 Mo** le 13/08, une fois le
         * transfert sorti de la mémoire. Il valait 10 Mo parce que les deux
         * bouts tenaient la pièce entière dans un `ByteArray` — pas parce que
         * le réseau peinait : à 15,5 Mo/s mesurés, 200 Mo passent en treize
         * secondes. C'était donc un plafond de mémoire déguisé en plafond de
         * débit, et il interdisait la vidéo à qualité initiale (10 Mo ne font
         * que 4,5 s de 1080p). Ce qui le borne maintenant est la place sur le
         * disque, et une copie ratée se voit franchement.
         */
        const val MAX_TRANSFER_BLE = 2_000_000
        const val MAX_TRANSFER_STREAM = 200_000_000

        /** Ce qu'un récepteur accepte, quel que soit le médium d'arrivée. */
        const val MAX_TRANSFER_BYTES = MAX_TRANSFER_STREAM

        /** Tampon des lectures qui ne servent qu'à calculer un CRC. */
        private const val CRC_BUFFER = 64 * 1024

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
         * Délais de grâce et espacement des connexions sortantes : voir
         * [DialPacing], qui les tient tous. Sans eux, un annonceur FFF1 du
         * voisinage qui refuse la connexion (status 133) déclenche une tempête
         * connexion/échec à ~0,5 Hz (observée sur banc le 2026-08-11), et un
         * annonceur qui tourne son adresse rend le délai par adresse
         * inopérant — chaque tentative monopolise la radio ~2 s et fait mourir
         * les transferts en cours.
         */
        private const val BACKOFF_MAX_ENTRIES = 64

        /**
         * Composition TCP vers une adresse annoncée. Court : le pair est sur le
         * même réseau local, et une adresse périmée (il a changé de station) ne
         * doit pas retenir la montée pendant une minute.
         */
        private const val DIAL_TIMEOUT_MS = 4_000

        /** Tout groupe Wi-Fi Direct vit dans ce sous-réseau, son maître en `.1`. */
        private const val P2P_SUBNET = "192.168.49."
    }

    enum class Chime { SENT, RECEIVED }

    data class Status(
        val advertising: Boolean = false,
        val scanning: Boolean = false,
        /** Liens utilisables (clients prêts à écrire + centraux abonnés). */
        val links: Int = 0,
        /**
         * Liens prêts dont le pair n'a pas signé d'attestation — il n'a pas de
         * noyau incarné. Compté ici parce qu'une personne attestée tient
         * souvent deux liens : `links - peers.size` mentirait.
         */
        val unattestedLinks: Int = 0,
        /**
         * Le médium par lequel la cabine parle réellement — le meilleur en
         * service, tous pairs confondus. null quand personne n'est là.
         */
        val medium: Medium? = null,
        /**
         * Un médium plus rapide qu'un pair nous a annoncé et que l'utilisateur
         * n'a pas encore accepté. C'est de là que vient la proposition de
         * montée : on ne bascule jamais dans son dos.
         */
        val offered: Medium? = null,
        /**
         * Le dernier incident, **en valeur** : ce moteur n'a pas de `Context`
         * et ne saurait pas dans quelle langue l'écrire. L'écran s'en charge
         * ([CabinError.text]).
         */
        val lastError: CabinError? = null,
    )

    /**
     * Un noyau présent dans la cabine, identifié par l'attestation qu'il a
     * signée pendant le handshake — pas par son adresse radio, qui tourne.
     *
     * Une même personne tient souvent DEUX liens avec nous (le sien vers nous,
     * le nôtre vers elle) : c'est le npub qui les réunit en une seule présence.
     */
    data class Peer(val nostrKey: ByteArray, val npub: String) {
        /** `npub1u9v…eqx2` — de quoi reconnaître sans étaler la clé. */
        val short: String get() = "${npub.take(8)}…${npub.takeLast(4)}"

        override fun equals(other: Any?) =
            this === other || (other is Peer && nostrKey.contentEquals(other.nostrKey))

        override fun hashCode() = nostrKey.contentHashCode()
    }

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /**
     * Une pièce refusée parce qu'elle dépasse le plafond du médium du moment.
     *
     * Portée en état (et non en simple message d'erreur) parce qu'elle appelle
     * un dialogue : quelqu'un vient de choisir un fichier dans un sélecteur
     * système, il doit apprendre pourquoi il ne part pas, et de combien.
     */
    /**
     * La cabine refuse une pièce, et dit pourquoi. Deux raisons aujourd'hui, et
     * elles ne se disent pas pareil : l'une est une question de patience, l'autre
     * une règle. Toutes deux méritent un dialogue — le sélecteur système vient
     * de se refermer, sans quoi il ne se passerait visiblement rien.
     */
    sealed interface Refusal {
        val name: String

        /** Le médium le plus lent qu'on emprunterait : c'est lui qui décide. */
        val medium: Medium

        /** Trop lourde pour ce que ce médium accepte. [bytes] < 0 si inconnue. */
        data class TooBig(
            override val name: String,
            val bytes: Long,
            val limit: Int,
            override val medium: Medium,
        ) : Refusal

        /**
         * Une vidéo part **à la qualité où elle a été filmée**, jamais
         * recompressée : c'est le médium qui l'autorise, pas la compression.
         * Le Wi-Fi la porte, la radio non — et ce n'est pas une affaire de
         * plafond, c'est la règle.
         */
        data class VideoNeedsWifi(
            override val name: String,
            override val medium: Medium,
        ) : Refusal
    }

    private val _refusal = MutableStateFlow<Refusal?>(null)
    val refusal: StateFlow<Refusal?> = _refusal.asStateFlow()

    fun dismissRefusal() {
        _refusal.value = null
    }

    /**
     * Le médium le plus lent parmi ceux qu'on emprunterait — celui qui décide
     * du plafond, et celui qu'on nomme dans un refus. Sans lien du tout, la
     * radio : c'est le pire cas, et promettre mieux serait mentir.
     */
    private fun slowestMedium(): Medium =
        routes().minByOrNull { it.medium.rank }?.medium ?: Medium.BLE

    /**
     * Une vidéo ne part que par Wi-Fi — pair à pair ou par le réseau du lieu.
     *
     * Ce n'est pas un plafond déguisé : elle part **telle qu'elle a été
     * filmée**, et la radio tient 14 Ko/s. Sans aucun lien, on laisse passer :
     * l'échec qui suit dira « aucun lien pour émettre », ce qui est la vraie
     * raison, plutôt que de réclamer un Wi-Fi qui ne changerait rien.
     */
    private fun videoAllowed(): Boolean {
        val media = attachmentTargets(routes()).map { it.medium }
        return media.isEmpty() || media.none { it == Medium.BLE }
    }

    /**
     * Plafond courant : celui du plus lent des liens qu'on emprunterait. Sans
     * lien du tout, on retient le plafond radio — c'est le pire cas, et
     * promettre 10 Mo avant de savoir par où ça passe serait mentir.
     */
    fun transferLimit(): Int {
        val media = attachmentTargets(routes()).map { it.medium }
        return if (media.isEmpty() || media.any { it == Medium.BLE }) {
            MAX_TRANSFER_BLE
        } else {
            MAX_TRANSFER_STREAM
        }
    }

    /**
     * Fil protocole. Qui reçoit une pièce jointe, parmi les routes ouvertes.
     *
     * La contrainte est celle de la RADIO, pas du protocole : les rafales
     * parallèles sur les connexions BLE croisées tuent l'antenne en ~20 s (banc
     * 2026-08-11 : le seul transfert qui a tenu 134 s roulait sur un lien
     * unique). Un seul lien BLE, donc, mais autant de liens en flux qu'il y a
     * de pairs — là, chaque transfert a sa socket.
     *
     * Les liens **non attestés** sont écartés dès qu'un pair attesté est
     * joignable : personne n'est encore derrière eux, le va-et-vient du scan en
     * fabrique sans cesse, et les laisser compter ferait osciller le plafond de
     * pièce jointe entre 2 et 10 Mo sous les yeux de l'utilisateur.
     */
    private fun attachmentTargets(routes: List<Link>): List<Link> {
        val attested = routes.filter { it.peerNostrKey != null }
        val considered = attested.ifEmpty { routes }
        val streamed = considered.filter { it.medium != Medium.BLE }
        val radio = considered.lastOrNull { it.medium == Medium.BLE }
        return streamed + listOfNotNull(radio)
    }

    /** Qui est là, une entrée par noyau attesté — jamais par lien. */
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

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
        /** Ouverte une fois par lien : le même message part vers plusieurs pairs. */
        val source: Source,
        /** Calculé une seule fois, à l'expédition — pas une fois par lien. */
        val crc32: Int,
        /** Ce lien pilote la barre de progression et le statut du message. */
        val primary: Boolean,
    ) {
        val size: Int get() = source.size
    }

    private class Link(val medium: Medium, val kind: LinkKind, val address: String) {
        var mtu = DEFAULT_MTU
        var gatt: BluetoothGatt? = null                              // CLIENT
        var characteristic: BluetoothGattCharacteristic? = null      // CLIENT
        var device: BluetoothDevice? = null                          // SERVER
        var stream: FramedSocket? = null                             // médiums Wi-Fi

        /** Le BLE compte ses octets par écriture ATT ; TCP par trame. */
        val capacity: Int
            get() = if (medium == Medium.BLE) ChatFrames.attPayload(mtu) else ChatFrames.STREAM_CAPACITY

        /** Sur TCP le handshake passe toujours : 32 Ko contre 96 octets. */
        val sealCapable: Boolean
            get() = medium != Medium.BLE || ChatFrames.canSeal(mtu)

        /** Place d'une trame ordinaire, réserve de scellement déduite. */
        val payload: Int
            get() = if (medium == Medium.BLE) {
                ChatFrames.framePayload(mtu)
            } else {
                ChatFrames.STREAM_CAPACITY - ChatFrames.SEAL_OVERHEAD
            }

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

        /**
         * Chronos d'un transfert, remis à zéro à chaque départ : temps passé à
         * sceller (Noise, sur le fil protocole) et temps passé à remettre au
         * transport. Les deux se mesurent séparément parce qu'ils se soignent
         * différemment — l'un est du calcul, l'autre de la contre-pression.
         */
        var sealMs = 0L
        var wireMs = 0L

        /**
         * Symétrique à la réception : temps passé à attendre des octets, et
         * temps passé à les traiter (ouverture Noise, réassemblage). Si le
         * traitement domine, c'est nous qui étranglons l'émetteur par
         * contre-pression TCP ; si c'est l'attente, le tort est au réseau.
         */
        var readMs = 0L
        var handleMs = 0L

        val ready: Boolean get() = when {
            medium != Medium.BLE -> stream != null
            kind == LinkKind.SERVER -> true
            else -> characteristic != null
        }

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

    /** clé = "<médium>:<rôle>:<adresse>" — `b:c:AA:BB:…` ou `w:s:10.42.0.99:41xxx`. */
    private val links = LinkedHashMap<String, Link>()

    /**
     * Médiums que l'utilisateur accepte d'emprunter. Le BLE y est d'office :
     * c'est la porte d'entrée, et la cabine doit s'établir sans rien demander.
     * Les autres n'entrent que par [enable].
     */
    private val enabledMedia = linkedSetOf(Medium.BLE)

    /**
     * Points d'entrée annoncés par les pairs et pas encore empruntés, par clé
     * NOSTR du pair. On les garde même quand le médium est refusé : accepter
     * plus tard doit pouvoir composer sans attendre une nouvelle annonce.
     */
    private val offers = LinkedHashMap<String, MutableMap<Medium, Pair<String, Int>>>()

    /**
     * Groupes Wi-Fi Direct auxquels un pair nous a invités, par clé NOSTR, avec
     * le port d'écoute du propriétaire. Séparé des [offers] parce qu'un groupe
     * ne se joint pas par une adresse : il faut d'abord y entrer, et son
     * propriétaire est toujours en `192.168.49.1`.
     */
    private val groupOffers = LinkedHashMap<String, Pair<P2pGroup.Credentials, Int>>()

    /** Médiums dont une adresse annoncée s'est révélée injoignable. */
    private val unreachable = linkedSetOf<Medium>()

    private val p2p = P2pGroup(context)

    /** Vrai dès qu'on tient un groupe ou qu'on en a rejoint un. */
    private var inGroup = false

    /** MTU annoncés côté serveur, parfois avant l'abonnement CCCD. */
    private val serverMtus = HashMap<String, Int>()

    /**
     * Adresses des centraux abonnés — miroir des liens serveur, consultable
     * depuis le fil Binder (lecture synchrone du CCCD) sous synchronized.
     */
    private val subscribedAddresses = mutableSetOf<String>()

    /**
     * Le texte atterrit en mémoire — il devient une phrase, et il est borné.
     * Tout le reste va droit sur le disque, fragment par fragment : c'est ce
     * qui permet à une vidéo de peser plus que ce que le téléphone peut tenir.
     */
    private val reassembler = Reassembler(MAX_TRANSFER_BYTES) { start ->
        if (start.kind == ChatFrames.KIND_TEXT) {
            MemorySink(start.totalBytes)
        } else {
            FileSink(Attachments.destination(appContext, start.name))
        }
    }

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

    /** Fil protocole. À quel rythme on compose, et vers quelles adresses. */
    private val pacing = DialPacing()

    /** Fil protocole : transferts en cours d'émission. */
    private var activeOutgoing = 0

    /** Fil protocole. Ce que le scan ne dit pas : qui annonce. */
    private val bleIdentities = BleIdentities()

    /** Fil protocole. Les adresses qu'on a cessé d'appeler, pour ne le dire qu'une fois. */
    private val dialSkipped = LinkedHashSet<String>()

    /** Scan et annonce suspendus pendant un transfert — l'antenne au débit. */
    private var radioPaused = false

    private fun key(medium: Medium, kind: LinkKind, address: String) =
        "${medium.tag}:${if (kind == LinkKind.CLIENT) 'c' else 's'}:$address"

    /**
     * Fil protocole. Un lien par **personne**, au meilleur médium accepté.
     *
     * C'est le cœur de l'harmonisation : le routage ne raisonne plus par
     * adresse mais par npub attesté. Sans ça, un pair joignable en BLE *et* en
     * Wi-Fi recevrait deux fois le même message — ses deux adresses n'ayant
     * rien qui les rapproche. Un pair non attesté n'a que son adresse pour
     * identité ; le double lien croisé du BLE se regroupe quand même, les deux
     * liens portant la même adresse radio.
     */
    private fun routes(): List<Link> {
        val best = LinkedHashMap<String, Link>()
        links.values.forEach { link ->
            if (!link.ready || link.medium !in enabledMedia) return@forEach
            val who = link.peerNostrKey?.let { Hex.encode(it) } ?: "@${link.address}"
            val current = best[who]
            if (current == null || outranks(link, current)) best[who] = link
        }
        return best.values.toList()
    }

    /** Médium le plus haut d'abord ; à médium égal, le lien client (acquitté). */
    private fun outranks(candidate: Link, current: Link): Boolean = when {
        candidate.medium != current.medium -> candidate.medium.rank > current.medium.rank
        candidate.kind != current.kind -> candidate.kind == LinkKind.CLIENT
        else -> false
    }

    fun start() {
        val adapter = this.adapter
        if (adapter == null || !adapter.isEnabled) {
            _status.update { it.copy(lastError = CabinError.BluetoothOff) }
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
        startListener()
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
        // fermée avant l'annulation du scope : c'est ce qui débloque l'accept()
        runCatching { listener?.close() }
        listener = null
        listenPort = 0
        releaseWifiLock()
        // le groupe P2P ne survit pas à la cabine : l'interface resterait
        // montée, et un groupe ouvert est un réseau que personne ne surveille
        if (inGroup) runCatching { p2p.release() }
        inGroup = false
        scope.cancel()
        // le démontage des liens passe par le fil protocole : l'executor FIFO
        // le sérialise derrière tout corps de coroutine encore en cours
        runCatching {
            executor.submit {
                links.values.forEach { link ->
                    link.failPending()
                    runCatching { link.gatt?.close() }
                    link.stream?.close()
                }
                links.clear()
                serverMtus.clear()
                offers.clear()
                groupOffers.clear()
                synchronized(subscribedAddresses) { subscribedAddresses.clear() }
            }.get()
        }
        runCatching { server?.close() }
        server = null
        dispatcher.close()
        // fermer la cabine en plein transfert laisserait la balise muette pour
        // toujours : le silence était le nôtre, il part avec nous
        RadioSilence.request(false)
        // Les réceptions en cours écrivent dans files/chat/ : les abandonner
        // AVANT le balayage, sinon un fragment en retard recréerait un fichier
        // juste après qu'on a fini de nettoyer.
        reassembler.abortAll()
        // « tout s'efface en fermant » est écrit dans la cabine, en trois
        // langues. La conversation s'en allait bien avec l'instance, mais les
        // pièces jointes restaient sur le disque — de la première cabine à
        // celle-ci. Suppression d'unlink, pas de réécriture : assez brève pour
        // rester ici, dans le geste qui la promet.
        val wiped = Attachments.wipe(appContext)
        if (wiped > 0) Log.i(TAG, "cabine fermée : $wiped pièce(s) effacée(s)")
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
                        Log.w(TAG, "Bluetooth coupé : liens radio fermés")
                        // Les seuls liens BLE. Tout jeter emporterait les
                        // sockets Wi-Fi, qui n'ont rien à voir avec la radio
                        // qu'on vient d'éteindre — la cabine perdrait sa
                        // liaison la plus rapide en coupant le Bluetooth.
                        links.values.filter { it.medium == Medium.BLE }.toList().forEach { link ->
                            removeLink(link.medium, link.kind, link.address)
                        }
                        runCatching { server?.close() }
                        server = null
                        _status.update {
                            it.copy(advertising = false, scanning = false, lastError = CabinError.BluetoothCut)
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
            _status.update { it.copy(lastError = CabinError.TextTooLong(MAX_TEXT_BYTES)) }
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
        scope.launch { dispatch(msgId, ChatFrames.KIND_TEXT, "", "", BytesSource(bytes)) }
        return true
    }

    /**
     * Prépare (recompression) puis envoie une image.
     *
     * Le sélecteur visuel propose aussi les vidéos : une vidéo choisie là
     * n'est pas une image, et surtout **ne doit pas être recompressée**. Elle
     * repart donc par le chemin des vidéos, quel que soit le bouton touché.
     */
    fun sendImage(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            if (isVideo(uri)) return@launch sendVideo(uri)
            val read = Attachments.prepareImage(appContext, uri)
            withContext(dispatcher) {
                dispatchAttachment(ChatKind.IMAGE, ChatFrames.KIND_IMAGE, read, transferLimit())
            }
        }
    }

    /** Envoie un fichier tel quel, plafonné selon le médium ([transferLimit]). */
    fun sendFile(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            if (isVideo(uri)) return@launch sendVideo(uri)
            // le plafond se lit AVANT la copie : inutile de recopier deux cents
            // mégaoctets pour découvrir ensuite qu'ils ne passeront pas
            val limit = withContext(dispatcher) { transferLimit() }
            val read = Attachments.stage(appContext, uri, limit)
            withContext(dispatcher) {
                dispatchAttachment(ChatKind.FILE, ChatFrames.KIND_FILE, read, limit)
            }
        }
    }

    /** Le type déclaré par le fournisseur, connu avant toute copie. */
    private fun isVideo(uri: Uri): Boolean =
        appContext.contentResolver.getType(uri)?.startsWith("video/") == true

    /**
     * Envoie une vidéo, ou refuse franchement.
     *
     * Elle part telle qu'elle a été filmée — aucun ré-encodage nulle part dans
     * cette application — donc seul le Wi-Fi la porte. Le refus se décide
     * **avant** la copie : recopier deux cents mégaoctets pour dire non
     * ensuite serait une minute perdue pour rien.
     */
    private suspend fun sendVideo(uri: Uri) {
        val verdict = withContext(dispatcher) {
            Triple(videoAllowed(), slowestMedium(), transferLimit())
        }
        val (allowed, medium, limit) = verdict
        if (!allowed) {
            val name = Attachments.displayName(appContext, uri) ?: ""
            Log.w(TAG, "vidéo refusée : le médium est ${medium.short}, pas du Wi-Fi")
            withContext(dispatcher) { _refusal.value = Refusal.VideoNeedsWifi(name, medium) }
            return
        }
        val read = Attachments.stage(appContext, uri, limit)
        withContext(dispatcher) {
            dispatchAttachment(ChatKind.FILE, ChatFrames.KIND_FILE, read, limit)
        }
    }

    private fun dispatchAttachment(
        kind: ChatKind,
        wireKind: Int,
        read: Attachments.Read,
        limit: Int,
    ) {
        when (read) {
            is Attachments.Read.TooBig -> {
                // un dialogue, pas une ligne d'état : la personne vient de
                // choisir ce fichier dans un sélecteur système, elle attend
                // qu'il parte
                _refusal.value = Refusal.TooBig(
                    read.name, read.bytes, limit, slowestMedium(),
                )
                Log.w(TAG, "pièce refusée : ${read.name} (${read.bytes} o > $limit)")
            }
            is Attachments.Read.Unreadable -> _status.update {
                it.copy(lastError = CabinError.UnreadableAttachment)
            }
            is Attachments.Read.Ok -> {
                if (read.size > limit) {
                    // le plafond a pu descendre pendant la copie : un lien BLE
                    // qui s'ouvre ramène les dix mégaoctets promis à deux
                    read.file.delete()
                    _refusal.value = Refusal.TooBig(
                        read.name, read.size.toLong(), limit, slowestMedium(),
                    )
                    return
                }
                val msgId = Random.nextInt()
                addMessage(
                    ChatMessage(
                        id = msgId, mine = true, from = "moi",
                        kind = kind, status = ChatStatus.SENDING,
                        // la copie posée par la préparation EST la pièce jointe :
                        // elle sert à la fois de source d'envoi et de trace à
                        // l'écran, là où on en écrivait deux
                        file = read.file, name = read.name, mime = read.mime,
                        sizeBytes = read.size,
                    ),
                )
                _chimes.tryEmit(Chime.SENT)
                dispatch(msgId, wireKind, read.name, read.mime, FileSource(read.file, read.size))
            }
        }
    }

    /** Fil protocole. Une émission par personne, au meilleur médium accepté. */
    private fun dispatch(msgId: Int, kind: Int, name: String, mime: String, source: Source) {
        val perAddress = routes()
        if (perAddress.isEmpty()) {
            updateMessage(msgId) { it.copy(status = ChatStatus.FAILED) }
            _status.update { it.copy(lastError = CabinError.NoLink) }
            return
        }
        // Texte : tout le monde (léger). Image/fichier : la contrainte est
        // celle de la RADIO, pas du protocole — les rafales parallèles sur les
        // connexions BLE croisées tuent l'antenne en ~20 s (banc 2026-08-11 :
        // le seul transfert qui a tenu 134 s roulait sur un lien unique). Un
        // seul lien BLE, donc, mais autant de liens Wi-Fi qu'il y a de pairs :
        // là, chaque transfert a sa socket et rien ne se dispute une antenne.
        //
        // Préférence en BLE : lien CLIENT — ses écritures sont acquittées, donc
        // il est plus probablement vivant. Préférer le lien serveur a été
        // essayé au banc le 2026-08-11 pour gagner en débit (la notification
        // n'attend aucun retour du pair) et rejeté : sur 1093 fragments,
        // 550 Ko ont disparu en silence — aucune trame reçue en face — pendant
        // que l'émetteur rapportait un succès. La file de notifications n'a ni
        // contrôle de flux ni signal d'échec. Le gain n'était d'ailleurs pas au
        // rendez-vous : 16 Ko/s de bout en bout, dans la fourchette de
        // l'écriture. [routes] applique déjà cette préférence.
        val targets = if (kind == ChatFrames.KIND_TEXT) perAddress else attachmentTargets(perAddress)
        // Une seule lecture pour le CRC, ici, et non une par lien : sur un
        // fichier, le calculer dans chaque transfert relirait tout le contenu
        // autant de fois qu'il y a de pairs.
        val crc = crcOf(source) ?: run {
            updateMessage(msgId) { it.copy(status = ChatStatus.FAILED) }
            _status.update { it.copy(lastError = CabinError.UnreadableAttachment) }
            return
        }
        var primary = true
        targets.forEach { link ->
            link.transfers.trySend(Outgoing(msgId, kind, name, mime, source, crc, primary))
            primary = false
        }
        // le médium retenu ET l'inventaire des liens : sans les deux, un message
        // parti par la radio alors qu'une socket était ouverte ne s'explique pas
        Log.i(
            TAG,
            "message $msgId (${source.size} o) par " +
                targets.joinToString { "${it.medium.short}/${it.address}" } +
                " — liens " +
                links.values.joinToString {
                    "${it.medium.tag}${if (it.ready) '+' else '-'}${it.address}"
                },
        )
    }

    /**
     * CRC du contenu à envoyer, lu une seule fois quel que soit le nombre de
     * pairs. null quand la source est illisible — un fichier disparu sous nos
     * pieds, par exemple : mieux vaut l'échec ici qu'un CRC faux en face.
     */
    private fun crcOf(source: Source): Int? = runCatching {
        val crc = CRC32()
        val buffer = ByteArray(CRC_BUFFER)
        source.open().use { reader ->
            while (true) {
                val n = reader.read(buffer)
                if (n <= 0) break
                crc.update(buffer, 0, n)
            }
        }
        crc.value.toInt()
    }.getOrNull()

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
        // On coupe annonce et scan pour TOUT transfert, y compris Wi-Fi.
        // Raisonner « le Wi-Fi ne dispute pas l'antenne BLE » était faux : les
        // deux partagent la même puce, et la coexistence se paie cher. Mesuré au
        // banc le 2026-08-11 sur le même fichier de 1,4 Mo par la station, scan
        // et annonce laissés tourner : 12,5 s puis 38 s d'un essai à l'autre
        // (112 puis 32 Ko/s) — la variance venant du va-et-vient de connexions
        // BLE que le scan relance sans cesse.
        activeOutgoing++
        pauseRadioForTransfer()
        // certains empilements relâchent la priorité au fil du temps :
        // on la redemande au début de chaque transfert
        if (link.medium == Medium.BLE) requestHighPriority(link)
        if (out.primary) sentAtMs[out.msgId] = SystemClock.elapsedRealtime() to out.size
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
        if (!link.sealCapable) {
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
        val frame = ChatFrames.encodeHandshake(step, message, link.capacity)
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
    private fun onHandshakeFrame(medium: Medium, kind: LinkKind, from: String, frame: ChatFrame.Handshake) {
        val link = links[key(medium, kind, from)] ?: return
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
        // La seule occasion d'associer une adresse BLE à quelqu'un : elle ne
        // se redonne qu'au prix d'une connexion.
        if (link.medium == Medium.BLE) bleIdentities.learn(link.address, Hex.encode(attested))
        // l'attestation arrive APRÈS que le lien soit prêt : sans ça, la
        // présence n'apparaîtrait jamais dans la cabine
        refreshLinks()
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
            // Ici, et pas plus tôt. Annoncer depuis checkVouch semblait naturel
            // — le pair vient d'être reconnu — mais la trame se glissait dans
            // la file de contrôle AVANT le 3e message du handshake : l'initiateur
            // devenant établi en *écrivant* ce 3e message, l'adresse partait
            // scellée vers un répondeur qui ne l'était pas encore. Le lien
            // mourait sur « trame scellée sans session établie » (banc du
            // 2026-08-11, les deux liens serveur tués à chaque fois). C'est le
            // miroir exact du piège déjà connu sur HELLO 3, à ceci près que la
            // parade `isHandshake` ne protège que le handshake lui-même. Depuis
            // onHandshakeDone, la file contient déjà HELLO 3 : l'ordre tient.
            announceAddress(link)
            followMedium(link)
            refreshLinks()
        }
    }

    /**
     * Fil protocole. La montée se **propose à qui l'engage** et se **suit pour
     * qui la reçoit**.
     *
     * Un pair qui a composé vers nous sur ce médium et s'y est attesté nous y
     * parle déjà : continuer d'émettre par la radio ne protégerait rien —
     * l'adresse, c'est nous qui la lui avons annoncée — et laisserait la moitié
     * de la conversation à la vitesse du BLE. C'est ce que Florent a vu au banc
     * le 2026-08-11 : un côté passé en Wi-Fi, l'autre resté en direct, jusqu'à
     * l'accusé de réception qui repartait par la radio.
     *
     * Le principe tient : personne ne bascule dans le dos de personne, puisque
     * l'annonce d'adresse est mutuelle et qu'il faut un accord humain **d'un**
     * côté pour que le premier lien s'ouvre.
     */
    private fun followMedium(link: Link) {
        if (link.medium == Medium.BLE || link.kind != LinkKind.SERVER) return
        if (link.peerNostrKey == null) return
        if (enabledMedia.add(link.medium)) {
            Log.i(TAG, "médium suivi : ${link.medium.short} — le pair l'a engagé")
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

    /**
     * Fil protocole. Fait taire la radio BLE le temps d'un transfert : scan,
     * annonce, **et l'intervalle des liens déjà établis**.
     *
     * Ce dernier point est celui qui compte, et il a coûté une soirée de banc.
     * Le 2026-08-11, même fichier, même chemin, en court-circuitant l'app
     * (blast TCP depuis le PC vers la socket d'écoute) : **100 Ko/s Bluetooth
     * allumé, 2 263 Ko/s Bluetooth coupé — 23×**. L'AP est sur le canal 1 en
     * 2,4 GHz, avec un PHY négocié à 72–144 Mbit/s, un signal à −26 dBm et zéro
     * retry : la radio va bien, c'est l'arbitre de coexistence qui donne le
     * temps d'antenne au Bluetooth. Or nos liens client tournent à
     * CONNECTION_PRIORITY_HIGH, soit un événement toutes les 15 ms sur la bande
     * même du Wi-Fi. Couper scan et annonce ne suffisait donc pas — ce sont les
     * liens **établis** qui mangent l'antenne, et c'est pourquoi les trois
     * premières mesures n'ont rien donné.
     *
     * On ne ferme pas ces liens : le BLE reste la porte, et un pair perdu se
     * rescanne. On les endort.
     */
    private fun pauseRadioForTransfer() {
        if (radioPaused) return
        radioPaused = true
        runCatching { scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) } }
        runCatching { advertiseCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) } }
        scanCallback = null
        advertiseCallback = null
        setBleInterval(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
        // La balise vit dans son propre service et tourne en permanence : elle
        // n'entend rien de la cabine sans qu'on le lui demande, et son annonce
        // continue coûterait à ce transfert le même prix que nos propres liens.
        RadioSilence.request(true)
        Log.i(TAG, "radio en pause : transfert en cours")
    }

    /** Fil protocole. Relance scan + annonce quand plus rien ne transite. */
    private fun maybeResumeRadio() {
        if (!radioPaused) return
        if (activeOutgoing > 0 || reassembler.activeStreams() > 0) return
        radioPaused = false
        setBleInterval(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
        RadioSilence.request(false)
        startAdvertising()
        startScan()
        Log.i(TAG, "radio relancée : transferts terminés")
    }

    /**
     * Change l'intervalle de tous les liens BLE sortants. Seul le rôle client
     * peut peser sur les paramètres du lien — côté serveur, Android n'offre
     * rien. Un transfert BLE redemandera aussitôt HIGH pour SON lien.
     */
    private fun setBleInterval(priority: Int) {
        links.values.forEach { link ->
            if (link.medium == Medium.BLE) {
                runCatching { link.gatt?.requestConnectionPriority(priority) }
            }
        }
    }

    private suspend fun runTransferInner(link: Link, out: Outgoing) {
        // budget d'une trame ordinaire, scellement Noise déjà déduit
        val att = link.payload
        val start = ChatFrames.encodeStart(
            ChatFrame.Start(out.msgId, out.kind, out.size, out.crc32, out.name, out.mime),
            att,
        )
        if (start == null || !writeFrame(link, start, withResponse = link.kind == LinkKind.CLIENT)) {
            onTransferFailed(link, out)
            if (start != null) failLink(link)
            return
        }
        val chunk = (att - ChatFrames.DATA_HEADER).coerceAtLeast(1)
        val startedAt = SystemClock.elapsedRealtime()
        var priorityRetried = false
        var offset = 0
        var index = 0
        // Où passe le temps d'un fragment : dans l'écriture (scellement +
        // remise au transport, donc contre-pression du pair) ou dans le reste
        // du tour de boucle (ordonnancement du fil protocole, progression).
        // Sans cette découpe, un transport lent est indiscernable d'un moteur
        // lent — c'est exactement la question ouverte du débit par la station.
        var loopMs = 0L
        link.sealMs = 0L
        link.wireMs = 0L
        val total = out.size
        // Le contenu se lit au fil de l'envoi, jamais d'un bloc : c'est ce qui
        // rend la taille d'une vidéo indifférente à la mémoire du téléphone.
        // Chaque lien ouvre son propre lecteur — un même message part vers
        // plusieurs pairs, chacun à son rythme.
        out.source.open().use { reader ->
            val buffer = ByteArray(chunk)
            while (offset < total) {
                // les acquittements se glissent entre deux fragments
                while (true) {
                    val control = link.control.tryReceive().getOrNull() ?: break
                    writeFrame(link, control)
                }
                val fragmentStart = SystemClock.elapsedRealtime()
                val read = runCatching { reader.read(buffer) }.getOrDefault(-1)
                if (read <= 0) {
                    // le contenu s'est dérobé en route : le pair attend des
                    // octets qui n'existent plus, on ne le laisse pas au CRC
                    Log.w(TAG, "message ${out.msgId} : source tarie à $offset/$total o")
                    onTransferFailed(link, out)
                    return
                }
                val end = offset + read
                // La contre-pression est une affaire de radio : TCP a la sienne,
                // et une écriture sur socket ne rend la main que quand l'octet est
                // parti. Rien à cadencer hors du BLE.
                val windowEdge = end == total || index % ACK_WINDOW == ACK_WINDOW - 1
                val reliable = link.medium != Medium.BLE || (windowEdge && link.kind == LinkKind.CLIENT)
                val encoded = ChatFrames.encodeData(out.msgId, index, buffer, 0, read)
                val encodedAt = SystemClock.elapsedRealtime()
                if (!writeFrame(link, encoded, reliable)) {
                    onTransferFailed(link, out)
                    failLink(link)
                    return
                }
                loopMs += encodedAt - fragmentStart
                // pas d'équivalent « avec réponse » pour une notification :
                // on relâche la pression d'un souffle à chaque fenêtre
                if (windowEdge && link.medium == Medium.BLE && link.kind == LinkKind.SERVER) {
                    delay(NOTIFY_WINDOW_PAUSE_MS)
                }
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
                if (out.primary) publishProgress(out.msgId, offset, total)
            }
        }
        if (out.primary) {
            progressPct.remove(out.msgId)
            updateMessage(out.msgId) {
                // un ACK arrivé pendant les derniers fragments a déjà posé ✓✓
                if (it.status == ChatStatus.SENDING) it.copy(status = ChatStatus.SENT, progress = 1f)
                else it.copy(progress = 1f)
            }
            // ✓ vaut promesse de remise sur le chemin écriture seulement :
            // côté notification, il faut l'ACK pour savoir, ou l'échec. Le
            // guetteur ne concerne que ce chemin-là — une socket qui a écrit
            // sans lever a bel et bien remis ses octets.
            if (link.medium == Medium.BLE && link.kind == LinkKind.SERVER) {
                armAckWatchdog(out.msgId, link.address, out.size)
            }
        }
        // ms/fragment journalisés : sans eux, un lien resté à 48 ms ressemble à
        // un lien à 15 ms, en trois fois plus lent. Le chemin notification, lui,
        // ne fait qu'empiler : son vrai débit se lit à l'accusé, pas ici.
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1)
        // « remis à la pile » ne vaut que pour une notification GATT, qui part
        // sans retour : une écriture de socket qui rend la main a bel et bien
        // remis ses octets, quel que soit le rôle du lien.
        val verb = if (link.medium != Medium.BLE || link.kind == LinkKind.CLIENT) {
            "émis"
        } else {
            "remis à la pile"
        }
        Log.i(
            TAG,
            "message ${out.msgId} $verb (${out.size} o, $index fragment(s)) " +
                "vers ${link.address} — $elapsed ms, ${elapsed / maxOf(index, 1)} ms/fragment " +
                "(scellement ${link.sealMs} ms, transport ${link.wireMs} ms, boucle $loopMs ms)",
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
                _status.update { it.copy(lastError = CabinError.NoAck(address.takeLast(5))) }
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
            _status.update { it.copy(lastError = CabinError.SendFailed(link.address.takeLast(5))) }
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
        removeLink(link.medium, link.kind, link.address)
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
        val sealStart = SystemClock.elapsedRealtime()
        val frame = seal(link, plain) ?: return false
        val sealed = SystemClock.elapsedRealtime()
        link.sealMs += sealed - sealStart
        if (link.medium != Medium.BLE) {
            return streamWrite(link, frame)
                .also { link.wireMs += SystemClock.elapsedRealtime() - sealed }
        }
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

    /**
     * Écriture sur un médium en flux. Bloquante, donc dépaysée sur [Dispatchers.IO] :
     * le fil protocole est unique, et l'y retenir le temps d'un `flush` figerait
     * tous les autres liens, radio comprise.
     */
    private suspend fun streamWrite(link: Link, frame: ByteArray): Boolean {
        val stream = link.stream ?: return false
        return withContext(Dispatchers.IO) {
            runCatching { stream.write(frame) }.isSuccess
        }
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
    private fun handleFrame(medium: Medium, kind: LinkKind, from: String, bytes: ByteArray) {
        when (val frame = ChatFrames.decode(bytes)) {
            null -> Log.w(TAG, "trame illisible de $from (${bytes.size} o)")
            is ChatFrame.Handshake -> onHandshakeFrame(medium, kind, from, frame)
            is ChatFrame.Sealed -> {
                val link = links[key(medium, kind, from)]
                if (link == null) Log.w(TAG, "trame scellée d'un lien inconnu : $from")
                else unseal(link, frame)?.let { handlePlainFrame(medium, kind, from, it) }
            }
            else -> handlePlainFrame(medium, kind, from, bytes)
        }
    }

    /** Fil protocole. Une trame en clair, scellée à l'origine ou non. */
    private fun handlePlainFrame(medium: Medium, kind: LinkKind, from: String, bytes: ByteArray) {
        when (val frame = ChatFrames.decode(bytes)) {
            null -> Log.w(TAG, "trame illisible de $from (${bytes.size} o)")
            // une trame ouverte ne peut pas en contenir une autre
            is ChatFrame.Sealed, is ChatFrame.Handshake ->
                Log.w(TAG, "trame ${frame::class.simpleName} imbriquée de $from : ignorée")
            is ChatFrame.Address -> onAddressFrame(links[key(medium, kind, from)], frame)
            is ChatFrame.Group -> onGroupFrame(links[key(medium, kind, from)], frame)
            is ChatFrame.Ack -> onAck(frame)
            else -> when (val event = reassembler.onFrame(from, frame)) {
                is Reassembler.Event.Started -> onIncomingStarted(medium, event)
                is Reassembler.Event.Progress ->
                    publishProgress(event.msgId, event.receivedBytes, event.totalBytes)
                is Reassembler.Event.Completed -> onIncomingCompleted(event)
                is Reassembler.Event.Failed -> onIncomingFailed(event)
                null -> Unit
            }
        }
    }

    private fun onIncomingStarted(medium: Medium, event: Reassembler.Event.Started) {
        val start = event.start
        Log.i(TAG, "réception de ${start.totalBytes} o (genre ${start.kind}) depuis ${event.from}")
        // même règle qu'à l'émission : la coexistence Bluetooth/Wi-Fi se paie
        // sur la même puce, quel que soit le médium qui porte le transfert
        pauseRadioForTransfer()
        val attested = attestedShort(event.from)
        addMessage(
            ChatMessage(
                id = start.msgId, mine = false,
                from = attested ?: event.from.takeLast(5),
                fromAttested = attested != null,
                kind = kindOf(start.kind), status = ChatStatus.RECEIVING,
                name = start.name, mime = start.mime, sizeBytes = start.totalBytes,
            ),
        )
    }

    /**
     * Le npub court de qui parle depuis [address], ou null tant que personne
     * n'a signé d'attestation sur cette adresse.
     *
     * On cherche par adresse et non par lien : le double lien croisé d'un même
     * pair porte la même adresse, et il suffit qu'un des deux ait vérifié
     * l'attestation pour qu'on sache à qui on parle. Sans ça, le panneau
     * nommait l'expéditeur par un suffixe d'adresse — un port qui change à
     * chaque session — alors que le npub était déjà connu, affiché juste
     * au-dessus dans la liste de ceux qui sont là.
     */
    private fun attestedShort(address: String): String? = links.values
        .firstOrNull { it.address == address && it.peerNostrKey != null }
        ?.peerNostrKey
        ?.let { Peer(it, Bech32.encode("npub", it)).short }

    private fun onIncomingCompleted(event: Reassembler.Event.Completed) {
        val start = event.start
        progressPct.remove(start.msgId)
        when (val payload = event.payload) {
            // le texte est arrivé en mémoire, c'est ce qu'on lui a demandé
            is Payload.InMemory -> updateMessage(start.msgId) {
                it.copy(
                    status = ChatStatus.RECEIVED, progress = 1f,
                    text = String(payload.bytes, Charsets.UTF_8),
                )
            }
            // la pièce jointe est déjà sur le disque, écrite au fil des
            // fragments : plus rien à recopier une fois complète
            is Payload.OnDisk -> updateMessage(start.msgId) {
                it.copy(status = ChatStatus.RECEIVED, progress = 1f, file = payload.file)
            }
        }
        val by = links.values.firstOrNull { it.address == event.from }
        Log.i(
            TAG,
            "message ${start.msgId} reçu au complet (${start.totalBytes} o)" +
                (by?.let { " — attente ${it.readMs} ms, traitement ${it.handleMs} ms" } ?: ""),
        )
        by?.let { it.readMs = 0L; it.handleMs = 0L }
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

    /** Diffuse une trame de contrôle sur un lien par personne. */
    private fun broadcastControl(frame: ByteArray) {
        routes().forEach { it.control.trySend(frame) }
    }

    // ── Médiums Wi-Fi : une socket d'écoute, des adresses annoncées ───────

    private var listener: ServerSocket? = null

    /**
     * Verrou de latence Wi-Fi, tenu tant que la cabine est ouverte.
     *
     * Sans lui, Android laisse la puce Wi-Fi en économie d'énergie : elle ne se
     * réveille qu'aux balises (~100 ms) et chaque aller-retour paie ce réveil.
     * Symptôme au banc le 2026-08-11 — un handshake Noise de trois messages de
     * cent octets prenait **5 à 10 secondes**, et chaque trame de 32 Ko
     * attendait des centaines de millisecondes alors que le récepteur ne
     * travaillait que 8 ms. Ce n'était pas du débit qui manquait, c'était du
     * réveil.
     */
    private var wifiLock: WifiManager.WifiLock? = null

    /** Port de notre socket d'écoute, 0 tant qu'elle n'est pas ouverte. */
    private var listenPort = 0

    /**
     * Ouvre l'écoute TCP sur un port éphémère, toutes interfaces.
     *
     * Le port n'a pas à être fixe ni connu : il voyage dans la trame `ADDR`,
     * scellée, vers un pair déjà attesté. Écouter ne révèle donc rien — sans
     * l'annonce, une socket ouverte sur un LAN n'est qu'un port muet parmi
     * d'autres, et le handshake Noise refuse tout inconnu.
     */
    private fun startListener() {
        if (listener != null) return
        acquireWifiLock()
        runCatching { ServerSocket(0) }
            .onFailure { Log.w(TAG, "écoute TCP impossible — $it") }
            .onSuccess { socket ->
                listener = socket
                listenPort = socket.localPort
                Log.i(TAG, "écoute des médiums Wi-Fi sur le port $listenPort")
                scope.launch { acceptLoop(socket) }
            }
    }

    /** Le mode basse latence existe depuis l'API 29 ; avant, la haute perf. */
    private fun acquireWifiLock() {
        if (wifiLock != null) return
        val manager = appContext.getSystemService(WifiManager::class.java) ?: return
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = runCatching {
            manager.createWifiLock(mode, "atom4love:cabine").apply { acquire() }
        }.onFailure { Log.w(TAG, "verrou Wi-Fi refusé — $it") }.getOrNull()
        if (wifiLock != null) Log.i(TAG, "verrou Wi-Fi basse latence tenu")
    }

    private fun releaseWifiLock() {
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        while (true) {
            val accepted = withContext(Dispatchers.IO) {
                runCatching { server.accept() }.getOrNull()
            } ?: return // socket fermée : l'écoute s'arrête avec le moteur
            // Celui qui compose initie le handshake, comme le central en BLE :
            // le lien serveur répond. Reprendre exactement la convention du
            // BLE fait que toute la machine à états Noise resserve telle quelle.
            adoptStream(inboundMedium(accepted), LinkKind.SERVER, accepted)
        }
    }

    /**
     * Par quel médium une socket nous est arrivée. L'écoute est unique et ne
     * distingue rien : c'est l'adresse **locale** de la socket acceptée qui le
     * dit — le groupe P2P vit en 192.168.49.0/24, son propriétaire en .1.
     *
     * Sans ça, un lien P2P entrant se ferait passer pour un lien d'infra :
     * l'indicateur annoncerait « par le réseau du lieu » à quelqu'un qui parle
     * en pair à pair, et [followMedium] accepterait le mauvais médium.
     */
    private fun inboundMedium(socket: Socket): Medium =
        if (socket.localAddress?.hostAddress?.startsWith(P2P_SUBNET) == true) {
            Medium.WIFI_DIRECT
        } else {
            Medium.WIFI_STATION
        }

    /** Compose vers un point d'entrée annoncé par un pair attesté. */
    private suspend fun dial(medium: Medium, host: String, port: Int) {
        if (links.values.any { it.medium == medium && it.address.startsWith("$host:") }) return
        val socket = withContext(Dispatchers.IO) {
            runCatching {
                // Une socket non liée part par le réseau PAR DÉFAUT — la
                // station — et n'atteindrait jamais le 192.168.49.1 d'un
                // propriétaire de groupe. Quand Android publie le réseau P2P,
                // on s'y attache ; sinon la route ordinaire suffit souvent, et
                // l'échec sera propre.
                val bound = if (medium == Medium.WIFI_DIRECT) p2p.network() else null
                val socket = bound?.socketFactory?.createSocket() ?: Socket()
                socket.apply { connect(InetSocketAddress(host, port), DIAL_TIMEOUT_MS) }
            }.getOrNull()
        }
        if (socket == null) {
            Log.w(TAG, "${medium.short} : $host:$port injoignable")
            // Une adresse annoncée n'est pas une adresse joignable : deux
            // noyaux sur deux réseaux différents s'annoncent chacun la sienne
            // en toute bonne foi. C'est l'échec qui fait descendre l'échelle
            // d'un cran vers le Direct — pas l'absence d'annonce.
            unreachable.add(medium)
            _status.update { it.copy(lastError = CabinError.MediumUnreachable(medium)) }
            refreshLinks()
            return
        }
        unreachable.remove(medium)
        adoptStream(medium, LinkKind.CLIENT, socket)
    }

    /** Fil protocole. Un lien neuf sur une socket, dans les deux sens. */
    private suspend fun adoptStream(medium: Medium, kind: LinkKind, socket: Socket) {
        val stream = runCatching { FramedSocket(socket) }.getOrElse {
            runCatching { socket.close() }
            return
        }
        val link = Link(medium, kind, stream.remote).apply { this.stream = stream }
        val k = key(medium, kind, stream.remote)
        links[k]?.let { removeLink(medium, kind, it.address) }
        links[k] = link
        startLinkJob(link)
        scope.launch { readLoop(link, stream) }
        Log.i(TAG, "lien ${medium.short} $kind ${stream.remote}")
        if (kind == LinkKind.CLIENT) beginHandshake(link)
        refreshLinks()
    }

    /**
     * Lecture bloquante d'une socket, dépaysée hors du fil protocole. Les
     * trames arrivent entières : le préfixe de longueur a déjà fait le travail
     * que l'ATT fait en BLE.
     */
    private suspend fun readLoop(link: Link, stream: FramedSocket) {
        while (true) {
            val waiting = SystemClock.elapsedRealtime()
            val frame = withContext(Dispatchers.IO) {
                runCatching { stream.read() }.getOrElse { error ->
                    Log.w(TAG, "lecture ${link.address} interrompue — $error")
                    null
                }
            } ?: break
            val arrived = SystemClock.elapsedRealtime()
            handleFrame(link.medium, link.kind, link.address, frame)
            link.readMs += arrived - waiting
            link.handleMs += SystemClock.elapsedRealtime() - arrived
        }
        Log.i(TAG, "lien ${link.medium.short} ${link.address} fermé")
        removeLink(link.medium, link.kind, link.address)
    }

    /**
     * Fil protocole. Notre adresse Wi-Fi courante, ou null si l'appareil n'est
     * pas sur une station. Rien à annoncer dans ce cas — et donc aucune montée
     * à proposer : la cabine reste en BLE sans que personne n'ait à choisir.
     */
    private fun localWifiHost(): String? {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        val caps = manager.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        return manager.getLinkProperties(network)
            ?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    /**
     * Fil protocole. Dit au pair par où nous joindre plus vite.
     *
     * N'est appelé qu'une fois le pair **attesté**, et la trame part par la
     * file de contrôle du lien — donc scellée. C'est toute la découverte
     * réseau du projet : pas de mDNS, pas de balayage, rien qui traîne sur le
     * LAN. Annoncer n'engage à rien : c'est le pair qui décidera de composer,
     * et seulement si son porteur a accepté la montée.
     */
    private fun announceAddress(link: Link) {
        if (link.medium != Medium.BLE || listenPort == 0) return
        // une adresse ne se confie qu'à quelqu'un dont on sait qui il est : un
        // pair sans noyau incarné a mené son handshake, mais n'a rien signé
        if (link.peerNostrKey == null) return
        val host = localWifiHost() ?: return
        link.control.trySend(
            ChatFrames.encodeAddress(Medium.WIFI_STATION.ordinal, host, listenPort),
        )
        Log.i(TAG, "adresse ${Medium.WIFI_STATION.short} annoncée à ${link.address} : $host:$listenPort")
    }

    /**
     * Fil protocole. Un pair nous dit par où le joindre. On ne compose que si
     * le médium est accepté ; sinon l'offre attend, et la cabine la propose.
     */
    private fun onAddressFrame(link: Link?, frame: ChatFrame.Address) {
        val medium = Medium.entries.getOrNull(frame.mediumOrdinal) ?: return
        if (medium == Medium.BLE) return // le BLE ne se compose pas par adresse IP
        val peer = link?.peerNostrKey ?: run {
            // sans attestation, rien ne dit de qui vient cette adresse
            Log.w(TAG, "adresse annoncée par un pair non attesté : ignorée")
            return
        }
        val who = Hex.encode(peer)
        offers.getOrPut(who) { linkedMapOf() }[medium] = frame.host to frame.port
        Log.i(TAG, "${medium.short} proposé par ${who.take(12)}… : ${frame.host}:${frame.port}")
        if (medium in enabledMedia) scope.launch { dial(medium, frame.host, frame.port) }
        refreshLinks()
    }

    /**
     * Fil protocole. Un pair nous invite dans son groupe Wi-Fi Direct.
     *
     * Même règle que pour une adresse : sans attestation, on ne sait pas de qui
     * vient l'invitation, et des identifiants de réseau ne se ramassent pas
     * dans la nature.
     */
    private fun onGroupFrame(link: Link?, frame: ChatFrame.Group) {
        val peer = link?.peerNostrKey ?: run {
            Log.w(TAG, "groupe proposé par un pair non attesté : ignoré")
            return
        }
        val who = Hex.encode(peer)
        groupOffers[who] = P2pGroup.Credentials(frame.networkName, frame.passphrase) to frame.port
        Log.i(TAG, "Wi-Fi Direct proposé par ${who.take(12)}… : ${frame.networkName}")
        // Règle C : le pair a ouvert un groupe pour nous, il a engagé le médium.
        if (Medium.WIFI_DIRECT in enabledMedia) scope.launch { joinGroup(who) }
        refreshLinks()
    }

    /** Fil protocole. Entre dans le groupe d'un pair, puis compose vers lui. */
    private suspend fun joinGroup(who: String) {
        val (credentials, port) = groupOffers[who] ?: return
        if (!p2p.join(credentials)) {
            _status.update { it.copy(lastError = CabinError.P2pUnreachable) }
            return
        }
        inGroup = true
        dial(Medium.WIFI_DIRECT, P2pGroup.OWNER_ADDRESS, port)
    }

    /**
     * Fil protocole. Ouvre un groupe et invite les pairs attestés à le
     * rejoindre. C'est l'équivalent Direct de [announceAddress] : on ne le fait
     * qu'après attestation, et l'invitation part scellée.
     */
    private suspend fun hostGroup() {
        if (listenPort == 0) return
        val credentials = p2p.host() ?: run {
            _status.update { it.copy(lastError = CabinError.P2pImpossible) }
            return
        }
        inGroup = true
        val frame = ChatFrames.encodeGroup(credentials.networkName, credentials.passphrase, listenPort)
        links.values.forEach { link ->
            if (link.medium == Medium.BLE && link.ready && link.peerNostrKey != null) {
                link.control.trySend(frame)
                Log.i(TAG, "groupe ${credentials.networkName} proposé à ${link.address}")
            }
        }
    }

    /**
     * Accepte un médium plus rapide. C'est le seul chemin par lequel la cabine
     * change de voie de son propre chef — elle s'établit en BLE, informe, et
     * attend. (Le pair qui a déjà engagé le médium, lui, est suivi : voir
     * [followMedium].)
     */
    fun enable(medium: Medium) {
        scope.launch {
            if (!enabledMedia.add(medium)) return@launch
            Log.i(TAG, "médium accepté : ${medium.short}")
            if (medium == Medium.WIFI_DIRECT) {
                // Déjà invité : on entre chez le pair plutôt que d'ouvrir un
                // second groupe, qui laisserait chacun maître du sien et
                // personne chez l'autre.
                val invitation = groupOffers.keys.firstOrNull()
                if (invitation != null) joinGroup(invitation) else hostGroup()
            }
            offers.values.forEach { byMedium ->
                val entry = byMedium[medium] ?: return@forEach
                scope.launch { dial(medium, entry.first, entry.second) }
            }
            refreshLinks()
        }
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
                scope.launch { removeLink(Medium.BLE, LinkKind.SERVER, device.address) }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            scope.launch {
                serverMtus[device.address] = mtu
                links[key(Medium.BLE, LinkKind.SERVER, device.address)]?.mtu = mtu
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
            scope.launch { handleFrame(Medium.BLE, LinkKind.SERVER, device.address, bytes) }
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
                    if (enable) addServerLink(device) else removeLink(Medium.BLE, LinkKind.SERVER, device.address)
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
                links[key(Medium.BLE, LinkKind.SERVER, device.address)]
                    ?.pending?.removeFirstOrNull()
                    ?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    /** Fil protocole. */
    private fun addServerLink(device: BluetoothDevice) {
        val k = key(Medium.BLE, LinkKind.SERVER, device.address)
        if (links.containsKey(k)) return
        val link = Link(Medium.BLE, LinkKind.SERVER, device.address).apply {
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
    private fun removeLink(medium: Medium, kind: LinkKind, address: String) {
        // avant l'early-return : un central peut négocier le MTU sans jamais s'abonner
        if (medium == Medium.BLE && kind == LinkKind.SERVER) {
            serverMtus.remove(address)
            synchronized(subscribedAddresses) { subscribedAddresses.remove(address) }
        }
        val link = links.remove(key(medium, kind, address)) ?: return
        link.stream?.close()
        if (medium == Medium.BLE && kind == LinkKind.CLIENT) {
            val now = SystemClock.elapsedRealtime()
            // Un lien qui avait marché revient vite ; une adresse qui n'a
            // jamais répondu s'éloigne davantage à chaque refus, au lieu
            // d'être rappelée toutes les 30 s jusqu'à ce qu'elle disparaisse.
            if (link.ready) pacing.lost(address, now) else pacing.failed(address, now)
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
            _status.update { it.copy(lastError = CabinError.AdvertiseUnavailable) }
            return
        }
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _status.update { it.copy(advertising = true, lastError = null) }
                Log.d(TAG, "annonce connectable démarrée")
            }

            override fun onStartFailure(errorCode: Int) {
                _status.update { it.copy(lastError = CabinError.AdvertiseRefused(errorCode)) }
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
                _status.update { it.copy(scanning = false, lastError = CabinError.ScanRefused(errorCode)) }
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
        val k = key(Medium.BLE, LinkKind.CLIENT, address)
        if (links.containsKey(k)) return
        // la radio reste au transfert : aucune connexion sortante pendant
        // qu'on émet ou réassemble
        if (activeOutgoing > 0 || reassembler.activeStreams() > 0) return
        // Déjà joignable par un médium plus rapide ? Alors cette adresse n'a
        // rien à nous apprendre et sa connexion coûterait de l'antenne pour
        // retrouver quelqu'un à qui l'on parle déjà. Le lien BLE tombe, le
        // scan le revoit, on recommençait : c'est ce va-et-vient qui s'arrête.
        if (!bleIdentities.shouldDial(address, reachedBeyondBle())) {
            if (dialSkipped.add(address)) {
                Log.i(TAG, "$address laissé de côté : ce pair est déjà là par un médium plus rapide")
                while (dialSkipped.size > BACKOFF_MAX_ENTRIES) {
                    dialSkipped.remove(dialSkipped.first())
                }
            }
            return
        }
        dialSkipped.remove(address)
        val now = SystemClock.elapsedRealtime()
        // Composer sert à rencontrer quelqu'un de nouveau. Quand la cabine
        // tient déjà un pair attesté — fût-ce par le lien qu'il a composé vers
        // nous —, la rencontre peut attendre : le banc du 2026-08-12 a compté
        // 8 compositions mortes en status 133 pendant que l'entrant et le
        // Wi-Fi portaient toute la conversation.
        if (!pacing.allow(address, now, engaged = attestedPeerPresent())) return
        pacing.dialed(now)
        Log.i(TAG, "pair de causerie vu : $address rssi=${result.rssi}, connexion…")
        val link = Link(Medium.BLE, LinkKind.CLIENT, address)
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
            pacing.failed(address, now)
            Log.w(TAG, "connectGatt null pour $address")
            return
        }
        link.gatt = gatt
    }

    /** Fil protocole. Quelqu'un d'attesté est-il joignable, par n'importe quel lien ? */
    private fun attestedPeerPresent(): Boolean = links.values.any {
        it.ready && it.peerNostrKey != null && it.medium in enabledMedia
    }

    /**
     * Fil protocole. Les pairs attestés que l'on tient par mieux que la radio.
     *
     * Un lien seulement *ouvert* ne compte pas : tant qu'il n'est pas prêt et
     * son médium accepté, il ne porterait rien, et se priver du BLE sur cette
     * foi laisserait la cabine muette.
     */
    private fun reachedBeyondBle(): Set<String> = links.values.asSequence()
        .filter { it.ready && it.medium.rank > Medium.BLE.rank && it.medium in enabledMedia }
        .mapNotNull { link -> link.peerNostrKey?.let { Hex.encode(it) } }
        .toSet()

    /** Lien client irrécupérable : on coupe pour que le scan retente. */
    private fun dropClient(gatt: BluetoothGatt, reason: String) {
        Log.w(TAG, "lien client ${gatt.device.address} abandonné : $reason")
        runCatching { gatt.disconnect() }
        scope.launch { removeLink(Medium.BLE, LinkKind.CLIENT, gatt.device.address) }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "client : connecté à $address, négociation MTU")
                if (!gatt.requestMtu(REQUESTED_MTU)) gatt.discoverServices()
            } else {
                Log.d(TAG, "client : $address perdu (status=$status)")
                scope.launch { removeLink(Medium.BLE, LinkKind.CLIENT, address) }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            scope.launch {
                val link = links[key(Medium.BLE, LinkKind.CLIENT, gatt.device.address)] ?: return@launch
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
                val link = links[key(Medium.BLE, LinkKind.CLIENT, gatt.device.address)] ?: return@launch
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
                links[key(Medium.BLE, LinkKind.CLIENT, gatt.device.address)]
                    ?.pending?.removeFirstOrNull()
                    ?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value?.copyOf() ?: return
            scope.launch { handleFrame(Medium.BLE, LinkKind.CLIENT, gatt.device.address, bytes) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val bytes = value.copyOf()
            scope.launch { handleFrame(Medium.BLE, LinkKind.CLIENT, gatt.device.address, bytes) }
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

    /**
     * Fil protocole. Le Wi-Fi Direct vaut-il d'être proposé ?
     *
     * Il est le dernier recours : on ne fabrique une station que si personne
     * n'en a. Le test honnête est **qu'aucun pair n'ait annoncé d'adresse de
     * station** — c'est la seule chose qu'on sache d'eux. Une invitation déjà
     * reçue le justifie de toute façon : le pair a ouvert son groupe pour nous.
     */
    private fun directOffer(): List<Medium> {
        // La permission ne se teste PAS ici : sans offre affichée, l'utilisateur
        // n'aurait aucun endroit où l'accorder. C'est l'écran qui la demande au
        // moment où l'on accepte.
        if (!p2p.usable()) return emptyList()
        if (links.values.none { it.ready && it.peerNostrKey != null }) return emptyList()
        val stationUsable = offers.values.any { Medium.WIFI_STATION in it } &&
            Medium.WIFI_STATION !in unreachable
        return if (groupOffers.isNotEmpty() || !stationUsable) {
            listOf(Medium.WIFI_DIRECT)
        } else {
            emptyList()
        }
    }

    private fun refreshLinks() {
        val ready = links.values.filter { it.ready }
        val routes = routes()
        // le médium réellement en service : celui du meilleur lien emprunté,
        // pas celui du meilleur lien ouvert
        val inUse = routes.maxByOrNull { it.medium.rank }?.medium
        // Une offre ne compte que si elle ferait mieux que ce qu'on emprunte, et
        // c'est la PLUS BASSE qu'on propose : l'ordre de l'échelle est BLE puis
        // station puis Direct — une station qui porte déjà les deux noyaux ne
        // demande rien à personne, là où un groupe P2P doit être formé exprès.
        val offered = (offers.values.flatMap { it.keys } + directOffer())
            .filter { it !in enabledMedia && (inUse == null || it.rank > inUse.rank) }
            .minByOrNull { it.rank }
        _status.update { status ->
            status.copy(
                links = ready.size,
                unattestedLinks = ready.count { it.peerNostrKey == null },
                medium = inUse,
                offered = offered,
            )
        }
        // dédoublonné par npub : les deux liens croisés d'une même personne ne
        // font qu'une présence, et une adresse radio qui tourne n'en crée pas
        // une nouvelle
        _peers.value = ready
            .mapNotNull { it.peerNostrKey }
            .map { Peer(it, Bech32.encode("npub", it)) }
            .distinctBy { it.npub }
    }
}
