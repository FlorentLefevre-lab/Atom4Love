package one.astroport.atom4love.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette ATOM4LOVE — reprise du bandeau NOSTR unifié (uplanet-header.js) :
 * glassmorphisme, couleurs d'état saturées.
 *
 * Elle existe en deux lumières, [A4LDark] et [A4LLight]. Ce sont **les mêmes
 * couleurs vues à deux heures du jour**, pas deux identités : la teinte d'un
 * accent ne bouge pas d'un thème à l'autre, seule sa luminosité descend pour
 * rester lisible sur fond clair. Un cyan reste ce cyan-là ; c'est ce qui rend
 * une cabine reconnaissable, de nuit comme de jour.
 *
 * Les champs sont en minuscule ; on ne les lit presque jamais directement — on
 * passe par [A4L], qui sert celle du thème courant sous les mêmes noms qu'avant.
 */
@Immutable
data class A4LPalette(
    // --- Fonds ---
    /** Fond de la station. */
    val void: Color,
    /** Fond d'écran (extérieur du dégradé). */
    val deep: Color,
    /** Idem, écran Incarnation. */
    val deepAlt: Color,
    /** Pastilles pleines (centre de jauge). */
    val ink: Color,
    /** Barre de navigation, translucide. */
    val navBackdrop: Color,

    // Cœurs de dégradé, un par écran
    val glowNucleus: Color,
    val glowRadar: Color,
    val glowBoard: Color,
    val glowBond: Color,

    // --- Couleurs d'état ---
    val cyan: Color,
    val mint: Color,
    val green: Color,
    val amber: Color,
    val gold: Color,
    val orange: Color,
    val indigo: Color,
    val violet: Color,
    val red: Color,

    // --- Marques des technologies de liaison ---
    /**
     * Le bleu du Bluetooth SIG, `#0082FC`, **identique dans les deux
     * lumières** : c'est une couleur de marque, pas un accent de la station.
     * La descendre pour le thème clair en ferait un autre bleu.
     */
    val bluetoothBrand: Color,
    /**
     * La marque Wi-Fi Alliance est **monochrome** : noire sur fond clair,
     * blanche en « reverse signature » sur fond sombre. Il n'existe pas de
     * couleur Wi-Fi — d'où ce champ, qui suit le thème au lieu d'une teinte.
     */
    val wifiBrand: Color,

    // --- Texte, du plus appuyé au plus effacé ---
    val textHigh: Color,
    val textStrong: Color,
    val textBody: Color,
    val textMuted: Color,
    val textDim: Color,
    val textFaint: Color,
    val textGhost: Color,

    // --- Surfaces vitrées ---
    val glass: Color,
    val glassSoft: Color,
    val glassFaint: Color,
    val stroke: Color,
    val strokeSoft: Color,
    val strokeFaint: Color,

    /** Vrai pour [A4LDark] : ce que les barres système et Material doivent savoir. */
    val dark: Boolean,
)

/** La nuit — le thème d'origine de la station, servi à qui le demande. */
val A4LDark = A4LPalette(
    void = Color(0xFF05050C),
    deep = Color(0xFF07070E),
    deepAlt = Color(0xFF08080F),
    ink = Color(0xFF0A0A14),
    navBackdrop = Color(0xCC06060E),      // rgba(6,6,14,.8)

    glowNucleus = Color(0xFF101024),      // 01 Incarnation
    glowRadar = Color(0xFF0A1A1A),        // 02 Radar
    glowBoard = Color(0xFF16101F),        // 03 Plateau
    glowBond = Color(0xFF101A18),         // 04 Résonance

    cyan = Color(0xFF00FFCC),             // ATOM4LOVE
    mint = Color(0xFF86EFAC),             // Onde Φ, lien covalent, actif
    green = Color(0xFF4ADE80),            // connecté (point d'état)
    amber = Color(0xFFFBBF24),            // ẐEN, alerte
    gold = Color(0xFFF59E0B),             // action primaire (forger)
    orange = Color(0xFFFB923C),           // ionisé, friction
    indigo = Color(0xFFAAB4FF),           // MULTIPASS
    violet = Color(0xFFC48AFF),           // harmonie
    red = Color(0xFFF87171),              // erreur

    bluetoothBrand = Color(0xFF0082FC),   // Bluetooth SIG
    wifiBrand = Color(0xFFF2F5F7),        // Wi-Fi Alliance, signature inversée

    // blanc dégressif, comme les opacités du bandeau web
    textHigh = Color.White.copy(alpha = 0.90f),
    textStrong = Color.White.copy(alpha = 0.72f),
    textBody = Color.White.copy(alpha = 0.50f),
    textMuted = Color.White.copy(alpha = 0.42f),
    textDim = Color.White.copy(alpha = 0.35f),
    textFaint = Color.White.copy(alpha = 0.28f),
    textGhost = Color.White.copy(alpha = 0.22f),

    glass = Color.White.copy(alpha = 0.06f),
    glassSoft = Color.White.copy(alpha = 0.04f),
    glassFaint = Color.White.copy(alpha = 0.03f),
    stroke = Color.White.copy(alpha = 0.12f),
    strokeSoft = Color.White.copy(alpha = 0.09f),
    strokeFaint = Color.White.copy(alpha = 0.07f),

    dark = true,
)

/**
 * Le jour — la même station, éclairée autrement. **C'est celui qu'on sert tant
 * que rien n'a été choisi.**
 *
 * Trois règles ont produit ces valeurs, et il faut les tenir si on en ajoute :
 *
 * 1. **La teinte ne bouge pas.** Un accent descend en luminosité jusqu'à porter
 *    sur blanc, il ne change pas de couleur. `#00FFCC` fait 1,3:1 sur du blanc,
 *    illisible ; `#00A383` en fait 3,4:1 et reste le même cyan.
 * 2. **Le verre s'inverse en encre.** Là où la nuit pose du blanc translucide,
 *    le jour pose du noir translucide — un peu moins, il pèse davantage.
 * 3. **Les traits se marquent.** Un filet à 7 % de blanc se voit sur du noir ;
 *    à 7 % de noir sur du blanc, il disparaît. Ils montent tous d'un cran.
 */
val A4LLight = A4LPalette(
    void = Color(0xFFF6F6FA),
    deep = Color(0xFFF2F2F7),
    deepAlt = Color(0xFFF1F1F6),
    // la pastille pleine était le point le plus sombre de la nuit ; de jour
    // c'est le plus clair, sinon elle se creuse au lieu de ressortir
    ink = Color(0xFFFFFFFF),
    navBackdrop = Color(0xCCF8F8FC),

    glowNucleus = Color(0xFFE8E8F6),
    glowRadar = Color(0xFFE2F2EF),
    glowBoard = Color(0xFFF0E9F7),
    glowBond = Color(0xFFE5F1EC),

    cyan = Color(0xFF00A383),
    mint = Color(0xFF3E9C63),
    green = Color(0xFF2E9E56),
    amber = Color(0xFFA87A06),
    gold = Color(0xFFB87503),
    orange = Color(0xFFC2600F),
    indigo = Color(0xFF5560C8),
    violet = Color(0xFF7B44C9),
    red = Color(0xFFC43D3D),

    bluetoothBrand = Color(0xFF0082FC),   // une marque ne change pas d'heure
    wifiBrand = Color(0xFF15181A),        // Wi-Fi Alliance, signature positive

    // noir dégressif ; les niveaux hauts descendent un peu (le noir sur blanc
    // pèse plus que l'inverse), les niveaux bas remontent pour rester lisibles
    textHigh = Color.Black.copy(alpha = 0.88f),
    textStrong = Color.Black.copy(alpha = 0.70f),
    textBody = Color.Black.copy(alpha = 0.55f),
    textMuted = Color.Black.copy(alpha = 0.48f),
    textDim = Color.Black.copy(alpha = 0.40f),
    textFaint = Color.Black.copy(alpha = 0.32f),
    textGhost = Color.Black.copy(alpha = 0.26f),

    glass = Color.Black.copy(alpha = 0.05f),
    glassSoft = Color.Black.copy(alpha = 0.035f),
    glassFaint = Color.Black.copy(alpha = 0.025f),
    stroke = Color.Black.copy(alpha = 0.14f),
    strokeSoft = Color.Black.copy(alpha = 0.10f),
    strokeFaint = Color.Black.copy(alpha = 0.07f),

    dark = false,
)

/** La palette du moment. Posée par `Atom4LoveTheme`, jamais ailleurs. */
val LocalA4L = staticCompositionLocalOf { A4LLight }

/**
 * La palette du thème courant, sous les noms qu'elle a toujours eus.
 *
 * `A4L.Cyan` s'écrit pareil qu'au temps où la palette était figée ; il lit
 * maintenant celle que le thème a posée. C'est pour ça que ces accès sont
 * `@Composable` : hors composition il n'y a pas de thème, et prendre la nuit
 * par défaut ferait un écran clair avec des morceaux de nuit dedans. Aux deux
 * ou trois endroits qui vivent hors composition — les barres système — on
 * nomme [A4LDark] ou [A4LLight] explicitement.
 */
object A4L {
    val Void: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.void
    val Deep: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.deep
    val DeepAlt: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.deepAlt
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.ink
    val NavBackdrop: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.navBackdrop

    val GlowNucleus: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.glowNucleus
    val GlowRadar: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.glowRadar
    val GlowBoard: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.glowBoard
    val GlowBond: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.glowBond

    val Cyan: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.cyan
    val Mint: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.mint
    val Green: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.green
    val Amber: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.amber
    val Gold: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.gold
    val Orange: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.orange
    val Indigo: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.indigo
    val Violet: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.violet
    val Red: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.red

    val TextHigh: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.textHigh
    val TextStrong: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.textStrong
    val TextBody: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.textBody
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.textMuted
    val TextDim: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.textDim
    val TextFaint: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.textFaint
    val TextGhost: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.textGhost

    val BluetoothBrand: Color
        @Composable @ReadOnlyComposable get() = LocalA4L.current.bluetoothBrand
    val WifiBrand: Color
        @Composable @ReadOnlyComposable get() = LocalA4L.current.wifiBrand

    val Glass: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.glass
    val GlassSoft: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.glassSoft
    val GlassFaint: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.glassFaint
    val Stroke: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.stroke
    val StrokeSoft: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.strokeSoft
    val StrokeFaint: Color @Composable @ReadOnlyComposable get() = LocalA4L.current.strokeFaint
}

/** Teinte d'accent à faible opacité, pour les fonds de carte colorés. */
fun Color.tint(alpha: Float): Color = copy(alpha = alpha)
