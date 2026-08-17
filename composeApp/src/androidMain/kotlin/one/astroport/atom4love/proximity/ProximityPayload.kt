package one.astroport.atom4love.proximity

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Format du service data de l'annonce BLE — le remplaçant structuré du « phix2 »
 * du POC ([one.astroport.atom4love.diag.BleConcurrencyProbe]).
 *
 * 17 octets :
 * ```
 * [0]      version du format (3)
 * [1..8]   adresse 4D (index H3 après rotation D2), big-endian ; 0 = cellule inconnue
 * [9..12]  jeton de présence — voir [token] ; 0 = noyau inconnu
 * [13]     sexe biologique 0 ou 1 ; 255 = inconnu
 * [14]     sceau maya 0..19 ; 99 = inconnu (la convention de cabine-33)
 * [15..16] phase personnelle en 65535ᵉ de tour ; 65535 = inconnue
 * ```
 *
 * **Les trois derniers champs sont ceux que Florent a retenus le 2026-08-13** :
 * de quoi calculer une résonance à l'instant où deux appareils se croisent, et
 * rien qui nomme qui que ce soit. Le SSID de cabine-33 y ajoute les huit
 * premiers caractères du npub et l'instrument ; ni l'un ni l'autre ne passent
 * ici — une carte de visite radio n'a pas à porter d'identité.
 *
 * Les versions 2 (13 octets) et 1 (9 octets) restent **lues** : un appareil qui
 * n'a pas encore le nouveau format continue d'être vu comme voisin, simplement
 * sans sa signature.
 *
 * Budget : une annonce legacy offre 31 octets, dont 3 pour les drapeaux et 4
 * d'entête pour ce bloc — il reste donc 24 octets utiles, et nous en tenons 17.
 */
object ProximityPayload {

    const val VERSION = 3
    private const val VERSION_WITHOUT_SIGNATURE = 2
    private const val VERSION_WITHOUT_TOKEN = 1
    private const val SIZE = 17
    private const val SIZE_WITHOUT_SIGNATURE = 13
    private const val SIZE_WITHOUT_TOKEN = 9

    /** Le sexe n'est pas diffusé tant qu'aucune onde n'est choisie. */
    private const val SEX_UNKNOWN = 0xFF

    /** 99 plutôt que −1 : cabine-33 l'a choisi pour ne pas écrire « --1 » dans un SSID. */
    private const val GLYPH_UNKNOWN = 99

    /** Le tour complet en 65535 pas, soit 9,6·10⁻⁵ rad — la précision du « %.4f » du SSID. */
    private const val PHASE_STEPS = 65535
    private const val PHASE_UNKNOWN = 0xFFFF
    private const val TAU = 2.0 * Math.PI

    /** Valeur diffusée quand la cellule n'a pas pu être résolue (position indisponible). */
    private const val CELL_UNKNOWN = 0L

    /** Valeur diffusée tant qu'aucun noyau n'est forgé. */
    private const val TOKEN_UNKNOWN = 0

    fun encode(
        cell4d: Long?,
        token: Int?,
        signature: Signature = Signature.Unknown,
    ): ByteArray = ByteBuffer.allocate(SIZE)
        .put(VERSION.toByte())
        .putLong(cell4d ?: CELL_UNKNOWN)
        .putInt(token ?: TOKEN_UNKNOWN)
        .put((signature.sex?.takeIf { it == 0 || it == 1 } ?: SEX_UNKNOWN).toByte())
        .put((signature.glyph?.takeIf { it in 0..19 } ?: GLYPH_UNKNOWN).toByte())
        .putShort(encodePhase(signature.phase).toShort())
        .array()

    /**
     * null si le payload n'est pas au format attendu — autre app squattant le même
     * UUID, ancienne sonde « phix2 » ou version future : dans tous les cas on ignore.
     */
    fun decode(bytes: ByteArray?): Decoded? {
        if (bytes == null) return null
        val buffer = ByteBuffer.wrap(bytes)
        return when {
            bytes.size == SIZE && buffer.get().toInt() == VERSION ->
                Decoded(
                    cell4d = buffer.long.takeIf { it != CELL_UNKNOWN },
                    token = buffer.int.takeIf { it != TOKEN_UNKNOWN },
                    signature = Signature(
                        sex = (buffer.get().toInt() and 0xFF).takeIf { it == 0 || it == 1 },
                        glyph = (buffer.get().toInt() and 0xFF).takeIf { it in 0..19 },
                        phase = decodePhase(buffer.short.toInt() and 0xFFFF),
                    ),
                )

            bytes.size == SIZE_WITHOUT_SIGNATURE &&
                buffer.get().toInt() == VERSION_WITHOUT_SIGNATURE ->
                Decoded(
                    cell4d = buffer.long.takeIf { it != CELL_UNKNOWN },
                    token = buffer.int.takeIf { it != TOKEN_UNKNOWN },
                )

            bytes.size == SIZE_WITHOUT_TOKEN && buffer.get().toInt() == VERSION_WITHOUT_TOKEN ->
                Decoded(cell4d = buffer.long.takeIf { it != CELL_UNKNOWN }, token = null)

            else -> null
        }
    }

    /**
     * La phase tient sur deux octets : le tour complet en 65535 pas. Le dernier
     * pas est réservé à « inconnue », et une phase qui tomberait pile dessus
     * recule d'un cran plutôt que de se faire passer pour absente — 10⁻⁴ rad de
     * moins, contre un voisin qui disparaîtrait de la résonance.
     */
    internal fun encodePhase(phase: Double?): Int {
        if (phase == null || !phase.isFinite()) return PHASE_UNKNOWN
        val turns = ((phase % TAU) + TAU) % TAU / TAU
        return Math.round(turns * PHASE_STEPS).toInt().coerceIn(0, PHASE_STEPS - 1)
    }

    private fun decodePhase(raw: Int): Double? =
        if (raw == PHASE_UNKNOWN) null else raw.toDouble() / PHASE_STEPS * TAU

    /**
     * Ce qu'un noyau dit de lui sans se nommer : sa polarité, son sceau maya et
     * sa phase personnelle. De quoi calculer une résonance à la volée, rien de
     * quoi remonter à quelqu'un.
     *
     * Chaque champ est indépendamment nullable : le sexe existe dès l'onde
     * choisie, le sceau dès la date, la phase seulement avec le lieu.
     */
    data class Signature(val sex: Int?, val glyph: Int?, val phase: Double?) {
        companion object {
            val Unknown = Signature(null, null, null)
        }
    }

    /**
     * De quoi reconnaître qu'une même personne annonce deux fois, sans dire qui.
     *
     * Le compteur du portail comptait des adresses, et les adresses tournent :
     * un seul appareil s'y présentait sous deux visages (mesuré le 2026-08-12 —
     * deux voisins comptés pour un appareil). Il fallait donc dans l'annonce
     * quelque chose de stable le temps d'un comptage, et de rien d'autre.
     *
     * Ce jeton est **dérivé de la cellule diffusée**, pas seulement du noyau.
     * Aujourd'hui la rotation D2 est l'identité ([CellRotation.None]) et le
     * jeton est donc stable tant qu'on ne change pas de cellule — ce que la
     * portée BLE borne déjà à des gens présents à dix ou cent mètres, le même
     * argument qui fait accepter la cellule en clair. Le jour où D2 tournera
     * pour de bon, ce jeton tournera avec elle, sans rien à redécider.
     *
     * Ce qu'il ne protège pas : quelqu'un qui connaît d'avance un npub et se
     * tient à portée peut vérifier sa présence en recalculant ce hachage. Il
     * ne révèle donc rien à un inconnu, et ne cache rien à qui vous cherche
     * nommément — c'est une question à trancher avec la spéc. D2, pas ici.
     */
    fun token(nostrKey: ByteArray?, cell4d: Long?): Int? {
        if (nostrKey == null || nostrKey.isEmpty() || cell4d == null) return null
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(nostrKey)
            digest(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(cell4d).array())
        }
        val value = ByteBuffer.wrap(digest).int
        // 0 est réservé à « inconnu » : le seul hachage qui tomberait dessus
        // se décalerait d'un, plutôt que de se faire passer pour absent.
        return if (value == TOKEN_UNKNOWN) 1 else value
    }

    data class Decoded(
        val cell4d: Long?,
        val token: Int?,
        val signature: Signature = Signature.Unknown,
    )
}
