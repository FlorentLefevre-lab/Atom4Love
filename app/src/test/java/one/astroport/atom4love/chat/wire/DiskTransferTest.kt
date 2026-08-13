package one.astroport.atom4love.chat.wire

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Le transfert qui ne passe plus par la mémoire.
 *
 * Deux exigences se croisent ici : une pièce jointe complète doit atterrir
 * intacte sur le disque, et un flux abandonné **ne doit rien laisser** — un
 * fichier à moitié écrit que plus personne ne réclame vaut une fuite, et
 * c'est précisément ce que la cabine promet de ne pas faire.
 */
class DiskTransferTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var now = 0L
    private lateinit var destination: File

    private fun reassembler(maxBytes: Int = 1000): Reassembler {
        destination = File(temp.newFolder(), "recu.bin")
        return Reassembler(maxBytes, nowMs = { now }, sinkFor = { FileSink(destination) })
    }

    private fun partial() = File(destination.parentFile, destination.name + ".partiel")

    private fun start(id: Int, content: ByteArray) =
        ChatFrame.Start(id, ChatFrames.KIND_FILE, content.size, ChatFrames.crc32(content), "n", "m")

    private fun frags(id: Int, content: ByteArray, chunk: Int): List<ChatFrame.Data> =
        content.toList().chunked(chunk).mapIndexed { index, part ->
            ChatFrame.Data(id, index, part.toByteArray())
        }

    @Test
    fun `une piece complete atterrit intacte, sans partiel derriere`() {
        val r = reassembler()
        val content = ByteArray(300) { (it * 7).toByte() }
        r.onFrame("A", start(1, content))
        var completed: Reassembler.Event.Completed? = null
        frags(1, content, 64).forEach {
            (r.onFrame("A", it) as? Reassembler.Event.Completed)?.let { c -> completed = c }
        }

        val payload = completed!!.payload as Payload.OnDisk
        assertArrayEquals(content, payload.file.readBytes())
        assertEquals(content.size, payload.size)
        assertFalse("le fichier provisoire doit avoir disparu", partial().exists())
    }

    /** Le CRC se calcule au fil de l'eau : plus de contenu à relire à la fin. */
    @Test
    fun `un CRC faux ne laisse aucun fichier`() {
        val r = reassembler()
        val content = ByteArray(100) { 3 }
        r.onFrame("A", start(1, content).copy(crc32 = 12345))
        val failed = frags(1, content, 50)
            .map { r.onFrame("A", it) }
            .last() as Reassembler.Event.Failed

        assertEquals(ChatFrames.ACK_CRC, failed.ackStatus)
        assertFalse(destination.exists())
        assertFalse(partial().exists())
    }

    @Test
    fun `un fragment hors sequence emporte le partiel`() {
        val r = reassembler()
        val content = ByteArray(200) { 1 }
        r.onFrame("A", start(1, content))
        r.onFrame("A", frags(1, content, 50)[0])
        assertTrue("le partiel doit exister à ce stade", partial().exists())

        assertTrue(r.onFrame("A", ChatFrame.Data(1, 5, ByteArray(10))) is Reassembler.Event.Failed)
        assertFalse(partial().exists())
        assertFalse(destination.exists())
    }

    @Test
    fun `un debordement emporte le partiel`() {
        val r = reassembler()
        r.onFrame("A", start(1, ByteArray(10)))
        assertTrue(r.onFrame("A", ChatFrame.Data(1, 0, ByteArray(11))) is Reassembler.Event.Failed)

        assertFalse(partial().exists())
    }

    /** Le pair est parti en plein transfert : rien n'attend la fermeture. */
    @Test
    fun `un flux elague emporte son partiel sur-le-champ`() {
        val r = reassembler()
        val content = ByteArray(200) { 9 }
        r.onFrame("A", start(1, content))
        r.onFrame("A", frags(1, content, 50)[0])
        assertTrue(partial().exists())

        now = 31_000
        assertEquals(1, r.prune().size)
        assertFalse(partial().exists())
    }

    /** La cabine ferme pendant une réception. */
    @Test
    fun `abortAll ne laisse rien des flux en cours`() {
        val r = reassembler()
        val content = ByteArray(200) { 4 }
        r.onFrame("A", start(1, content))
        r.onFrame("A", frags(1, content, 50)[0])

        r.abortAll()

        assertEquals(0, r.activeStreams())
        assertFalse(partial().exists())
        assertFalse(destination.exists())
    }

    /** Une taille refusée n'ouvre rien du tout — pas même un fichier vide. */
    @Test
    fun `une taille refusee n'ouvre aucun fichier`() {
        val r = reassembler(maxBytes = 100)
        r.onFrame("A", ChatFrame.Start(1, ChatFrames.KIND_FILE, 101, 0, "gros", "m"))

        assertFalse(destination.exists())
        assertFalse(partial().exists())
    }

    // ── Les sources d'émission ────────────────────────────────────────────

    @Test
    fun `une source sur fichier se relit en entier, et plusieurs fois de suite`() {
        val file = File(temp.newFolder(), "envoi.bin")
        val content = ByteArray(500) { (it % 251).toByte() }
        file.writeBytes(content)
        val source = FileSource(file, content.size)

        // deux lecteurs en parallèle : un message part vers plusieurs pairs
        assertArrayEquals(content, drain(source))
        assertArrayEquals(content, drain(source))
        assertEquals(content.size, source.size)
    }

    @Test
    fun `une source en memoire rend exactement ses octets`() {
        val content = ByteArray(70) { it.toByte() }

        assertArrayEquals(content, drain(BytesSource(content)))
    }

    @Test
    fun `un lecteur epuise rend une fin de source`() {
        val reader = BytesSource(ByteArray(4)).open()
        assertEquals(4, reader.read(ByteArray(16)))
        assertTrue(reader.read(ByteArray(16)) <= 0)
    }

    // ── La destination, seule ─────────────────────────────────────────────

    @Test
    fun `un FileSink abandonne ne laisse pas de provisoire`() {
        val destination = File(temp.newFolder(), "x.bin")
        val sink = FileSink(destination)
        sink.write(ByteArray(10), 10)

        sink.abort()

        assertFalse(destination.exists())
        assertFalse(File(destination.parentFile, destination.name + ".partiel").exists())
    }

    @Test
    fun `un FileSink refuse d'ecrire apres un abandon`() {
        val sink = FileSink(File(temp.newFolder(), "x.bin"))
        sink.write(ByteArray(4), 4)
        sink.abort()

        assertFalse(sink.write(ByteArray(4), 4))
        assertNull(sink.finish())
    }

    private fun drain(source: Source): ByteArray {
        val out = ArrayList<Byte>()
        val buffer = ByteArray(64)
        source.open().use { reader ->
            while (true) {
                val n = reader.read(buffer)
                if (n <= 0) break
                repeat(n) { out.add(buffer[it]) }
            }
        }
        return out.toByteArray()
    }
}
