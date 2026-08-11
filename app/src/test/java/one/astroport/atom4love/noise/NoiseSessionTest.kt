package one.astroport.atom4love.noise

import javax.crypto.BadPaddingException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Le handshake XX et le canal chiffré, hors radio. Deux sessions dialoguent en
 * mémoire : tout ce qui casse ici aurait cassé sur les ondes, en bien plus
 * difficile à observer.
 */
class NoiseSessionTest {

    private fun key(seed: Byte) = ByteArray(32) { (seed + it).toByte() }

    /** Déroule les trois messages XX et rend les deux sessions établies. */
    private fun handshake(
        initiatorKey: ByteArray = key(1),
        responderKey: ByteArray = key(64),
    ): Pair<NoiseSession, NoiseSession> {
        val initiator = NoiseSession.initiator(initiatorKey)
        val responder = NoiseSession.responder(responderKey)
        responder.readHandshake(initiator.writeHandshake())   // → e
        initiator.readHandshake(responder.writeHandshake())   // ← e, ee, s, es
        responder.readHandshake(initiator.writeHandshake())   // → s, se
        return initiator to responder
    }

    @Test
    fun `les trois messages XX etablissent le canal des deux cotes`() {
        val (initiator, responder) = handshake()
        assertTrue(initiator.established)
        assertTrue(responder.established)
        assertEquals(NoiseSession.Step.DONE, initiator.step)
        assertEquals(NoiseSession.Step.DONE, responder.step)
    }

    @Test
    fun `chacun repart avec la cle statique de l'autre`() {
        val initiatorKey = key(1)
        val responderKey = key(64)
        val (initiator, responder) = handshake(initiatorKey, responderKey)
        assertArrayEquals(
            NoiseIdentity.staticPublicKey(responderKey),
            initiator.remoteStaticKey,
        )
        assertArrayEquals(
            NoiseIdentity.staticPublicKey(initiatorKey),
            responder.remoteStaticKey,
        )
    }

    @Test
    fun `le canal chiffre dans les deux sens`() {
        val (initiator, responder) = handshake()
        val montant = "bonjour depuis l'initiateur".toByteArray()
        assertArrayEquals(montant, responder.decrypt(initiator.encrypt(montant)))
        val descendant = "et la réponse du répondeur".toByteArray()
        assertArrayEquals(descendant, initiator.decrypt(responder.encrypt(descendant)))
    }

    @Test
    fun `plusieurs messages d'affilee gardent leur ordre`() {
        val (initiator, responder) = handshake()
        val envoyes = (1..50).map { "message $it".toByteArray() }
        val chiffres = envoyes.map { initiator.encrypt(it) }
        chiffres.forEachIndexed { index, chiffre ->
            assertArrayEquals(envoyes[index], responder.decrypt(chiffre))
        }
    }

    @Test
    fun `le chiffre ne ressemble pas au clair et porte le MAC`() {
        val (initiator, _) = handshake()
        val clair = ByteArray(100) { 0 }
        val chiffre = initiator.encrypt(clair)
        // ChaChaPoly ajoute 16 octets d'authentification
        assertEquals(clair.size + 16, chiffre.size)
        assertFalse(chiffre.copyOf(clair.size).contentEquals(clair))
    }

    @Test
    fun `deux sessions successives ne produisent pas le meme chiffre`() {
        val clair = "même texte".toByteArray()
        val premier = handshake().first.encrypt(clair)
        val second = handshake().first.encrypt(clair)
        // clés éphémères différentes : rejouer une capture ne dit rien
        assertFalse(premier.contentEquals(second))
    }

    @Test
    fun `un octet altere fait echouer le dechiffrement`() {
        val (initiator, responder) = handshake()
        val chiffre = initiator.encrypt("charge utile".toByteArray())
        chiffre[chiffre.size / 2] = (chiffre[chiffre.size / 2] + 1).toByte()
        assertThrows(BadPaddingException::class.java) { responder.decrypt(chiffre) }
    }

    @Test
    fun `un message hors sequence est refuse`() {
        val (initiator, responder) = handshake()
        val premier = initiator.encrypt("un".toByteArray())
        val second = initiator.encrypt("deux".toByteArray())
        // le second arrive avant le premier : le compteur ne colle plus
        assertThrows(BadPaddingException::class.java) { responder.decrypt(second) }
    }

    @Test
    fun `un tiers ne dechiffre pas la conversation`() {
        val (initiator, _) = handshake()
        val (_, intrus) = handshake(initiatorKey = key(1), responderKey = key(64))
        assertThrows(BadPaddingException::class.java) {
            intrus.decrypt(initiator.encrypt("secret".toByteArray()))
        }
    }

    @Test
    fun `le handshake transporte une charge utile`() {
        val initiator = NoiseSession.initiator(key(1))
        val responder = NoiseSession.responder(key(64))
        responder.readHandshake(initiator.writeHandshake())
        // second message : déjà chiffré, c'est là que le npub pourra voyager
        val duRepondeur = "npub-du-répondeur".toByteArray()
        assertArrayEquals(
            duRepondeur,
            initiator.readHandshake(responder.writeHandshake(duRepondeur)),
        )
        val deLInitiateur = "npub-de-l-initiateur".toByteArray()
        assertArrayEquals(
            deLInitiateur,
            responder.readHandshake(initiator.writeHandshake(deLInitiateur)),
        )
    }

    @Test
    fun `ecrire hors de son tour est refuse`() {
        val initiator = NoiseSession.initiator(key(1))
        initiator.writeHandshake()
        // à l'initiateur de lire, maintenant
        assertEquals(NoiseSession.Step.READ, initiator.step)
        assertThrows(IllegalStateException::class.java) { initiator.writeHandshake() }
    }

    @Test
    fun `chiffrer avant la fin du handshake est refuse`() {
        val initiator = NoiseSession.initiator(key(1))
        assertThrows(IllegalStateException::class.java) { initiator.encrypt("trop tôt".toByteArray()) }
    }

    @Test
    fun `une cle statique de mauvaise taille est refusee`() {
        assertThrows(IllegalArgumentException::class.java) { NoiseSession.initiator(ByteArray(31)) }
        assertThrows(IllegalArgumentException::class.java) { NoiseSession.responder(ByteArray(0)) }
    }

    @Test
    fun `une session detruite n'est plus utilisable`() {
        val (initiator, _) = handshake()
        assertNotNull(initiator.remoteStaticKey)
        initiator.destroy()
        assertFalse(initiator.established)
        assertThrows(IllegalStateException::class.java) { initiator.encrypt("après".toByteArray()) }
    }
}
