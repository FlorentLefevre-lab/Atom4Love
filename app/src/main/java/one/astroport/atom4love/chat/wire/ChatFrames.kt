package one.astroport.atom4love.chat.wire

import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * Trames de la causerie fragmentée sur GATT.
 *
 * Une écriture/notification ATT transporte au plus (MTU − 3) octets : tout
 * contenu (texte, image, fichier) est annoncé par une trame d'ouverture puis
 * découpé en fragments. Couche pure JVM, sans dépendance Android — c'est le
 * 2e prérequis du portage Noise : le handshake, puis les messages chiffrés,
 * voyageront dans ces mêmes trames.
 *
 * Format (entiers big-endian) :
 *   START  [0x01][id 4][genre 1][taille 4][crc32 4][lgNom 1][nom…][lgMime 1][mime…]
 *   DATA   [0x02][id 4][index 3][fragment…]
 *   ACK    [0x03][id 4][statut 1]
 *
 * L'id est tiré au hasard par l'émetteur ; il sert aussi à dédoublonner les
 * flux jumeaux du double lien croisé (deux connexions entre les deux mêmes
 * appareils). L'index sur 3 octets couvre 16,7 M de fragments : même au MTU
 * plancher (23 → fragments de 12 o), le plafond de transfert tient large.
 */
sealed interface ChatFrame {
    val msgId: Int

    data class Start(
        override val msgId: Int,
        val kind: Int,
        val totalBytes: Int,
        val crc32: Int,
        val name: String,
        val mime: String,
    ) : ChatFrame

    /** Fragment de contenu — égalité par identité, le tableau n'est pas comparé. */
    class Data(
        override val msgId: Int,
        val index: Int,
        val chunk: ByteArray,
    ) : ChatFrame

    data class Ack(
        override val msgId: Int,
        val status: Int,
    ) : ChatFrame
}

object ChatFrames {

    const val KIND_TEXT = 0
    const val KIND_IMAGE = 1
    const val KIND_FILE = 2

    const val ACK_OK = 0
    const val ACK_CRC = 1
    const val ACK_ABORT = 2

    private const val TYPE_START = 0x01
    private const val TYPE_DATA = 0x02
    private const val TYPE_ACK = 0x03

    /** En-tête ATT d'une écriture/notification. */
    private const val ATT_HEADER = 3

    /** [type][id][index u24]. */
    const val DATA_HEADER = 8

    /** Partie fixe d'une trame START, longueurs de nom et de mime comprises. */
    const val START_FIXED = 16

    private const val MAX_INDEX = 0xFFFFFF

    /** Octets utiles d'une écriture ATT pour un MTU donné. */
    fun attPayload(mtu: Int): Int = mtu - ATT_HEADER

    /** Octets de contenu par trame DATA pour un MTU donné. */
    fun dataChunk(mtu: Int): Int = attPayload(mtu) - DATA_HEADER

    fun crc32(bytes: ByteArray): Int = CRC32().apply { update(bytes) }.value.toInt()

    /**
     * Encode la trame d'ouverture ; nom puis mime sont tronqués pour tenir
     * dans [maxBytes] (l'ATT du lien). null si la partie fixe ne tient pas.
     */
    fun encodeStart(frame: ChatFrame.Start, maxBytes: Int): ByteArray? {
        if (maxBytes < START_FIXED) return null
        var budget = maxBytes - START_FIXED
        val name = fitUtf8(frame.name, minOf(budget, 255))
        budget -= name.size
        val mime = fitUtf8(frame.mime, minOf(budget, 255))
        return ByteBuffer.allocate(START_FIXED + name.size + mime.size)
            .put(TYPE_START.toByte())
            .putInt(frame.msgId)
            .put(frame.kind.toByte())
            .putInt(frame.totalBytes)
            .putInt(frame.crc32)
            .put(name.size.toByte())
            .put(name)
            .put(mime.size.toByte())
            .put(mime)
            .array()
    }

    /** Encode le fragment [from, until) de [content]. */
    fun encodeData(msgId: Int, index: Int, content: ByteArray, from: Int, until: Int): ByteArray {
        require(index in 0..MAX_INDEX) { "index de fragment hors bornes : $index" }
        return ByteBuffer.allocate(DATA_HEADER + (until - from))
            .put(TYPE_DATA.toByte())
            .putInt(msgId)
            .put((index ushr 16).toByte())
            .put((index ushr 8).toByte())
            .put(index.toByte())
            .put(content, from, until - from)
            .array()
    }

    fun encodeAck(msgId: Int, status: Int): ByteArray =
        ByteBuffer.allocate(6)
            .put(TYPE_ACK.toByte())
            .putInt(msgId)
            .put(status.toByte())
            .array()

    /** null si la trame est malformée — on ignore, on ne plante pas. */
    fun decode(bytes: ByteArray): ChatFrame? {
        if (bytes.size < 6) return null
        val buffer = ByteBuffer.wrap(bytes)
        return when (buffer.get().toInt()) {
            TYPE_START -> {
                if (bytes.size < START_FIXED) return null
                val id = buffer.int
                val kind = buffer.get().toInt() and 0xFF
                val total = buffer.int
                val crc = buffer.int
                val name = lengthPrefixed(buffer) ?: return null
                val mime = lengthPrefixed(buffer) ?: return null
                ChatFrame.Start(id, kind, total, crc, name, mime)
            }
            TYPE_DATA -> {
                if (bytes.size <= DATA_HEADER) return null
                val id = buffer.int
                val index = ((buffer.get().toInt() and 0xFF) shl 16) or
                    ((buffer.get().toInt() and 0xFF) shl 8) or
                    (buffer.get().toInt() and 0xFF)
                val chunk = ByteArray(buffer.remaining()).also { buffer.get(it) }
                ChatFrame.Data(id, index, chunk)
            }
            TYPE_ACK -> ChatFrame.Ack(buffer.int, buffer.get().toInt() and 0xFF)
            else -> null
        }
    }

    private fun lengthPrefixed(buffer: ByteBuffer): String? {
        if (!buffer.hasRemaining()) return null
        val length = buffer.get().toInt() and 0xFF
        if (buffer.remaining() < length) return null
        val raw = ByteArray(length).also { buffer.get(it) }
        return String(raw, Charsets.UTF_8)
    }

    /** Tronque à la frontière UTF-8 — jamais au milieu d'un point de code. */
    private fun fitUtf8(text: String, maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        var value = text
        while (true) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            if (bytes.size <= maxBytes) return bytes
            // un point de code entier : isoler un haut substitut produirait un '?'
            val drop = if (value.length >= 2 &&
                Character.isLowSurrogate(value.last()) &&
                Character.isHighSurrogate(value[value.length - 2])
            ) 2 else 1
            value = value.dropLast(drop)
        }
    }
}
