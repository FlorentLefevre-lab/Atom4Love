package one.astroport.atom4love.nostr

import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.util.GregorianCalendar
import java.util.TimeZone
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

    fun forge(birth: BirthData): NostrKeys {
        val seed = "${LoveKey.salt(birth)}|${provisionalPepper(birth)}"
        var candidate = sha256(seed.toByteArray(Charsets.UTF_8))
        // Probabilité ~2^-128 d'un scalaire hors courbe : on re-hache jusqu'à validité.
        while (!Secp256k1.secKeyVerify(candidate)) candidate = sha256(candidate)
        return NostrKeys(candidate)
    }

    /**
     * Le PEPPER du noyau provisoire, figé ici et nulle part ailleurs.
     *
     * C'est l'ancienne gestation de l'app — 280 jours corrigés de 4 par 100 g —
     * gardée telle quelle **exprès**. Elle n'a aucune valeur de protocole : la
     * station ne la connaît pas, et la clé LOVE la remplacera. Mais toute
     * retouche ici change le npub de chaque appareil déjà en service, et une
     * cabine ouverte hier ne reconnaîtrait plus personne. On ne fait pas bouger
     * des identités vivantes pour une question d'affichage.
     */
    private fun provisionalPepper(b: BirthData): Long {
        require(b.dateComplete) { "date de naissance manquante" }
        // Heure et poids passent par les valeurs par défaut de la fiche quand
        // ils n'ont pas été saisis : un noyau déjà scellé les portait tous les
        // deux, son npub ne bouge donc pas d'un iota.
        val gestationDays = 280.0 + (b.saltWeightKg - 3.5) * 4.0
        val birth = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(b.year!!, b.month!! - 1, b.day!!, b.saltHour, b.saltMinute)
        }
        return birth.timeInMillis - (gestationDays * 24.0 * 3600.0 * 1000.0).toLong()
    }

    internal fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}
