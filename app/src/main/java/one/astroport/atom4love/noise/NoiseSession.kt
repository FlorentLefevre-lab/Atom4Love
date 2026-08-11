package one.astroport.atom4love.noise

import com.southernstorm.noise.protocol.CipherStatePair
import com.southernstorm.noise.protocol.HandshakeState
import com.southernstorm.noise.protocol.Noise

/**
 * Une session Noise XX entre deux noyaux : trois messages de handshake, puis un
 * canal chiffré dans les deux sens.
 *
 * ```
 * initiateur                          répondeur
 *     │  ── e ──────────────────────────▶  │
 *     │  ◀───────────── e, ee, s, es ───   │
 *     │  ── s, se ─────────────────────▶   │
 *     │      (canal chiffré établi)        │
 * ```
 *
 * XX est le motif retenu parce qu'aucun des deux pairs ne connaît la clé de
 * l'autre à l'avance : chacun transmet sa clé statique **chiffrée** pendant le
 * handshake, ce qu'un observateur des ondes ne peut pas relier à une identité.
 * C'est ce qui distingue ce chat du POC en clair d'aujourd'hui.
 *
 * Cette classe ne connaît rien du transport : elle produit et consomme des
 * tableaux d'octets. Le porteur (GATT aujourd'hui, autre chose demain) les
 * achemine et décide du découpage.
 *
 * **Pas de sûreté vis-à-vis des fils** : une session appartient à un lien, donc
 * au fil protocole unique de ce lien.
 *
 * Le pair n'est **pas authentifié** par cette classe : XX garantit qu'on parle
 * bien au porteur de [remoteStaticKey], pas que cette clé soit celle qu'on
 * croit. Confronter la clé reçue à une identité connue reste à la charge de
 * l'appelant.
 */
class NoiseSession private constructor(
    private val handshake: HandshakeState,
) {

    private var ciphers: CipherStatePair? = null

    /** Vrai dès que le handshake a abouti et que le canal est utilisable. */
    val established: Boolean get() = ciphers != null

    /**
     * Clé statique du pair, disponible une fois le handshake abouti. C'est
     * l'identité de noyau d'en face — à confronter à ce qu'on attend.
     */
    var remoteStaticKey: ByteArray? = null
        private set

    /** Ce que la session attend de l'appelant à cet instant. */
    enum class Step { WRITE, READ, DONE, FAILED }

    val step: Step
        get() = when {
            ciphers != null -> Step.DONE
            else -> when (handshake.action) {
                HandshakeState.WRITE_MESSAGE -> Step.WRITE
                HandshakeState.READ_MESSAGE -> Step.READ
                HandshakeState.SPLIT, HandshakeState.COMPLETE -> Step.DONE
                else -> Step.FAILED
            }
        }

    /**
     * Produit le prochain message de handshake, avec une charge utile
     * facultative — c'est par là que transitera le npub.
     *
     * ⚠ La charge utile du **premier** message XX voyage en clair : elle
     * précède tout échange de clés. Ne rien y mettre qui identifie le noyau.
     */
    fun writeHandshake(payload: ByteArray = ByteArray(0)): ByteArray {
        check(step == Step.WRITE) { "handshake : écriture demandée hors tour ($step)" }
        val buffer = ByteArray(Noise.MAX_PACKET_LEN)
        val length = handshake.writeMessage(buffer, 0, payload, 0, payload.size)
        captureRemoteStatic()
        splitIfComplete()
        return buffer.copyOf(length)
    }

    /** Consomme un message de handshake et rend la charge utile qu'il portait. */
    fun readHandshake(message: ByteArray): ByteArray {
        check(step == Step.READ) { "handshake : lecture demandée hors tour ($step)" }
        val payload = ByteArray(message.size)
        val length = handshake.readMessage(message, 0, message.size, payload, 0)
        // capturée ici, et pas seulement à la scission : l'initiateur reçoit la
        // clé du pair dans le 2e message et doit pouvoir vérifier dans la
        // foulée l'attestation que ce même message transporte
        captureRemoteStatic()
        splitIfComplete()
        return payload.copyOf(length)
    }

    /** Chiffre pour le pair. Le résultat est plus long de 16 octets (le MAC). */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val pair = ciphers ?: error("canal non établi : handshake inachevé")
        val sender = pair.sender
        val buffer = ByteArray(plaintext.size + sender.macLength)
        val length = sender.encryptWithAd(null, plaintext, 0, buffer, 0, plaintext.size)
        return buffer.copyOf(length)
    }

    /**
     * Déchiffre un message du pair.
     *
     * Lève si le MAC ne colle pas : message altéré, hors séquence, ou venu d'un
     * autre que le pair. Un échec n'est pas récupérable — la session est à
     * jeter, pas à réessayer.
     */
    fun decrypt(ciphertext: ByteArray): ByteArray {
        val pair = ciphers ?: error("canal non établi : handshake inachevé")
        val receiver = pair.receiver
        val buffer = ByteArray(ciphertext.size)
        val length = receiver.decryptWithAd(null, ciphertext, 0, buffer, 0, ciphertext.size)
        return buffer.copyOf(length)
    }

    /** Efface les secrets. Une session détruite ne se réutilise pas. */
    fun destroy() {
        runCatching { handshake.destroy() }
        runCatching { ciphers?.destroy() }
        ciphers = null
        remoteStaticKey = null
    }

    /**
     * Le handshake achevé, on récupère la clé du pair et on scinde l'état en
     * deux chiffreurs — un par sens.
     */
    private fun splitIfComplete() {
        if (ciphers != null || handshake.action != HandshakeState.SPLIT) return
        ciphers = handshake.split()
    }

    /** Retient la clé statique du pair dès que le motif l'a transmise. */
    private fun captureRemoteStatic() {
        if (remoteStaticKey != null || !handshake.hasRemotePublicKey()) return
        remoteStaticKey = handshake.remotePublicKey?.let { remote ->
            ByteArray(remote.publicKeyLength).also { remote.getPublicKey(it, 0) }
        }
    }

    companion object {
        /**
         * Suite retenue : XX sur Curve25519, ChaCha20-Poly1305, SHA-256.
         *
         * ChaChaPoly plutôt qu'AES-GCM parce que noise-java l'implémente en
         * Java pur : pas de dépendance à ce que la plateforme expose, et
         * `minSdk = 26` ne donne accès ni à ChaCha20-Poly1305 (API 28) ni à
         * X25519 (API 33).
         */
        const val PROTOCOL = "Noise_XX_25519_ChaChaPoly_SHA256"

        /** Session côté appelant — le lien client, dont les écritures sont acquittées. */
        fun initiator(staticPrivateKey: ByteArray): NoiseSession =
            create(staticPrivateKey, HandshakeState.INITIATOR)

        /** Session côté appelé. */
        fun responder(staticPrivateKey: ByteArray): NoiseSession =
            create(staticPrivateKey, HandshakeState.RESPONDER)

        private fun create(staticPrivateKey: ByteArray, role: Int): NoiseSession {
            require(staticPrivateKey.size == NoiseIdentity.KEY_LENGTH) {
                "clé statique de ${NoiseIdentity.KEY_LENGTH} octets attendue"
            }
            val handshake = HandshakeState(PROTOCOL, role)
            handshake.localKeyPair.setPrivateKey(staticPrivateKey, 0)
            handshake.start()
            return NoiseSession(handshake)
        }
    }
}
