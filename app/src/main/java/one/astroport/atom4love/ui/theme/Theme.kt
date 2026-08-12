package one.astroport.atom4love.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * ATOM4LOVE a deux lumières et une seule identité : le glassmorphisme du
 * bandeau NOSTR, de nuit comme de jour. Pas de couleur dynamique — les accents
 * sont les mêmes sur toutes les stations, c'est ce qui rend une cabine
 * reconnaissable ; le thème ne fait que dire à quelle heure on la regarde.
 */
private fun schemeOf(p: A4LPalette) = if (p.dark) {
    darkColorScheme(
        primary = p.cyan,
        onPrimary = p.void,
        primaryContainer = p.cyan.tint(0.10f),
        onPrimaryContainer = p.cyan,
        secondary = p.mint,
        onSecondary = p.void,
        secondaryContainer = p.mint.tint(0.10f),
        onSecondaryContainer = p.mint,
        tertiary = p.indigo,
        onTertiary = p.void,
        background = p.deep,
        onBackground = p.textHigh,
        surface = p.ink,
        onSurface = p.textHigh,
        surfaceVariant = p.glass,
        onSurfaceVariant = p.textBody,
        outline = p.stroke,
        outlineVariant = p.strokeFaint,
        error = p.red,
        onError = p.void,
        scrim = Color.Black,
    )
} else {
    lightColorScheme(
        primary = p.cyan,
        // un accent qui a descendu en luminosité porte du blanc, plus du fond
        onPrimary = Color.White,
        primaryContainer = p.cyan.tint(0.12f),
        onPrimaryContainer = p.cyan,
        secondary = p.mint,
        onSecondary = Color.White,
        secondaryContainer = p.mint.tint(0.12f),
        onSecondaryContainer = p.mint,
        tertiary = p.indigo,
        onTertiary = Color.White,
        background = p.deep,
        onBackground = p.textHigh,
        surface = p.ink,
        onSurface = p.textHigh,
        surfaceVariant = p.glass,
        onSurfaceVariant = p.textBody,
        outline = p.stroke,
        outlineVariant = p.strokeFaint,
        error = p.red,
        onError = Color.White,
        scrim = Color.Black,
    )
}

@Composable
fun Atom4LoveTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) A4LDark else A4LLight
    MaterialTheme(
        colorScheme = schemeOf(palette),
        typography = Typography,
    ) {
        // Sans Surface englobante, LocalContentColor vaut noir : les glyphes
        // monochromes (⚛, Φ) disparaissent sur le fond de la station.
        CompositionLocalProvider(
            LocalA4L provides palette,
            LocalContentColor provides palette.textHigh,
            content = content,
        )
    }
}
