package one.astroport.atom4love.proximity

/**
 * Quand prévenir quelqu'un qu'on veut le reconnaître.
 *
 * ## Le cas, et il est le plus fréquent de tous
 *
 * Le rythme de [Rendezvous] ne bat que si **les deux** ont ouvert leur lanterne
 * sur l'autre. Celui qui cherche peut donc chercher pour rien, sans jamais
 * savoir si l'autre a refusé ou s'il n'a simplement rien ouvert — et le second
 * cas est de loin le plus courant : le téléphone est dans une poche. Demandé
 * par Florent le 20/08 : **si la lanterne d'en face n'est pas ouverte, le lui
 * dire, et l'inviter à ouvrir la sienne.**
 *
 * ## ⚠ Ce que ça change, et il faut l'écrire
 *
 * [PresenceAlert] annonçait une **présence**, jamais une recherche, précisément
 * pour ne pas désigner le chercheur : dans une salle à deux, « quelqu'un vous
 * cherche » le nomme. Cette règle-là a déjà bougé le 19/08, quand la carte du
 * Plateau s'est mise à porter « vous cherche » : **celui qui ouvre sa lanterne
 * se déclare, en connaissance de cause**, et c'est ce qui rend l'aveu
 * acceptable — il est le sien, pas une fuite.
 *
 * Le réveil ci-dessous étend cette déclaration à l'écran éteint. Il en garde
 * les deux garde-fous : il **ne nomme personne** (le Plateau, lui, nomme, parce
 * qu'on y est venu), et il ne se pose jamais sur un téléphone qu'on tient en
 * main.
 *
 * ## Les trois conditions
 *
 * 1. **On ne cherche pas déjà en face.** Si notre propre lanterne est ouverte
 *    sur cette personne, les deux écrans battent déjà : il n'y a rien à dire.
 * 2. **L'écran est éteint ou ailleurs.** Faire sonner un téléphone qu'on tient
 *    en main est une faute — le Plateau montre déjà « vous cherche » sur la
 *    carte, et il le montre mieux.
 * 3. **Un temps de garde par personne.** Une lanterne qu'on ouvre et referme
 *    trois fois dans un bar ne doit pas sonner trois fois.
 */
object SeekAlert {

    /**
     * Dix minutes. Plus court que le quart d'heure de [PresenceAlert] : celui
     * qui cherche est là, maintenant, et l'occasion ne dure pas une soirée.
     */
    const val GUARD_MS = 10 * 60 * 1000L

    fun shouldWake(
        seekingBack: Boolean,
        onScreen: Boolean,
        lastWokeMs: Long,
        nowMs: Long,
    ): Boolean = !seekingBack && !onScreen && nowMs - lastWokeMs >= GUARD_MS
}
