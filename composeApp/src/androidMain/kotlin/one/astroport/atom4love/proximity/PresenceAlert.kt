package one.astroport.atom4love.proximity

/**
 * Quand réveiller quelqu'un dont le téléphone est en poche.
 *
 * Le problème vient du jeu lui-même : le rythme de [Rendezvous] ne bat que si
 * **les deux** ont ouvert leur écran et choisi l'autre. Celui qui cherche peut
 * donc chercher pour rien, sans jamais savoir si l'autre a refusé ou s'il n'a
 * simplement rien ouvert. C'est le cas « l'un des deux a oublié », et il n'a
 * aucune solution de son côté à lui.
 *
 * ⚠ **Ce qu'ON annonce ICI est une PRÉSENCE, jamais une recherche.**
 * « Quelqu'un montre sa carte à portée » serait tout aussi vrai si personne ne
 * cherchait, ne désigne personne, et laisse l'endormi ouvrir le Plateau et
 * choisir librement — y compris quelqu'un d'autre, ou personne.
 *
 * ⚠ La recherche, elle, a fini par se dire — mais **ailleurs et à sa manière** :
 * [SeekAlert], depuis le 20/08. Ce n'est pas un revirement de cette règle-ci,
 * c'est l'autre moitié du cas « l'un des deux a oublié » : celui qui ouvre sa
 * lanterne **se déclare**, en connaissance de cause, comme il le fait déjà sur
 * la carte du Plateau depuis le 19/08. Le réveil de [SeekAlert] ne nomme
 * personne non plus, pour la même raison qu'ici.
 *
 * Deux garde-fous, sans quoi un bar sonnerait toute la soirée :
 *
 * 1. **La transition seule** — on n'annonce qu'un passage de personne à
 *    quelqu'un, jamais chaque arrivée. Une salle qui se remplit ne sonne
 *    qu'une fois.
 * 2. **Un temps de garde** — un voisin qui va et vient au bord de la portée
 *    ferait autrement une dizaine de transitions par heure.
 */
object PresenceAlert {

    /**
     * Un quart d'heure. Assez pour couvrir les allers-retours d'un pair au bord
     * des sept mètres de portée, assez court pour qu'entrer dans un autre lieu
     * une demi-heure plus tard se dise encore.
     */
    const val GUARD_MS = 15 * 60 * 1000L

    /**
     * [before] et [now] comptent les voisins **qui montrent une carte** —
     * signature connue. Un pair qui n'a pas rempli sa fiche est bien là, mais
     * il n'y a rien à jouer avec lui : le réveil serait creux.
     */
    fun shouldAnnounce(
        before: Int,
        now: Int,
        lastAnnouncedMs: Long,
        nowMs: Long,
    ): Boolean = before == 0 && now > 0 && nowMs - lastAnnouncedMs >= GUARD_MS
}
