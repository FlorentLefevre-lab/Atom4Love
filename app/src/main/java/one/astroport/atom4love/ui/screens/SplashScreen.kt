package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.ui.components.AtomLogo
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/** Durée d'affichage minimale du splash, le temps d'admirer une orbite. */
const val SPLASH_HOLD_MS = 2400L

/** Le bleu du titre, repris du logo d'origine. */
private val TitleBlue = Color(0xFF5B8AFB)

/**
 * 00 · Splash — l'atome au cœur battant dessiné en vectoriel ([AtomLogo] :
 * cadence et nombre d'électrons maîtrisés, rendu net à toute taille), le temps
 * que la station restaure l'incarnation depuis le DataStore.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowNucleus, A4L.Void, centerY = 0.42f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AtomLogo(Modifier.size(220.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                "ATOM4LOVE",
                style = A4LText.Data.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.2.sp,
                ),
                color = TitleBlue,
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
