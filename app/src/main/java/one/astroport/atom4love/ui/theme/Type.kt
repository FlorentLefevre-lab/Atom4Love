package one.astroport.atom4love.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * La maquette utilise Manrope (texte) et JetBrains Mono (données, adresses, compteurs).
 * Aucune des deux n'est embarquée ici : on tombe sur les familles système, qui tiennent
 * le même rôle (grotesque / chasse fixe). Pour coller à la maquette au pixel près il
 * suffira de déposer les .ttf dans res/font et de changer ces deux constantes.
 */
val Display = FontFamily.SansSerif   // ← Manrope
val Mono = FontFamily.Monospace      // ← JetBrains Mono

/** Styles de texte de la maquette, en px CSS → sp (le cadre fait 428 px de large). */
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
        fontSize = 14.sp, lineHeight = 18.sp,
    )

    /** Titre de ligne de liste. */
    val ItemTitle = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp, lineHeight = 17.sp,
    )

    /** Paragraphe d'explication. */
    val Body = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 19.sp,
    )

    /** Légende, aide contextuelle. */
    val Caption = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp, lineHeight = 16.sp,
    )

    /** Libellé d'onglet de la barre du bas. */
    val Tab = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 10.sp, lineHeight = 12.sp,
    )

    /** Label de section : mono 9 px, capitales, très espacé (.14em). */
    val SectionLabel = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal,
        fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.26.sp,
    )

    /** Donnée en chasse fixe (npub, coordonnées, KIN…). */
    val Data = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp, lineHeight = 13.sp,
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
