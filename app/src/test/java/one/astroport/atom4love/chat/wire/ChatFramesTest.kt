package one.astroport.atom4love.chat.wire

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFramesTest {

    @Test
    fun `aller-retour d'une trame START`() {
        val start = ChatFrame.Start(
            msgId = -123456789,
            kind = ChatFrames.KIND_IMAGE,
            totalBytes = 314_159,
            crc32 = 0x0BADCAFE,
            name = "photo de l'été.jpg",
            mime = "image/jpeg",
        )
        val encoded = ChatFrames.encodeStart(start, maxBytes = 512)!!
        assertEquals(start, ChatFrames.decode(encoded))
    }

    @Test
    fun `aller-retour d'une trame DATA, index u24`() {
        val content = ByteArray(600) { it.toByte() }
        val encoded = ChatFrames.encodeData(42, 70_000, content, 100, 250)
        val decoded = ChatFrames.decode(encoded) as ChatFrame.Data
        assertEquals(42, decoded.msgId)
        assertEquals(70_000, decoded.index)
        assertArrayEquals(content.copyOfRange(100, 250), decoded.chunk)
    }

    @Test
    fun `aller-retour d'une trame ACK`() {
        val encoded = ChatFrames.encodeAck(-7, ChatFrames.ACK_CRC)
        assertEquals(ChatFrame.Ack(-7, ChatFrames.ACK_CRC), ChatFrames.decode(encoded))
    }

    @Test
    fun `aller-retour d'une trame ADDR`() {
        val encoded = ChatFrames.encodeAddress(mediumOrdinal = 1, host = "10.42.0.208", port = 41_237)
        assertEquals(ChatFrame.Address(1, "10.42.0.208", 41_237), ChatFrames.decode(encoded))
    }

    @Test
    fun `ADDR porte les ports hauts sans passer par un entier signé`() {
        // 60000 déborde d'un Short signé : encodé tel quel il reviendrait négatif
        val encoded = ChatFrames.encodeAddress(1, "192.168.49.1", 60_000)
        assertEquals(60_000, (ChatFrames.decode(encoded) as ChatFrame.Address).port)
    }

    @Test
    fun `ADDR sans hôte ou sans port est refusée`() {
        assertNull(ChatFrames.decode(ChatFrames.encodeAddress(1, "", 9_000)))
        assertNull(ChatFrames.decode(ChatFrames.encodeAddress(1, "10.0.0.1", 0)))
    }

    @Test
    fun `START tronque le nom à la frontière UTF-8 pour tenir dans l'ATT`() {
        val start = ChatFrame.Start(1, ChatFrames.KIND_FILE, 10, 0, "é".repeat(300), "application/pdf")
        val maxBytes = 64
        val encoded = ChatFrames.encodeStart(start, maxBytes)!!
        assertTrue(encoded.size <= maxBytes)
        val decoded = ChatFrames.decode(encoded) as ChatFrame.Start
        // aucun caractère de remplacement : jamais coupé au milieu d'un « é »
        assertTrue(decoded.name.all { it == 'é' })
        assertTrue(decoded.name.isNotEmpty())
    }

    @Test
    fun `la troncature n'isole jamais un substitut - pas de '?' fantôme`() {
        // "ab😀" = 6 octets UTF-8 ; budget nom = 5 → l'émoji entier saute
        val start = ChatFrame.Start(1, ChatFrames.KIND_FILE, 10, 0, "ab😀", "")
        val encoded = ChatFrames.encodeStart(start, ChatFrames.START_FIXED + 5)!!
        val decoded = ChatFrames.decode(encoded) as ChatFrame.Start
        assertEquals("ab", decoded.name)
    }

    @Test
    fun `START refuse un budget plus petit que la partie fixe`() {
        val start = ChatFrame.Start(1, ChatFrames.KIND_TEXT, 10, 0, "", "")
        assertNull(ChatFrames.encodeStart(start, ChatFrames.START_FIXED - 1))
        assertEquals(
            ChatFrames.START_FIXED,
            ChatFrames.encodeStart(start, ChatFrames.START_FIXED)!!.size,
        )
    }

    @Test
    fun `budget des fragments selon le MTU, plafond ATT de 512 compris`() {
        // MTU plancher : trop court pour le handshake, donc jamais scellé —
        // rien à réserver, le fragment garde toute la place
        assertEquals(20, ChatFrames.attPayload(23))
        assertFalse(ChatFrames.canSeal(23))
        assertEquals(12, ChatFrames.dataChunk(23))
        // MTU 517 : l'espace MTU−3 (514) dépasse le plafond spec de 512
        assertEquals(512, ChatFrames.attPayload(517))
        assertTrue(ChatFrames.canSeal(517))
        // 512 − 17 de scellement réservé − 8 d'en-tête DATA
        assertEquals(487, ChatFrames.dataChunk(517))
    }

    @Test
    fun `les trames malformées sont ignorées, pas d'exception`() {
        assertNull(ChatFrames.decode(ByteArray(0)))
        assertNull(ChatFrames.decode(byteArrayOf(0x01, 0, 0)))              // START trop courte
        assertNull(ChatFrames.decode(ByteArray(8) { 0x02 }))                // DATA sans fragment
        assertNull(ChatFrames.decode(byteArrayOf(0x7F, 1, 2, 3, 4, 5, 6))) // type inconnu
        // START dont la longueur de nom déborde de la trame
        val lying = ChatFrames.encodeStart(
            ChatFrame.Start(1, 0, 10, 0, "abc", ""),
            maxBytes = 512,
        )!!.copyOf().also { it[14] = 200.toByte() }
        assertNull(ChatFrames.decode(lying))
    }

    @Test
    fun `le CRC32 est stable et sensible au contenu`() {
        val bytes = "atom4love".toByteArray()
        assertEquals(ChatFrames.crc32(bytes), ChatFrames.crc32(bytes.copyOf()))
        val altered = bytes.copyOf().also { it[0] = 'A'.code.toByte() }
        assertTrue(ChatFrames.crc32(bytes) != ChatFrames.crc32(altered))
    }
}
