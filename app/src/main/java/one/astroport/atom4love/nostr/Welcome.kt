package one.astroport.atom4love.nostr

/**
 * Qui reste-t-il à fêter — la décision, séparée du réseau et de l'écran.
 *
 * Tout ce qui suit est du calcul pur sur une liste et un ensemble de clés déjà
 * saluées. C'est voulu : c'est la seule partie de la bienvenue qu'on puisse
 * éprouver sans relais et sans appareil, et c'est aussi celle qui a le plus de
 * façons d'être fausse — fêter deux fois, se fêter soi-même, ou fêter en
 * rafale les deux mille certificats d'une première lecture.
 */
object Welcome {

    /**
     * Les noyaux à saluer, du plus ancien au plus récent.
     *
     * Trois filtres, et chacun répare une faute précise :
     *
     * 1. **Récent** ([Constellation.Atom.isNewcomer]) — sans quoi la toute
     *    première lecture fêterait la constellation entière d'un coup.
     * 2. **Jamais salué** — le 30078 est remplaçable et le relais rejoue son
     *    stock à chaque souscription : sans mémoire, une réactivation ou un
     *    simple redémarrage referaient la fête aux mêmes gens.
     * 3. **Pas nous** — on ne se souhaite pas la bienvenue. Notre propre
     *    certificat est récent le jour où on l'active, et c'est justement ce
     *    jour-là qu'on ouvrirait l'application pour regarder.
     *
     * L'ordre est chronologique et non par résonance : c'est une file
     * d'arrivée, pas un classement.
     */
    fun toCelebrate(
        atoms: List<Constellation.Atom>,
        alreadyCelebrated: Set<String>,
        myPubkey: String?,
        nowMs: Long,
    ): List<Constellation.Atom> = atoms
        .filter { it.isNewcomer(nowMs) }
        .filter { it.pubkey != myPubkey }
        .filter { it.pubkey !in alreadyCelebrated }
        .sortedBy { it.createdAt }

    /**
     * Les clés à retenir comme saluées, une fois la fête faite — les
     * anciennes purgées.
     *
     * ⚠ **La mémoire se purge, sinon elle grossit sans fin.** Une clé n'a
     * besoin d'être retenue que tant que son certificat pourrait encore passer
     * pour neuf : au-delà de la fenêtre, le filtre « récent » suffit à lui seul
     * et se souvenir d'elle ne sert plus à rien. La borne est donc naturelle,
     * pas arbitraire.
     *
     * Chaque entrée porte sa date pour que la purge soit possible :
     * `<clé publique>:<createdAt>`. Sans elle, il n'y aurait aucun moyen de
     * savoir laquelle a expiré.
     */
    fun remember(
        previous: Set<String>,
        justCelebrated: List<Constellation.Atom>,
        nowMs: Long,
    ): Set<String> {
        val kept = previous.filter { entry ->
            val at = entry.substringAfterLast(':', "").toLongOrNull() ?: return@filter false
            nowMs - at * 1000L < Constellation.NEWCOMER_WINDOW_MS
        }
        return (kept + justCelebrated.map { "${it.pubkey}:${it.createdAt}" }).toSet()
    }

    /** Les clés publiques d'une mémoire, sans les dates — ce que lit [toCelebrate]. */
    fun keysOf(memory: Set<String>): Set<String> =
        memory.mapTo(mutableSetOf()) { it.substringBeforeLast(':') }
}
