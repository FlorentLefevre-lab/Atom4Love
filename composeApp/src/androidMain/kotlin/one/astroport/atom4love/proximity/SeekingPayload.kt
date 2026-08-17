package one.astroport.atom4love.proximity

import java.nio.ByteBuffer

/**
 * « Je cherche ces cartes-là » — l'annonce qui fait enfin fonctionner le jeu.
 *
 * ## Pourquoi elle existe
 *
 * Le rythme de [Rendezvous] ne bat que si **les deux** ont ouvert le Plateau et
 * choisi l'autre. Tant que rien ne circule, celui qui cherche ne peut que
 * l'espérer : mesuré sur 400 salles simulées, la première carte d'une main est
 * réciproque deux fois sur trois — c'est beaucoup, ce n'est pas *toujours*. Et
 * quelqu'un qui n'a même pas ouvert le Plateau ne joue jamais.
 *
 * ⚠ **C'est un changement de cahier des charges, demandé et assumé.** Jusqu'ici
 * le consentement tenait au silence : personne ne savait qu'on l'avait choisi.
 * Ici, celui qui cherche **se déclare**. Ce qu'on garde intact, et qui compte
 * autant : le cherché n'est jamais obligé de répondre. Rien ne bat tant qu'il
 * n'a pas touché la carte à son tour. On a déplacé la révélation du côté de
 * **celui qui la décide** — le chercheur choisit de se montrer, le cherché
 * choisit de venir.
 *
 * ## Pourquoi elle ne tient pas dans l'annonce ordinaire
 *
 * [ProximityPayload] occupe **31 octets sur 31** en annonce legacy, et ses
 * tailles sont gelées avec Fred : pas un bit de libre. L'annonce **étendue**
 * du Bluetooth 5 ouvre plusieurs centaines d'octets et tourne **en parallèle**
 * de la legacy, qui reste intacte — cabine-33 continue de lire exactement ce
 * qu'elle lisait. Relevé sur les deux appareils du banc le 16/08 :
 * annonce étendue supportée des deux côtés, **1650 octets** sur le Pixel,
 * **304** sur la tablette. Le plancher est donc dix fois le budget legacy.
 *
 * ## Le format
 *
 * ```
 * [0]      version (1)
 * [1..4]   le jeton de celui qui cherche — son [ProximityPayload.token]
 * [5]      combien de cartes il cherche, N ∈ 1..[MAX_TARGETS]
 * [6..]    N × 4 octets : les jetons cherchés
 * ```
 *
 * ⚠ **Rien de neuf n'est révélé au monde.** Ces jetons sont déjà dans l'air —
 * chacun diffuse le sien en permanence. Ce que cette annonce ajoute, c'est le
 * **lien** entre deux d'entre eux ; et un jeton ne remonte pas à un npub (c'est
 * un SHA-256 tronqué de la clé et de la cellule). Un observateur apprend donc
 * qu'une carte en cherche une autre, jamais qui sont l'une ou l'autre.
 */
object SeekingPayload {

    const val VERSION = 1

    /**
     * Trois, comme la recherche multiple du Plateau : c'est le nombre qui
     * couvre les rangs 0 et 1 chez l'autre, donc l'immense majorité des
     * réciprocités. Au-delà, on déclarerait chercher tout le monde, ce qui ne
     * dit plus rien de personne.
     */
    const val MAX_TARGETS = 3

    private const val HEADER = 6

    fun encode(myToken: Int, targets: List<Int>): ByteArray? {
        val kept = targets.distinct().take(MAX_TARGETS)
        if (kept.isEmpty()) return null
        val buffer = ByteBuffer.allocate(HEADER + kept.size * Int.SIZE_BYTES)
            .put(VERSION.toByte())
            .putInt(myToken)
            .put(kept.size.toByte())
        kept.forEach { buffer.putInt(it) }
        return buffer.array()
    }

    /** null si ce n'est pas une déclaration de recherche lisible. */
    fun decode(bytes: ByteArray?): Seeking? {
        if (bytes == null || bytes.size < HEADER + Int.SIZE_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.get().toInt() != VERSION) return null
        val from = buffer.int
        val count = buffer.get().toInt() and 0xFF
        if (count !in 1..MAX_TARGETS) return null
        if (bytes.size < HEADER + count * Int.SIZE_BYTES) return null
        val targets = (0 until count).map { buffer.int }
        return Seeking(from = from, targets = targets)
    }

    /** Une carte qui en cherche d'autres. */
    data class Seeking(val from: Int, val targets: List<Int>) {
        /** Vrai si c'est **nous** qu'elle cherche. */
        fun seeks(myToken: Int?): Boolean = myToken != null && myToken in targets
    }
}
