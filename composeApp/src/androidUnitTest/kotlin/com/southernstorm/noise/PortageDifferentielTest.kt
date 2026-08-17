package com.southernstorm.noise

import com.southernstorm.noise.crypto.Blake2bMessageDigest
import com.southernstorm.noise.crypto.Blake2sMessageDigest
import com.southernstorm.noise.crypto.Curve25519
import com.southernstorm.noise.crypto.Curve448
import com.southernstorm.noise.crypto.MessageDigest
import com.southernstorm.noise.crypto.NewHope
import com.southernstorm.noise.crypto.NewHopeTor
import com.southernstorm.noise.crypto.Poly1305
import com.southernstorm.noise.crypto.SHA256MessageDigest
import com.southernstorm.noise.crypto.SHA512MessageDigest
import com.southernstorm.noise.protocol.HandshakeState
import com.southernstorm.noise.protocol.Noise
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import com.southernstorm.noise.ref.crypto.Blake2bMessageDigest as RefBlake2b
import com.southernstorm.noise.ref.crypto.Blake2sMessageDigest as RefBlake2s
import com.southernstorm.noise.ref.crypto.Curve25519 as RefCurve25519
import com.southernstorm.noise.ref.crypto.Curve448 as RefCurve448
import com.southernstorm.noise.ref.crypto.NewHope as RefNewHope
import com.southernstorm.noise.ref.crypto.NewHopeTor as RefNewHopeTor
import com.southernstorm.noise.ref.crypto.Poly1305 as RefPoly1305
import com.southernstorm.noise.ref.crypto.SHA256MessageDigest as RefSHA256
import com.southernstorm.noise.ref.crypto.SHA512MessageDigest as RefSHA512
import com.southernstorm.noise.ref.protocol.HandshakeState as RefHandshakeState
import com.southernstorm.noise.ref.protocol.Noise as RefNoise

/**
 * Le portage Kotlin de Noise, comparé octet par octet au Java d'origine.
 *
 * ## Pourquoi ce test existe
 *
 * Les 28 tests de `one.astroport.atom4love.noise` sont **comportementaux** :
 * ils vérifient qu'un handshake XX aboutit entre nos deux propres
 * implémentations, qu'un octet altéré est refusé, etc. Un port faux **de la
 * même façon des deux côtés** les passerait tous sans broncher. Ce n'est pas
 * un filet suffisant pour de la cryptographie.
 *
 * Ici l'oracle est extérieur : `com.southernstorm.noise.ref` est la copie
 * **Java intacte** de l'amont (commit `49377b6`), au paquet près. Elle ne vit
 * que dans les tests, ne part dans aucun APK, et ne sert qu'à répondre à une
 * seule question — le Kotlin produit-il exactement les mêmes octets ?
 *
 * ## Ce qui est comparé
 *
 * Les primitives sur des entrées aléatoires de tailles variées (les tailles
 * limites comptent : blocs pleins, blocs partiels, zéro octet), puis le
 * handshake XX complet **à clés éphémères fixées** — sans quoi rien ne serait
 * reproductible, l'aléa changeant chaque message.
 *
 * ⚠ Le jour où Noise monte dans `commonMain`, ce fichier ne peut pas suivre :
 * il dépend de sources Java. Il restera un test Android, ou disparaîtra avec
 * la copie de référence — mais alors le port n'aura plus d'oracle du tout.
 */
class PortageDifferentielTest {

    private val alea = Random(20260817)

    /** Tailles qui exercent les blocs pleins, partiels, et le cas vide. */
    private val tailles = listOf(0, 1, 15, 16, 17, 31, 32, 33, 63, 64, 65, 127, 128, 129, 200, 1000)

    // ── Les hachages ────────────────────────────────────────────────────────

    private fun comparerHachage(
        nom: String,
        porte: MessageDigest,
        empreinteRef: (ByteArray) -> ByteArray,
    ) {
        for (taille in tailles) {
            val donnees = alea.nextBytes(taille)
            porte.reset()
            porte.update(donnees, 0, donnees.size)
            val obtenu = ByteArray(porte.digestLength)
            porte.digest(obtenu, 0, obtenu.size)
            assertArrayEquals("$nom diverge sur $taille octets", empreinteRef(donnees), obtenu)
        }
    }

    @Test
    fun `SHA-256 porte rend les memes empreintes que le Java d'origine`() {
        comparerHachage("SHA-256", SHA256MessageDigest()) { donnees ->
            val ref = RefSHA256()
            ref.update(donnees, 0, donnees.size)
            val sortie = ByteArray(32)
            ref.digest(sortie, 0, 32)
            sortie
        }
    }

    @Test
    fun `SHA-512 porte rend les memes empreintes que le Java d'origine`() {
        comparerHachage("SHA-512", SHA512MessageDigest()) { donnees ->
            val ref = RefSHA512()
            ref.update(donnees, 0, donnees.size)
            val sortie = ByteArray(64)
            ref.digest(sortie, 0, 64)
            sortie
        }
    }

    @Test
    fun `BLAKE2s porte rend les memes empreintes que le Java d'origine`() {
        comparerHachage("BLAKE2s", Blake2sMessageDigest()) { donnees ->
            val ref = RefBlake2s()
            ref.update(donnees, 0, donnees.size)
            val sortie = ByteArray(32)
            ref.digest(sortie, 0, 32)
            sortie
        }
    }

    @Test
    fun `BLAKE2b porte rend les memes empreintes que le Java d'origine`() {
        comparerHachage("BLAKE2b", Blake2bMessageDigest()) { donnees ->
            val ref = RefBlake2b()
            ref.update(donnees, 0, donnees.size)
            val sortie = ByteArray(64)
            ref.digest(sortie, 0, 64)
            sortie
        }
    }

    @Test
    fun `les hachages absorbent par morceaux comme l'original`() {
        // Alimenter en plusieurs `update` doit donner la même empreinte qu'en
        // un seul : c'est là que se cachent les erreurs de gestion de bloc.
        val donnees = alea.nextBytes(777)
        val porte = SHA256MessageDigest()
        porte.reset()
        var offset = 0
        for (morceau in listOf(1, 63, 64, 65, 200, 384)) {
            porte.update(donnees, offset, morceau)
            offset += morceau
        }
        val obtenu = ByteArray(32)
        porte.digest(obtenu, 0, 32)

        val ref = RefSHA256()
        ref.update(donnees, 0, offset)
        val attendu = ByteArray(32)
        ref.digest(attendu, 0, 32)

        assertArrayEquals(attendu, obtenu)
    }

    // ── Poly1305 ────────────────────────────────────────────────────────────

    @Test
    fun `Poly1305 porte rend les memes jetons que le Java d'origine`() {
        for (taille in tailles) {
            val cle = alea.nextBytes(32)
            val donnees = alea.nextBytes(taille)

            val porte = Poly1305()
            porte.reset(cle, 0)
            porte.update(donnees, 0, donnees.size)
            val obtenu = ByteArray(16)
            porte.finish(obtenu, 0)

            val ref = RefPoly1305()
            ref.reset(cle, 0)
            ref.update(donnees, 0, donnees.size)
            val attendu = ByteArray(16)
            ref.finish(attendu, 0)

            assertArrayEquals("Poly1305 diverge sur $taille octets", attendu, obtenu)
        }
    }

    // ── Curve25519 ──────────────────────────────────────────────────────────

    @Test
    fun `Curve25519 porte derive les memes cles publiques`() {
        repeat(20) {
            val privee = alea.nextBytes(32)
            val obtenu = ByteArray(32)
            val attendu = ByteArray(32)
            Curve25519.eval(obtenu, 0, privee, null)
            RefCurve25519.eval(attendu, 0, privee, null)
            assertArrayEquals(attendu, obtenu)
        }
    }

    @Test
    fun `Curve25519 porte calcule les memes secrets partages`() {
        repeat(20) {
            val a = alea.nextBytes(32)
            val b = alea.nextBytes(32)
            val publiqueB = ByteArray(32)
            RefCurve25519.eval(publiqueB, 0, b, null)

            val obtenu = ByteArray(32)
            val attendu = ByteArray(32)
            Curve25519.eval(obtenu, 0, a, publiqueB)
            RefCurve25519.eval(attendu, 0, a, publiqueB)
            assertArrayEquals(attendu, obtenu)
        }
    }

    // ── Curve448 ────────────────────────────────────────────────────────────

    @Test
    fun `Curve448 porte derive les memes cles publiques`() {
        repeat(10) {
            val privee = alea.nextBytes(56)
            val obtenu = ByteArray(56)
            val attendu = ByteArray(56)
            Curve448.eval(obtenu, 0, privee, null)
            RefCurve448.eval(attendu, 0, privee, null)
            assertArrayEquals(attendu, obtenu)
        }
    }

    @Test
    fun `Curve448 porte calcule les memes secrets partages`() {
        repeat(10) {
            val a = alea.nextBytes(56)
            val b = alea.nextBytes(56)
            val publiqueB = ByteArray(56)
            RefCurve448.eval(publiqueB, 0, b, null)

            val obtenu = ByteArray(56)
            val attendu = ByteArray(56)
            val okPorte = Curve448.eval(obtenu, 0, a, publiqueB)
            val okRef = RefCurve448.eval(attendu, 0, a, publiqueB)
            assertEquals("le drapeau de domaine diverge", okRef, okPorte)
            assertArrayEquals(attendu, obtenu)
        }
    }

    // ── New Hope, post-quantique ────────────────────────────────────────────

    /**
     * New Hope tire de l'aléa à chaque étape ; sans le fixer, rien n'est
     * comparable. `randombytes` est prévue pour être surchargée — c'est ce que
     * l'amont fait lui-même pour ses vecteurs de test.
     */
    private class TorFixe(private val donnees: ByteArray) : NewHopeTor() {
        override fun randombytes(buffer: ByteArray) {
            donnees.copyInto(buffer, 0, 0, buffer.size)
        }
    }

    private class TorFixeRef(private val donnees: ByteArray) : RefNewHopeTor() {
        override fun randombytes(buffer: ByteArray) {
            donnees.copyInto(buffer, 0, 0, buffer.size)
        }
    }

    @Test
    fun `New Hope porte produit la meme cle publique pour Alice`() {
        repeat(3) {
            val graine = alea.nextBytes(128)

            val porte = ByteArray(NewHope.SENDABYTES)
            TorFixe(graine).keygen(porte, 0)

            val ref = ByteArray(RefNewHope.SENDABYTES)
            TorFixeRef(graine).keygen(ref, 0)

            assertArrayEquals("la clé publique d'Alice diverge", ref, porte)
        }
    }

    @Test
    fun `New Hope porte produit le meme echange complet`() {
        // Alice engendre, Bob répond, Alice conclut — les deux
        // implémentations doivent produire les mêmes octets à chaque étape,
        // et les deux secrets partagés doivent être égaux.
        val graineAlice = alea.nextBytes(128)
        val graineBob = alea.nextBytes(128)

        val pubAlicePorte = ByteArray(NewHope.SENDABYTES)
        val alicePorte = TorFixe(graineAlice)
        alicePorte.keygen(pubAlicePorte, 0)

        val pubAliceRef = ByteArray(RefNewHope.SENDABYTES)
        val aliceRef = TorFixeRef(graineAlice)
        aliceRef.keygen(pubAliceRef, 0)
        assertArrayEquals("étape 1", pubAliceRef, pubAlicePorte)

        val pubBobPorte = ByteArray(NewHope.SENDBBYTES)
        val secretBobPorte = ByteArray(NewHope.SHAREDBYTES)
        TorFixe(graineBob).sharedb(secretBobPorte, 0, pubBobPorte, 0, pubAlicePorte, 0)

        val pubBobRef = ByteArray(RefNewHope.SENDBBYTES)
        val secretBobRef = ByteArray(RefNewHope.SHAREDBYTES)
        TorFixeRef(graineBob).sharedb(secretBobRef, 0, pubBobRef, 0, pubAliceRef, 0)
        assertArrayEquals("étape 2 — clé publique de Bob", pubBobRef, pubBobPorte)
        assertArrayEquals("étape 2 — secret de Bob", secretBobRef, secretBobPorte)

        val secretAlicePorte = ByteArray(NewHope.SHAREDBYTES)
        alicePorte.shareda(secretAlicePorte, 0, pubBobPorte, 0)

        val secretAliceRef = ByteArray(RefNewHope.SHAREDBYTES)
        aliceRef.shareda(secretAliceRef, 0, pubBobRef, 0)
        assertArrayEquals("étape 3 — secret d'Alice", secretAliceRef, secretAlicePorte)

        // Et le test qui dit que l'échange a un sens : les deux côtés
        // aboutissent au même secret.
        assertArrayEquals("Alice et Bob n'ont pas le même secret", secretBobPorte, secretAlicePorte)
    }

    // ── Le handshake XX complet ─────────────────────────────────────────────

    /**
     * Les trois messages du handshake XX, octet par octet, port contre
     * original — puis les clés de session qui en sortent.
     *
     * Les clés éphémères sont **fixées** des deux côtés : sans cela chaque
     * exécution tirerait un aléa neuf et rien ne serait comparable. C'est
     * exactement l'usage que l'amont prévoit pour `getFixedEphemeralKey()`.
     */
    @Test
    fun `le handshake XX porte produit exactement les memes octets`() {
        val protocole = "Noise_XX_25519_ChaChaPoly_SHA256"

        val statiqueInit = alea.nextBytes(32)
        val statiqueRep = alea.nextBytes(32)
        val ephemereInit = alea.nextBytes(32)
        val ephemereRep = alea.nextBytes(32)
        val charge1 = alea.nextBytes(12)
        val charge2 = alea.nextBytes(30)
        val charge3 = alea.nextBytes(64)

        val porte = executerXxPorte(
            protocole, statiqueInit, statiqueRep, ephemereInit, ephemereRep,
            charge1, charge2, charge3,
        )
        val ref = executerXxReference(
            protocole, statiqueInit, statiqueRep, ephemereInit, ephemereRep,
            charge1, charge2, charge3,
        )

        assertEquals("nombre de messages", ref.size, porte.size)
        val etiquettes = listOf(
            "message 1 (init -> rep)",
            "message 2 (rep -> init)",
            "message 3 (init -> rep)",
            "hash de handshake",
            "clé d'émission de l'initiateur",
            "clé d'émission du répondeur",
        )
        for (i in ref.indices) {
            assertArrayEquals(etiquettes.getOrElse(i) { "élément $i" }, ref[i], porte[i])
        }
    }

    private fun executerXxPorte(
        protocole: String,
        statiqueInit: ByteArray,
        statiqueRep: ByteArray,
        ephemereInit: ByteArray,
        ephemereRep: ByteArray,
        charge1: ByteArray,
        charge2: ByteArray,
        charge3: ByteArray,
    ): List<ByteArray> {
        val init = HandshakeState(protocole, HandshakeState.INITIATOR)
        val rep = HandshakeState(protocole, HandshakeState.RESPONDER)
        init.localKeyPair!!.setPrivateKey(statiqueInit, 0)
        rep.localKeyPair!!.setPrivateKey(statiqueRep, 0)
        init.getFixedEphemeralKey()!!.setPrivateKey(ephemereInit, 0)
        rep.getFixedEphemeralKey()!!.setPrivateKey(ephemereRep, 0)
        init.start()
        rep.start()

        val sortie = mutableListOf<ByteArray>()
        val tampon = ByteArray(Noise.MAX_PACKET_LEN)
        val recu = ByteArray(Noise.MAX_PACKET_LEN)

        var n = init.writeMessage(tampon, 0, charge1, 0, charge1.size)
        sortie += tampon.copyOf(n)
        rep.readMessage(tampon, 0, n, recu, 0)

        n = rep.writeMessage(tampon, 0, charge2, 0, charge2.size)
        sortie += tampon.copyOf(n)
        init.readMessage(tampon, 0, n, recu, 0)

        n = init.writeMessage(tampon, 0, charge3, 0, charge3.size)
        sortie += tampon.copyOf(n)
        rep.readMessage(tampon, 0, n, recu, 0)

        sortie += init.getHandshakeHash().copyOf()
        val cotesInit = init.split()
        val cotesRep = rep.split()
        // Les clés elles-mêmes ne sortent pas : on compare ce qu'elles
        // produisent, ce qui revient au même et vaut mieux.
        sortie += chiffrerTemoin(cotesInit.sender!!)
        sortie += chiffrerTemoin(cotesRep.sender!!)
        return sortie
    }

    private fun executerXxReference(
        protocole: String,
        statiqueInit: ByteArray,
        statiqueRep: ByteArray,
        ephemereInit: ByteArray,
        ephemereRep: ByteArray,
        charge1: ByteArray,
        charge2: ByteArray,
        charge3: ByteArray,
    ): List<ByteArray> {
        val init = RefHandshakeState(protocole, RefHandshakeState.INITIATOR)
        val rep = RefHandshakeState(protocole, RefHandshakeState.RESPONDER)
        init.localKeyPair.setPrivateKey(statiqueInit, 0)
        rep.localKeyPair.setPrivateKey(statiqueRep, 0)
        init.fixedEphemeralKey.setPrivateKey(ephemereInit, 0)
        rep.fixedEphemeralKey.setPrivateKey(ephemereRep, 0)
        init.start()
        rep.start()

        val sortie = mutableListOf<ByteArray>()
        val tampon = ByteArray(RefNoise.MAX_PACKET_LEN)
        val recu = ByteArray(RefNoise.MAX_PACKET_LEN)

        var n = init.writeMessage(tampon, 0, charge1, 0, charge1.size)
        sortie += tampon.copyOf(n)
        rep.readMessage(tampon, 0, n, recu, 0)

        n = rep.writeMessage(tampon, 0, charge2, 0, charge2.size)
        sortie += tampon.copyOf(n)
        init.readMessage(tampon, 0, n, recu, 0)

        n = init.writeMessage(tampon, 0, charge3, 0, charge3.size)
        sortie += tampon.copyOf(n)
        rep.readMessage(tampon, 0, n, recu, 0)

        sortie += init.handshakeHash.copyOf()
        val cotesInit = init.split()
        val cotesRep = rep.split()
        sortie += chiffrerTemoinRef(cotesInit.sender)
        sortie += chiffrerTemoinRef(cotesRep.sender)
        return sortie
    }

    /** Un clair connu, chiffré : deux clés identiques donnent le même chiffré. */
    private fun chiffrerTemoin(etat: com.southernstorm.noise.protocol.CipherState): ByteArray {
        val clair = ByteArray(48) { it.toByte() }
        val tampon = ByteArray(clair.size + 16)
        val n = etat.encryptWithAd(null, clair, 0, tampon, 0, clair.size)
        return tampon.copyOf(n)
    }

    private fun chiffrerTemoinRef(etat: com.southernstorm.noise.ref.protocol.CipherState): ByteArray {
        val clair = ByteArray(48) { it.toByte() }
        val tampon = ByteArray(clair.size + 16)
        val n = etat.encryptWithAd(null, clair, 0, tampon, 0, clair.size)
        return tampon.copyOf(n)
    }

    // ── Le chiffrement de session ───────────────────────────────────────────

    @Test
    fun `ChaChaPoly porte chiffre exactement comme l'original`() {
        for (taille in tailles) {
            val cle = alea.nextBytes(32)
            val clair = alea.nextBytes(taille)
            val ad = alea.nextBytes(13)

            val porte = Noise.createCipher("ChaChaPoly")
            porte.initializeKey(cle, 0)
            val tamponPorte = ByteArray(taille + 16)
            val nPorte = porte.encryptWithAd(ad, clair, 0, tamponPorte, 0, taille)

            val ref = RefNoise.createCipher("ChaChaPoly")
            ref.initializeKey(cle, 0)
            val tamponRef = ByteArray(taille + 16)
            val nRef = ref.encryptWithAd(ad, clair, 0, tamponRef, 0, taille)

            assertEquals("longueur sur $taille octets", nRef, nPorte)
            assertArrayEquals(
                "ChaChaPoly diverge sur $taille octets",
                tamponRef.copyOf(nRef),
                tamponPorte.copyOf(nPorte),
            )
        }
    }

    // ── AES-GCM, que rien n'appelle mais qui est porté quand même ───────────

    @Test
    fun `AES-GCM porte chiffre exactement comme l'original`() {
        // `setForceFallbacks(true)` des deux côtés : on compare l'AES en Kotlin
        // à l'AES en Java, pas à celui de la plateforme. Sans cela l'original
        // passerait par `javax.crypto` et le test ne dirait rien du portage de
        // RijndaelAES.
        Noise.setForceFallbacks(true)
        RefNoise.setForceFallbacks(true)
        try {
            for (taille in tailles) {
                val cle = alea.nextBytes(32)
                val clair = alea.nextBytes(taille)
                val ad = alea.nextBytes(7)

                val porte = Noise.createCipher("AESGCM")
                porte.initializeKey(cle, 0)
                val tamponPorte = ByteArray(taille + 16)
                val nPorte = porte.encryptWithAd(ad, clair, 0, tamponPorte, 0, taille)

                val ref = RefNoise.createCipher("AESGCM")
                ref.initializeKey(cle, 0)
                val tamponRef = ByteArray(taille + 16)
                val nRef = ref.encryptWithAd(ad, clair, 0, tamponRef, 0, taille)

                assertEquals("longueur sur $taille octets", nRef, nPorte)
                assertArrayEquals(
                    "AES-GCM diverge sur $taille octets",
                    tamponRef.copyOf(nRef),
                    tamponPorte.copyOf(nPorte),
                )
            }
        } finally {
            Noise.setForceFallbacks(false)
            RefNoise.setForceFallbacks(false)
        }
    }

    @Test
    fun `AES-GCM porte dechiffre ce que l'original a chiffre`() {
        // Le sens croisé : ce que le Java scelle, le Kotlin doit l'ouvrir.
        // C'est la vraie question d'interopérabilité, celle qu'un aller-retour
        // dans une seule implémentation ne pose jamais.
        Noise.setForceFallbacks(true)
        RefNoise.setForceFallbacks(true)
        try {
            val cle = alea.nextBytes(32)
            val clair = alea.nextBytes(120)
            val ad = alea.nextBytes(5)

            val ref = RefNoise.createCipher("AESGCM")
            ref.initializeKey(cle, 0)
            val scelle = ByteArray(clair.size + 16)
            val n = ref.encryptWithAd(ad, clair, 0, scelle, 0, clair.size)

            val porte = Noise.createCipher("AESGCM")
            porte.initializeKey(cle, 0)
            val ouvert = ByteArray(n)
            val longueur = porte.decryptWithAd(ad, scelle, 0, ouvert, 0, n)

            assertArrayEquals(clair, ouvert.copyOf(longueur))
        } finally {
            Noise.setForceFallbacks(false)
            RefNoise.setForceFallbacks(false)
        }
    }

    @Test
    fun `ChaChaPoly porte suit le meme compteur sur plusieurs messages`() {
        // Le nonce avance à chaque message : une erreur d'incrément ne se voit
        // qu'à partir du deuxième.
        val cle = alea.nextBytes(32)
        val porte = Noise.createCipher("ChaChaPoly").apply { initializeKey(cle, 0) }
        val ref = RefNoise.createCipher("ChaChaPoly").apply { initializeKey(cle, 0) }
        repeat(50) { tour ->
            val clair = alea.nextBytes(40)
            val tamponPorte = ByteArray(clair.size + 16)
            val tamponRef = ByteArray(clair.size + 16)
            val nPorte = porte.encryptWithAd(null, clair, 0, tamponPorte, 0, clair.size)
            val nRef = ref.encryptWithAd(null, clair, 0, tamponRef, 0, clair.size)
            assertArrayEquals(
                "divergence au message $tour",
                tamponRef.copyOf(nRef),
                tamponPorte.copyOf(nPorte),
            )
        }
    }
}
