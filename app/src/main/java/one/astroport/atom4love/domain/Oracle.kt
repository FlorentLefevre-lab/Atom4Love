package one.astroport.atom4love.domain

import one.astroport.atom4love.domain.KinMaya.Kin

/**
 * L'Oracle du Tzolkin — les KIN qui complètent le vôtre.
 *
 * Portage de la planche `assets/miz/tzolkin_oracle.svg` et du texte de
 * `miz.html`, **à la lettre** — mais la lettre a changé le 16 août 2026 :
 * la planche elle-même se contredisait (sa bulle « Sceau±10 » et son texte
 * « même sceau » pour l'alternance ne s'accordaient pas), et son nommage du
 * ton était inversé par rapport à `kin_oracle.sh`, qui envoie déjà les
 * emails Oracle réels aux membres depuis des mois. Corrigée sur la planche
 * comme ici, la règle qui tourne en production est :
 *
 * ```
 * Antipode : sceau+10, ton complém. (14−ton).
 * Analogue : sceau+10, même ton.
 * Occulte  : K+K′ = 261.
 * Guide    : même famille-couleur (sceau%4), position (ton−1)%5 dans la famille.
 * ```
 *
 * ⚠ **Opposable ne veut pas dire contraire, mais complémentaire.** Aucune de
 * ces quatre relations n'est une note ni un rejet : ce sont quatre *pouvoirs*,
 * comme les nomme Fred — le guide oriente, le défi renforce, l'alternance
 * soutient, l'occulte élargit. C'est la même règle qui interdit à
 * [Phi2X.classifyResonance] de dégrader un rang avec son ⚡ : il dit de
 * quelle sorte, jamais combien.
 *
 * ## Le défi et l'alternance partagent le sceau
 *
 * `+10` et `−10` sont **la même opération modulo 20** : le « sceau+10 » de
 * l'antipode et le « sceau±10 » de l'analogue désignent donc le même sceau, et
 * les deux cercles de la planche tombent sur la même colonne. Seul le ton les
 * sépare — retourné pour le défi, identique pour l'alternance. Conséquence
 * directe, et c'est elle qui compte pour le jeu : **un sceau vu de loin ne
 * permet pas de trancher entre les deux** (cf. [sealBond]).
 *
 * ## Le Guide, résolu
 *
 * Sa seule définition chez Fred était « même famille-couleur », qui ne
 * désigne pas un KIN unique (52 à 65 candidats selon le découpage), et le mot
 * couleur y était ambigu : `tzolkin_cycle` colore les **sceaux** par cycle de
 * quatre (Rouge, Blanc, Bleu, Jaune), quand le champ `color` de `phi2x.py` —
 * celui que porte [Kin.color] — compte les **châteaux** par cycle de cinq, le
 * Vert compris. Fred a tranché : c'est le premier découpage, celui des
 * sceaux. `kin_oracle.sh::_kin_guide` fixe la position dans la famille par le
 * ton — cinq groupes qui se recoupent tous les cinq tons ({1,6,11}, {2,7,12},
 * {3,8,13}, {4,9}, {5,10}) — et [guide] en est le portage direct.
 *
 * ⚠ La quatrième combinaison possible — sceau miroir et ton inchangé — n'a pas
 * de nom sur sa planche. On ne l'invente pas non plus.
 */
object Oracle {

    /**
     * Le guide : **même famille-couleur (sceau % 4)**, à la position que
     * fixe le ton. Le pouvoir qui oriente.
     *
     * La famille regroupe les sceaux par cycle de quatre (Rouge, Blanc, Bleu,
     * Jaune — celle de `tzolkin_cycle`, pas les cinq châteaux de [Kin.color]).
     * La position dans la famille est le ton modulo cinq : les treize tons se
     * recoupent en cinq groupes de trois ou deux ({1,6,11}, {2,7,12},
     * {3,8,13}, {4,9}, {5,10}), et chacun désigne un des cinq membres de la
     * famille. Le ton lui-même ne change pas.
     *
     * N'est pas involutif ni même symétrique : le guide de votre guide n'a
     * aucune raison d'être vous. Kin 1 et Kin 67 sont chacun leur propre
     * guide (position 0, déjà à la base de leur famille) — ce n'est pas la
     * règle générale, seulement une coïncidence de position.
     */
    fun guide(k: Kin): Kin? {
        val family = k.glyph % 4
        val position = k.tone % 5
        return KinMaya.ofSealAndTone(family + position * 4, k.tone)
    }

    /**
     * Le défi : **sceau + 10, ton retourné**. Le pouvoir qui renforce.
     *
     * Involutif — le défi de votre défi est vous, puisque vingt sceaux plus
     * loin on a fait le tour, et que retourner le ton deux fois le rend.
     *
     * Le « 14 − ton » de la planche se lit sur des tons comptés de 1 à 13, là
     * où [Kin.tone] les compte de 0 à 12 : `14 − (t+1) − 1` se ramène donc à
     * `12 − t`. La somme des deux tons **affichés** fait bien quatorze, ce qui
     * est la propriété à vérifier ([OracleTest] s'en charge).
     *
     * ⚠ **Au ton 7, le défi EST l'alternance** — `14 − 7 = 7`, le Résonnant est
     * son propre complément. Les deux relations partageant déjà le sceau, plus
     * rien ne les sépare et elles se referment sur un seul KIN. Vingt cas sur
     * les 260, un par sceau. Ce n'est pas un défaut à rattraper : l'écran qui
     * les affiche doit accepter de montrer deux fois le même.
     */
    fun antipode(k: Kin): Kin? = KinMaya.ofSealAndTone((k.glyph + 10) % 20, 12 - k.tone)

    /**
     * L'alternance : **même sceau que le défi, même ton**. Le pouvoir qui
     * soutient.
     *
     * Involutif — le sceau avance de 20 (retour au même) et le ton ne bouge
     * pas.
     */
    fun analogue(k: Kin): Kin? = KinMaya.ofSealAndTone((k.glyph + 10) % 20, k.tone)

    /**
     * L'occulte : **K + K′ = 261**. Le partenaire caché.
     *
     * Donné par le nombre seul, sans passer par le sceau ni le ton — c'est sa
     * définition. Les deux s'en déduisent quand même, et se recoupent avec le
     * reste de la planche : sceau miroir (`19 − sceau`, soit une somme de 21
     * sur des sceaux comptés à partir de un) et ton retourné, le même que
     * celui du défi.
     *
     * ⚠ Null au-delà de 260 — la station produit quelques KIN hors grille sur
     * des dates de fin septembre ([KinMaya.of]), et `261 − K` n'y veut plus
     * rien dire. On préfère ne rien montrer plutôt qu'un nombre inventé.
     */
    fun occult(k: Kin): Kin? = KinMaya.ofNumber(261 - k.kin)

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
     * Les trois sceaux visés sont toujours distincts du nôtre et distincts
     * entre eux : `2·sceau ≡ 9 [20]` et `2·sceau ≡ 19 [20]` n'ont pas de
     * solution, un pair ne valant jamais un impair.
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
