package one.astroport.atom4love.chat.wire

import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * D'où un lien tire les octets d'un message sortant.
 *
 * Un même message part vers plusieurs pairs à la fois, chacun sur son propre
 * fil : la source doit donc pouvoir être **ouverte plusieurs fois en
 * parallèle**, chaque lecteur avançant à son rythme. C'est pour ça que la
 * lecture vit dans [Reader] et non dans la source elle-même.
 *
 * La taille est connue d'avance — le protocole l'annonce dans le START avant
 * d'envoyer le premier fragment.
 */
sealed interface Source {

    val size: Int

    fun open(): Reader

    interface Reader : Closeable {
        /** Lit la suite dans [into] ; rend le nombre d'octets, ≤ 0 si fini. */
        fun read(into: ByteArray): Int
    }
}

/** Source en mémoire — le texte, qui ne gagnerait rien à passer par le disque. */
class BytesSource(private val bytes: ByteArray) : Source {

    override val size: Int get() = bytes.size

    override fun open(): Source.Reader = object : Source.Reader {
        private var offset = 0

        override fun read(into: ByteArray): Int {
            if (offset >= bytes.size) return -1
            val n = minOf(into.size, bytes.size - offset)
            bytes.copyInto(into, 0, offset, offset + n)
            offset += n
            return n
        }

        override fun close() = Unit
    }
}

/**
 * Source sur disque — les pièces jointes, y compris celles qui ne tiendraient
 * pas en mémoire. La taille est figée à la construction : le fichier vit dans
 * notre dossier et personne d'autre ne le touche, mais un START qui annoncerait
 * autre chose que ce qu'on envoie ferait échouer le CRC en face.
 */
class FileSource(private val file: File, override val size: Int) : Source {

    override fun open(): Source.Reader = object : Source.Reader {
        private val stream: InputStream = file.inputStream().buffered()

        override fun read(into: ByteArray): Int = stream.read(into)

        override fun close() {
            runCatching { stream.close() }
        }
    }
}
