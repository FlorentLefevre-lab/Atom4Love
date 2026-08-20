package one.astroport.atom4love.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import one.astroport.atom4love.R
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Pièces jointes de la causerie : lecture d'un contenu partagé, préparation
 * d'une image pour le débit BLE (5-20 Ko/s : on recompresse), copie locale
 * dans files/chat/ et ouverture via FileProvider.
 */
object Attachments {

    sealed interface Read {
        /**
         * La pièce est **déjà posée sur le disque** : c'est de là qu'elle
         * partira, morceau par morceau. Elle n'a traversé la mémoire à aucun
         * moment — sans quoi « qualité initiale » n'aurait pas dépassé quelques
         * secondes de vidéo.
         */
        class Ok(val name: String, val mime: String, val file: File, val size: Int) : Read

        /**
         * Trop lourde pour le médium du moment. Porte le nom et la **vraie**
         * taille : un refus qui ne dit pas de combien on dépasse ne vaut pas
         * grand-chose pour celui qui vient de choisir son fichier.
         * [bytes] vaut −1 quand le fournisseur ne déclare pas la taille.
         */
        data class TooBig(val name: String, val bytes: Long) : Read
        data object Unreadable : Read
    }

    /** En deçà, une image part telle quelle ; au-delà, recompression JPEG. */
    private const val TAG = "ChatEngine"
    private const val IMAGE_KEEP_BYTES = 200_000
    private const val IMAGE_MAX_DIM = 1280
    private const val JPEG_QUALITY = 80

    /**
     * Le visage de reconnaissance, taillé **pour la radio**.
     *
     * ⚠ Ce n'est pas une photo qu'on garde : elle sert trois secondes, à
     * reconnaître quelqu'un dans une salle, et elle passe par du BLE à 5-20
     * ko/s. Une image de causerie ordinaire (1280 px, qualité 80) pèse 150 à
     * 300 ko — vingt à soixante secondes d'antenne pour un coup d'œil. À
     * 448 px et qualité 45, un visage reste reconnaissable à bout de bras et
     * l'envoi tombe sous la poignée de secondes.
     *
     * La lanterne l'affiche dans un disque de 168 dp : 448 px couvrent encore
     * un écran à 3× sans qu'on lise le grain.
     */
    private const val SELFIE_MAX_DIM = 448
    private const val SELFIE_QUALITY = 45
    private const val DIR = "chat"

    /**
     * Copie une pièce choisie dans files/chat/, telle quelle, refusée au-delà
     * de [maxBytes]. **Rien ne passe par la mémoire** : le contenu va du
     * fournisseur au disque par tampons de 64 Ko, ce qui rend la taille d'une
     * vidéo indifférente à ce que le téléphone peut tenir.
     *
     * Le fichier n'est nommé qu'une fois complet : un refus de taille ou une
     * lecture rompue ne laissent rien.
     */
    fun stage(context: Context, uri: Uri, maxBytes: Int): Read {
        val resolver = context.contentResolver
        val name = displayName(context, uri) ?: "piece-jointe"
        // La taille déclarée d'abord : refuser sur elle évite de recopier
        // cinquante mégaoctets pour finalement dire non. Tous les fournisseurs
        // ne la donnent pas — d'où le contrôle qui suit, en cours de copie.
        declaredSize(context, uri)?.let { size ->
            if (size > maxBytes) return Read.TooBig(name, size)
        }
        val destination = destination(context, name)
        val partial = File(destination.parentFile, destination.name + ".partiel")
        var copied = 0L
        val outcome = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        // au-delà du plafond, on s'arrête net : inutile de
                        // finir de recopier ce qu'on va refuser
                        if (copied > maxBytes) return@runCatching false
                        output.write(buffer, 0, n)
                    }
                }
                true
            }
        }.getOrNull()
        if (outcome != true) {
            partial.delete()
            return if (outcome == false) Read.TooBig(name, copied) else Read.Unreadable
        }
        if (!partial.renameTo(destination)) {
            partial.delete()
            return Read.Unreadable
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        return Read.Ok(name, mime, destination, copied.toInt())
    }

    /** Taille annoncée par le fournisseur, null s'il ne la déclare pas. */
    private fun declaredSize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (column < 0 || cursor.isNull(column)) null else cursor.getLong(column)
            }
    }.getOrNull()

    /**
     * Prépare une image pour le lien BLE : orientation EXIF appliquée (API
     * 28+), bornée à [IMAGE_MAX_DIM] px et recompressée JPEG — sauf si
     * l'original est déjà assez léger, envoyé tel quel.
     */
    fun prepareImage(context: Context, uri: Uri): Read {
        val direct = stage(context, uri, IMAGE_KEEP_BYTES)
        if (direct is Read.Ok) {
            if (direct.mime.startsWith("image/")) return direct
            // pas une image malgré le sélecteur : on ne garde pas sa copie
            direct.file.delete()
        }
        // Ici seulement la mémoire entre en jeu, et elle est bornée : l'image
        // est ramenée à 1280 px avant d'être recompressée.
        val bitmap = decodeScaled(context, uri) ?: return Read.Unreadable
        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        if (!ok) return Read.Unreadable
        val name = "img-${System.currentTimeMillis().toString(36)}.jpg"
        val file = runCatching { saveCopy(context, name, out.toByteArray()) }.getOrNull()
            ?: return Read.Unreadable
        return Read.Ok(name, "image/jpeg", file, file.length().toInt())
    }

    /**
     * Un emplacement libre dans files/chat/, sous un nom rendu inoffensif.
     * C'est la destination d'une pièce qu'on prépare à envoyer comme de celle
     * qu'un [one.astroport.atom4love.chat.wire.FileSink] est en train de recevoir.
     */
    fun destination(context: Context, preferredName: String): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val safe = preferredName.ifBlank { "piece-jointe" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeLast(80)
        return File(dir, "${System.currentTimeMillis().toString(36)}-$safe")
    }

    /** Copie locale d'un contenu déjà en mémoire (image recompressée). */
    fun saveCopy(context: Context, preferredName: String, bytes: ByteArray): File =
        destination(context, preferredName).apply { writeBytes(bytes) }

    /**
     * Efface tout ce que la cabine détient dans files/chat/.
     *
     * « Fermer = effacer » ne portait jusqu'ici que sur la conversation, qui
     * meurt avec l'instance de `ChatEngine` ; les fichiers, eux, n'étaient
     * supprimés nulle part et s'accumulaient depuis la toute première cabine.
     *
     * Ce qui est parti dans Téléchargements ([saveToDownloads]) n'est **pas**
     * concerné : cette copie-là est hors de notre dossier, quelqu'un l'a
     * demandée, elle lui appartient. Effacer aussi la sienne ferait de la
     * sortie de cabine un piège pour qui a pris soin de garder une photo.
     *
     * Le dossier lui-même survit à son contenu : [saveCopy] le recréerait de
     * toute façon, et le garder évite une course avec un transfert qui
     * démarrerait dans la même seconde.
     *
     * Rend le nombre d'entrées retirées — de quoi le dire au journal.
     */
    fun wipe(context: Context): Int {
        val entries = File(context.filesDir, DIR).listFiles() ?: return 0
        return entries.count { it.deleteRecursively() }
    }

    /**
     * Copie une pièce reçue vers Téléchargements (MediaStore, API 29 — le
     * plancher) pour la sortir du stockage privé de l'appli. false si échec.
     */
    fun saveToDownloads(context: Context, file: File, name: String, mime: String): Boolean {
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

    /**
     * Un fichier vide pour l'appareil photo, et l'URI qui le lui ouvre.
     *
     * ⚠ **On ne passe pas par `ACTION_IMAGE_CAPTURE` sans destination** : sans
     * `EXTRA_OUTPUT`, l'appareil photo ne rend qu'une vignette de quelques
     * dizaines de kilopixels, bonne pour un aperçu et pour rien d'autre. Avec,
     * il écrit la photo pleine dans notre dossier, d'où [prepareImage] la
     * reprend et la recompresse pour la radio.
     *
     * Le nom porte l'instant : deux selfies pris dans la même seconde ne
     * s'écrasent pas, et le dossier reste lisible quand on l'inspecte au banc.
     */
    fun newPhoto(context: Context): Pair<File, Uri> {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "selfie-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** Ouverture par la visionneuse système, via FileProvider. */
    fun viewIntent(context: Context, file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Une taille lisible, dans la langue de l'appareil : « 2,4 Mo » en
     * français, « 2.4 MB » en anglais. L'unité ET le séparateur décimal
     * viennent des ressources — les écrire ici les figerait en français.
     *
     * Le seuil : dès 999,5 Ko, le Ko arrondi afficherait « 1000 Ko ».
     */
    fun humanSize(res: Resources, bytes: Long): String = when {
        bytes >= 999_500L -> res.getString(R.string.size_megabytes, bytes / 1_000_000f)
        bytes >= 1_000L -> res.getString(R.string.size_kilobytes, bytes / 1_000f)
        else -> res.getString(R.string.size_bytes, bytes)
    }

    fun humanSize(res: Resources, bytes: Int): String = humanSize(res, bytes.toLong())

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /**
     * ImageDecoder date de l'API 28, sous le plancher : c'est le seul chemin.
     * Il porte l'orientation EXIF, que `BitmapFactory` ignorait.
     */
    /**
     * Le visage de reconnaissance : **toujours recompressé**, jamais la copie
     * d'origine — voir [SELFIE_MAX_DIM]. Une photo d'appareil fait quatre
     * mégaoctets, et ce qui doit traverser la radio en tient trente fois moins.
     */
    fun prepareSelfie(context: Context, uri: Uri): Read {
        val bitmap = decodeScaled(context, uri, SELFIE_MAX_DIM) ?: return Read.Unreadable
        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, SELFIE_QUALITY, out)
        bitmap.recycle()
        if (!ok) return Read.Unreadable
        val bytes = out.toByteArray()
        val name = "visage-${System.currentTimeMillis().toString(36)}.jpg"
        val file = runCatching { saveCopy(context, name, bytes) }.getOrNull()
            ?: return Read.Unreadable
        Log.i(TAG, "visage préparé : ${bytes.size} o (${SELFIE_MAX_DIM} px, q$SELFIE_QUALITY)")
        return Read.Ok(name, "image/jpeg", file, file.length().toInt())
    }

    private fun decodeScaled(
        context: Context,
        uri: Uri,
        maxDim: Int = IMAGE_MAX_DIM,
    ): Bitmap? = runCatching {
        val resolver = context.contentResolver
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            // compress() lit les pixels : pas de bitmap matériel
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = maxOf(info.size.width, info.size.height)
            if (largest > maxDim) {
                val scale = maxDim.toFloat() / largest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }.getOrNull()

}
