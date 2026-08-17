package one.astroport.atom4love.noise

import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.nostr.LoveKeyForge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * L'attestation qui lie npub et clé Noise — et surtout, ce qu'elle refuse.
 */
class NoiseVouchTest {

    private val mine = LoveKeyForge.forge(BirthData.Sample)
    private val theirs = LoveKeyForge.forge(BirthData.Sample.copy(day = 18))

    private val myNoise = NoiseIdentity.staticPrivateKey(mine)
    private val myNoisePublic = NoiseIdentity.staticPublicKey(myNoise)
    private val theirNoisePublic =
        NoiseIdentity.staticPublicKey(NoiseIdentity.staticPrivateKey(theirs))

    @Test
    fun `une attestation valide rend le npub atteste`() {
        val vouch = NoiseVouch.sign(mine, myNoisePublic)
        assertEquals(NoiseVouch.LENGTH, vouch.size)
        assertArrayEquals(mine.publicKey, NoiseVouch.verify(vouch, myNoisePublic))
    }

    @Test
    fun `une attestation ne vaut pas pour une autre cle Noise`() {
        // le rejeu : reprendre l'attestation d'autrui sur son propre handshake
        val stolen = NoiseVouch.sign(mine, myNoisePublic)
        assertNull(NoiseVouch.verify(stolen, theirNoisePublic))
    }

    @Test
    fun `une signature alteree est refusee`() {
        val vouch = NoiseVouch.sign(mine, myNoisePublic)
        vouch[NoiseVouch.LENGTH - 1] = (vouch[NoiseVouch.LENGTH - 1] + 1).toByte()
        assertNull(NoiseVouch.verify(vouch, myNoisePublic))
    }

    @Test
    fun `un npub substitue est refuse`() {
        val vouch = NoiseVouch.sign(mine, myNoisePublic)
        // on remplace la clé publique en gardant la signature
        theirs.publicKey.copyInto(vouch, 0)
        assertNull(NoiseVouch.verify(vouch, myNoisePublic))
    }

    @Test
    fun `une attestation malformee est refusee sans exception`() {
        assertNull(NoiseVouch.verify(ByteArray(0), myNoisePublic))
        assertNull(NoiseVouch.verify(ByteArray(NoiseVouch.LENGTH - 1), myNoisePublic))
        assertNull(NoiseVouch.verify(ByteArray(NoiseVouch.LENGTH + 1), myNoisePublic))
        assertNull(NoiseVouch.verify(ByteArray(NoiseVouch.LENGTH), myNoisePublic))
        // clé statique de mauvaise taille : rien à vérifier contre
        assertNull(NoiseVouch.verify(NoiseVouch.sign(mine, myNoisePublic), ByteArray(16)))
    }

    @Test
    fun `signer exige une cle statique de la bonne taille`() {
        assertThrows(IllegalArgumentException::class.java) {
            NoiseVouch.sign(mine, ByteArray(31))
        }
    }

    @Test
    fun `l'attestation traverse un handshake XX complet`() {
        val theirNoise = NoiseIdentity.staticPrivateKey(theirs)
        val initiator = NoiseSession.initiator(myNoise)
        val responder = NoiseSession.responder(theirNoise)

        responder.readHandshake(initiator.writeHandshake())
        // 2e message : charge utile déjà chiffrée, le répondeur s'atteste
        val fromResponder = initiator.readHandshake(
            responder.writeHandshake(NoiseVouch.sign(theirs, theirNoisePublic)),
        )
        // 3e message : l'initiateur s'atteste à son tour
        val fromInitiator = responder.readHandshake(
            initiator.writeHandshake(NoiseVouch.sign(mine, myNoisePublic)),
        )

        // chacun vérifie l'attestation contre la clé tirée de SON handshake
        assertNotNull(initiator.remoteStaticKey)
        assertArrayEquals(
            theirs.publicKey,
            NoiseVouch.verify(fromResponder, initiator.remoteStaticKey!!),
        )
        assertArrayEquals(
            mine.publicKey,
            NoiseVouch.verify(fromInitiator, responder.remoteStaticKey!!),
        )
    }

    @Test
    fun `l'attestation tient dans la charge utile d'une trame de handshake`() {
        val initiator = NoiseSession.initiator(myNoise)
        val responder = NoiseSession.responder(NoiseIdentity.staticPrivateKey(theirs))
        responder.readHandshake(initiator.writeHandshake())
        val second = responder.writeHandshake(NoiseVouch.sign(theirs, theirNoisePublic))
        val att = one.astroport.atom4love.chat.wire.ChatFrames.attPayload(517)
        assertNotNull(
            one.astroport.atom4love.chat.wire.ChatFrames.encodeHandshake(2, second, att),
        )
    }
}
