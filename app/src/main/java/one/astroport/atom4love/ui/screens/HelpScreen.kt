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
                    "Ce que fait la station, et pourquoi elle le fait ainsi.",
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            }

            Column(
                Modifier.padding(top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                // Le socle du discours vient d'ATOM4LOVE, l'interface web de
                // Fred (u.copylaradio.com/earth/atomic.html) : mêmes notions,
                // mêmes mots — une seule idée du monde des deux côtés.
                HelpCard(
                    accent = A4L.Violet,
                    label = "Tout est vibration",
                    body = "ATOM4LOVE n'est pas de l'astrologie. Au moment exact de votre " +
                        "naissance, la Terre occupait une position précise dans " +
                        "l'espace-temps. Vous en portez l'empreinte géométrique — et elle " +
                        "se calcule, à la minute et au degré près.",
                )
                HelpCard(
                    accent = A4L.Cyan,
                    label = "Votre empreinte · l'accord fondamental",
                    body = "Imaginez votre corps comme un instrument. Votre lieu et votre " +
                        "heure de naissance déterminent la note que vous jouez en " +
                        "permanence : votre phase personnelle. Votre poids et votre " +
                        "polarité en donnent la fréquence — l'onde biologique.",
                )
                HelpCard(
                    accent = A4L.Mint,
                    label = "La résonance · quand deux ondes se croisent",
                    body = "Rencontrer quelqu'un, c'est croiser deux phases. Si elles " +
                        "s'emboîtent, c'est la super-cohérence : on se comprend sans " +
                        "parler. Si elles se heurtent, c'est la friction créatrice : on " +
                        "est faits pour inventer ensemble. Aucun des deux n'est un score, " +
                        "et aucun n'est meilleur que l'autre.",
                )
                HelpCard(
                    accent = A4L.Violet,
                    label = "La singularité · l'esprit et la matière",
                    body = "Votre conception est l'Esprit, votre naissance la Matière. " +
                        "En les combinant, la station calcule votre archétype — la façon " +
                        "dont vous transformez l'énergie autour de vous. Vous n'avez pas " +
                        "à connaître cette date : elle se déduit de votre naissance.",
                )
                HelpCard(
                    accent = A4L.Gold,
                    label = "Votre clé temporaire",
                    body = "Au premier lancement, la station se forge une clé à elle, " +
                        "dérivée de vos cinq données. Elle suffit à tout ce qui se passe " +
                        "à portée d'antenne : le radar, la cabine, les liens de proximité. " +
                        "Elle est temporaire — c'est une station Astroport.ONE qui " +
                        "dérivera un jour votre vraie clé LOVE.",
                )
                HelpCard(
                    accent = A4L.Indigo,
                    label = "Le MULTIPASS",
                    body = "C'est votre identité décentralisée sur le réseau UPlanet : " +
                        "une clé NOSTR publiée, un portefeuille Ğ1 / Ẑen, un espace de " +
                        "stockage, et votre clé LOVE. Il s'ouvre depuis le bas de " +
                        "l'onglet Noyau, avec une adresse email. UPlanet ORIGIN, où l'on " +
                        "arrive, est un bac à sable : le Ẑen y vaut 0,1 Ğ1, et un compte " +
                        "laissé à zéro sans mouvement y est supprimé après sept jours.",
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
                        "peuvent vous redire vos cinq données — et la même clé renaîtra, " +
                        "sur n'importe quelle station. Votre lieu de naissance compte à " +
                        "1 km près : notez-le précieusement, c'est lui qui rouvre votre " +
                        "clé LOVE si vous perdez cet appareil. Le compte lui-même, en " +
                        "revanche, se récupère avec le code PASS reçu par mail.",
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
