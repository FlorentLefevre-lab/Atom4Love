package one.astroport.atom4love.domain

import one.astroport.atom4love.domain.KinMaya.Kin

/**
 * L'Oracle du Tzolkin — les KIN qui complètent le vôtre.
 *
 * Portage des formules de Fred **telles qu'elles tournent en production** dans
 * `kin_oracle.sh` (celui qui envoie les courriels Oracle aux membres), données
 * le 16 août 2026 :
 *
 * ```
 * Antipode (Défi)      : sceau+10, ton complémentaire (14−ton)
 * Analogue (Alternance): sceau+10, MÊME ton
 * Occulte              : K + K′ = 261  (sceau miroir 19−sceau, ton 14−ton)
 * Guide                : même famille de sceaux, position donnée par le ton
 * ```
 *
 * ⚠ **La planche s'était trompée, pas la production.** Le texte « formules
 * clés » de `tzolkin_oracle.svg` — celui d'où venait notre premier portage —
 * **intervertissait le ton du défi et celui de l'alternance**. Fred a corrigé le
 * dessin ; c'est la version ci-dessus qui fait foi. Deux conséquences à retenir
 * plutôt qu'à redécouvrir :
 *
 * 1. **Le couple de KIN ne bouge pas.** Défi et alternance partageant le sceau,
 *    échanger leurs tons échange leurs *noms*, pas les deux cartes affichées.
 *    Ce que nous montrions était juste ; ce que nous en disions ne l'était pas.
 * 2. **Ce sont l'occulte et le DÉFI qui retournent le ton**, pas l'occulte et
 *    l'alternance. L'identité que nous avions remarquée existe bien — elle se
 *    lisait juste sur la mauvaise paire, notre transcription portant la même
 *    inversion que sa planche.
 *
 * Le ton range donc les cinq pouvoirs en deux camps : **ton gardé** pour soi,
 * le guide et l'alternance ; **ton retourné** pour le défi et l'occulte.
 *
 * ⚠ **Opposable ne veut pas dire contraire, mais complémentaire.** Aucune de
 * ces relations n'est une note ni un rejet : ce sont des *pouvoirs*, comme les
 * nomme Fred — le défi renforce, l'alternance soutient, l'occulte élargit, le
 * guide oriente. C'est la même règle qui interdit à [Phi2X.classifyResonance]
 * de dégrader un rang avec son ⚡ : il dit de quelle sorte, jamais combien.
 *
 * ## Le défi et l'alternance partagent le sceau
 *
 * `+10` et `−10` sont **la même opération modulo 20** : les deux cercles de la
 * planche tombent sur la même colonne, et seul le ton les sépare. Conséquence
 * directe, et c'est elle qui compte pour le jeu : **un sceau vu de loin ne
 * permet pas de trancher entre les deux** (cf. [sealBond]).
 */
object Oracle {

    /**
     * Le défi : **sceau + 10, ton retourné**. Le pouvoir qui renforce.
     *
     * Le « 14 − ton » de Fred se lit sur des tons comptés de 1 à 13, là où
     * [Kin.tone] les compte de 0 à 12 : `14 − (t+1) − 1` se ramène donc à
     * `12 − t`. La somme des deux tons **affichés** fait bien quatorze, ce qui
     * est la propriété à vérifier ([OracleTest] s'en charge).
     *
     * Involutif — le défi de votre défi est vous : vingt sceaux plus loin on a
     * fait le tour, et retourner deux fois un ton le laisse en place.
     *
     * ⚠ **Au ton 7, le défi EST l'alternance** — `14 − 7 = 7`, le Résonnant est
     * son propre complément. Les deux relations partageant déjà le sceau, plus
     * rien ne les sépare et elles se referment sur un seul KIN. Vingt cas sur
     * les 260, un par sceau. **Fred le confirme et n'a rien prévu de spécial**
     * (son `KIN.daily.sh` ne le traite pas davantage) : l'écran affiche les deux
     * et le dit d'une ligne, plutôt que d'en masquer une.
     */
    fun antipode(k: Kin): Kin? = KinMaya.ofSealAndTone((k.glyph + 10) % 20, 12 - k.tone)

    /**
     * L'alternance : **même sceau que le défi, même ton que vous**. Le pouvoir
     * qui soutient.
     */
    fun analogue(k: Kin): Kin? = KinMaya.ofSealAndTone((k.glyph + 10) % 20, k.tone)

    /**
     * L'occulte : **K + K′ = 261**. Le partenaire caché.
     *
     * Donné par le nombre seul, sans passer par le sceau ni le ton — c'est sa
     * définition. Les deux s'en déduisent quand même, et se recoupent avec le
     * reste de la planche : sceau miroir (`19 − sceau`, soit une somme de 21
     * sur des sceaux comptés à partir de un) et ton retourné, **le même que
     * celui du défi**.
     *
     * ⚠ Null au-delà de 260 — la station produit quelques KIN hors grille sur
     * des dates de fin septembre ([KinMaya.of]), et `261 − K` n'y veut plus
     * rien dire. On préfère ne rien montrer plutôt qu'un nombre inventé.
     */
    fun occult(k: Kin): Kin? = KinMaya.ofNumber(261 - k.kin)

    /**
     * Le guide : **votre famille de sceaux, à la place que dicte votre ton.**
     * Le pouvoir qui oriente.
     *
     * ```
     * famille  = sceau % 4          # Rouge, Blanc, Bleu, Jaune
     * position = (ton − 1) % 5      # le rang dans la famille
     * sceau    = famille + position × 4
     * ton      = inchangé
     * ```
     *
     * ⚠ **Quatre familles, pas cinq châteaux.** C'est le découpage des *sceaux*
     * de `tzolkin_cycle.svg` — celui qui fait « Tempête Bleue » — et **non** le
     * champ `color` de `phi2x.py` que porte [Kin.color], lequel compte les
     * châteaux par cycle de treize et inclut le Vert. Les deux se disent avec
     * les mêmes mots et ne désignent pas la même chose ; la question a été posée
     * à Fred et il a tranché pour les sceaux par quatre.
     *
     * ⚠ **Le guide n'est pas symétrique, et Fred le dit.** Trois propriétés
     * mesurées sur les 260 KIN ([OracleTest]), qui surprennent si on attend une
     * relation comme les trois autres :
     *
     * - il **efface** votre rang dans la famille et le remplace par celui du
     *   ton, si bien que **cinq KIN partagent le même guide** — l'image ne
     *   compte que 52 KIN sur 260, un par couple (famille, ton) ;
     * - il est **idempotent** : le guide d'un guide est ce même guide. Ces 52
     *   KIN sont leur propre guide, les 208 autres n'en sont le guide de
     *   personne ;
     * - il reste **toujours dans votre famille**, quand le défi et l'occulte
     *   en sortent toujours. C'est ce qui en fait une orientation et pas une
     *   rencontre.
     */
    fun guide(k: Kin): Kin? =
        KinMaya.ofSealAndTone(k.glyph % 4 + (k.tone % 5) * 4, k.tone)

    /** Les quatre compléments d'un KIN, chacun null s'il sort de la grille. */
    data class Reading(
        val self: Kin,
        val guide: Kin?,
        val antipode: Kin?,
        val analogue: Kin?,
        val occult: Kin?,
    )

    fun of(k: Kin): Reading = Reading(
        self = k,
        guide = guide(k),
        antipode = antipode(k),
        analogue = analogue(k),
        occult = occult(k),
    )

    /**
     * Ce qu'un sceau **seul** dit de son lien avec le nôtre.
     *
     * C'est tout ce que le Plateau peut lire d'un voisin : l'annonce BLE porte
     * le sceau et la phase, jamais le ton (`ProximityPayload.Signature`). Or
     * sans le ton, le défi et l'alternance sont indiscernables — ils partagent
     * le sceau. La réponse le dit donc telle quelle, [Bond.Challenge] couvrant
     * les deux, plutôt que de trancher à pile ou face.
     *
     * Les deux sceaux visés sont toujours distincts du nôtre et distincts entre
     * eux : `2·sceau ≡ 9 [20]` et `2·sceau ≡ 19 [20]` n'ont pas de solution, un
     * pair ne valant jamais un impair.
     *
     * ⚠ **Le guide n'est délibérément pas un lien de sceau**, alors qu'il
     * pourrait l'être : son sceau se calcule aussi sans le ton. Deux raisons —
     * il vit dans notre propre famille quand les deux autres en sortent, donc
     * il ne dit pas « allez vers cette personne » ; et l'ajouter porterait la
     * part des gens qui répondent de 10 % à 15 %, ce qui déplace les échelons
     * de [Match] sans que Fred l'ait demandé.
     *
     * **Tranché par Florent le 16/08 : le guide reste dehors.** Ne pas rouvrir
     * sans une raison neuve.
     *
     * Deux sceaux suffisent, sans les KIN : c'est une relation de colonne, le
     * ton n'y entre pas. Un pair qui s'annonce sans sceau connu
     * ([KinMaya.GLYPH_UNKNOWN]) tombe donc hors grille et ne dit rien.
     */
    enum class Bond { Challenge, Hidden }

    fun sealBond(myGlyph: Int, theirGlyph: Int): Bond? {
        if (myGlyph !in 0..19 || theirGlyph !in 0..19) return null
        return when (theirGlyph) {
            (myGlyph + 10) % 20 -> Bond.Challenge
            19 - myGlyph -> Bond.Hidden
            else -> null
        }
    }
}
