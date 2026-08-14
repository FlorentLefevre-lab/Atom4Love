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
 *   SEALED [0x05][chiffré…]
 *   ADDR   [0x06][médium 1][port 2][lgHôte 1][hôte…]
 *   GROUP  [0x07][port 2][lgNom 1][nom…][lgPasse 1][passe…]
 *   CANCEL [0x08][id 4]
 *   BYE    [0x09]
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

    /** L'onde biologique du pair, en hertz. Voir [ChatFrames.encodeResonance]. */
    data class Resonance(val omegaBio: Float) : ChatFrame

    /**
     * L'émetteur renonce à un transfert en cours.
     *
     * Ce n'est pas un ACK et ça ne pouvait pas l'être : l'accusé remonte du
     * récepteur vers l'émetteur (« je l'ai eu », « je le refuse »), l'annulation
     * fait le chemin inverse. Les confondre aurait fait passer une annulation
     * pour un verdict de remise, et relevé un message que personne ne recevra.
     *
     * Sans elle, un envoi abandonné laissait le pair attendre en silence
     * jusqu'à l'élagage — trente secondes, avec un fichier à moitié écrit.
     */
    data class Cancel(val msgId: Int) : ChatFrame

    /**
     * « Je m'en vais » — un octet, avant de fermer la cabine.
     *
     * Ça ne peut pas se déduire de la radio : celui qui tient une connexion
     * BLE, c'est la **centrale**, et un périphérique ne peut pas la congédier
     * — `cancelConnection` ne vaut que pour les liens qu'il a lui-même
     * composés (mesuré au banc le 13/08). Sans ce mot, le pair nous comptait
     * « ici » pendant tout le délai de supervision, une quinzaine de secondes,
     * et sa cabine proposait de nous rejoindre.
     */
    data object Bye : ChatFrame

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

    /**
     * Par où ce pair est joignable sur un autre médium.
     *
     * Ne circule **que scellée**, sur un canal dont le pair est déjà attesté :
     * c'est ce qui remplace toute découverte réseau. Personne ne peut énumérer
     * les cabines d'un LAN, et l'adresse n'est révélée qu'à quelqu'un qu'on a
     * déjà reconnu. [mediumOrdinal] est laissé brut : une valeur inconnue vient
     * d'une version plus récente et s'ignore sans casser la session.
     */
    data class Address(
        val mediumOrdinal: Int,
        val host: String,
        val port: Int,
    ) : ChatFrame

    /**
     * Un groupe Wi-Fi Direct ouvert par ce pair, et de quoi le rejoindre.
     *
     * Le Direct ne s'annonce pas par une adresse : il faut d'abord entrer dans
     * le groupe, et son propriétaire est toujours en 192.168.49.1. D'où une
     * trame à part, qui porte les identifiants — **jamais en clair, jamais à
     * un pair non attesté** : donner de quoi rejoindre son groupe, c'est donner
     * la clé d'un réseau.
     */
    data class Group(
        val networkName: String,
        val passphrase: String,
        val port: Int,
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
    private const val TYPE_ADDRESS = 0x06
    private const val TYPE_GROUP = 0x07
    private const val TYPE_CANCEL = 0x08
    private const val TYPE_BYE = 0x09
    private const val TYPE_RESONANCE = 0x0A
    private const val RESONANCE_LEN = 5

    /** [type][médium][port 2][longueur de l'hôte]. */
    private const val ADDRESS_FIXED = 5

    /** [type][port 2][longueur du nom] — la longueur de la passe suit le nom. */
    private const val GROUP_FIXED = 4

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

    /**
     * Ce qu'une trame peut occuper sur un transport en flux (TCP), scellement
     * compris. Rien n'oblige à fragmenter sur une socket — mais garder le même
     * cadrage partout fait que réassemblage, CRC, progression et accusés
     * servent à l'identique sur les trois médiums. 32 Ko tient largement sous
     * le plafond d'un message Noise (65 535 o) et fait 65 fragments pour une
     * image de 2 Mo, contre 4 000 en BLE.
     */
    const val STREAM_CAPACITY = 32 * 1024

    /** Encode l'annonce d'un point d'entrée sur un autre médium. */
    fun encodeAddress(mediumOrdinal: Int, host: String, port: Int): ByteArray {
        val bytes = fitUtf8(host, 255)
        return ByteBuffer.allocate(ADDRESS_FIXED + bytes.size)
            .put(TYPE_ADDRESS.toByte())
            .put(mediumOrdinal.toByte())
            .putShort(port.toShort())
            .put(bytes.size.toByte())
            .put(bytes)
            .array()
    }

    /** Encode l'invitation à rejoindre un groupe Wi-Fi Direct. */
    fun encodeGroup(networkName: String, passphrase: String, port: Int): ByteArray {
        val name = fitUtf8(networkName, 255)
        val pass = fitUtf8(passphrase, 255)
        return ByteBuffer.allocate(GROUP_FIXED + 1 + name.size + pass.size)
            .put(TYPE_GROUP.toByte())
            .putShort(port.toShort())
            .put(name.size.toByte())
            .put(name)
            .put(pass.size.toByte())
            .put(pass)
            .array()
    }

    /** Un seul octet : la plus courte trame du protocole. */
    fun encodeBye(): ByteArray = byteArrayOf(TYPE_BYE.toByte())

    /**
     * L'onde biologique de celui qui parle — cinq octets, une seule fois par
     * lien, scellée comme le reste.
     *
     * Elle ne dit ni la taille ni le poids : ω_bio les mélange dans une somme
     * dont on ne les ressort pas (deux inconnues, une équation). C'est ce qui
     * la rend échangeable là où les mesures ne le sont pas — Fred, le
     * 2026-08-14 : ces données ne se divulguent qu'entre gens qui se suivent.
     * Ici, il a fallu ouvrir une cabine et mener un handshake attesté.
     */
    fun encodeResonance(omegaBio: Float): ByteArray =
        ByteBuffer.allocate(RESONANCE_LEN)
            .put(TYPE_RESONANCE.toByte())
            .putFloat(omegaBio)
            .array()

    /** [type][id] — cinq octets, la plus courte trame à identifiant. */
    fun encodeCancel(msgId: Int): ByteArray =
        ByteBuffer.allocate(5)
            .put(TYPE_CANCEL.toByte())
            .putInt(msgId)
            .array()

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
        // ADDR est court aussi : une adresse tient en 14 octets, sous le
        // plancher des trames à identifiant vérifié juste après
        if (bytes.isNotEmpty() && bytes[0].toInt() == TYPE_ADDRESS) {
            if (bytes.size < ADDRESS_FIXED) return null
            val buffer = ByteBuffer.wrap(bytes)
            buffer.get()
            val medium = buffer.get().toInt() and 0xFF
            val port = buffer.short.toInt() and 0xFFFF
            val host = lengthPrefixed(buffer) ?: return null
            if (host.isEmpty() || port == 0) return null
            return ChatFrame.Address(medium, host, port)
        }
        if (bytes.isNotEmpty() && bytes[0].toInt() == TYPE_GROUP) {
            if (bytes.size < GROUP_FIXED + 1) return null
            val buffer = ByteBuffer.wrap(bytes)
            buffer.get()
            val port = buffer.short.toInt() and 0xFFFF
            val name = lengthPrefixed(buffer) ?: return null
            val passphrase = lengthPrefixed(buffer) ?: return null
            if (name.isEmpty() || passphrase.isEmpty() || port == 0) return null
            return ChatFrame.Group(name, passphrase, port)
        }
        if (bytes.size == 1 && bytes[0].toInt() == TYPE_BYE) return ChatFrame.Bye
        // La taille d'abord : `decode` reçoit aussi des trames vides, et lire
        // l'octet de tête avant de l'avoir vérifié coûte une exception.
        if (bytes.size >= RESONANCE_LEN && bytes[0].toInt() == TYPE_RESONANCE) {
            val omega = ByteBuffer.wrap(bytes, 1, 4).float
            // Une onde biologique vaut quelques centaines de hertz ; ni un NaN
            // ni un négatif n'en est une, et les deux se propageraient jusqu'au
            // synthétiseur.
            if (!omega.isFinite() || omega <= 0f) return null
            return ChatFrame.Resonance(omega)
        }
        // CANCEL ne porte qu'un identifiant : cinq octets, sous le plancher
        // des trames vérifié juste après
        if (bytes.isNotEmpty() && bytes[0].toInt() == TYPE_CANCEL) {
            if (bytes.size < 5) return null
            val buffer = ByteBuffer.wrap(bytes)
            buffer.get()
            return ChatFrame.Cancel(buffer.int)
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
