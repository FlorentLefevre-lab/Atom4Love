package one.astroport.atom4love.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

/** L'antenne face à un faux relais : compteur d'états et extinction propre. */
class RelayStationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MockWebServer()

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    /** Un relais minimal : EOSE à chaque REQ, poignée de fermeture honorée. */
    private fun fakeRelayListener() = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val arr = NostrEvent.json.parseToJsonElement(text).jsonArray
            if (arr[0].jsonPrimitive.content == "REQ") {
                webSocket.send("""["EOSE","${arr[1].jsonPrimitive.content}"]""")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
    }

    @Test
    fun `allumer - le compteur passe à 1 sur 1 puis retombe à l'extinction`() = runBlocking {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(fakeRelayListener()).build())
        server.start()

        val station = RelayStation(scope, defaultUrls = listOf(server.url("/").toString()))
        assertEquals(RelayStation.Status(0, 1), station.status.value)
        assertFalse(station.status.value.online)

        station.start(LoveKeyForge.forge(BirthData.Sample))
        val onlineStatus = withTimeout(5_000) {
            station.status.first { it.connected == 1 }
        }
        assertTrue(onlineStatus.online)
        assertEquals("relay · 1 / 1", onlineStatus.label)

        station.stop()
        assertEquals(RelayStation.Status(0, 1), station.status.value)
    }
}
