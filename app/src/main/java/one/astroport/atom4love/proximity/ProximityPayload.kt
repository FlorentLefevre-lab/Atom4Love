package one.astroport.atom4love.proximity

import java.nio.ByteBuffer

/**
 * Format du service data de l'annonce BLE — le remplaçant structuré du « phix2 »
 * du POC ([one.astroport.atom4love.diag.BleConcurrencyProbe]).
 *
 * 9 octets :
 * ```
 * [0]     version du format (1)
 * [1..8]  adresse 4D (index H3 après rotation D2), big-endian ; 0 = cellule inconnue
 * ```
 *
 * Budget : une annonce legacy offre 31 octets utiles ; l'UUID 16 bits et ces
 * 9 octets laissent de la marge pour les évolutions (ton, phase, npub tronqué…),
 * qui passeront par un nouvel octet de version.
 */
object ProximityPayload {

    const val VERSION = 1
    private const val SIZE = 9

    /** Valeur diffusée quand la cellule n'a pas pu être résolue (position indisponible). */
    private const val CELL_UNKNOWN = 0L

    fun encode(cell4d: Long?): ByteArray = ByteBuffer.allocate(SIZE)
        .put(VERSION.toByte())
        .putLong(cell4d ?: CELL_UNKNOWN)
        .array()

    /**
     * null si le payload n'est pas au format attendu — autre app squattant le même
     * UUID, ancienne sonde « phix2 » ou version future : dans tous les cas on ignore.
     */
    fun decode(bytes: ByteArray?): Decoded? {
        if (bytes == null || bytes.size != SIZE) return null
        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.get().toInt() != VERSION) return null
        val cell = buffer.long
        return Decoded(cell4d = cell.takeIf { it != CELL_UNKNOWN })
    }

    data class Decoded(val cell4d: Long?)
}