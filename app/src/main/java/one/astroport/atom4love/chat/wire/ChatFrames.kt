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
 *   HELLO  [0x04][étape 1][message Noise…]
 *
 * L'id est tiré au hasard par l'émetteur ; il sert aussi à dédoublonner les
 * flux jumeaux du double lien croisé (deux connexions entre les deux mêmes
 * appareils). L'index sur 3 octets couvre 16,7 M de fragments : même au MTU
 * plancher (23 → fragments de 12 o), le plafond de transfert tient large.
 *
 * HELLO porte le handshake Noise XX et n'a pas d'id : il précède tout message,
 * et un lien n'a qu'un handshake à la fois. Les trois messages XX tiennent
 * chacun dans une trame (32, ~96 et ~64 octets contre 512 disponibles), donc
 * aucune fragmentation à prévoir de ce côté.
 */
sealed interface ChatFrame {

    data class Start(
        val msgId: Int,
        val kind: Int,
        val totalBytes: Int,
        val crc32: Int,
        val name: String,
        val mime: String,
    ) : ChatFrame

    /** Fragment de contenu — égalité par identité, le tableau n'est pas comparé. */
    class Data(
        val msgId: Int,
        val index: Int,
        val chunk: ByteArray,
    ) : ChatFrame

    data class Ack(
        val msgId: Int,
        val status: Int,
    ) : ChatFrame

    /**
     * Un des trois messages du handshake Noise XX. [step] vaut 1, 2 ou 3 : il
     * ne sert qu'à repérer une trame hors séquence, la machine de Noise étant
     * seule juge de ce qui est acceptable.
     */
    class Handshake(
        val step: Int,
        val message: ByteArray,
    ) : ChatFrame

    /** Une trame quelconque scellée par la session Noise du lien. */
    class Sealed(
        val ciphertext: ByteArray,
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
    private const val TYPE_HANDSHAKE = 0x04
    private const val TYPE_SEALED = 0x05

    /** [type][étape] — le reste de la trame est le message Noise. */
    const val HANDSHAKE_HEADER = 2

    const val HANDSHAKE_STEPS = 3

    /** Authentificateur ChaCha20-Poly1305 ajouté à chaque scellement. */
    private const val MAC_LENGTH = 16

    /**
     * Ce qu'un scellement coûte : l'octet de type de la trame SEALED plus le
     * MAC. Toujours réservé, même sur un lien encore en clair — un handshake
     * peut aboutir au milieu d'un transfert, et les fragments déjà dimensionnés
     * deviendraient alors trop grands pour l'ATT une fois scellés.
     */
    const val SEAL_OVERHEAD = 1 + MAC_LENGTH

    /** En-tête ATT d'une écriture/notification. */
    private const val ATT_HEADER = 3

    /**
     * Longueur maximale d'une valeur d'attribut (spec ATT), quel que soit le
     * MTU : à 517, l'espace « MTU − 3 » (514 o) dépasse ce plafond et les
     * framworks récents jettent IllegalArgumentException à chaque écriture
     * (vu sur banc : Pixel/Android 16 refuse, ZUI/Android 14 laisse passer).
     */
    private const val ATT_MAX_VALUE = 512

    /** [type][id][index u24]. */
    const val DATA_HEADER = 8

    /** Partie fixe d'une trame START, longueurs de nom et de mime comprises. */
    const val START_FIXED = 16

    private const val MAX_INDEX = 0xFFFFFF

    /** Octets utiles d'une écriture ATT pour un MTU donné, plafond spec inclus. */
    fun attPayload(mtu: Int): Int = minOf(mtu - ATT_HEADER, ATT_MAX_VALUE)

    /** Le plus court message XX : le premier, réduit à la clé éphémère. */
    private const val MIN_HANDSHAKE_BYTES = 32

    /**
     * Un lien ne peut chiffrer que si son ATT porte déjà le premier message du
     * handshake. Au MTU plancher (23 → 20 octets utiles) c'est impossible : ce
     * lien restera en clair, et n'a donc rien à réserver.
     */
    fun canSeal(mtu: Int): Boolean =
        attPayload(mtu) >= HANDSHAKE_HEADER + MIN_HANDSHAKE_BYTES

    /**
     * Place pour une trame ordinaire, le scellement Noise déduit. Sert de
     * budget à START comme à DATA, que le lien chiffre **déjà** ou non : un
     * handshake peut aboutir au milieu d'un transfert, et des fragments
     * dimensionnés sans la réserve déborderaient alors de l'ATT.
     */
    fun framePayload(mtu: Int): Int =
        attPayload(mtu) - if (canSeal(mtu)) SEAL_OVERHEAD else 0

    /** Octets de contenu par trame DATA pour un MTU donné. */
    fun dataChunk(mtu: Int): Int = framePayload(mtu) - DATA_HEADER

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

    /**
     * Encode un message de handshake. null si le message ne tient pas dans une
     * écriture ATT — le handshake ne se fragmente pas, et n'en a pas besoin.
     */
    fun encodeHandshake(step: Int, message: ByteArray, maxBytes: Int): ByteArray? {
        require(step in 1..HANDSHAKE_STEPS) { "étape de handshake hors bornes : $step" }
        if (HANDSHAKE_HEADER + message.size > maxBytes) return null
        return ByteBuffer.allocate(HANDSHAKE_HEADER + message.size)
            .put(TYPE_HANDSHAKE.toByte())
            .put(step.toByte())
            .put(message)
            .array()
    }

    /**
     * Une trame de handshake ne se scelle jamais : elle établit la session.
     *
     * Le cas qui mord : l'initiateur devient établi **en écrivant** le 3e
     * message XX. Si ce message attend son tour dans la file du lien, il
     * partirait scellé alors que le pair l'attend en clair — le handshake se
     * saborderait au dernier pas.
     */
    fun isHandshake(frame: ByteArray): Boolean =
        frame.isNotEmpty() && frame[0].toInt() == TYPE_HANDSHAKE

    /** Enveloppe un chiffré produit par la session Noise du lien. */
    fun encodeSealed(ciphertext: ByteArray): ByteArray =
        ByteArray(1 + ciphertext.size).also {
            it[0] = TYPE_SEALED.toByte()
            ciphertext.copyInto(it, 1)
        }

    fun encodeAck(msgId: Int, status: Int): ByteArray =
        ByteBuffer.allocate(6)
            .put(TYPE_ACK.toByte())
            .putInt(msgId)
            .put(status.toByte())
            .array()

    /** null si la trame est malformée — on ignore, on ne plante pas. */
    fun decode(bytes: ByteArray): ChatFrame? {
        // HELLO et SEALED sont les trames courtes : les autres ont au moins
        // type + id + 1, et leur longueur minimale est vérifiée plus bas
        if (bytes.isNotEmpty() && bytes[0].toInt() == TYPE_HANDSHAKE) {
            if (bytes.size <= HANDSHAKE_HEADER) return null
            val step = bytes[1].toInt() and 0xFF
            if (step !in 1..HANDSHAKE_STEPS) return null
            return ChatFrame.Handshake(step, bytes.copyOfRange(HANDSHAKE_HEADER, bytes.size))
        }
        if (bytes.isNotEmpty() && bytes[0].toInt() == TYPE_SEALED) {
            // un chiffré vaut au moins son MAC, sinon il n'y a rien à ouvrir
            if (bytes.size <= MAC_LENGTH) return null
            return ChatFrame.Sealed(bytes.copyOfRange(1, bytes.size))
        }
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
