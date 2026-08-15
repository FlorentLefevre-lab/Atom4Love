package one.astroport.atom4love.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
import org.junit.Before
import org.junit.Test

/**
 * Le ménage d'une identité abandonnée, face à un faux relais.
 *
 * Ce qu'on vérifie n'est pas qu'un octet part : c'est **qu'on ne dérange
 * personne pour rien** (aucune clé privée touchée quand il n'y a rien à
 * retirer), **qu'on signe du bon nom**, et **qu'on ne signe jamais d'une
 * identité qu'on n'a pas demandée** — le défaut que le coffre du Pixel a
 * failli produire le 15/08.
 */
class CertificateRetirementTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MockWebServer()

    private val abandoned = LoveKeyForge.forge(BirthData.Sample)
    private val stranger = NostrKeys(ByteArray(32) { 3 })

    @Before
    fun start() {
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun certificate() = Certificate(scope, relayUrl = server.url("/").toString().replace("http", "ws"))

    /** Les demandes de suppression reçues par le faux relais. */
    private val deletions = mutableListOf<NostrEvent>()

    /**
     * Un relais minimal : il rend [holds] à la souscription, puis EOSE, et
     * accepte tout EVENT en notant les demandes de suppression.
     */
    private fun fakeRelay(holds: List<NostrEvent>) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val arr = NostrEvent.json.parseToJsonElement(text).jsonArray
            when (arr[0].jsonPrimitive.content) {
                "REQ" -> {
                    val subId = arr[1].jsonPrimitive.content
                    holds.forEach {
                        webSocket.send(
                            """["EVENT","$subId",${NostrEvent.json.encodeToString(NostrEvent.serializer(), it)}]""",
                        )
                    }
                    webSocket.send("""["EOSE","$subId"]""")
                }
                "EVENT" -> {
                    val event = NostrEvent.json.decodeFromJsonElement(NostrEvent.serializer(), arr[1])
                    if (event.kind == Certificate.DELETION_KIND) deletions += event
                    webSocket.send("""["OK","${event.id}",true,""]""")
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
    }

    private fun certificateOf(keys: NostrKeys): NostrEvent =
        Certificate(scope).build(keys, BirthData.Sample)!!

    @Test
    fun `rien sur le relais - on ne touche même pas à la clé privée`() = runBlocking {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(fakeRelay(emptyList())).build())
        var asked = false
        val outcome = certificate().retire(abandoned.publicKeyHex) {
            asked = true
            abandoned
        }
        assertEquals(Certificate.Retirement.Nothing, outcome)
        assertTrue("la clé privée a été demandée pour rien", !asked)
        assertTrue(deletions.isEmpty())
    }

    @Test
    fun `un certificat trouvé - la suppression part, signée du bon nom`() = runBlocking {
        val doomed = certificateOf(abandoned)
        server.enqueue(MockResponse.Builder().webSocketUpgrade(fakeRelay(listOf(doomed))).build())
        server.enqueue(MockResponse.Builder().webSocketUpgrade(fakeRelay(emptyList())).build())

        val outcome = certificate().retire(abandoned.publicKeyHex) { abandoned }
        assertTrue("$outcome", outcome is Certificate.Retirement.Done)

        assertEquals(1, deletions.size)
        val request = deletions.first()
        assertEquals(5, request.kind)
        assertEquals(abandoned.publicKeyHex, request.pubkey)
        assertTrue("l'événement visé n'est pas désigné", request.tags.contains(listOf("e", doomed.id)))
        assertTrue(
            "l'adresse remplaçable n'est pas désignée",
            request.tags.contains(
                listOf("a", "30078:${abandoned.publicKeyHex}:atom4love"),
            ),
        )
        assertTrue("la demande n'est pas signée correctement", request.hasValidId())
    }

    /**
     * Le garde-fou : si le coffre rend une **autre** identité que celle qu'on a
     * demandée, on ne signe rien. C'est exactement ce qui est arrivé sur le
     * Pixel — `LoveKeyStore` a rendu la clé de la station à la place de la
     * provisoire —, et sans ce contrôle on aurait demandé au relais d'effacer
     * le certificat de la station avec la clé de la station.
     */
    @Test
    fun `une clé inattendue ne signe rien`() = runBlocking {
        val doomed = certificateOf(abandoned)
        server.enqueue(MockResponse.Builder().webSocketUpgrade(fakeRelay(listOf(doomed))).build())

        val outcome = certificate().retire(abandoned.publicKeyHex) { stranger }
        assertEquals(Certificate.Retirement.Refused("clé inattendue"), outcome)
        assertTrue(deletions.isEmpty())
    }
}
