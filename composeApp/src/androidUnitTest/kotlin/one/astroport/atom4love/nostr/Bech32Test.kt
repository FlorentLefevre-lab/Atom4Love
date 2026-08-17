package one.astroport.atom4love.nostr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Vecteurs officiels de NIP-19. */
class Bech32Test {

    private val pubkeyHex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val npub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"

    private val privkeyHex = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"

    @Test
    fun `npub - vecteur NIP-19`() {
        assertEquals(npub, Bech32.encode("npub", Hex.decode(pubkeyHex)))
    }

    @Test
    fun `nsec - vecteur NIP-19`() {
        assertEquals(nsec, Bech32.encode("nsec", Hex.decode(privkeyHex)))
    }

    @Test
    fun `décodage - aller-retour`() {
        val (hrp, data) = Bech32.decode(npub)
        assertEquals("npub", hrp)
        assertArrayEquals(Hex.decode(pubkeyHex), data)
    }

    @Test
    fun `checksum corrompu rejeté`() {
        assertThrows(IllegalArgumentException::class.java) {
            Bech32.decode(npub.dropLast(1) + "q")
        }
    }
}
