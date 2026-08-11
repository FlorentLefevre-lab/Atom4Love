package one.astroport.atom4love.chat.net

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Socket

/**
 * Une socket TCP qui parle en trames, pas en octets.
 *
 * TCP livre un flux : sans marque de bord, deux trames collées à l'émission
 * arriveraient comme une seule et le décodeur lirait de travers. Un préfixe de
 * longueur suffit — et c'est toute la différence avec le BLE, où l'ATT découpe
 * déjà pour nous mais à 512 octets près.
 *
 * Ce que ça fait disparaître par rapport à GATT : la file d'écritures appariée
 * aux callbacks, la fenêtre de contre-pression, les retentatives « pile
 * occupée », les liens fantômes. `write` rend la main quand l'octet est parti
 * ou lève — il n'y a pas de troisième cas.
 */
class FramedSocket(val socket: Socket) {

    init {
        // Sans ça, l'algorithme de Nagle retient la fin de chaque trame — le
        // dernier segment, partiel — jusqu'à l'accusé du pair, lui-même retardé
        // par son delayed-ACK. Mesuré au banc le 2026-08-11 : 290 ms par trame
        // de 32 Ko, soit 112 Ko/s là où la même liaison porte 11,8 Mo/s. On
        // écrit des trames entières et on veut qu'elles partent : Nagle n'a rien
        // à regrouper ici.
        runCatching { socket.tcpNoDelay = true }
    }

    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = DataOutputStream(socket.getOutputStream().buffered())

    /** `ip:port` du pair — l'adresse du lien, comme une adresse radio en BLE. */
    val remote: String =
        "${socket.inetAddress?.hostAddress ?: "?"}:${socket.port}"

    /**
     * Bloquant. Rend la trame suivante, ou null quand le pair a fermé
     * proprement. Lève sur coupure.
     */
    @Throws(IOException::class)
    fun read(): ByteArray? {
        val length = try {
            input.readInt()
        } catch (_: EOFException) {
            return null
        }
        // Une longueur folle vient d'un pair cassé ou hostile : allouer sur sa
        // parole ouvrirait un déni de service à quatre octets.
        if (length <= 0 || length > MAX_FRAME) throw IOException("trame de $length o hors bornes")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return bytes
    }

    /** Bloquant. Lève si le lien est tombé. */
    @Throws(IOException::class)
    fun write(frame: ByteArray) {
        output.writeInt(frame.size)
        output.write(frame)
        output.flush()
    }

    fun close() {
        runCatching { socket.close() }
    }

    companion object {
        /**
         * De quoi porter une trame de [one.astroport.atom4love.chat.wire.ChatFrames.STREAM_CAPACITY]
         * scellée, avec de la marge — jamais de quoi laisser un inconnu réserver
         * de la mémoire à volonté.
         */
        const val MAX_FRAME = 64 * 1024
    }
}
