package one.astroport.atom4love.multipass

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import one.astroport.atom4love.data.AccountVault
import one.astroport.atom4love.data.MultipassAccount
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.nostr.LoveKeyForge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'enchaînement des deux temps, là où il peut se casser : un compte créé mais
 * pas activé ne doit jamais conduire à en recréer un second.
 */
class EnrollmentTest {

    private val server = MockWebServer()
    private val scope = CoroutineScope(Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    /** Un coffre en mémoire — le vrai s'adosse au Keystore, absent de la JVM. */
    private class FakeVault(var account: MultipassAccount? = null) : AccountVault {
        override suspend fun load(): MultipassAccount? = account

        override suspend fun save(station: String, response: MultipassResponse) {
            account = MultipassAccount(
                email = response.email, station = station, npub = response.npub,
                hex = response.hex, nsec = response.nsec, g1pub = response.g1pub,
                pass = response.pass, ssss = response.ssss, isOrigin = response.isOrigin,
            )
        }

        override suspend fun saveLove(activation: Atom4LoveActivation): MultipassAccount? {
            val current = account ?: return null
            return current.copy(
                loveNpub = activation.loveNpub,
                loveHex = activation.loveHex,
                loveNsec = activation.loveNsec,
            ).also { account = it }
        }
    }

    private val keys = LoveKeyForge.forge(BirthData.Sample)

    private fun enrollment(vault: AccountVault): Enrollment {
        server.start()
        val service = MultipassService(server.url("/").toString().trimEnd('/'))
        return Enrollment(scope, service, vault)
    }

    private fun creationResponse() = MockResponse.Builder().code(200).body(
        """{"email":"kim@example.org","nsec":"${keys.nsec}","npub":"${keys.npub}",
            "hex":"${keys.publicKeyHex}","g1pub":"g1abc","pass":"1234","ssss":"M-x",
            "is_origin":true}""",
    ).build()

    private fun challengeResponse() = MockResponse.Builder().code(200)
        .body("""{"challenge":"ch-1","pubkey_hex":"${keys.publicKeyHex}","expires_in":120}""")
        .build()

    private fun activationResponse() = MockResponse.Builder().code(200).body(
        """{"activated":true,"email":"kim@example.org","love_nsec":"nsec1love",
            "love_npub":"npub1love","love_hex":"${"c".repeat(64)}","kin_num":7,
            "personal_phase":1.5}""",
    ).build()

    /** Attend l'étape voulue plutôt qu'un délai arbitraire. */
    private suspend fun Enrollment.await(predicate: (Enrollment.Step) -> Boolean): Enrollment.Step =
        withTimeout(10_000) { step.first(predicate) }

    @Test
    fun `inscription complète - création puis activation, la clé LOVE atterrit au coffre`() =
        runBlocking {
            server.enqueue(creationResponse())
            server.enqueue(challengeResponse())
            server.enqueue(activationResponse())
            val vault = FakeVault()
            val enrollment = enrollment(vault)

            enrollment.enroll("Kim@Example.org", BirthData.Sample, 48.86, 2.35)
            val done = enrollment.await { it is Enrollment.Step.Done } as Enrollment.Step.Done

            assertTrue(done.account.loveActivated)
            assertEquals("nsec1love", done.account.loveNsec)
            assertEquals("nsec1love", vault.account?.loveNsec)
            // L'adresse est normalisée avant l'envoi : la station range ses
            // comptes par email en minuscules.
            assertEquals("kim@example.org", server.takeRequest().body?.utf8()
                ?.split("&")?.first { it.startsWith("email=") }
                ?.removePrefix("email=")?.let { java.net.URLDecoder.decode(it, "UTF-8") })
        }

    @Test
    fun `email déjà pris - la demande s'arrête net pour réclamer le PASS`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(409).body("""{"error":"MULTIPASS_EXISTS"}""").build(),
        )
        val vault = FakeVault()
        val enrollment = enrollment(vault)

        enrollment.enroll("kim@example.org", BirthData.Sample, null, null)
        val step = enrollment.await { it is Enrollment.Step.NeedPass }

        assertEquals("kim@example.org", (step as Enrollment.Step.NeedPass).email)
        // Aucune activation n'a été tentée : le compte n'est pas à nous.
        assertNull(vault.account)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `activation en échec - le compte est gardé, seule la clé LOVE manque`() = runBlocking {
        server.enqueue(creationResponse())
        server.enqueue(MockResponse.Builder().code(500).body("""{"message":"relais muet"}""").build())
        val vault = FakeVault()
        val enrollment = enrollment(vault)

        enrollment.enroll("kim@example.org", BirthData.Sample, null, null)
        val failed = enrollment.await { it is Enrollment.Step.Failed } as Enrollment.Step.Failed

        assertTrue("l'échec doit être rattrapable", failed.recoverable)
        // Le MULTIPASS est bien au coffre — c'est ce qui interdit d'en recréer un.
        assertEquals("kim@example.org", vault.account?.email)
        assertEquals(false, vault.account?.loveActivated)
    }

    @Test
    fun `reprise - la clé LOVE se redemande sans repasser par la création`() = runBlocking {
        server.enqueue(challengeResponse())
        server.enqueue(activationResponse())
        val vault = FakeVault(
            MultipassAccount(
                email = "kim@example.org", station = "https://u.copylaradio.com",
                npub = keys.npub, hex = keys.publicKeyHex, nsec = keys.nsec,
                g1pub = "g1abc", pass = "1234", ssss = "M-x", isOrigin = true,
            ),
        )
        val enrollment = enrollment(vault)

        enrollment.retryActivation(BirthData.Sample)
        val done = enrollment.await { it is Enrollment.Step.Done } as Enrollment.Step.Done

        assertTrue(done.account.loveActivated)
        // Deux appels seulement : challenge et activation. Aucun /g1nostr.
        assertEquals(2, server.requestCount)
        assertEquals("/atom4love/challenge", server.takeRequest().url.encodedPath)
        assertEquals("/atom4love/activate", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `fiche incomplète - on ne dérange pas la station`() = runBlocking {
        val vault = FakeVault(
            MultipassAccount(
                email = "kim@example.org", station = "https://u.copylaradio.com",
                npub = keys.npub, hex = keys.publicKeyHex, nsec = keys.nsec,
                g1pub = "g1abc", pass = "1234", ssss = "M-x", isOrigin = true,
            ),
        )
        val enrollment = enrollment(vault)

        enrollment.retryActivation(BirthData.Empty)
        val failed = enrollment.await { it is Enrollment.Step.Failed } as Enrollment.Step.Failed

        assertEquals(EnrollError.IncompleteForm, failed.reason)
        assertEquals(0, server.requestCount)
    }
}
