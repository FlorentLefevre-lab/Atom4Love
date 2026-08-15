package one.astroport.atom4love.domain

/**
 * Le KIN du Tzolkin — portage de `calc_kin()` de `tools/phi2x.py`.
 *
 * L'Aide dit encore que le KIN « ne se calcule pas sur cet appareil ». C'était
 * vrai tant que la table vivait chez Fred : elle est publique, et un sceau qui
 * n'existe que pour les titulaires d'un MULTIPASS ne peut pas entrer dans une
 * annonce de proximité.
 *
 * Le calcul ne demande **que la date** : ni heure, ni lieu. Il est donc
 * disponible dès que la fiche porte sa date, avant même la forge.
 */
object KinMaya {

    /**
     * Décalage cumulé de chaque mois. Les trois derniers repartent bas parce
     * que le compte reboucle sur 260 en cours d'année.
     *
     * ⚠ Le 29 février n'y est **pas** compté : le calendrier Dreamspell
     * l'appelle « Jour hors du Temps » et le laisse dehors. Ce n'est pas un
     * oubli, c'est la cosmologie d'Argüelles — ne jamais le « corriger ».
     */
    private val MONTH_OFFSET = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 13, 44, 74)

    /** Le cycle de 52 ans, indexé par `année % 52`. */
    private val YEAR_SUM = mapOf(
        30 to 2, 35 to 7, 40 to 12, 45 to 17, 50 to 22, 3 to 27, 8 to 32, 13 to 37,
        18 to 42, 23 to 47, 28 to 52, 32 to 57, 38 to 62, 42 to 67, 48 to 72, 1 to 76,
        6 to 82, 11 to 87, 16 to 92, 21 to 97, 26 to 102, 31 to 107, 36 to 112, 41 to 117,
        46 to 122, 51 to 127, 4 to 132, 9 to 137, 14 to 142, 19 to 147, 24 to 152,
        29 to 157, 34 to 162, 39 to 167, 44 to 172, 49 to 177, 2 to 182, 7 to 187,
        12 to 192, 17 to 197, 22 to 202, 27 to 207, 37 to 217, 47 to 227, 0 to 232,
        5 to 237, 10 to 242, 15 to 247, 20 to 252, 25 to 257,
    )

    /** Valeur diffusée quand le sceau n'est pas connu — celle de cabine-33. */
    const val GLYPH_UNKNOWN = 99

    /**
     * Les vingt sceaux sous leur nom maya. Ce sont des noms propres : ils ne
     * passent pas par `strings.xml` et se disent pareil dans les trois langues,
     * là où « Dragon, Vent, Nuit… » demanderait trois traductions et perdrait
     * ce que Fred et l'interface web nomment.
     */
    private val GLYPH_NAMES = arrayOf(
        "Imix", "Ik", "Akbal", "Kan", "Chicchan", "Cimi", "Manik", "Lamat",
        "Muluc", "Oc", "Chuen", "Eb", "Ben", "Ix", "Men", "Cib",
        "Caban", "Etznab", "Cauac", "Ahau",
    )

    /**
     * Les mêmes vingt sceaux, et les cinq familles-couleurs, **sous les noms
     * français de Fred** — `KIN_GLYPHS_FR` et `KIN_COLORS` de `phi2x.py`.
     *
     * Ils ne servent qu'aux tags `glyph` et `color` du certificat, qui les
     * portent tels quels : c'est le vocabulaire du relais, pas celui de
     * l'interface. Celle-ci garde les noms mayas de [GLYPH_NAMES], qui sont des
     * noms propres et se disent pareil dans les trois langues.
     */
    private val GLYPH_NAMES_FR = arrayOf(
        "Dragon", "Vent", "Nuit", "Graine", "Serpent", "Lieur", "Main", "Étoile",
        "Lune", "Chien", "Singe", "Chemin", "Roseau", "Jaguar", "Aigle", "Guerrier",
        "Terre", "Miroir", "Tempête", "Soleil",
    )

    private val COLOR_NAMES_FR = arrayOf("Rouge", "Blanc", "Bleu", "Jaune", "Vert")

    /**
     * Un pictogramme par sceau — `EMOJI_BY_GLYPH_FR` d'`atomic-glyphs.js`.
     *
     * C'est la seule forme du sceau qui traverse une salle : dans un bar on ne
     * lit pas « Akbal », on montre 🌌. Et il ne se traduit pas, ce qui règle la
     * question des trois langues sans la poser.
     */
    private val GLYPH_EMOJI = arrayOf(
        "🐉", "🌬️", "🌌", "🌱", "🐍", "🌉", "✋", "⭐",
        "🌕", "🐕", "🐒", "🚶", "🎋", "🐆", "🦅", "⚔️",
        "🌍", "🪞", "⛈️", "☀️",
    )

    /** Le pictogramme du sceau, ⚛ quand on ne le connaît pas — comme chez lui. */
    fun glyphEmoji(glyph: Int?): String =
        glyph?.takeIf { it in GLYPH_EMOJI.indices }?.let { GLYPH_EMOJI[it] } ?: "⚛"

    /** Le nom du sceau 0..19, ou null hors de la table. */
    fun glyphName(glyph: Int?): String? = glyph?.takeIf { it in GLYPH_NAMES.indices }
        ?.let { GLYPH_NAMES[it] }

    /** Le nom français du sceau, celui qu'attend le tag `glyph`. */
    fun glyphNameFr(glyph: Int?): String? = glyph?.takeIf { it in GLYPH_NAMES_FR.indices }
        ?.let { GLYPH_NAMES_FR[it] }

    /** Le nom français de la famille-couleur, celui qu'attend le tag `color`. */
    fun colorNameFr(color: Int?): String? = color?.takeIf { it in COLOR_NAMES_FR.indices }
        ?.let { COLOR_NAMES_FR[it] }

    /**
     * Un KIN et ses trois index, tous **comptés à partir de zéro** comme chez
     * Fred : [glyph] 0..19, [tone] 0..12, [color] 0..4. `Resonance.kt` compte
     * ses sceaux et ses tons à partir de 1 — c'est le même nombre décalé d'un.
     */
    data class Kin(val kin: Int, val glyph: Int, val tone: Int, val color: Int)

    /**
     * null si la date n'a pas de sens.
     *
     * ⚠ **Anomalie reproduite volontairement** : la station retranche 260 une
     * seule fois au lieu de prendre un modulo, si bien que quelques dates de
     * fin septembre rendent un KIN **au-delà de 260** — 263 pour le 28/09/1944.
     * `Kin_Maya.gd` de cabine-33, lui, prend le modulo et rend 3. Nous suivons
     * la station, parce que c'est son `kin_num` qui reviendra dans le MULTIPASS
     * et qu'un désaccord se verrait ; le test `KinMayaTest` épingle la valeur
     * pour que personne ne la « répare » sans le décider. À porter à Fred.
     */
    fun of(year: Int, month: Int, day: Int): Kin? {
        if (month !in 1..12 || day !in 1..31) return null
        var kin = day + MONTH_OFFSET[month - 1] + (YEAR_SUM[Math.floorMod(year, 52)] ?: 0)
        if (kin > 260) kin -= 260
        if (kin <= 0) kin += 260
        return Kin(
            kin = kin,
            glyph = (kin - 1) % 20,
            tone = (kin - 1) % 13,
            color = ((kin - 1) / 13) % 5,
        )
    }

    /**
     * Le KIN tel qu'il arrive déjà calculé — le tag `kin` d'un certificat lu sur
     * le relais. Portage de `calcKinFromNum()` : mêmes trois index que [of],
     * mais rien à dériver, le nombre est donné.
     *
     * Hors de 1..260, null — comme chez Fred, y compris pour les KIN au-delà de
     * 260 que la station produit sur quelques dates (voir [of]) : sa carte et
     * son projecteur les affichent alors sans sceau, et nous faisons pareil
     * plutôt que d'inventer un glyphe qu'eux ne montrent pas.
     */
    fun ofNumber(num: Int): Kin? {
        if (num !in 1..260) return null
        return Kin(
            kin = num,
            glyph = (num - 1) % 20,
            tone = (num - 1) % 13,
            color = ((num - 1) / 13) % 5,
        )
    }

    /**
     * Le KIN d'une fiche d'incarnation, ou null tant qu'elle n'a pas sa date.
     *
     * Le jour est celui de l'**instant scellé** ([BirthData.birthInstantUtc]),
     * comme chez la station qui appelle `calc_kin_unix(birth_unix)` sur son
     * horodatage converti. Ce n'est pas un détail d'arrondi : une naissance
     * juste après minuit à Tokyo tombe la veille en UTC, et change de sceau.
     *
     * Sans longitude, il n'y a pas d'instant : on prend alors la date
     * d'horloge. Le KIN qui s'affiche avant que le lieu soit saisi est donc
     * provisoire — mais l'assistant exige le lieu avant de le montrer.
     */
    fun of(b: BirthData): Kin? {
        if (!b.dateComplete) return null
        val instant = b.birthInstantUtc
            ?: return of(b.year!!, b.month!!, b.day!!)
        return of(instant.year, instant.monthValue, instant.dayOfMonth)
    }
}
