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
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * 🔑 Le mur — **l'essai est fini, et la porte est ouverte**.
 *
 * Il paraît quand la proposition de MULTIPASS a été refusée après une première
 * expérience complète ([one.astroport.atom4love.trial.Trial]). Il couvre le
 * Plateau, les conversations et le Monde ; il ne couvre **pas** le Noyau, qui
 * porte la fiche, le nom et les données de la personne — on ne prend jamais
 * quelqu'un en otage de ses propres données.
 *
 * ## Ce qu'il doit dire, et le ton qu'il ne doit pas prendre
 *
 * Il dit trois choses, dans cet ordre : ce dont on vient de profiter, pourquoi
 * ça s'arrête, et comment ça reprend. Il ne dit **pas** « vous avez refusé » —
 * la personne le sait, le lui rappeler ne fait que la mettre en tort. Ce n'est
 * pas une sanction : c'est la fin d'une période d'essai, qui est justement ce
 * qui lui a permis de savoir si l'application valait le compte.
 *
 * ⚠ **Rien n'est perdu.** Le noyau est scellé, la clé dérivée, le nom choisi :
 * ouvrir le compte reprend exactement là. Le mur doit le dire, sans quoi il se
 * lit comme un ultimatum, et un ultimatum se répond par une désinstallation.
 *
 * ⚠ **Le geste est unique.** Un seul bouton, qui ouvre le MULTIPASS. Pas de
 * « plus tard » : on l'a déjà proposé, et une porte de sortie qui ramène au même
 * mur n'est pas une porte, c'est une boucle.
 */
@Composable
fun TrialWall(onOpenMultipass: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowNucleus, A4L.Deep, centerY = 0.08f, radiusFactor = 1.3f)
            .padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔑", fontSize = 40.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.trial_over_title),
            style = A4LText.H2,
            color = A4L.TextHigh,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.trial_over_body),
            style = A4LText.Body,
            color = A4L.TextBody,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.trial_over_kept),
            style = A4LText.Caption,
            color = A4L.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .glass(12.dp, A4L.Indigo.tint(0.12f), A4L.Indigo.tint(0.38f))
                .clickable(onClick = onOpenMultipass),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.trial_over_open),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Indigo,
            )
        }
    }
}
