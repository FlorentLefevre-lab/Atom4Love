package one.astroport.atom4love.noise

import com.southernstorm.noise.protocol.DHState
import com.southernstorm.noise.protocol.Noise
import java.security.MessageDigest
import one.astroport.atom4love.nostr.NostrKeys

/**
 * Identité statique Noise d'un noyau — la clé Curve25519 que le pair vérifie
 * pendant le handshake XX.
 *
 * Elle est **dérivée** de la clé NOSTR, jamais confondue avec elle : Noise
 * travaille sur Curve25519 quand NOSTR est en secp256k1, et mélanger les usages
 * d'un même secret entre deux courbes est une faute de conception. La
 * dérivation est à sens unique et porte une étiquette de domaine explicite,
 * pour qu'aucun autre usage futur du même secret ne puisse produire cette clé.
 *
 * Conséquence de la dérivation : l'identité Noise est **re-dérivable** depuis
 * l'incarnation, comme le npub. Deux appareils du même noyau présentent donc la
 * même clé statique — c'est voulu, c'est ce qui fait de la clé statique une
 * identité de noyau et non d'appareil.
 *
 * ⚠ Cette clé suit la clé NOSTR courante. Tant que le noyau n'est que le
 * provisoire de [one.astroport.atom4love.nostr.LoveKeyForge], elle bougera le
 * jour où la clé LOVE d'une station Astroport.ONE prendra le relais. Sans
 * gravité pour le chat BLE, où rien ne sort de la portée radio.
 */
object NoiseIdentity {

    /**
     * Étiquette de séparation de domaine. La changer change toutes les
     * identités Noise : à ne toucher qu'avec un numéro de version en plus.
     */
    private const val DOMAIN = "atom4love/noise/static/v1"

    /** Longueur d'une clé Curve25519, publique comme privée. */
    const val KEY_LENGTH = 32

    /**
     * Clé statique Curve25519 du noyau, dérivée de sa clé NOSTR.
     *
     * Pas de clamping ici : `Curve25519.eval` s'en charge à chaque
     * multiplication scalaire (bits bas effacés, bit 254 forcé), donc n'importe
     * quels 32 octets forment une clé privée valide.
     */
    fun staticPrivateKey(keys: NostrKeys): ByteArray =
        MessageDigest.getInstance("SHA-256").apply {
            update(DOMAIN.toByteArray(Charsets.UTF_8))
            update(keys.privateKey)
        }.digest()

    /** Clé publique correspondante, celle que le pair verra passer. */
    fun staticPublicKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_LENGTH) { "clé statique de $KEY_LENGTH octets attendue" }
        val dh: DHState = Noise.createDH("25519")
        try {
            dh.setPrivateKey(privateKey, 0)
            return ByteArray(dh.publicKeyLength).also { dh.getPublicKey(it, 0) }
        } finally {
            dh.destroy()
        }
    }
}
