package one.astroport.atom4love.chat

import one.astroport.atom4love.data.Pseudo
import one.astroport.atom4love.nostr.Bech32
import one.astroport.atom4love.nostr.Hex

/**
 * Un fil — **tout ce qu'on a échangé avec une personne, et elle seule**.
 *
 * ## Pourquoi c'est une vue et non un magasin
 *
 * La tentation, en passant d'une salle à des conversations, est d'inventer un
 * magasin de conversations : une table, des identifiants, une écriture à chaque
 * message. On ne le fait pas, et ce n'est pas de la paresse.
 *
 * Le moteur tient déjà **une** liste de messages et **une** liste de pairs. Un
 * message porte désormais la clé de son correspondant ([ChatMessage.peer]) : les
 * fils sont donc entièrement contenus dans ce qui existe, et il n'y a qu'à les
 * lire. Un magasin en plus voudrait dire deux sources de vérité à tenir
 * d'accord, et la première divergence — un message envoyé, rangé nulle part —
 * serait une conversation qui manque.
 *
 * ⚠ **Conséquence assumée : les fils ne survivent pas à la fermeture.** C'est la
 * promesse de la cabine, tenue telle quelle : `stop()` vide les messages, et un
 * fil sans message n'existe plus. Rendre les conversations durables est une
 * décision d'un autre ordre — elle demande un magasin, un chiffrement au repos
 * et une phrase honnête à l'écran. Elle n'est pas prise ici.
 */
data class Conversation(
    /** La clé publique NOSTR du correspondant, en hexadécimal — l'identité du fil. */
    val peerHex: String,
    /** La même, en `npub1…` : c'est sous cette forme que le jeu des questions la range. */
    val npub: String,
    /** Le nom qu'il s'est donné, null s'il n'en a pas envoyé. */
    /**
     * Ce que l'écran écrit de cette personne — son pseudo, **suivi de la queue
     * de sa clé si quelqu'un d'autre porte le même** ([Pseudo.labels]). null
     * quand elle ne s'est pas nommée : c'est alors à l'écran de choisir le mot,
     * dans sa langue.
     */
    val name: String?,
    /**
     * Il est joignable en ce moment.
     *
     * ⚠ Un fil **hors de portée reste dans la liste**. Le retirer effacerait ce
     * qui vient d'être dit à quelqu'un qui a seulement franchi une porte — et
     * en salle, un pair disparaît puis revient plusieurs fois par minute. Ce qui
     * change quand il s'éloigne, c'est qu'on ne peut plus écrire, pas qu'on
     * n'a plus rien lu.
     */
    val inRange: Boolean,
    val messages: List<ChatMessage>,
) {
    /** Le dernier mot échangé, quel qu'en soit le sens. */
    val last: ChatMessage? get() = messages.lastOrNull()

    val lastAtMs: Long get() = last?.atMs ?: 0L

    /** Un fil sans un mot : la personne est là, on ne s'est encore rien dit. */
    val empty: Boolean get() = messages.isEmpty()
}

object Conversations {

    /**
     * Range les messages par correspondant, et complète avec ceux qui sont là
     * sans qu'on leur ait rien dit.
     *
     * L'ordre est celui d'une messagerie et il obéit à une seule règle : **ce
     * qui appelle un geste vient en premier**. Donc d'abord ce qui a bougé le
     * plus récemment ; à égalité de silence — c'est-à-dire pour tous les fils
     * vides —, ceux qui sont à portée, parce qu'à eux on peut écrire. Un fil
     * hors de portée et muet est le seul cas où il n'y a vraiment rien à faire,
     * et il descend.
     *
     * ⚠ Les messages **sans correspondant** ([ChatMessage.peer] à null) ne
     * tombent nulle part, et c'est voulu : ils viennent d'un lien que personne
     * n'a signé, donc d'un appareil qu'on ne sait pas nommer. Les ranger dans un
     * fil « inconnu » commun ferait cohabiter deux étrangers dans la même
     * conversation, ce qui est exactement ce qu'on vient de défaire.
     */
    fun of(
        peers: List<ChatEngine.Peer>,
        messages: List<ChatMessage>,
        /**
         * Des gens qu'on garde dans la liste sans qu'ils soient là ni qu'on
         * leur ait rien dit — ceux qu'on a retenus depuis le Monde.
         *
         * ⚠ C'est le seul endroit où une conversation ne naît pas de la radio.
         * Elle naît d'un geste : toucher le 💬 d'une cartouche KIN, à mille
         * kilomètres de la personne. Le fil existe alors, hors de portée, et il
         * s'allumera le jour où elle sera joignable — parce que la radio
         * l'entend, ou parce que le canal par le relais existera.
         *
         * En clés publiques hexadécimales, comme partout ailleurs ici.
         */
        pinned: Set<String> = emptySet(),
        /**
         * Les pseudos déjà entendus, par npub — **ce qu'on sait d'eux ne
         * s'oublie pas quand ils sortent de portée.**
         *
         * ⚠ Sans cette mémoire, quelqu'un avec qui l'on parlait sous le nom de
         * « Tablette » redevenait « sans pseudo » à la seconde où il franchissait
         * une porte : le nom vit sur le lien, et le lien meurt avec la portée.
         * Vu à l'écran le 19/08. Le pseudo n'est pourtant pas une propriété de
         * la radio, c'est une propriété de la personne — le perdre parce que la
         * radio l'a perdue confond les deux.
         */
        remembered: Map<String, String> = emptyMap(),
    ): List<Conversation> {
        val byPeer = messages.filter { it.peer != null }.groupBy { it.peer!! }
        val present = peers.associateBy { Hex.encode(it.nostrKey) }
        val keys = byPeer.keys + present.keys + pinned
        val npubs = keys.associateWith { hex ->
            present[hex]?.npub ?: runCatching {
                Bech32.encode("npub", Hex.decode(hex))
            }.getOrDefault(hex)
        }
        // ⚠ **Les homonymes se séparent ici, sur la liste entière.** Un pseudo ne
        // peut pas savoir tout seul qu'un autre lui ressemble : la question ne se
        // pose qu'au moment où l'on range plusieurs personnes côte à côte, et
        // c'est exactement cet endroit-là.
        val labels = Pseudo.labels(
            keys.associate { hex ->
                npubs.getValue(hex) to (
                    present[hex]?.display
                        ?: byPeer[hex]?.lastOrNull { it.fromAttested }?.from
                        ?: remembered[npubs.getValue(hex)]
                    )
            },
        )
        return keys
            .map { hex ->
                val npub = npubs.getValue(hex)
                Conversation(
                    peerHex = hex,
                    npub = npub,
                    name = labels[npub],
                    inRange = present[hex] != null,
                    messages = byPeer[hex].orEmpty().sortedBy { it.atMs },
                )
            }
            .sortedWith(
                compareByDescending<Conversation> { it.lastAtMs }
                    .thenByDescending { it.inRange },
            )
    }

    /**
     * Ce qui attend d'être lu, message par message.
     *
     * ⚠ **Une date de lecture par personne, et rien de plus.** Marquer chaque
     * message « lu » demanderait de retoucher la liste du moteur à chaque coup
     * d'œil ; retenir *quand* un fil a été regardé suffit, et se range en un
     * nombre par correspondant. Ces marques vivent le temps de la station,
     * exactement comme les fils eux-mêmes — ce qui ne se garde pas n'a pas de
     * non-lus à garder.
     *
     * ⚠ Les messages **sans correspondant** ([ChatMessage.peer] à null) ne
     * comptent pas : ils viennent d'un lien que personne n'a signé, [of] ne
     * leur donne aucun fil, et les compter ferait une pastille qu'aucun écran
     * ne peut faire retomber.
     *
     * @param readAt la dernière fois que chaque fil a été regardé, par clé
     *   publique hexadécimale ; un fil absent n'a jamais été ouvert.
     */
    fun unread(messages: List<ChatMessage>, readAt: Map<String, Long>): List<ChatMessage> =
        messages.filter { message ->
            val peer = message.peer
            !message.mine && peer != null && message.atMs > (readAt[peer] ?: 0L)
        }

    /**
     * Le même compte, **fil par fil**.
     *
     * La pastille de l'onglet disait « 3 » et aucune ligne ne disait lesquelles :
     * il fallait ouvrir les conversations une à une pour retrouver les trois
     * messages. C'est la somme de cette carte qui fait la pastille — une seule
     * lecture des mêmes messages, donc jamais deux comptes qui divergent.
     */
    fun unreadByPeer(messages: List<ChatMessage>, readAt: Map<String, Long>): Map<String, Int> =
        unread(messages, readAt).groupingBy { it.peer!! }.eachCount()
}
