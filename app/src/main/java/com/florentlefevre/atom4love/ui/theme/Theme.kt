package com.florentlefevre.atom4love.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * ATOM4LOVE n'a qu'un seul thème : le dark glassmorphique du bandeau NOSTR.
 * Pas de thème clair, pas de couleur dynamique — l'identité visuelle est la même
 * sur toutes les stations, c'est ce qui rend une cabine reconnaissable.
 */
private val A4LColorScheme = darkColorScheme(
    primary = A4L.Cyan,
    onPrimary = A4L.Void,
    primaryContainer = A4L.Cyan.tint(0.10f),
    onPrimaryContainer = A4L.Cyan,
    secondary = A4L.Mint,
    onSecondary = A4L.Void,
    secondaryContainer = A4L.Mint.tint(0.10f),
    onSecondaryContainer = A4L.Mint,
    tertiary = A4L.Indigo,
    onTertiary = A4L.Void,
    background = A4L.Deep,
    onBackground = A4L.TextHigh,
    surface = A4L.Ink,
    onSurface = A4L.TextHigh,
    surfaceVariant = A4L.Glass,
    onSurfaceVariant = A4L.TextBody,
    outline = A4L.Stroke,
    outlineVariant = A4L.StrokeFaint,
    error = A4L.Red,
    onError = A4L.Void,
    scrim = Color.Black,
)

@Composable
fun Atom4LoveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = A4LColorScheme,
        typography = Typography,
    ) {
        // Sans Surface englobante, LocalContentColor vaut noir : les glyphes
        // monochromes (⚛, Φ) disparaissent sur le fond de la station.
        CompositionLocalProvider(LocalContentColor provides A4L.TextHigh, content = content)
    }
}
