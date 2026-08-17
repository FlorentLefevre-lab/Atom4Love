package one.astroport.atom4love.domain

import androidx.annotation.StringRes
import one.astroport.atom4love.R

/**
 * ❓ Les questions — le troisième coup du « Qui est-ce ? ».
 *
 * Les deux premiers coups ne demandaient rien à personne. Le tirage lit ce qui
 * est déjà dans l'air, et la reconnaissance se calcule des deux côtés sans que
 * rien ne circule. Ici, pour la première fois, il faut un canal — et ce n'est
 * pas un choix d'architecture, c'est une impossibilité : φ est public, donc
 * toute clé qu'on en dériverait le serait aussi, et deux personnes qui n'ont
 * échangé aucun secret ne peuvent rien se dire que la salle n'entende. Les
 * questions passent donc par la cabine, chiffrées et attestées, entre deux
 * noyaux qui se sont déjà reconnus.
 *
 * ## La règle : proposer, c'est donner
 *
 * Une question est un **attribut échangé symétriquement**, et celui qui demande
 * répond le premier : son offre porte déjà sa propre valeur, sans retour
 * possible. L'autre voit qu'on lui demande, et ne voit la réponse qu'en donnant
 * la sienne.
 *
 * Ce n'est pas une contrainte technique, c'est le sens du jeu. Elle interdit
 * l'interrogatoire — on ne peut pas récolter des attributs en posant des
 * questions, puisque chaque question coûte exactement ce qu'elle demande — et
 * elle rend le premier pas coûteux, donc signifiant. **On ne peut demander que
 * ce qu'on est prêt à répondre, et on le répond d'abord.**
 *
 * ## Ce que le protocole ne garantit pas, et qu'il faut dire
 *
 * Celui qui reçoit une offre tient la valeur de l'autre dans sa main avant
 * d'avoir répondu : rien n'oblige son appareil à la lui cacher. Un échange
 * équitable au sens cryptographique n'existe pas à deux sans arbitre, et un
 * schéma d'engagement ne changerait rien — on peut toujours ne pas révéler.
 * Plutôt qu'une fausse garantie, l'écran le dit : proposer, c'est donner. Le
 * dommage est borné — il faut être dans la salle, s'être fait reconnaître, et
 * ça ne rapporte qu'un attribut, une fois.
 *
 * ## Ce qui est demandable
 *
 * Seulement ce qui tient en **deux octets**. La contrainte vient de la trame,
 * et elle tombe juste : elle exclut par construction les noms, les textes
 * libres et les adresses. Ne restent que des nombres tirés de la fiche, rangés
 * ci-dessous **du moins au plus révélateur** — et chacun dit à l'écran ce qu'il
 * donne, parce que quelqu'un qui répond doit savoir ce qu'il vient de céder.
 */
object Questions {

    /**
     * Un attribut échangeable. [id] voyage dans la trame : il est **gelé**, on
     * n'insère pas au milieu et on ne réordonne pas — un appareil resté à une
     * version antérieure lirait autre chose que ce qu'on a envoyé.
     */
    enum class Trait(
        val id: Int,
        @StringRes val labelRes: Int,
        /** Ce que répondre donne réellement. Affiché avant de répondre. */
        @StringRes val tellsRes: Int,
    ) {
        /** 1..13 — la tonalité du KIN. Une chance sur treize : ça ne désigne personne. */
        Tone(1, R.string.trait_tone, R.string.trait_tone_tells),

        /** 0..4 — la famille de couleur. Encore plus large. */
        Color(2, R.string.trait_color, R.string.trait_color_tells),

        /** L'année arrondie à la dizaine. L'âge en gros, et rien de plus. */
        Decade(3, R.string.trait_decade, R.string.trait_decade_tells),

        /** 0..23 — l'heure seule, sans le jour. Sans intérêt pour qui ignore la date. */
        BirthHour(4, R.string.trait_hour, R.string.trait_hour_tells),

        /**
         * 1..260 — le KIN complet. Le plus révélateur de la liste : croisé avec
         * la décennie il ramène la date de naissance à quelques jours près, et
         * la date est la moitié de ce qui forge la clé. Placé en dernier, et
         * annoncé comme tel.
         */
        Kin(5, R.string.trait_kin, R.string.trait_kin_tells),

        // ⚠ Une sixième question a vécu ici : **Bio**, l'onde biologique en
        // dixièmes de hertz. Elle est partie le 15/08 avec toute la formule de
        // Watson, sur décision de Florent. Le jeu compte donc cinq questions,
        // et toutes se lisent dans la fiche de naissance — ce qui referme une
        // exception : celle-là était la seule que la cabine devait joindre
        // depuis l'extérieur, parce que le corps d'aujourd'hui n'est pas dans
        // la fiche.
        ;

        /**
         * La valeur qu'on donnerait, ou null si la fiche ne permet pas de
         * répondre — l'heure manque souvent, elle est facultative depuis le
         * 13/08. Un attribut sans valeur ne se propose pas et ne s'accepte pas.
         */
        fun read(birth: BirthData): Int? = when (this) {
            Tone -> KinMaya.of(birth)?.let { it.tone + 1 }
            Color -> KinMaya.of(birth)?.color
            Decade -> birth.year?.takeIf { it in 1900..2100 }?.let { it / 10 * 10 }
            BirthHour -> birth.hour?.takeIf { it in 0..23 }
            Kin -> KinMaya.of(birth)?.kin
        }

        /** Une valeur venue du réseau n'est crue que si elle a la bonne forme. */
        fun accepts(value: Int): Boolean = when (this) {
            Tone -> value in 1..13
            Color -> value in 0..4
            Decade -> value in 1900..2100 && value % 10 == 0
            BirthHour -> value in 0..23
            Kin -> value in 1..260
        }

        companion object {
            fun of(id: Int): Trait? = entries.firstOrNull { it.id == id }
        }
    }

    /**
     * Où en est une question entre nous et **une** personne.
     *
     * Les deux valeurs sont indépendantes, et leur combinaison dit tout l'état
     * sans qu'il faille un automate à côté : [mine] non nulle veut dire que
     * nous avons donné — offre ou réponse, c'est le même geste et il est
     * définitif ; [theirs] non nulle veut dire que nous avons reçu.
     */
    data class Exchange(
        val trait: Trait,
        /** Ce que nous avons donné. Une fois posé, ne redevient jamais null. */
        val mine: Int? = null,
        /** Ce que l'autre a donné. */
        val theirs: Int? = null,
        /** L'autre a refusé de répondre à notre offre. */
        val declined: Boolean = false,
    ) {
        /** Les deux cartes sont retournées : la question est jouée. */
        val settled: Boolean get() = mine != null && theirs != null

        /** On nous demande, et nous n'avons pas encore répondu. */
        val owed: Boolean get() = theirs != null && mine == null

        /** Nous avons donné et nous attendons. */
        val pending: Boolean get() = mine != null && theirs == null && !declined
    }


    /**
     * Ce qu'on peut encore proposer à quelqu'un : parmi les attributs que notre
     * fiche sait remplir, ceux dont on n'a pas déjà parlé avec lui.
     *
     * [answerable] vient de ce qui a été **lié** à la cabine, pas de la fiche
     * du moment : une réponse déjà donnée ne se rétracte pas, et l'écran ne
     * doit pas proposer ce que la cabine ne saurait pas envoyer.
     *
     * Un refus ferme la question pour de bon. Le contraire — pouvoir reproposer —
     * transformerait le refus en simple délai, et l'insistance en mécanique de
     * jeu ; il n'y a pas de raison de coder ça.
     */
    fun offerable(answerable: Set<Trait>, history: List<Exchange>): List<Trait> =
        Trait.entries.filter { trait ->
            trait in answerable && history.none { it.trait == trait }
        }
}
