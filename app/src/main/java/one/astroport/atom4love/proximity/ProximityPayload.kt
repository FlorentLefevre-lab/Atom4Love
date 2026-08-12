package one.astroport.atom4love.proximity

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Format du service data de l'annonce BLE — le remplaçant structuré du « phix2 »
 * du POC ([one.astroport.atom4love.diag.BleConcurrencyProbe]).
 *
 * 13 octets :
 * ```
 * [0]      version du format (2)
 * [1..8]   adresse 4D (index H3 après rotation D2), big-endian ; 0 = cellule inconnue
 * [9..12]  jeton de présence — voir [token] ; 0 = noyau inconnu
 * ```
 *
 * La version 1 (9 octets, sans jeton) reste **lue** : un appareil qui n'a pas
 * encore le nouveau format continue d'être vu comme voisin, simplement sans
 * pouvoir être dédoublonné.
 *
 * Budget : une annonce legacy offre 31 octets utiles ; l'UUID 16 bits et ces
 * 13 octets laissent de la marge pour les évolutions (ton, phase…), qui
 * passeront par un nouvel octet de version.
 */
object ProximityPayload {

    const val VERSION = 2
    private const val VERSION_WITHOUT_TOKEN = 1
    private const val SIZE = 13
    private const val SIZE_WITHOUT_TOKEN = 9

    /** Valeur diffusée quand la cellule n'a pas pu être résolue (position indisponible). */
    private const val CELL_UNKNOWN = 0L

    /** Valeur diffusée tant qu'aucun noyau n'est forgé. */
    private const val TOKEN_UNKNOWN = 0

    fun encode(cell4d: Long?, token: Int?): ByteArray = ByteBuffer.allocate(SIZE)
        .put(VERSION.toByte())
        .putLong(cell4d ?: CELL_UNKNOWN)
        .putInt(token ?: TOKEN_UNKNOWN)
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
                )

            bytes.size == SIZE_WITHOUT_TOKEN && buffer.get().toInt() == VERSION_WITHOUT_TOKEN ->
                Decoded(cell4d = buffer.long.takeIf { it != CELL_UNKNOWN }, token = null)

            else -> null
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

    data class Decoded(val cell4d: Long?, val token: Int?)
}
