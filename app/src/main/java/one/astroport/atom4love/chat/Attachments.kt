package one.astroport.atom4love.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Pièces jointes de la causerie : lecture d'un contenu partagé, préparation
 * d'une image pour le débit BLE (5-20 Ko/s : on recompresse), copie locale
 * dans files/chat/ et ouverture via FileProvider.
 */
object Attachments {

    sealed interface Read {
        class Ok(val name: String, val mime: String, val bytes: ByteArray) : Read
        data object TooBig : Read
        data object Unreadable : Read
    }

    /** En deçà, une image part telle quelle ; au-delà, recompression JPEG. */
    private const val IMAGE_KEEP_BYTES = 200_000
    private const val IMAGE_MAX_DIM = 1280
    private const val JPEG_QUALITY = 80
    private const val DIR = "chat"

    /** Lit une pièce jointe telle quelle, refusée au-delà de [maxBytes]. */
    fun read(context: Context, uri: Uri, maxBytes: Int): Read {
        val resolver = context.contentResolver
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { readAtMost(it, maxBytes) }
        }.getOrNull() ?: return Read.Unreadable
        if (bytes.size > maxBytes) return Read.TooBig
        val name = displayName(context, uri) ?: "piece-jointe"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        return Read.Ok(name, mime, bytes)
    }

    /**
     * Prépare une image pour le lien BLE : orientation EXIF appliquée (API
     * 28+), bornée à [IMAGE_MAX_DIM] px et recompressée JPEG — sauf si
     * l'original est déjà assez léger, envoyé tel quel.
     */
    fun prepareImage(context: Context, uri: Uri): Read {
        val direct = read(context, uri, IMAGE_KEEP_BYTES)
        if (direct is Read.Ok && direct.mime.startsWith("image/")) return direct
        val bitmap = decodeScaled(context, uri) ?: return Read.Unreadable
        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        if (!ok) return Read.Unreadable
        val name = "img-${System.currentTimeMillis().toString(36)}.jpg"
        return Read.Ok(name, "image/jpeg", out.toByteArray())
    }

    /** Copie locale d'un contenu (reçu ou émis) dans files/chat/. */
    fun saveCopy(context: Context, preferredName: String, bytes: ByteArray): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val safe = preferredName.ifBlank { "piece-jointe" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeLast(80)
        val file = File(dir, "${System.currentTimeMillis().toString(36)}-$safe")
        file.writeBytes(bytes)
        return file
    }

    /**
     * Copie une pièce reçue vers Téléchargements (MediaStore, API 29+) pour
     * la sortir du stockage privé de l'appli. false si échec ou API < 29.
     */
    fun saveToDownloads(context: Context, file: File, name: String, mime: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name.ifBlank { file.name })
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { it.copyTo(output) }
            } ?: return false
            true
        }.getOrDefault(false)
    }

    /** Ouverture par la visionneuse système, via FileProvider. */
    fun viewIntent(context: Context, file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun humanSize(bytes: Int): String = when {
        // dès 999,5 Ko, le Ko arrondi afficherait « 1000 Ko »
        bytes >= 999_500 -> "%.1f Mo".format(bytes / 1_000_000f)
        bytes >= 1_000 -> "%.0f Ko".format(bytes / 1_000f)
        else -> "$bytes o"
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                // compress() lit les pixels : pas de bitmap matériel
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = maxOf(info.size.width, info.size.height)
                if (largest > IMAGE_MAX_DIM) {
                    val scale = IMAGE_MAX_DIM.toFloat() / largest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            // Pas d'orientation EXIF avant API 28 — assumé pour ces appareils.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (inSampleSize * 2) >= IMAGE_MAX_DIM) {
                    inSampleSize *= 2
                }
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    private fun readAtMost(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (out.size() <= maxBytes) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
