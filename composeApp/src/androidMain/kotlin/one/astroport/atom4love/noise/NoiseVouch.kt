package one.astroport.atom4love.noise

import fr.acinq.secp256k1.Secp256k1
import one.astroport.atom4love.nostr.NostrKeys

/**
 * Attestation liant une identité NOSTR à une identité Noise.
 *
 * Le handshake XX prouve qu'on parle au porteur d'une clé Curve25519, et rien
 * de plus. Annoncer son npub dans la charge utile serait donc une simple
 * **déclaration** : la clé Noise étant dérivée à sens unique de la clé NOSTR,
 * le récepteur ne peut pas recalculer le lien, et n'importe quel pair au
 * handshake valide pourrait revendiquer le npub d'un autre.
 *
 * D'où l'attestation : le noyau **signe sa propre clé statique Noise** avec sa
 * clé NOSTR. Vérifier cette signature contre la clé statique effectivement
 * obtenue du handshake prouve que le porteur du npub a bien adoubé ce canal —
 * on ne peut pas rejouer l'attestation d'autrui sur son propre handshake.
 *
 * Format : `[clé publique NOSTR 32][signature Schnorr 64]`, soit 96 octets, qui
 * tiennent sans peine dans la charge utile chiffrée des 2e et 3e messages XX.
 *
 * ⚠ Ce que ça ne prouve pas : que ce npub soit celui qu'on attendait. Ça lie
 * deux identités entre elles, ça ne les rattache à personne de connu — la
 * confiance au premier contact reste entière.
 */
object NoiseVouch {

    private const val PUBKEY_LENGTH = 32
    private const val SIGNATURE_LENGTH = 64

    const val LENGTH = PUBKEY_LENGTH + SIGNATURE_LENGTH

    /**
     * Atteste que [keys] adoube la clé statique Noise [staticPublicKey].
     *
     * La clé statique fait 32 octets, exactement ce que signe BIP-340 : elle
     * est signée telle quelle, sans hachage intermédiaire.
     */
    fun sign(keys: NostrKeys, staticPublicKey: ByteArray): ByteArray {
        require(staticPublicKey.size == NoiseIdentity.KEY_LENGTH) {
            "clé statique de ${NoiseIdentity.KEY_LENGTH} octets attendue"
        }
        return keys.publicKey + keys.sign(staticPublicKey)
    }

    /**
     * Vérifie une attestation contre la clé statique tirée du handshake.
     *
     * Rend la clé publique NOSTR attestée, ou null si l'attestation est
     * malformée, si la signature ne colle pas, ou si elle vaut pour une autre
     * clé Noise que celle du canal — le cas du rejeu.
     */
    fun verify(payload: ByteArray, remoteStaticKey: ByteArray): ByteArray? {
        if (payload.size != LENGTH) return null
        if (remoteStaticKey.size != NoiseIdentity.KEY_LENGTH) return null
        val pubkey = payload.copyOfRange(0, PUBKEY_LENGTH)
        val signature = payload.copyOfRange(PUBKEY_LENGTH, LENGTH)
        val valid = runCatching {
            Secp256k1.verifySchnorr(signature, remoteStaticKey, pubkey)
        }.getOrDefault(false)
        return if (valid) pubkey else null
    }
}
