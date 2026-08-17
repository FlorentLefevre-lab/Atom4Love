package one.astroport.atom4love.update

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import one.astroport.atom4love.BuildConfig

/**
 * La mise à jour, du manifeste à l'installeur du système.
 *
 * ── Où vivent les versions ────────────────────────────────────────────────
 * Deux adresses, le même fichier :
 *
 *  - **GitHub**, qui fait foi — un tag, une release, un APK et son empreinte ;
 *  - **le site de Fred** (`u.copylaradio.com/earth/apk/`), qui en est le
 *    miroir. Il servait déjà un APK à cette adresse-là, avec le bon type MIME,
 *    avant qu'on s'y mette : on suit son chemin, on n'en trace pas un autre.
 *
 * On essaie l'un puis l'autre. Peu importe lequel répond : c'est le même
 * fichier, et l'empreinte du manifeste dit de toute façon quels octets sont
 * les bons.
 *
 * ── Ce que l'app fait, et ce qu'elle ne fait pas ──────────────────────────
 * Elle télécharge, elle vérifie l'empreinte, puis elle **passe la main** : le
 * dernier écran est celui du système, et c'est la personne qui appuie sur
 * « Installer ». Rien ne s'installe sans ce geste-là.
 *
 * ⚠ La vérification SHA-256 n'est pas décorative : sans elle, un miroir
 * compromis, un proxy bavard ou un transfert tronqué mettrait un APK inconnu
 * devant l'installeur. On efface plutôt que d'installer un fichier qu'on ne
 * reconnaît pas.
 *
 * ⚠ Un build **debug** porte l'applicationId `…​.debug` : l'APK release qu'on
 * télécharge ici ne le remplacera jamais, il s'installera **à côté**. C'est
 * normal au banc, et c'est pourquoi les deux peuvent cohabiter.
 */
/**
 * Les octets reçus ne sont pas ceux que le manifeste annonce.
 *
 * Distincte d'un échec réseau, et il faut que l'écran le dise autrement : un
 * réseau qui tombe se retente, une empreinte qui ne colle pas ne se retente
 * pas — elle se rapporte.
 */
class ChecksumMismatch(val expected: String, val got: String) :
    IOException("empreinte attendue $expected, reçue $got")

class UpdateService(
    /** Là où l'APK attend l'installeur. Passé en clair pour rester testable
     *  hors d'Android — voir [forApp], qui donne le dossier réel. */
    private val cacheDir: File,
    client: OkHttpClient? = null,
    private val manifestUrls: List<String> = listOf(
        BuildConfig.UPDATE_MANIFEST_URL,
        BuildConfig.UPDATE_MANIFEST_MIRROR,
    ),
) {

    companion object {
        private const val TAG = "Update"

        /** Le service tel que l'app l'utilise, sur le cache de l'application. */
        fun forApp(context: Context): UpdateService =
            UpdateService(File(context.cacheDir, CACHE_DIR))

        /** Le manifeste est minuscule : s'il traîne, c'est qu'il ne viendra pas. */
        private const val MANIFEST_TIMEOUT_S = 20L

        /** Un APK se compte en dizaines de mégaoctets, et le réseau d'un lieu est lent. */
        private const val DOWNLOAD_TIMEOUT_S = 20L * 60L

        /** Là où l'APK attend l'installeur — le système peut le balayer, tant mieux. */
        private const val CACHE_DIR = "update"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val http = client ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(DOWNLOAD_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(DOWNLOAD_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    /**
     * Le manifeste publié, ou null si aucune des adresses ne répond.
     *
     * Ne dit pas s'il y a mieux que la version installée — c'est
     * [UpdateManifest.isWorthOffering] qui en juge, et l'appelant qui décide
     * quoi en dire.
     */
    suspend fun latest(): UpdateManifest? = withContext(Dispatchers.IO) {
        for (url in manifestUrls) {
            currentCoroutineContext().ensureActive()
            val manifest = runCatching { fetchManifest(url) }
                .onFailure { Log.d(TAG, "manifeste illisible à $url", it) }
                .getOrNull()
            if (manifest != null) {
                Log.d(TAG, "manifeste lu à $url : ${manifest.versionName} (${manifest.versionCode})")
                return@withContext manifest
            }
        }
        Log.d(TAG, "aucune adresse n'a rendu de manifeste")
        null
    }

    private fun fetchManifest(url: String): UpdateManifest {
        val request = Request.Builder().url(url).build()
        val client = http.newBuilder()
            .callTimeout(MANIFEST_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(MANIFEST_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("corps vide")
            return json.decodeFromString(UpdateManifest.serializer(), body)
        }
    }

    /**
     * Télécharge l'APK et n'en rend le fichier que si son empreinte est celle
     * annoncée. Essaie chaque source dans l'ordre.
     *
     * @param onProgress avancement de 0 à 1, ou -1 quand la taille est inconnue.
     */
    suspend fun download(
        manifest: UpdateManifest,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = File(cacheDir(), "atom4love-${manifest.versionCode}.apk")

        // Déjà là et déjà bon : un réseau coupé en pleine installation ne doit
        // pas coûter un second téléchargement de trente mégaoctets.
        if (target.isFile && sha256(target) == manifest.sha256.lowercase()) {
            Log.d(TAG, "APK déjà présent et conforme")
            onProgress(1f)
            return@withContext Result.success(target)
        }

        var last: Throwable = IOException("aucune source")
        for (source in manifest.sources) {
            currentCoroutineContext().ensureActive()
            val attempt = runCatching { fetch(source, target, manifest.sizeBytes, onProgress) }
            attempt.onFailure {
                Log.w(TAG, "échec depuis $source", it)
                last = it
                target.delete()
            }
            if (attempt.isFailure) continue

            val got = sha256(target)
            if (got == manifest.sha256.lowercase()) {
                Log.d(TAG, "APK vérifié (${target.length()} o) depuis $source")
                return@withContext Result.success(target)
            }
            // Les octets ne sont pas ceux annoncés : on ne les garde pas une
            // seconde de plus, et surtout on ne les montre pas à l'installeur.
            Log.w(TAG, "empreinte inattendue depuis $source : $got")
            target.delete()
            last = ChecksumMismatch(expected = manifest.sha256.lowercase(), got = got)
        }
        Result.failure(last)
    }

    private fun fetch(url: String, target: File, expectedSize: Long, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("corps vide")
            val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
            var read = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(if (total > 0) (read.toFloat() / total).coerceAtMost(1f) else -1f)
                    }
                }
            }
        }
    }

    /** Efface l'APK téléchargé : trente mégaoctets n'ont rien à faire là après coup. */
    fun forget() {
        runCatching { cacheDir().listFiles()?.forEach { it.delete() } }
    }

    private fun cacheDir(): File = cacheDir.apply { mkdirs() }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
