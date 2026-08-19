package one.astroport.atom4love.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le salon face à un faux relais : une pensée étrangère arrive à la
 * souscription, la nôtre part et revient en écho local.
 */
class HexagonSalonTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MockWebServer()

    private val myKeys = LoveKeyForge.forge(BirthData.Sample)
    private val strangerKeys = NostrKeys(ByteArray(32) { 7 })
    private val cell = "881FB5B861"

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    /**
     * Un relais de cabine minimal : à la souscription du salon il pousse une
     * pensée étrangère puis EOSE ; à chaque EVENT publié il répond OK.
     */
    private fun fakeCabinRelay() = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val arr = NostrEvent.json.parseToJsonElement(text).jsonArray
            when (arr[0].jsonPrimitive.content) {
                "REQ" -> {
                    val subId = arr[1].jsonPrimitive.content
                    if (subId == "a4l-cabine") {
                        val stranger = NostrEvent.create(
                            keys = strangerKeys,
                            kind = HexagonSalon.KIND_PENSEE,
                            content = "bienvenue dans la cabine",
                            tags = listOf(listOf("h", cell)),
                        )
                        webSocket.send(
                            """["EVENT","$subId",${NostrEvent.json.encodeToString(NostrEvent.serializer(), stranger)}]""",
                        )
                    }
                    webSocket.send("""["EOSE","$subId"]""")
                }
                "EVENT" -> {
                    val id = NostrEvent.json.decodeFromJsonElement(NostrEvent.serializer(), arr[1]).id
                    webSocket.send("""["OK","$id",true,""]""")
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
    }

    @Test
    fun `une pensée étrangère arrive et la mienne revient en écho`() = runBlocking {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(fakeCabinRelay()).build())
        server.start()

        val client = RelayClient(server.url("/").toString(), scope)
        val localRelay = MutableStateFlow<RelayClient?>(null)
        val salon = HexagonSalon(scope, localRelay)
        salon.start(myKeys)
        salon.setCell(cell)

        client.connect()
        withTimeout(5_000) { client.state.first { it is RelayClient.State.Connected } }
        localRelay.value = client

        // La pensée étrangère poussée à la souscription.
        val received = withTimeout(5_000) {
            salon.pensees.first { list -> list.any { !it.mine } }
        }
        assertEquals("bienvenue dans la cabine", received.single { !it.mine }.text)

        // La nôtre : acceptée par le relais, écho local immédiat.
        assertTrue(salon.send("première pensée d'ici"))
        val after = withTimeout(5_000) {
            salon.pensees.first { list -> list.any { it.mine } }
        }
        assertEquals("première pensée d'ici", after.single { it.mine }.text)
        assertEquals(2, after.size)

        // Salon fermé (relais local disparu) : les pensées s'effacent, envoi refusé.
        localRelay.value = null
        withTimeout(5_000) { salon.pensees.first { it.isEmpty() } }
        assertFalse(salon.send("dans le vide"))
    }
}
