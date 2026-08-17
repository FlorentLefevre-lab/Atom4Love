package one.astroport.atom4love.multipass

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.nostr.LoveKeyForge
import one.astroport.atom4love.nostr.NostrEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le guichet face à une fausse station : ce qu'on lui envoie compte autant que
 * ce qu'on en lit. Les corps de requête sont vérifiés champ par champ — c'est
 * là que se joue la fidélité au client de Fred.
 */
class MultipassServiceTest {

    private val server = MockWebServer()

    @After
    fun tearDown() = server.close()

    private fun service(): MultipassService {
        server.start()
        return MultipassService(server.url("/").toString().trimEnd('/'))
    }

    /** Le corps d'un formulaire posté, décodé en couples clé/valeur. */
    private fun formOf(body: String): Map<String, String> =
        body.split("&").filter { it.isNotEmpty() }.associate { pair ->
            val (k, v) = pair.split("=", limit = 2)
            java.net.URLDecoder.decode(k, "UTF-8") to java.net.URLDecoder.decode(v, "UTF-8")
        }

    private val creationJson = """
        {"g1pub":"g1abc","nsec":"nsec1xyz","npub":"npub1xyz","hex":"ab12",
         "pass":"1234","ssss":"M-part","nostrns":"k51ns","salt":"s","pepper":"p",
         "email":"kim@example.org","lat":"48.86","lon":"2.35",
         "uplanetname_g1":"g1planet","is_origin":true,
         "oc_urls":{"satellite":"https://sat","constellation":"","cloud":"","membre":""},
         "uplanet_home":"https://home"}
    """.trimIndent()

    @Test
    fun `créer - poste le strict nécessaire, sans aucune donnée de naissance`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(creationJson).build())

        val response = service().createMultipass(
            email = "kim@example.org", lang = "fr", lat = "48.86", lon = "2.35",
        )

        val request = server.takeRequest()
        assertEquals("/g1nostr", request.url.encodedPath)
        val form = formOf(request.body?.utf8().orEmpty())
        assertEquals("kim@example.org", form["email"])
        assertEquals("fr", form["lang"])
        assertEquals("json", form["format"])
        // Le cœur du contrat : l'identité principale est tirée au sort par la
        // station, jamais dérivée de la naissance. Rien de tout cela ne part.
        listOf("birth_datetime", "birth_lat", "birth_lon", "birth_weight",
            "polarity", "salt", "pepper", "pass_code").forEach { forbidden ->
            assertFalse("$forbidden n'a rien à faire ici", form.containsKey(forbidden))
        }

        assertEquals("npub1xyz", response.npub)
        assertEquals("1234", response.pass)
        assertEquals("M-part", response.ssss)
        assertTrue(response.isOrigin)
        assertEquals("https://sat", response.ocUrls.satellite)
    }

    @Test
    fun `créer - le code PASS n'est joint que s'il est fourni`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(creationJson).build())

        service().createMultipass(
            email = "kim@example.org", lang = "fr", lat = "0", lon = "0", passCode = "4242",
        )

        assertEquals("4242", formOf(server.takeRequest().body?.utf8().orEmpty())["pass_code"])
    }

    @Test
    fun `créer - email déjà pris, la station réclame le PASS`() {
        server.enqueue(
            MockResponse.Builder().code(409)
                .body("""{"error":"MULTIPASS_EXISTS","need_pass":true}""").build(),
        )
        val service = service()

        assertThrows<MultipassError.Exists> {
            runBlocking { service.createMultipass("kim@example.org", "fr", "0", "0") }
        }
    }

    @Test
    fun `créer - mêmes clés sous un autre email, la station refuse`() {
        server.enqueue(
            MockResponse.Builder().code(409).body("""{"error":"IDENTITY_CONFLICT"}""").build(),
        )
        val service = service()

        assertThrows<MultipassError.IdentityConflict> {
            runBlocking { service.createMultipass("kim@example.org", "fr", "0", "0") }
        }
    }

    @Test
    fun `créer - PASS incorrect`() {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"INVALID_PASS"}""").build())
        val service = service()

        assertThrows<MultipassError.InvalidPass> {
            runBlocking { service.createMultipass("kim@example.org", "fr", "0", "0", "0000") }
        }
    }

    @Test
    fun `créer - le PASS n'est pas sur ce nœud`() {
        server.enqueue(
            MockResponse.Builder().code(503).body("""{"error":"PASS_UNAVAILABLE"}""").build(),
        )
        val service = service()

        assertThrows<MultipassError.PassUnavailable> {
            runBlocking { service.createMultipass("kim@example.org", "fr", "0", "0", "0000") }
        }
    }

    @Test
    fun `activer - signe le challenge sans jamais livrer la clé du compte`() = runBlocking {
        val keys = LoveKeyForge.forge(BirthData.Sample)
        server.enqueue(
            MockResponse.Builder().code(200)
                .body("""{"challenge":"ch-7f3a","pubkey_hex":"${keys.publicKeyHex}","expires_in":120}""")
                .build(),
        )
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"activated":true,"email":"kim@example.org","love_nsec":"nsec1love",
                    "love_npub":"npub1love","love_hex":"cafe","kin_num":42,
                    "personal_phase":3.141592}""",
            ).build(),
        )

        val activation = service().activateAtom4Love(
            email = "kim@example.org",
            primaryNsec = keys.nsec,
            birthDatetime = "1985-04-17T15:30",
            birthLat = "48.86",
            birthLon = "2.35",
            birthWeight = "3.2",
            polarity = "0",
            birthPlace = "Paris, France",
        )

        val challengeRequest = server.takeRequest()
        assertEquals("/atom4love/challenge", challengeRequest.url.encodedPath)
        assertEquals("kim@example.org", challengeRequest.url.queryParameter("email"))

        val activateRequest = server.takeRequest()
        assertEquals("/atom4love/activate", activateRequest.url.encodedPath)
        val body = activateRequest.body?.utf8().orEmpty()
        val form = formOf(body)
        assertEquals("1985-04-17T15:30", form["birth_datetime"])
        assertEquals("3.2", form["birth_weight"])
        assertEquals("0", form["polarity"])
        assertEquals("Paris, France", form["birth_place"])
        // La preuve de possession voyage ; le secret qui la produit, non.
        assertFalse("le nsec ne doit jamais partir", body.contains(keys.nsec))

        val event = NostrEvent.json.parseToJsonElement(form["auth_event"]!!).jsonObject
        assertEquals(22242, event["kind"]!!.jsonPrimitive.content.toInt())
        assertEquals(keys.publicKeyHex, event["pubkey"]!!.jsonPrimitive.content)
        assertEquals("", event["content"]!!.jsonPrimitive.content)
        val tag = event["tags"]!!.jsonArray[0].jsonArray
        assertEquals("challenge", tag[0].jsonPrimitive.content)
        assertEquals("ch-7f3a", tag[1].jsonPrimitive.content)
        // L'id doit être celui de la sérialisation canonique, sinon le relais
        // comme la station rejettent la signature.
        val rebuilt = NostrEvent.json.decodeFromJsonElement(NostrEvent.serializer(), event)
        assertTrue(rebuilt.hasValidId())

        assertEquals("nsec1love", activation.loveNsec)
        assertEquals("cafe", activation.loveHex)
        assertEquals(42, activation.kinNum)
    }

    @Test
    fun `activer - sans MULTIPASS derrière l'email`() {
        server.enqueue(MockResponse.Builder().code(404).body("""{"detail":"introuvable"}""").build())
        val keys = LoveKeyForge.forge(BirthData.Sample)
        val service = service()

        assertThrows<MultipassError.PrimaryAccountNotFound> {
            runBlocking {
                service.activateAtom4Love(
                    email = "inconnu@example.org", primaryNsec = keys.nsec,
                    birthDatetime = "1985-04-17T15:30", birthLat = "48.86",
                    birthLon = "2.35", birthWeight = "3.2", polarity = "0",
                )
            }
        }
    }

    @Test
    fun `clé LOVE - lue à la racine, ou sous le vecteur de rêve`() = runBlocking {
        val hex = "a".repeat(64)
        server.enqueue(MockResponse.Builder().code(200).body("""{"love_hex":"$hex"}""").build())
        server.enqueue(
            MockResponse.Builder().code(200)
                .body("""{"dream_vector":{"love_hex":"$hex"}}""").build(),
        )
        server.enqueue(MockResponse.Builder().code(404).body("{}").build())
        val service = service()

        assertEquals(hex, service.fetchLoveHex("kim@example.org"))
        assertEquals(hex, service.fetchLoveHex("kim@example.org"))
        assertNull(service.fetchLoveHex("kim@example.org"))
    }

    /** JUnit 4 n'a pas d'équivalent typé confortable pour les coroutines. */
    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            assertTrue(
                "attendu ${T::class.simpleName}, reçu ${t::class.simpleName} : ${t.message}",
                t is T,
            )
            return
        }
        throw AssertionError("aucune exception levée, ${T::class.simpleName} attendue")
    }
}
