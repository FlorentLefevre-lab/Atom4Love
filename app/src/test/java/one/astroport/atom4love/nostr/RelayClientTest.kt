package one.astroport.atom4love.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import one.astroport.atom4love.domain.BirthData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le client face à un faux relais : accusé OK et flux d'abonnement. */
class RelayClientTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MockWebServer()

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    /** Un relais minimal : OK à chaque EVENT, EOSE à chaque REQ. */
    private fun fakeRelayListener() = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val arr = NostrEvent.json.parseToJsonElement(text).jsonArray
            when (arr[0].jsonPrimitive.content) {
                "EVENT" -> {
                    val event = NostrEvent.json
                        .decodeFromJsonElement(NostrEvent.serializer(), arr[1])
                    webSocket.send("""["OK","${event.id}",true,""]""")
                }
                "REQ" -> webSocket.send("""["EOSE","${arr[1].jsonPrimitive.content}"]""")
            }
        }

        // Sans cette réponse, la poignée de fermeture reste ouverte et
        // MockWebServer.close() attend indéfiniment.
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
    }

    @Test
    fun `publier - reçoit l'accusé OK du relais`() = runBlocking {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(fakeRelayListener()).build())
        server.start()

        val client = RelayClient(server.url("/").toString(), scope)
        client.connect()
        withTimeout(5_000) {
            client.state.first { it is RelayClient.State.Connected }
        }

        val keys = LoveKeyForge.forge(BirthData.Sample)
        val event = NostrEvent.create(keys, kind = 1, content = "test relai")
        val ok = client.publishAndWait(event, timeoutMs = 5_000)

        assertEquals(event.id, ok?.eventId)
        assertTrue(ok?.accepted == true)
        client.close()
    }

    @Test
    fun `souscrire - reçoit EOSE`() = runBlocking {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(fakeRelayListener()).build())
        server.start()

        val client = RelayClient(server.url("/").toString(), scope)
        client.connect()
        withTimeout(5_000) {
            client.state.first { it is RelayClient.State.Connected }
        }

        client.subscribe("cabine", NostrFilter(kinds = listOf(1), limit = 5))
        val eose = withTimeout(5_000) {
            client.inbound.filterIsInstance<RelayMessage.Eose>().first()
        }
        assertEquals("cabine", eose.subscriptionId)
        client.close()
    }
}
