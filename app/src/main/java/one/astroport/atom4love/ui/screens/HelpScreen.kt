package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * 05 · Aide — le mode d'emploi de la station, dans ses mots à elle.
 * Accessible par l'onglet « Aide » une fois le noyau forgé, et par le « ? »
 * de l'écran de forge avant (c'est là qu'on en a le plus besoin).
 */
@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowBond, A4L.DeepAlt, radiusFactor = 1.4f)
            .statusBarsPadding(),
    ) {

        // ── Barre d'état applicative ──────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚛", color = A4L.Cyan, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    "ATOM4LOVE",
                    style = A4LText.Data.copy(letterSpacing = 1.7.sp),
                    color = A4L.TextMuted,
                )
            }
            if (onClose != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 13.sp, color = A4L.TextStrong) }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(top = 18.dp)) {
                Text("Aide", style = A4LText.H1, color = A4L.TextHigh)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Comment fonctionne la station, en cinq réponses.",
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            }

            Column(
                Modifier.padding(top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                HelpCard(
                    accent = A4L.Cyan,
                    label = "La clé LOVE",
                    body = "Votre identité se dérive de l'instant où vous êtes né : " +
                        "date, heure, lieu, onde et poids de naissance. Pas de compte, " +
                        "pas de mot de passe, rien à retenir — les cinq mêmes données " +
                        "produisent toujours la même clé, sur n'importe quelle station.",
                )
                HelpCard(
                    accent = A4L.Gold,
                    label = "Forger son noyau",
                    body = "Remplissez la fiche d'incarnation : la ligne « À renseigner » " +
                        "vous dit ce qui manque. Pour le lieu, tapez trois lettres et " +
                        "choisissez votre commune dans la liste, ou touchez 📍 pour " +
                        "utiliser votre position. Avant de forger, un récapitulatif vous " +
                        "demande une dernière relecture : après confirmation, plus rien " +
                        "n'est modifiable.",
                )
                HelpCard(
                    accent = A4L.Amber,
                    label = "Retrouver sa clé",
                    body = "Il n'y a pas de phrase de récupération à noter : vos proches " +
                        "la connaissent déjà. Ceux qui savent où et quand vous êtes né " +
                        "peuvent vous redire vos cinq données — et la même clé renaîtra.",
                )
                HelpCard(
                    accent = A4L.Red,
                    label = "Se tromper, recommencer",
                    body = "Une donnée fausse ? Dans l'onglet Noyau, « Dissoudre le " +
                        "noyau » efface la fiche et la clé de cette station (deux " +
                        "confirmations). Rien ne part sur le réseau : ressaisissez les " +
                        "bonnes données et forgez à nouveau.",
                )
                HelpCard(
                    accent = A4L.Mint,
                    label = "La station",
                    body = "🌀 Radar : les cabines à portée, par proximité réelle. " +
                        "🎴 Plateau : vos cartes. 💜 Résonance : vos liens. " +
                        "⚛ Noyau : votre fiche scellée et votre npub. En haut, " +
                        "« relay · x / y » indique combien de relais NOSTR répondent — " +
                        "vert dès que la station est reliée au réseau.",
                )
            }
        }

        // ── Pied ──────────────────────────────────────────────────────────
        Text(
            "Atom4Love ${BuildConfig.VERSION_NAME} · by AstroPort.ONE",
            style = A4LText.Data.copy(fontSize = 9.sp),
            color = A4L.TextGhost,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .navigationBarsPadding(),
        )
    }
}

/** Une carte d'aide : liseré à la couleur du sujet, titre, réponse. */
@Composable
private fun HelpCard(accent: Color, label: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(12.dp, accent.tint(0.05f), accent.tint(0.18f))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SectionLabel(label, color = accent.copy(alpha = 0.65f))
        Text(body, style = A4LText.Body, color = A4L.TextBody.copy(alpha = 0.8f))
    }
}
