package one.astroport.atom4love.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.components.DataBadge
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/** État d'une liaison entre deux noyaux. */
private enum class BondState(@StringRes val labelRes: Int) {
    Covalent(R.string.bond_covalent),
    Network(R.string.bond_network),
    Ionised(R.string.bond_ionised),
    ;

    /** L'état dit lequel ; la palette dit de quelle couleur, à cette heure-ci. */
    val color: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Covalent -> A4L.Mint
            // le lien de réseau n'a pas de couleur à lui : il prend celle du
            // texte le plus appuyé, blanc la nuit et noir le jour
            Network -> A4L.TextHigh
            Ionised -> A4L.Orange
        }
}

/**
 * Une liaison telle qu'elle s'affiche. Titre et détail sont des ressources et
 * non des phrases : cet écran est encore une maquette, mais ce qu'elle montre
 * s'affiche pour de vrai, et doit donc se lire dans la langue choisie.
 */
private data class Bond(
    val score: Int,
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val state: BondState,
)

private val BONDS = listOf(
    Bond(
        91,
        R.string.resonance_demo_bond1_title,
        R.string.resonance_demo_bond1_detail,
        BondState.Covalent,
    ),
    Bond(
        74,
        R.string.resonance_demo_bond2_title,
        R.string.resonance_demo_bond2_detail,
        BondState.Network,
    ),
    Bond(
        58,
        R.string.resonance_demo_bond3_title,
        R.string.resonance_demo_bond3_detail,
        BondState.Ionised,
    ),
)

/**
 * 04 · Résonance — valence sociale, liaisons covalentes, ionisation.
 *
 * La valence mesure l'équilibre abonnements / abonnés : trop de déséquilibre et le
 * noyau s'ionise. La première liaison, covalente, est la seule à proposer des actions.
 */
@Composable
fun ResonanceScreen(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowBond, A4L.Deep, centerY = 0.06f, radiusFactor = 1.3f)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {

        // ── En-tête ───────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.resonance_title), style = A4LText.Title, color = A4L.TextHigh)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DataBadge("🔑 a4l", A4L.Cyan, border = A4L.Cyan.tint(0.35f))
                DataBadge("⚡ 42,0 ẑen", A4L.Amber, border = A4L.Amber.tint(0.30f))
            }
        }

        // ── Valence ───────────────────────────────────────────────────────
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                .fillMaxWidth()
                .glass(15.dp, A4L.GlassSoft.copy(alpha = 0.035f), A4L.StrokeSoft)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ValenceGauge(value = 63)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.resonance_valence_stable),
                    style = A4LText.ItemTitle,
                    color = A4L.TextHigh,
                )
                Text(
                    stringResource(R.string.resonance_valence_detail),
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                )
            }
        }

        // ── Portail ───────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(R.string.resonance_in_your_portal))
            Text(
                "P02 · Sirius",
                style = A4LText.Caption.copy(fontSize = 11.sp),
                color = A4L.TextDim.copy(alpha = 0.3f),
                maxLines = 1,
            )
        }

        // ── Liaisons ──────────────────────────────────────────────────────
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            BONDS.forEach { bond ->
                if (bond.state == BondState.Covalent) CovalentBondCard(bond) else BondRow(bond)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/** Jauge circulaire de valence — anneau menthe, score au centre. */
@Composable
private fun ValenceGauge(value: Int) {
    // le Canvas dessine hors composition : les deux teintes se prennent avant
    val mint = A4L.Mint
    val rail = A4L.StrokeFaint
    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = 6.dp.toPx()
            drawArc(
                color = rail,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = ring),
                topLeft = androidx.compose.ui.geometry.Offset(ring / 2, ring / 2),
                size = androidx.compose.ui.geometry.Size(size.width - ring, size.height - ring),
            )
            drawArc(
                color = mint,
                startAngle = -90f, sweepAngle = 360f * (value / 100f), useCenter = false,
                style = Stroke(width = ring),
                topLeft = androidx.compose.ui.geometry.Offset(ring / 2, ring / 2),
                size = androidx.compose.ui.geometry.Size(size.width - ring, size.height - ring),
            )
        }
        Text(
            value.toString(),
            style = A4LText.Data.copy(fontSize = 13.sp),
            color = A4L.Mint,
        )
    }
}

/** La liaison covalente : mise en avant, avec ses deux actions. */
@Composable
private fun CovalentBondCard(bond: Bond) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(15.dp, A4L.Mint.tint(0.07f), A4L.Mint.tint(0.32f))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                bond.score.toString(),
                style = A4LText.Data.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                color = A4L.Mint,
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(bond.title), style = A4LText.ItemTitle, color = A4L.TextHigh)
                Text(
                    stringResource(bond.detail),
                    style = A4LText.Data,
                    color = A4L.TextMuted.copy(alpha = 0.38f),
                )
            }
            Spacer(Modifier.width(8.dp))
            DataBadge(
                stringResource(bond.state.labelRes),
                bond.state.color,
                background = bond.state.color.tint(0.14f),
                border = null,
                radius = 7.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BondAction(
                stringResource(R.string.resonance_attest),
                A4L.Mint,
                primary = true,
                modifier = Modifier.weight(1f),
            )
            BondAction(
                stringResource(R.string.resonance_open_channel),
                A4L.TextHigh,
                primary = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Une liaison secondaire : score, identité, état. */
@Composable
private fun BondRow(bond: Bond) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(14.dp, A4L.GlassFaint, A4L.Stroke.copy(alpha = 0.08f))
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            bond.score.toString(),
            style = A4LText.Data.copy(fontSize = 22.sp),
            color = A4L.TextStrong,
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(bond.title),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.TextHigh,
            )
            Text(stringResource(bond.detail), style = A4LText.Data, color = A4L.TextDim)
        }
        Spacer(Modifier.width(8.dp))
        when (bond.state) {
            BondState.Ionised -> DataBadge(
                stringResource(bond.state.labelRes),
                A4L.Orange,
                background = A4L.Orange.tint(0.12f),
                border = A4L.Orange.tint(0.30f),
                radius = 7.dp,
            )
            else -> DataBadge(
                stringResource(bond.state.labelRes),
                A4L.TextBody.copy(alpha = 0.45f),
                background = A4L.Glass,
                border = null,
                radius = 7.dp,
            )
        }
    }
}

/** Bouton d'action d'une liaison. */
@Composable
private fun BondAction(
    label: String,
    accent: Color,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(44.dp)
            .glass(
                radius = 11.dp,
                background = if (primary) accent.tint(0.14f) else A4L.Glass.copy(alpha = 0.05f),
                border = if (primary) accent.tint(0.40f) else A4L.Stroke.copy(alpha = 0.13f),
            )
            .clickable { /* TODO : émettre le kind 7 (attestation) / ouvrir le canal NOSTR */ },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = A4LText.Body.copy(
                fontSize = 12.5.sp,
                fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = if (primary) accent else A4L.TextStrong,
        )
    }
}
