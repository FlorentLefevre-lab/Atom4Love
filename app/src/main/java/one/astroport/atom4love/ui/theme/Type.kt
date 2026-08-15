package one.astroport.atom4love.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R

/**
 * Atkinson Hyperlegible Next pour le texte, JetBrains Mono pour les données.
 *
 * **Le critère est la fatigue oculaire, pas le goût.** Manrope, celle de la
 * maquette, tenait ici jusqu'au 15/08 ; elle a été mesurée contre Inter — la
 * référence des interfaces — puis contre celle-ci, et c'est celle-ci qui gagne
 * sur le seul terrain qui compte quand rien ne se lit au-dessus de 12,5 sp.
 *
 * - **Atkinson Hyperlegible Next** est dessinée par le Braille Institute pour
 *   la basse vision, et son principe est la distinction des formes : `I` à
 *   empattements, `l` à queue, `1` à drapeau, **zéro barré**. Ni Manrope ni
 *   Inter ne donnent ça — les deux écrivent `I`, `l` et `1` en trois fûts nus
 *   et un zéro plein ; c'est même un reproche connu fait à Inter. Elle est en
 *   prime la plus étroite des trois (381 dp pour une ligne type, contre 386 et
 *   408), ce qui a payé la montée des corps ci-dessous : une hauteur d'x plus
 *   basse (0,52) se rattrape en grandissant, une lettre ambiguë ne se rattrape
 *   pas.
 * - **JetBrains Mono** a la plus haute hauteur d'x des monos (0,56 du corps, vs
 *   0,52 pour IBM Plex ou Source Code Pro) — aux corps où vivent les données
 *   c'est ce qui sépare lire de plisser les yeux. Et elle distingue `0/O` et
 *   `1/l/I`, ce que la chasse fixe du système ne faisait pas : son zéro est nu,
 *   et l'app affiche des npub et des hexagones H3 qu'on relit caractère par
 *   caractère.
 *
 * Les glyphes absentes de ces fontes retombent sur le système : les flèches, les
 * pastilles et les emoji des libellés continuent de s'afficher.
 *
 * SIL Open Font License 1.1 pour les deux — textes dans `licenses/`.
 */
val Display = FontFamily(
    Font(R.font.atkinson_next_regular, FontWeight.Normal),
    Font(R.font.atkinson_next_bold, FontWeight.Bold),
    Font(R.font.atkinson_next_extrabold, FontWeight.ExtraBold),
)
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * Cinzel Decorative — la fonte d'apparat, et elle ne sert **qu'au** nom de
 * l'application sur le splash. Des capitales romaines à empattements, avec des
 * pleins et des déliés qu'aucun grotesque n'a : ce qui s'ouvre là est une
 * histoire, pas un tableau de bord, et le nom doit le dire avant le premier
 * écran.
 *
 * Elle ne descend nulle part ailleurs : une fonte à caractère se fatigue vite,
 * et tout ce qui se lit longtemps (fiche, cabine, données) reste à [Display] et
 * à [Mono]. SIL Open Font License 1.1 — voir `licenses/`.
 */
val Ornament = FontFamily(Font(R.font.cinzel_decorative_bold, FontWeight.Bold))

/**
 * Styles de texte, à l'origine la maquette en px CSS → sp (cadre de 428 px).
 *
 * ⚠ **Les corps de lecture ont monté d'un point le 15/08**, contre la fatigue
 * oculaire : Body 12,5 → 13,5, Caption 11,5 → 12,5, Data 9,5 → 10, et les
 * titres de ligne et d'onglet d'autant. La maquette n'est donc plus suivie au
 * pixel là-dessus, et c'est délibéré : elle avait été dessinée à l'écran d'un
 * poste, où 11,5 px se lisent, pas dans une main à 30 cm.
 *
 * C'est le levier qui compte le plus — bien avant le dessin de la fonte. Il
 * n'était payable que parce qu'[Display] est plus étroite que ce qu'elle
 * remplace : la ligne type occupe 411 dp à 13,5 sp, soit ce que l'ancienne
 * prenait déjà à 12,5.
 *
 * Les appels qui forcent leur taille (`.copy(fontSize = …)`, une soixantaine)
 * ne suivent pas cette montée : ce sont des ajustements locaux, chacun avec sa
 * raison sur place.
 */
object A4LText {

    /** Titre d'écran — « Forger votre noyau ». */
    val H1 = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 25.sp, lineHeight = 29.sp, letterSpacing = (-0.5).sp,
    )

    /** Titre d'écran secondaire — « Cabine à portée ». */
    val H2 = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.44).sp,
    )

    /** Titre de barre / de carte. */
    val Title = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 19.sp,
    )

    /** Titre de ligne de liste. */
    val ItemTitle = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp, lineHeight = 18.sp,
    )

    /** Paragraphe d'explication. */
    val Body = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 20.sp,
    )

    /** Légende, aide contextuelle. */
    val Caption = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 18.sp,
    )

    /** Libellé d'onglet de la barre du bas. */
    val Tab = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 13.sp,
    )

    /** Label de section : mono 9 px, capitales, très espacé (.14em). */
    val SectionLabel = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp, lineHeight = 12.sp, letterSpacing = 1.26.sp,
    )

    /** Donnée en chasse fixe (npub, coordonnées, KIN…). */
    val Data = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal,
        fontSize = 10.sp, lineHeight = 14.sp,
    )

    /** Grand nombre en chasse fixe (compteur, valence, score). */
    val Metric = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 24.sp,
    )
}

/** Typographie Material, alignée sur la famille d'affichage de la maquette. */
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = A4LText.Body,
    titleMedium = A4LText.Title,
    labelSmall = A4LText.SectionLabel,
)
