package one.astroport.atom4love.update

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * La mécanique de mise à jour, au banc.
 *
 * Ce qui est vérifié ici n'est pas « ça télécharge » — c'est **ce qu'on refuse
 * d'installer** : une version qui n'est pas plus récente, une version qui
 * demande un Android que l'appareil n'a pas, et surtout des octets dont
 * l'empreinte ne correspond pas.
 */
class UpdateServiceTest {

    @get:Rule val temp = TemporaryFolder()

    private val server = MockWebServer()

    @Before fun setUp() {
        server.start()
    }

    @After fun tearDown() {
        server.close()
    }

    private fun service(vararg paths: String) = UpdateService(
        cacheDir = temp.newFolder(),
        manifestUrls = paths.map { server.url(it).toString() },
    )

    private val apkBytes = ByteArray(4096) { (it % 251).toByte() }

    /** L'empreinte de [apkBytes], calculée comme le service la calcule. */
    private val apkSha = java.security.MessageDigest.getInstance("SHA-256")
        .digest(apkBytes)
        .joinToString("") { "%02x".format(it) }

    private fun manifestJson(
        versionCode: Int = 2,
        sha: String = apkSha,
        minSdk: Int = 26,
        path: String = "/atom4love.apk",
    ) = """
        {"versionCode":$versionCode,"versionName":"0.2.0","minSdk":$minSdk,
         "sha256":"$sha","sizeBytes":${apkBytes.size},
         "url":"${server.url(path)}","notes":"essai"}
    """.trimIndent()

    private fun apkResponse() = MockResponse.Builder()
        .code(200)
        .body(Buffer().write(apkBytes))
        .build()

    @Test fun `lit le manifeste`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(manifestJson()).build())

        val manifest = service("/latest.json").latest()!!

        assertEquals(2, manifest.versionCode)
        assertEquals("0.2.0", manifest.versionName)
        assertEquals(apkSha, manifest.sha256)
    }

    /** Un champ ajouté par une version future ne doit pas rendre le fichier
     *  illisible aux APK déjà installés — sinon ils ne sauront jamais qu'ils
     *  sont vieux. */
    @Test fun `ignore les champs inconnus`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"versionCode":3,"versionName":"0.3.0","sha256":"ab","url":"https://x/a.apk",
                    "quelqueChoseDeNouveau":{"encore":[1,2,3]}}""",
            ).build(),
        )

        assertEquals(3, service("/latest.json").latest()!!.versionCode)
    }

    /** Le dépôt est muet : on essaie le miroir avant d'abandonner. */
    @Test fun `bascule sur le miroir`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(MockResponse.Builder().code(200).body(manifestJson()).build())

        assertNotNull(service("/latest.json", "/mirror.json").latest())
    }

    @Test fun `aucune source ne repond`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(MockResponse.Builder().code(404).build())

        assertNull(service("/latest.json", "/mirror.json").latest())
    }

    @Test fun `ne propose pas une version deja installee`() {
        val manifest = UpdateManifest(
            versionCode = 4, versionName = "0.4.0", sha256 = "ab", url = "https://x/a.apk",
        )

        assertFalse(manifest.isWorthOffering(installedCode = 4, sdk = 34))
        assertFalse(manifest.isWorthOffering(installedCode = 5, sdk = 34))
        assertTrue(manifest.isWorthOffering(installedCode = 3, sdk = 34))
    }

    /** Proposer un APK qui refusera de s'installer, c'est promettre une porte
     *  qui ne s'ouvre pas. */
    @Test fun `ne propose pas une version que l'appareil ne peut pas installer`() {
        val manifest = UpdateManifest(
            versionCode = 4, versionName = "0.4.0", minSdk = 29,
            sha256 = "ab", url = "https://x/a.apk",
        )

        assertFalse(manifest.isWorthOffering(installedCode = 1, sdk = 26))
        assertTrue(manifest.isWorthOffering(installedCode = 1, sdk = 29))
    }

    @Test fun `telecharge et verifie l'empreinte`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(manifestJson()).build())
        server.enqueue(apkResponse())
        val service = service("/latest.json")
        val manifest = service.latest()!!

        var seen = 0f
        val file = service.download(manifest) { seen = it }.getOrThrow()

        assertTrue(file.isFile)
        assertEquals(apkBytes.size.toLong(), file.length())
        assertEquals(1f, seen, 0f)
    }

    /** Le cœur de l'affaire : des octets qui ne sont pas ceux annoncés ne
     *  vont pas jusqu'à l'installeur, et ne restent pas sur l'appareil. */
    @Test fun `refuse et efface un APK dont l'empreinte ne colle pas`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(manifestJson(sha = "00".repeat(32))).build())
        server.enqueue(apkResponse())
        val service = service("/latest.json")
        val manifest = service.latest()!!

        val result = service.download(manifest)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ChecksumMismatch)
        assertTrue(cacheIsEmpty())
    }

    /** Une source qui tombe n'est pas la fin : le miroir porte les mêmes octets. */
    @Test fun `bascule sur le miroir de l'APK`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(manifestJson()).build())
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(apkResponse())
        val service = service("/latest.json")
        val manifest = service.latest()!!
            .copy(mirrors = listOf(server.url("/mirror.apk").toString()))

        assertTrue(service.download(manifest).isSuccess)
    }

    @Test fun `oublie l'APK telecharge`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(manifestJson()).build())
        server.enqueue(apkResponse())
        val service = service("/latest.json")
        service.download(service.latest()!!).getOrThrow()

        service.forget()

        assertTrue(cacheIsEmpty())
    }

    private fun cacheIsEmpty(): Boolean =
        temp.root.walkTopDown().none { it.isFile && it.extension == "apk" }
}
