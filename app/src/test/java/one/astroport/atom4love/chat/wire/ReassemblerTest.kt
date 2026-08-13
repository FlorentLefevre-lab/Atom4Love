package one.astroport.atom4love.chat.wire

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReassemblerTest {

    private var now = 0L
    private val reassembler = Reassembler(maxBytes = 1000, nowMs = { now })

    private fun start(id: Int, content: ByteArray, kind: Int = ChatFrames.KIND_TEXT) =
        ChatFrame.Start(id, kind, content.size, ChatFrames.crc32(content), "n", "m")

    private fun frags(id: Int, content: ByteArray, chunk: Int): List<ChatFrame.Data> =
        content.toList().chunked(chunk).mapIndexed { index, part ->
            ChatFrame.Data(id, index, part.toByteArray())
        }

    @Test
    fun `flux nominal - ouvert, progression, complet, contenu intact`() {
        val content = ByteArray(30) { it.toByte() }
        val events = mutableListOf<Reassembler.Event?>()
        events += reassembler.onFrame("A", start(1, content))
        frags(1, content, 12).forEach { events += reassembler.onFrame("A", it) }

        assertTrue(events[0] is Reassembler.Event.Started)
        assertEquals(Reassembler.Event.Progress(1, 12, 30), events[1])
        assertEquals(Reassembler.Event.Progress(1, 24, 30), events[2])
        val completed = events[3] as Reassembler.Event.Completed
        assertArrayEquals(content, (completed.payload as Payload.InMemory).bytes)
        assertEquals("A", completed.from)
    }

    @Test
    fun `le flux jumeau du double lien est ignoré en silence`() {
        val content = ByteArray(20) { 7 }
        assertTrue(reassembler.onFrame("A", start(1, content)) is Reassembler.Event.Started)
        // même id arrivant par l'autre connexion : ni événement, ni écrasement
        assertNull(reassembler.onFrame("B", start(1, content)))
        assertNull(reassembler.onFrame("B", ChatFrame.Data(1, 0, ByteArray(5))))
        // le flux élu continue, lui
        assertTrue(reassembler.onFrame("A", frags(1, content, 20)[0]) is Reassembler.Event.Completed)
    }

    @Test
    fun `un id déjà terminé n'est pas rejoué`() {
        val content = ByteArray(10) { 1 }
        reassembler.onFrame("A", start(1, content))
        reassembler.onFrame("A", frags(1, content, 10)[0])
        assertNull(reassembler.onFrame("A", start(1, content)))
        assertNull(reassembler.onFrame("B", start(1, content)))
    }

    @Test
    fun `fragment hors séquence - flux abandonné`() {
        reassembler.onFrame("A", start(1, ByteArray(100)))
        val failed = reassembler.onFrame("A", ChatFrame.Data(1, 3, ByteArray(10)))
        assertTrue(failed is Reassembler.Event.Failed)
        assertNull(reassembler.onFrame("A", ChatFrame.Data(1, 0, ByteArray(10))))
    }

    @Test
    fun `CRC invalide - échec avec acquittement dédié`() {
        val content = ByteArray(10) { 3 }
        val lying = start(1, content).copy(crc32 = 12345)
        reassembler.onFrame("A", lying)
        val failed = reassembler.onFrame("A", frags(1, content, 10)[0]) as Reassembler.Event.Failed
        assertEquals(ChatFrames.ACK_CRC, failed.ackStatus)
    }

    @Test
    fun `taille refusée au-delà du plafond`() {
        val failed = reassembler.onFrame(
            "A",
            ChatFrame.Start(1, ChatFrames.KIND_FILE, 1001, 0, "gros", "application/octet-stream"),
        ) as Reassembler.Event.Failed
        assertEquals(ChatFrames.ACK_ABORT, failed.ackStatus)
        assertNull(reassembler.onFrame("A", ChatFrame.Data(1, 0, ByteArray(10))))
    }

    @Test
    fun `débordement du contenu annoncé - flux abandonné`() {
        reassembler.onFrame("A", start(1, ByteArray(10)))
        val failed = reassembler.onFrame("A", ChatFrame.Data(1, 0, ByteArray(11)))
        assertTrue((failed as Reassembler.Event.Failed).ackStatus == ChatFrames.ACK_ABORT)
    }

    @Test
    fun `un flux muet est élagué après le délai`() {
        reassembler.onFrame("A", start(1, ByteArray(100)))
        now = 29_000
        assertTrue(reassembler.prune().isEmpty())
        now = 31_000
        val pruned = reassembler.prune()
        assertEquals(1, pruned.size)
        assertEquals(1, pruned[0].msgId)
        // le flux n'existe plus, et son id n'est pas rejouable
        assertNull(reassembler.onFrame("A", ChatFrame.Data(1, 0, ByteArray(10))))
        assertNull(reassembler.onFrame("A", start(1, ByteArray(100))))
    }

    @Test
    fun `un START réémis n'écrase pas le flux en cours`() {
        val content = ByteArray(24) { it.toByte() }
        reassembler.onFrame("A", start(1, content))
        reassembler.onFrame("A", frags(1, content, 12)[0])
        // réémission (même source) : ignorée, ni deuxième Started ni remise à zéro
        assertNull(reassembler.onFrame("A", start(1, content)))
        assertTrue(reassembler.onFrame("A", frags(1, content, 12)[1]) is Reassembler.Event.Completed)
    }

    @Test
    fun `le doublon exact du dernier fragment accepté est ignoré`() {
        val content = ByteArray(24) { it.toByte() }
        reassembler.onFrame("A", start(1, content))
        val parts = frags(1, content, 12)
        assertEquals(Reassembler.Event.Progress(1, 12, 24), reassembler.onFrame("A", parts[0]))
        // réémission du fragment 0 (callback tardif côté émetteur) : sans effet
        assertNull(reassembler.onFrame("A", parts[0]))
        assertTrue(reassembler.onFrame("A", parts[1]) is Reassembler.Event.Completed)
    }
}
