package one.astroport.atom4love.nostr

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Connexion à un relais NOSTR (NIP-01) : une WebSocket, des souscriptions
 * rejouées à chaque reconnexion, un backoff exponentiel en cas de chute.
 *
 * Le client est passif tant que [connect] n'est pas appelé ; [close] est
 * définitif (pas de reconnexion ensuite).
 */
class RelayClient(
    val url: String,
    private val scope: CoroutineScope,
    httpClient: OkHttpClient? = null,
) {

    companion object {
        private const val TAG = "Nostr"
        private const val BACKOFF_INITIAL_MS = 1_000L
        private const val BACKOFF_MAX_MS = 30_000L
    }

    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Connected : State
        data class Retrying(val attempt: Int, val inMs: Long) : State
    }

    private val client = httpClient ?: OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _inbound = MutableSharedFlow<RelayMessage.Inbound>(extraBufferCapacity = 256)
    /** Tous les messages du relais — s'abonner AVANT d'émettre pour ne rien rater. */
    val inbound: SharedFlow<RelayMessage.Inbound> = _inbound.asSharedFlow()

    private val subscriptions = LinkedHashMap<String, NostrFilter>()
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var attempts = 0

    @Volatile
    private var closed = false

    fun connect() {
        if (closed || _state.value is State.Connecting || _state.value is State.Connected) return
        _state.value = State.Connecting
        webSocket = client.newWebSocket(
            Request.Builder().url(url).build(),
            listener,
        )
    }

    /** Publie un événement (best effort — voir [publishAndWait] pour l'accusé). */
    fun publish(event: NostrEvent): Boolean =
        webSocket?.send(RelayMessage.publish(event)) == true

    /** Publie puis attend le ["OK", id, …] du relais. null = pas de réponse à temps. */
    suspend fun publishAndWait(event: NostrEvent, timeoutMs: Long = 10_000): RelayMessage.Ok? {
        val socket = webSocket ?: return null
        return withTimeoutOrNull(timeoutMs) {
            // L'envoi n'a lieu qu'une fois la collecte attachée : lancer l'attente
            // dans une coroutine et émettre aussitôt laisse une fenêtre où l'accusé
            // d'un relais rapide (local, ou en mémoire) arrive avant qu'on écoute —
            // [inbound] ne rejoue rien, l'accusé serait perdu jusqu'au délai.
            inbound.onSubscription { socket.send(RelayMessage.publish(event)) }
                .filterIsInstance<RelayMessage.Ok>()
                .first { it.eventId == event.id }
        }
    }

    /** Souscrit (et re-souscrira automatiquement à chaque reconnexion). */
    fun subscribe(subscriptionId: String, filter: NostrFilter) {
        synchronized(subscriptions) { subscriptions[subscriptionId] = filter }
        webSocket?.send(RelayMessage.subscribe(subscriptionId, filter))
    }

    fun unsubscribe(subscriptionId: String) {
        synchronized(subscriptions) { subscriptions.remove(subscriptionId) }
        webSocket?.send(RelayMessage.unsubscribe(subscriptionId))
    }

    fun close() {
        closed = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
        _state.value = State.Idle
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempts = 0
            _state.value = State.Connected
            Log.d(TAG, "connecté à $url")
            synchronized(subscriptions) { subscriptions.toList() }.forEach { (id, filter) ->
                webSocket.send(RelayMessage.subscribe(id, filter))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _inbound.tryEmit(RelayMessage.parse(text))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // La pile d'appels au PREMIER échec seulement. « Handshake
            // failed » tout court ne dit rien : la vraie raison vit dans la
            // cause (le 16/08, un SSLV3_ALERT_HANDSHAKE_FAILURE envoyé par le
            // serveur, qui n'accepte que la courbe P-384 — introuvable sans
            // elle). Les essais suivants reviennent toutes les 30 s : une
            // trace à chaque fois noierait le journal.
            if (attempts == 0) Log.w(TAG, "connexion perdue ($url) : $t", t)
            else Log.w(TAG, "connexion perdue ($url) : $t")
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!closed) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (closed) return
        webSocket = null
        val delayMs = (BACKOFF_INITIAL_MS shl attempts.coerceAtMost(5)).coerceAtMost(BACKOFF_MAX_MS)
        attempts++
        _state.value = State.Retrying(attempts, delayMs)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!closed) {
                _state.value = State.Idle
                connect()
            }
        }
    }
}
