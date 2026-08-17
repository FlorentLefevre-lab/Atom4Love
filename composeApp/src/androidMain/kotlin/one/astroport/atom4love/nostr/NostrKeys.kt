package one.astroport.atom4love.nostr

import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.LoveKey

/**
 * Paire de clés NOSTR d'un noyau : secp256k1, signatures Schnorr BIP-340.
 *
 * La clé privée reste en mémoire, jamais persistée pour l'instant : elle est
 * re-dérivable à volonté depuis les données d'incarnation (c'est tout le
 * principe de la clé LOVE).
 */
class NostrKeys(val privateKey: ByteArray) {

    init {
        require(privateKey.size == 32 && Secp256k1.secKeyVerify(privateKey)) {
            "clé privée secp256k1 invalide"
        }
    }

    /** Clé publique x-only (32 octets) — l'identité NOSTR au format BIP-340. */
    val publicKey: ByteArray =
        Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privateKey)).copyOfRange(1, 33)

    val publicKeyHex: String get() = Hex.encode(publicKey)
    val npub: String get() = Bech32.encode("npub", publicKey)
    val nsec: String get() = Bech32.encode("nsec", privateKey)

    /** npub tronqué pour l'affichage : `npub1q4v…7f6c`. */
    val npubShort: String get() = "${npub.take(8)}…${npub.takeLast(4)}"

    /** Signature Schnorr d'un id d'événement (32 octets). */
    fun sign(eventId: ByteArray): ByteArray = Secp256k1.signSchnorr(eventId, privateKey, null)
}

/**
 * Forge du noyau **provisoire** — dérivation déterministe de clés NOSTR depuis
 * les cinq données d'incarnation, pour que la station fonctionne seule.
 *
 * ⚠ Ce n'est pas la clé LOVE. La clé LOVE est dérivée par une station
 * Astroport.ONE, à l'activation d'ATOM4LOVE sur un MULTIPASS, et rendue au
 * client (`love_nsec` — voir
 * [one.astroport.atom4love.multipass.MultipassService.activateAtom4Love]).
 * Toute la fabrication d'identité appartient à la station : le client ne
 * redérive rien, c'est le choix d'architecture d'Astroport.ONE, et le client de
 * référence (Ẑelkova) a retiré sa propre dérivation pour cette raison.
 *
 * Cette forge-ci ne sert donc qu'avant l'inscription, et seulement à ce qui
 * reste à portée radio : proximité BLE, chat de cabine, identité Noise. Les
 * clés changent le jour où la station rend les vraies — l'utilisateur doit en
 * être prévenu explicitement avant de forger.
 */
object LoveKeyForge {

    /** Le domaine de l'étirement PBKDF2 — `DOMAIN_SALT` d'`atom4love_publish.py`. */
    private val DOMAIN_SALT = "uplanet-a4l-v1".toByteArray(Charsets.UTF_8)
    private const val PBKDF2_ITERATIONS = 600_000
    private const val STRETCHED_LEN = 32

    /** Les paramètres scrypt de Duniter — `~/.config/keygen/keygen.conf`. */
    private const val SCRYPT_N = 4096
    private const val SCRYPT_R = 16
    private const val SCRYPT_P = 1

    /**
     * La chaîne complète de la station, refaite ici.
     *
     * ```
     * salt_étiré   = b64url( PBKDF2-HMAC-SHA256(SALT,   "uplanet-a4l-v1", 600 000, 32) )
     * pepper_étiré = b64url( PBKDF2-HMAC-SHA256(PEPPER, "uplanet-a4l-v1", 600 000, 32) )
     * graine       = scrypt(pepper_étiré, sel = salt_étiré, N=4096, r=16, p=1, 32)
     * clé privée   = SHA-256(graine)
     * ```
     *
     * Les deux premières lignes viennent d'`atom4love_publish.py`, les deux
     * autres de `tools/keygen` : il écrit les deux chaînes étirées dans un
     * fichier (ligne 1 = identifiant, ligne 2 = mot de passe), les passe à
     * `duniterpy.SigningKey.from_credentials`, puis hache la graine ed25519
     * obtenue pour en tirer un scalaire secp256k1
     * (`nostr_from_ed25519_from_existing_seed`).
     *
     * ⚠ **Une inconnue subsiste** : l'ordre des deux arguments de scrypt.
     * `from_credentials(identifiant, mot_de_passe)` appelle
     * `scrypt(mot_de_passe, sel = identifiant)` — c'est ce qui est appliqué
     * ici, mais faute d'un vrai npub LOVE à confronter, ça n'a pas pu être
     * vérifié contre la station. Dès qu'un compte MULTIPASS rend sa clé, il
     * suffit d'essayer les deux ordres : l'un des deux tombe juste.
     *
     * ⚠ **Lent par construction** — 600 000 tours deux fois, puis 8 Mo de
     * scrypt. Compter quelques secondes sur un mobile : cette fonction ne
     * s'appelle jamais depuis le fil principal, et son résultat se range dans
     * [one.astroport.atom4love.data.LoveKeyStore] plutôt que d'être refait à
     * chaque démarrage.
     */
    fun forge(birth: BirthData): NostrKeys {
        val stretchedSalt = stretch(LoveKey.salt(birth))
        val stretchedPepper = stretch(LoveKey.pepper(birth))
        val seed = Scrypt.generate(
            password = stretchedPepper.toByteArray(Charsets.UTF_8),
            salt = stretchedSalt.toByteArray(Charsets.UTF_8),
            n = SCRYPT_N, r = SCRYPT_R, p = SCRYPT_P, dkLen = STRETCHED_LEN,
        )
        var candidate = sha256(seed)
        // Probabilité ~2^-128 d'un scalaire hors courbe. La station ne prévoit
        // pas ce cas ; on re-hache plutôt que de rendre une clé invalide, et le
        // jour où ça arriverait à quelqu'un, il faudra en parler à Fred.
        while (!Secp256k1.secKeyVerify(candidate)) candidate = sha256(candidate)
        return NostrKeys(candidate)
    }

    /**
     * De quoi reconnaître une fiche sans refaire la dérivation.
     *
     * C'est le SALT et le PEPPER hachés une fois — instantané, là où
     * [forge] prend des secondes. Sert à savoir si une clé rangée correspond
     * encore à la fiche courante ; ne sort jamais de l'appareil, et ne
     * remplace aucune des deux chaînes.
     */
    fun fingerprint(birth: BirthData): String =
        Hex.encode(sha256("${LoveKey.salt(birth)}|${LoveKey.pepper(birth)}".toByteArray()))

    /**
     * L'étirement d'une des deux chaînes : PBKDF2, puis base64url **sans
     * remplissage** — `base64.urlsafe_b64encode(...).rstrip(b"=")` chez elle.
     * C'est cette chaîne-là, texte, qui nourrit scrypt.
     */
    fun stretch(raw: String): String = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(
            Scrypt.pbkdf2(
                raw.toByteArray(Charsets.UTF_8),
                DOMAIN_SALT,
                PBKDF2_ITERATIONS,
                STRETCHED_LEN,
            ),
        )

    internal fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}
