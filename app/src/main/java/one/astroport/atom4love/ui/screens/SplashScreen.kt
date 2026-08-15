package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.ui.components.AtomLogo
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.Ornament

/** Durée d'affichage minimale du splash, le temps d'admirer une orbite. */
const val SPLASH_HOLD_MS = 2400L

/** Le bleu du titre, repris du logo d'origine. */
private val TitleBlue = Color(0xFF5B8AFB)

/**
 * 00 · Splash — l'atome au cœur battant dessiné en vectoriel ([AtomLogo] :
 * cadence et nombre d'électrons maîtrisés, rendu net à toute taille), le temps
 * que la station restaure l'incarnation depuis le DataStore.
 *
 * L'atome et le nom tiennent le centre ; la filiation — *Designed, Made, and
 * Powered by Astroport.ONE* — se pose **tout en bas de l'écran**, en grand. Le
 * pied de page qu'on a chassé de partout ailleurs revient ici, et c'est le seul
 * endroit où il a sa place : rien ne se lit sur cet écran, on le regarde. La
 * signature peut donc occuper le bord sans voler de la place à quoi que ce soit.
 *
 * En anglais, et non traduite : c'est une signature, pas une phrase. La bulle de
 * version des Réglages porte la même, en petit, là où l'on cherche qui publie.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowNucleus, A4L.Void, centerY = 0.42f),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AtomLogo(Modifier.size(220.dp))
            Spacer(Modifier.height(18.dp))
            // Le nom en capitales romaines à pleins et déliés. Moins d'interlettrage
            // qu'au grotesque : les empattements espacent déjà l'œil, et 5,2 sp
            // faisaient tomber le mot en lettres détachées.
            Text(
                "ATOM4LOVE",
                style = A4LText.Data.copy(
                    fontFamily = Ornament,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.4.sp,
                ),
                color = TitleBlue,
            )
        }

        // La signature, posée sur le bord bas. La marge latérale porte la
        // coupure : sur un écran large la phrase tient d'un trait, sur un
        // téléphone étroit elle passe à deux lignes et « Astroport.ONE » tombe
        // seule sur la seconde — c'est le mot qu'on veut voir en dernier.
        Text(
            "Designed, Made, and Powered by Astroport.ONE",
            style = A4LText.Body.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            ),
            color = A4L.TextBody,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 32.dp, end = 32.dp, bottom = 30.dp),
        )
    }
}
