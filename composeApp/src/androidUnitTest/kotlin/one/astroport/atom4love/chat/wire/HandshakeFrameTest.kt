package one.astroport.atom4love.chat.wire

import one.astroport.atom4love.noise.NoiseSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** La trame HELLO, et la preuve qu'un handshake XX entier y tient. */
class HandshakeFrameTest {

    private val att = ChatFrames.attPayload(517)

    @Test
    fun `aller-retour d'une trame HELLO`() {
        val message = ByteArray(96) { it.toByte() }
        val encoded = ChatFrames.encodeHandshake(2, message, att)
        assertNotNull(encoded)
        val frame = ChatFrames.decode(encoded!!) as ChatFrame.Handshake
        assertEquals(2, frame.step)
        assertArrayEquals(message, frame.message)
    }

    @Test
    fun `une etape hors bornes est refusee a l'encodage`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatFrames.encodeHandshake(0, ByteArray(32), att)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatFrames.encodeHandshake(4, ByteArray(32), att)
        }
    }

    @Test
    fun `une etape hors bornes est ignoree au decodage`() {
        // trame forgée à la main : personne ne doit planter dessus
        assertNull(ChatFrames.decode(byteArrayOf(0x04, 0x00, 1, 2, 3)))
        assertNull(ChatFrames.decode(byteArrayOf(0x04, 0x07, 1, 2, 3)))
    }

    @Test
    fun `une trame HELLO vide est ignoree`() {
        assertNull(ChatFrames.decode(byteArrayOf(0x04, 0x01)))
        assertNull(ChatFrames.decode(byteArrayOf(0x04)))
    }

    @Test
    fun `un message trop long pour l'ATT est refuse`() {
        assertNull(ChatFrames.encodeHandshake(1, ByteArray(att), att))
        assertNotNull(ChatFrames.encodeHandshake(1, ByteArray(att - ChatFrames.HANDSHAKE_HEADER), att))
    }

    @Test
    fun `les trois messages XX tiennent chacun dans une trame`() {
        val initiator = NoiseSession.initiator(ByteArray(32) { 1 })
        val responder = NoiseSession.responder(ByteArray(32) { 2 })
        val npub = ByteArray(63) { 'n'.code.toByte() }

        val messages = mutableListOf<ByteArray>()
        val first = initiator.writeHandshake()
        messages += first
        responder.readHandshake(first)
        val second = responder.writeHandshake(npub)
        messages += second
        initiator.readHandshake(second)
        val third = initiator.writeHandshake(npub)
        messages += third
        responder.readHandshake(third)

        messages.forEachIndexed { index, message ->
            val encoded = ChatFrames.encodeHandshake(index + 1, message, att)
            assertNotNull("message XX ${index + 1} ne tient pas dans une trame", encoded)
            val frame = ChatFrames.decode(encoded!!) as ChatFrame.Handshake
            assertArrayEquals(message, frame.message)
        }
        // même au MTU plancher, on veut savoir si ça passe encore
        val floor = ChatFrames.attPayload(23)
        assertTrue(
            "le premier message XX dépasse l'ATT plancher",
            messages[0].size + ChatFrames.HANDSHAKE_HEADER > floor,
        )
    }

    @Test
    fun `une trame de handshake se reconnait pour ne jamais etre scellee`() {
        val hello = ChatFrames.encodeHandshake(3, ByteArray(64), att)!!
        assertTrue(ChatFrames.isHandshake(hello))
        // tout le reste doit être scellable
        assertFalse(ChatFrames.isHandshake(ChatFrames.encodeAck(1, 0)))
        assertFalse(ChatFrames.isHandshake(ChatFrames.encodeData(1, 0, ByteArray(8), 0, 8)))
        assertFalse(ChatFrames.isHandshake(ByteArray(0)))
    }

    @Test
    fun `une trame scellee fait l'aller-retour`() {
        val initiator = NoiseSession.initiator(ByteArray(32) { 1 })
        val responder = NoiseSession.responder(ByteArray(32) { 2 })
        responder.readHandshake(initiator.writeHandshake())
        initiator.readHandshake(responder.writeHandshake())
        responder.readHandshake(initiator.writeHandshake())

        val inner = ChatFrames.encodeAck(0x1234, ChatFrames.ACK_OK)
        val sealed = ChatFrames.encodeSealed(initiator.encrypt(inner))
        assertTrue(sealed.size <= att)

        val frame = ChatFrames.decode(sealed) as ChatFrame.Sealed
        val opened = responder.decrypt(frame.ciphertext)
        val ack = ChatFrames.decode(opened) as ChatFrame.Ack
        assertEquals(0x1234, ack.msgId)
        assertEquals(ChatFrames.ACK_OK, ack.status)
    }

    @Test
    fun `le budget d'une trame reserve toujours le scellement`() {
        val att517 = ChatFrames.attPayload(517)
        assertEquals(att517 - ChatFrames.SEAL_OVERHEAD, ChatFrames.framePayload(517))
        // un fragment scellé doit tenir dans l'écriture ATT
        val chunk = ChatFrames.dataChunk(517)
        val data = ChatFrames.encodeData(1, 0, ByteArray(chunk), 0, chunk)
        assertEquals(att517, data.size + ChatFrames.SEAL_OVERHEAD)
    }

    @Test
    fun `un chiffre tronque est ignore plutot que de faire planter`() {
        assertNull(ChatFrames.decode(byteArrayOf(0x05)))
        assertNull(ChatFrames.decode(ByteArray(16) { if (it == 0) 0x05 else 0 }))
        assertNotNull(ChatFrames.decode(ByteArray(18) { if (it == 0) 0x05 else 0 }))
    }

    @Test
    fun `un handshake complet survit a l'aller-retour par les trames`() {
        val initiator = NoiseSession.initiator(ByteArray(32) { 1 })
        val responder = NoiseSession.responder(ByteArray(32) { 2 })

        fun send(from: NoiseSession, to: NoiseSession, step: Int, payload: ByteArray) {
            val encoded = ChatFrames.encodeHandshake(step, from.writeHandshake(payload), att)!!
            val frame = ChatFrames.decode(encoded) as ChatFrame.Handshake
            assertEquals(step, frame.step)
            to.readHandshake(frame.message)
        }

        send(initiator, responder, 1, ByteArray(0))
        send(responder, initiator, 2, "npub-répondeur".toByteArray())
        send(initiator, responder, 3, "npub-initiateur".toByteArray())

        assertTrue(initiator.established)
        assertTrue(responder.established)
        val clair = "premier message chiffré".toByteArray()
        assertArrayEquals(clair, responder.decrypt(initiator.encrypt(clair)))
    }
}
