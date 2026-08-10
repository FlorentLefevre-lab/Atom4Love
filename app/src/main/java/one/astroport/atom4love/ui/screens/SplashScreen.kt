package one.astroport.atom4love.ui.screens

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/** Durée d'affichage minimale du splash, le temps d'un tour d'électrons. */
const val SPLASH_HOLD_MS = 2400L

/**
 * 00 · Splash — l'atome au cœur battant, électrons en orbite (le GIF du logo),
 * le temps que la station restaure l'incarnation depuis le DataStore.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Le décodeur animé n'existe qu'à partir d'API 28 ; avant, le GifDecoder
    // logiciel de Coil prend le relais (minSdk 26).
    val gifLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    // Respiration lente du titre, calée sur le rythme du cœur du logo.
    val pulse by rememberInfiniteTransition(label = "splash-pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash-pulse-alpha",
    )

    Box(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowNucleus, A4L.Void, centerY = 0.42f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("android.resource://${context.packageName}/${R.raw.atome_coeur}")
                    .build(),
                imageLoader = gifLoader,
                contentDescription = "Atome ATOM4LOVE, électrons en orbite",
                modifier = Modifier.size(190.dp),
            )
            Spacer(Modifier.height(26.dp))
            Text(
                "ATOM4LOVE",
                style = A4LText.Data.copy(fontSize = 13.sp, letterSpacing = 4.6.sp),
                color = A4L.Cyan,
                modifier = Modifier.alpha(pulse),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "by AstroPort.ONE",
                style = A4LText.Data.copy(fontSize = 9.sp),
                color = A4L.TextGhost,
            )
        }
    }
}
