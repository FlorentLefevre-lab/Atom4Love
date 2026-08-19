package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.tint
import one.astroport.atom4love.ui.theme.A4LText

/**
 * 🌍 Le monde, fermé — et pourquoi.
 *
 * **Aucune requête ne part d'ici.** Tant que la clé n'est pas activée, la
 * constellation n'est pas lue du tout : « rester en local » se tient jusqu'au
 * réseau, sinon ce ne serait qu'un rideau devant une fenêtre déjà ouverte.
 *
 * ⚠ **Le cadenas est dans la barre, pas seulement ici.** Cet écran vivait
 * derrière un segment de la Carte : il fallait aller sur la Carte, puis toucher
 * le segment fermé, pour apprendre qu'une porte existait. Le monde est
 * maintenant un onglet à lui, cadenassé et visible depuis n'importe où — on sait
 * qu'il existe avant d'avoir voulu y entrer, ce qui était l'intention depuis le
 * début. Cet écran ne fait plus que répondre à qui a poussé la porte.
 *
 * ⚠ Ce n'est pas une punition, et le texte doit continuer de le dire : la
 * constellation est faite de certificats qu'une station scelle, et la regarder
 * sans en avoir un revient à lire le registre d'un village où l'on n'habite pas.
 *
 * ## ⚠ Il n'y a PAS de bouton ici, et c'est la règle du jeu
 *
 * Cet écran portait « Ouvrir mon MULTIPASS ». Il tombait juste avant la refonte,
 * quand la porte du compte était ouverte en permanence ; il ne tombe plus. Le
 * MULTIPASS ne se propose **qu'une fois la première expérience vécue** — c'est
 * le GPS qui le dit, quand on a quitté le lieu et qu'on revient
 * ([one.astroport.atom4love.trial.Trial]). Un bouton posé là court-circuiterait
 * exactement ça : on n'aurait qu'à toucher le cadenas, le premier jour, pour se
 * voir demander une adresse e-mail avant d'avoir croisé qui que ce soit.
 *
 * La porte fermée doit donc rester **fermée et muette sur le moyen de l'ouvrir**.
 * Ce qu'elle dit — ce qu'il y a derrière et pourquoi c'est fermé — suffit à
 * donner envie ; c'est même tout ce qu'on lui demande.
 */
@Composable
fun WorldLocked(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowRadar, A4L.Deep, centerY = 0.05f, radiusFactor = 1.3f)
            .padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = 40.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.world_locked_title),
            style = A4LText.H2,
            color = A4L.TextHigh,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.world_locked_body),
            style = A4LText.Body,
            color = A4L.TextBody,
            textAlign = TextAlign.Center,
        )
    }
}
