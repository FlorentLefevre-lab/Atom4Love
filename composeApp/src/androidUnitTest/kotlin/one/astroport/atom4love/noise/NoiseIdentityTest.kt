package one.astroport.atom4love.noise

import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.nostr.LoveKeyForge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * L'identité Noise dérivée de l'incarnation : déterministe comme le npub, mais
 * bien distincte de la clé NOSTR dont elle sort.
 */
class NoiseIdentityTest {

    private val sample = BirthData.Sample
    private val other = BirthData.Sample.copy(day = 18)

    @Test
    fun `la meme incarnation donne toujours la meme cle statique`() {
        val first = NoiseIdentity.staticPrivateKey(LoveKeyForge.forge(sample))
        val second = NoiseIdentity.staticPrivateKey(LoveKeyForge.forge(sample))
        assertArrayEquals(first, second)
        assertEquals(NoiseIdentity.KEY_LENGTH, first.size)
    }

    @Test
    fun `deux incarnations donnent des cles differentes`() {
        val one = NoiseIdentity.staticPrivateKey(LoveKeyForge.forge(sample))
        val two = NoiseIdentity.staticPrivateKey(LoveKeyForge.forge(other))
        assertFalse(one.contentEquals(two))
    }

    @Test
    fun `la cle Noise n'est pas la cle NOSTR`() {
        val keys = LoveKeyForge.forge(sample)
        val noise = NoiseIdentity.staticPrivateKey(keys)
        // séparation de domaine : le secret NOSTR ne doit jamais transparaître
        assertFalse(noise.contentEquals(keys.privateKey))
        assertFalse(noise.contentEquals(keys.publicKey))
        assertFalse(NoiseIdentity.staticPublicKey(noise).contentEquals(keys.publicKey))
    }

    @Test
    fun `la cle publique se deduit de la privee, et de facon stable`() {
        val private = NoiseIdentity.staticPrivateKey(LoveKeyForge.forge(sample))
        val public = NoiseIdentity.staticPublicKey(private)
        assertEquals(NoiseIdentity.KEY_LENGTH, public.size)
        assertArrayEquals(public, NoiseIdentity.staticPublicKey(private))
    }

    @Test
    fun `la cle statique traverse un vrai handshake`() {
        val mine = NoiseIdentity.staticPrivateKey(LoveKeyForge.forge(sample))
        val theirs = NoiseIdentity.staticPrivateKey(LoveKeyForge.forge(other))
        val initiator = NoiseSession.initiator(mine)
        val responder = NoiseSession.responder(theirs)
        responder.readHandshake(initiator.writeHandshake())
        initiator.readHandshake(responder.writeHandshake())
        responder.readHandshake(initiator.writeHandshake())
        // chacun reconnaît l'identité de noyau d'en face
        assertArrayEquals(NoiseIdentity.staticPublicKey(theirs), initiator.remoteStaticKey)
        assertArrayEquals(NoiseIdentity.staticPublicKey(mine), responder.remoteStaticKey)
    }

    @Test
    fun `une cle de mauvaise taille est refusee`() {
        assertThrows(IllegalArgumentException::class.java) { NoiseIdentity.staticPublicKey(ByteArray(16)) }
    }
}
