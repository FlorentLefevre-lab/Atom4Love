package one.astroport.atom4love.chat.wire

import java.io.File
import java.io.OutputStream

/**
 * Ce qu'un flux reçu a fini par former.
 *
 * Deux formes, parce que deux besoins : le texte veut ses octets sous la main
 * pour devenir une phrase, une pièce jointe veut un fichier qu'on ouvre. Faire
 * passer une vidéo par la mémoire pour la réécrire ensuite sur le disque
 * n'aurait servi qu'à la refuser.
 */
sealed interface Payload {
    /** Tenu en mémoire — le texte, borné par `MAX_TEXT_BYTES`. */
    class InMemory(val bytes: ByteArray) : Payload

    /** Déjà posé sur le disque, jamais passé entier par la mémoire. */
    class OnDisk(val file: File, val size: Int) : Payload
}

/**
 * Où atterrissent les octets d'un flux en cours de réassemblage.
 *
 * Le réassembleur ne sait rien de plus : c'est ce qui lui permet de rester du
 * JVM pur, testable hors appareil, alors même que la cabine y branche des
 * fichiers. Le CRC n'est pas ici — il appartient au protocole, pas au rangement.
 */
interface Sink {
    /** Écrit les [length] premiers octets de [chunk]. false si l'écriture a échoué. */
    fun write(chunk: ByteArray, length: Int): Boolean

    /** Clôt et rend ce qui a été reçu ; null si la clôture échoue. */
    fun finish(): Payload?

    /**
     * Abandon. **Ne doit rien laisser derrière** : un flux abandonné en route
     * — fragment hors séquence, CRC faux, pair parti — laisserait sinon un
     * fichier à moitié écrit que plus personne ne réclame.
     */
    fun abort()
}

/** Destination en mémoire : le texte, et tout ce que les tests réassemblent. */
class MemorySink(private val total: Int) : Sink {

    private val buffer = ByteArray(total)
    private var offset = 0

    override fun write(chunk: ByteArray, length: Int): Boolean {
        if (offset + length > total) return false
        chunk.copyInto(buffer, offset, 0, length)
        offset += length
        return true
    }

    override fun finish(): Payload = Payload.InMemory(buffer)

    override fun abort() = Unit
}

/**
 * Destination sur disque, pour tout ce qui n'a pas à traverser la mémoire.
 *
 * Le fichier s'écrit sous un nom **provisoire** et n'est nommé qu'à la
 * clôture : tant qu'il porte le suffixe, personne ne peut le prendre pour une
 * pièce reçue, et un abandon n'a qu'à le supprimer.
 */
class FileSink(private val destination: File) : Sink {

    private val partial = File(destination.parentFile, destination.name + PARTIAL_SUFFIX)
    private var out: OutputStream? = null
    private var failed = false

    private fun stream(): OutputStream? {
        if (failed) return null
        out?.let { return it }
        return runCatching {
            partial.parentFile?.mkdirs()
            partial.outputStream().buffered().also { out = it }
        }.getOrNull().also { if (it == null) failed = true }
    }

    override fun write(chunk: ByteArray, length: Int): Boolean {
        val stream = stream() ?: return false
        return runCatching { stream.write(chunk, 0, length) }
            .onFailure { failed = true }
            .isSuccess
    }

    override fun finish(): Payload? {
        val stream = stream() ?: return null
        val ok = runCatching { stream.flush(); stream.close() }.isSuccess
        out = null
        if (!ok || !runCatching { partial.renameTo(destination) }.getOrDefault(false)) {
            partial.delete()
            return null
        }
        return Payload.OnDisk(destination, destination.length().toInt())
    }

    override fun abort() {
        runCatching { out?.close() }
        out = null
        failed = true
        partial.delete()
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".partiel"
    }
}
