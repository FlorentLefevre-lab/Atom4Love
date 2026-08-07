package one.astroport.atom4love.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette ATOM4LOVE — reprise du bandeau NOSTR unifié (uplanet-header.js) :
 * dark glassmorphique, couleurs d'état saturées sur fond quasi noir.
 */
object A4L {

    // --- Fonds ---
    val Void = Color(0xFF05050C)          // fond de la station
    val Deep = Color(0xFF07070E)          // fond d'écran (extérieur du dégradé)
    val DeepAlt = Color(0xFF08080F)       // idem, écran Incarnation
    val Ink = Color(0xFF0A0A14)           // pastilles pleines (centre de jauge)
    val NavBackdrop = Color(0xCC06060E)   // rgba(6,6,14,.8) — barre de navigation

    // Cœurs de dégradé, un par écran
    val GlowNucleus = Color(0xFF101024)   // 01 Incarnation
    val GlowRadar = Color(0xFF0A1A1A)     // 02 Radar
    val GlowBoard = Color(0xFF16101F)     // 03 Plateau
    val GlowBond = Color(0xFF101A18)      // 04 Résonance

    // --- Couleurs d'état ---
    val Cyan = Color(0xFF00FFCC)          // ATOM4LOVE
    val Mint = Color(0xFF86EFAC)          // Onde Φ, lien covalent, actif
    val Green = Color(0xFF4ADE80)         // connecté (point d'état)
    val Amber = Color(0xFFFBBF24)         // ẐEN, alerte
    val Gold = Color(0xFFF59E0B)          // action primaire (forger)
    val Orange = Color(0xFFFB923C)        // ionisé, friction
    val Indigo = Color(0xFFAAB4FF)        // MULTIPASS
    val Violet = Color(0xFFC48AFF)        // harmonie
    val Red = Color(0xFFF87171)           // erreur

    // --- Texte (blanc dégressif, comme les opacités du bandeau web) ---
    val TextHigh = Color.White.copy(alpha = 0.90f)
    val TextStrong = Color.White.copy(alpha = 0.72f)
    val TextBody = Color.White.copy(alpha = 0.50f)
    val TextMuted = Color.White.copy(alpha = 0.42f)
    val TextDim = Color.White.copy(alpha = 0.35f)
    val TextFaint = Color.White.copy(alpha = 0.28f)
    val TextGhost = Color.White.copy(alpha = 0.22f)

    // --- Surfaces vitrées ---
    val Glass = Color.White.copy(alpha = 0.06f)
    val GlassSoft = Color.White.copy(alpha = 0.04f)
    val GlassFaint = Color.White.copy(alpha = 0.03f)
    val Stroke = Color.White.copy(alpha = 0.12f)
    val StrokeSoft = Color.White.copy(alpha = 0.09f)
    val StrokeFaint = Color.White.copy(alpha = 0.07f)
}

/** Teinte d'accent à faible opacité, pour les fonds de carte colorés. */
fun Color.tint(alpha: Float): Color = copy(alpha = alpha)
