package one.astroport.atom4love.domain

/**
 * Les échelons du jeu : **match** et **super match**.
 *
 * Le vocabulaire vient des applications de rencontre, les seuils viennent de
 * Fred — c'est la condition pour que le mot ne mente pas. Rien n'est noté ici,
 * rien n'est classé, et surtout **rien n'est inventé** : les deux échelons se
 * lisent sur deux grandeurs qui existaient déjà et se croisent à portée
 * d'antenne, sans compte, sans serveur et sans historique.
 *
 * ## Deux plans, jamais un score
 *
 * - **La phase** — [Phi2X.resonanceK] au-delà de [Phi2X.SUPER_COHERENCE_K], le
 *   seuil que Fred appelle lui-même le « match quantique ». Continu.
 * - **Le sceau** — un lien de l'[Oracle] : le sceau du défi (donc aussi de
 *   l'alternance, ils le partagent) ou celui de l'occulte. Discret.
 *
 * Un plan qui s'aligne fait un [Level.Match]. Les deux ensemble font un
 * [Level.Super]. C'est tout, et c'est volontairement pauvre : la rencontre
 * n'est pas une note.
 *
 * ⚠ **Un match n'est pas un jugement, et pas davantage une invitation.** Il dit
 * qu'à cet instant deux ondes se croisent bien et que deux sceaux se répondent
 * — l'un des deux pouvant être un **défi**, qui n'est pas moins bon qu'une
 * union ([Oracle]). Le geste, lui, reste entier : personne n'est prévenu qu'on
 * l'a vu, et [one.astroport.atom4love.ui.screens.RendezvousScreen] ne se
 * déclenche que sur une carte qu'on a retournée soi-même.
 *
 * ## Ce qu'un match ne peut pas être ici
 *
 * ⚠ Il ne peut pas être **réciproque au sens des applications**, parce que rien
 * ne circule : le Plateau ne dit à personne qu'on l'a choisi. La réciprocité
 * existe pourtant, et elle est physique — deux écrans qui battent le même
 * rythme dans la salle, chacun l'ayant calculé de son côté. Elle se constate
 * avec les yeux, jamais par un serveur. Ne pas « améliorer » ça en la faisant
 * circuler : ce serait retirer au jeu la seule chose qui le distingue.
 */
object Match {

    /**
     * ## Les seuils, et ce qu'ils produisent
     *
     * Un seuil ne se choisit pas au goût : il se choisit sur la **fréquence**
     * qu'il fabrique. Un super match qui tombe tous les soirs ne veut plus rien
     * dire ; un match qui n'arrive jamais laisse un écran mort. Les trois
     * valeurs ci-dessous sont donc données avec ce qu'elles donnent, et
     * `MatchTest` mesure les taux réels pour qu'un réglage futur se fasse en
     * connaissance de cause plutôt qu'à l'estime.
     *
     * `k = 1/(1+|sin Δφ|)` se renverse en `|sin Δφ| ≤ 1/k − 1`, et la fraction
     * du tour couverte se lit directement — en comptant **les deux** zones, en
     * phase et en opposition, à égalité comme le veut [Oracle] :
     *
     * | seuil sur k | écart toléré | part des rencontres |
     * |---|---|---|
     * | 0,95 ([SUPER_K]) | 0,053 rad | 3,4 % |
     * | 0,90 ([MATCH_K]) | 0,111 rad | 7,1 % |
     * | 0,80 | 0,253 rad | 16,1 % |
     *
     * Côté sceau, la fréquence ne se règle pas : elle est fixée par la grille.
     * Deux sceaux sur vingt répondent — celui du défi (partagé avec
     * l'alternance) et celui de l'occulte — soit **10 %** des gens, quoi qu'on
     * veuille. C'est [Oracle.sealBond], et il n'y a pas de bouton dessus.
     *
     * Ce qui donne, en croisant les deux axes :
     *
     * | | pas de lien de sceau | lien de sceau (10 %) |
     * |---|---|---|
     * | k < 0,90 | — | **MATCH** |
     * | 0,90 ≤ k < 0,95 | **MATCH** | **MATCH** |
     * | k ≥ 0,95 | **MATCH** | **SUPER MATCH** |
     *
     * → un match pour ~6 personnes croisées, un super match pour ~300.
     *
     * ## ✅ Vérifié, pas supposé
     *
     * Ces parts supposent φ **uniforme** sur le tour, et l'hypothèse ne va pas
     * de soi : l'autre grandeur de terrain de Fred, le champ Ψ de
     * [Phi2X.resonanceField], est si tassée sur des coordonnées réelles —
     * [0,50 ; 0,545] — qu'il a retiré de son projecteur la teinte qui en
     * dépendait. Une φ qui se comporterait pareil ferait matcher tout le monde
     * avec tout le monde, et ces seuils ne vaudraient rien.
     *
     * `MatchCalibrationTest` a donc construit 400 naissances plausibles — 60 ans
     * de dates, douze villes sur cinq continents — et mesuré les 79 800 paires,
     * le 2026-08-16 :
     *
     * | | prédit (uniforme) | **mesuré** |
     * |---|---|---|
     * | match | 16,4 % | **16,38 %** |
     * | super match | 0,335 % | **0,335 %** |
     * | lien de sceau | 10,0 % | **10,17 %** |
     *
     * φ couvre bien le tour entier — aucun secteur de 30° vide, aucun au-delà
     * de 20 % — et le minimum de k observé touche 0,5, le quart de tour. Les
     * seuils sont donc calibrés sur ce qui arrive vraiment.
     */

    /**
     * Le seuil du super match, côté phase : **celui de Fred**, tel quel
     * ([Phi2X.SUPER_COHERENCE_K] = 0,95, son « match quantique »). On ne le
     * bouge pas — c'est le seul des trois qui vienne de son code, et le faire
     * dériver couperait le mot de ce qu'il désigne chez lui.
     */
    const val SUPER_K = Phi2X.SUPER_COHERENCE_K

    /**
     * Le seuil du match ordinaire, côté phase : **0,90**, choisi ici.
     *
     * Il vaut à peu près le double d'écart toléré (0,111 rad contre 0,053), et
     * il ouvre le match à 7,1 % des rencontres sur la seule phase. Descendre à
     * 0,80 le porterait à 16 % — un écran où presque tout le monde matche, donc
     * un mot sans valeur. Le monter à 0,95 supprimerait purement et simplement
     * l'échelon intermédiaire, puisqu'il se confondrait avec le super.
     *
     * ⚠ C'est **notre** valeur, pas la sienne : à revoir si Fred en publie une.
     */
    const val MATCH_K = 0.90

    enum class Level {
        /** Rien de notable — ce qui est le cas ordinaire, et ce n'est pas un échec. */
        None,

        /** Un plan s'aligne : la phase **ou** le sceau. */
        Match,

        /** Les deux plans à la fois. Environ trois rencontres sur mille. */
        Super,
    }

    /** Le détail derrière l'échelon, pour que l'écran puisse dire *pourquoi*. */
    data class Reading(
        val level: Level,
        /** k au-delà du seuil de Fred — le « match quantique ». */
        val quantum: Boolean,
        /** Le lien de sceau, quand il y en a un. */
        val bond: Oracle.Bond?,
        /** k lui-même, null si l'une des deux phases manque. */
        val k: Double?,
    )

    /**
     * Ce que deux signatures se disent. Les quatre valeurs sont celles de
     * l'annonce BLE, donc toutes facultatives : un pair qui n'a pas rempli sa
     * fiche diffuse des inconnues, et on ne conclut rien de rien.
     */
    fun read(
        myPhase: Double?,
        myGlyph: Int?,
        theirPhase: Double?,
        theirGlyph: Int?,
    ): Reading {
        val k = if (myPhase != null && theirPhase != null) {
            Phi2X.resonanceK(myPhase, theirPhase)
        } else {
            null
        }
        val quantum = k != null && k >= SUPER_K
        val close = k != null && k >= MATCH_K
        val bond = if (myGlyph != null && theirGlyph != null) {
            Oracle.sealBond(myGlyph, theirGlyph)
        } else {
            null
        }
        // Le super match demande les DEUX axes — c'est ce qui le rend rare, et
        // c'est la seule façon de le tenir rare sans durcir un seuil : la
        // conjonction fait le travail que l'échelle ne peut pas faire.
        val level = when {
            quantum && bond != null -> Level.Super
            close || bond != null -> Level.Match
            else -> Level.None
        }
        return Reading(level = level, quantum = quantum, bond = bond, k = k)
    }
}
