package one.astroport.atom4love.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.ui.components.DataBadge
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/** État d'une liaison entre deux noyaux. */
private enum class BondState(val label: String, val color: Color) {
    Covalent("COVALENT", A4L.Mint),
    Network("N2", Color.White),
    Ionised("IONISÉ", A4L.Orange),
}

private data class Bond(
    val score: Int,
    val title: String,
    val detail: String,
    val state: BondState,
)

private val BONDS = listOf(
    Bond(91, "Onde Octave · KIN 44", "npub1h8p…2a19 · 1,2 km", BondState.Covalent),
    Bond(74, "Onde Φ · KIN 12", "npub1c7k…9d31 · même hexagone", BondState.Network),
    Bond(58, "Onde Octave · KIN 91", "npub1t2m…5b80 · 640 m", BondState.Ionised),
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
            Text("Résonance", style = A4LText.Title, color = A4L.TextHigh)
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
                    "Votre valence est stable",
                    style = A4LText.ItemTitle,
                    color = A4L.TextHigh,
                )
                Text(
                    "18 abonnements · 21 abonnés. Deux liaisons de plus et vous saturez.",
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
            SectionLabel("Dans votre portail")
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
    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = 6.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = ring),
                topLeft = androidx.compose.ui.geometry.Offset(ring / 2, ring / 2),
                size = androidx.compose.ui.geometry.Size(size.width - ring, size.height - ring),
            )
            drawArc(
                color = A4L.Mint,
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
                Text(bond.title, style = A4LText.ItemTitle, color = A4L.TextHigh)
                Text(bond.detail, style = A4LText.Data, color = A4L.TextMuted.copy(alpha = 0.38f))
            }
            Spacer(Modifier.width(8.dp))
            DataBadge(
                bond.state.label,
                bond.state.color,
                background = bond.state.color.tint(0.14f),
                border = null,
                radius = 7.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BondAction("Attester", A4L.Mint, primary = true, modifier = Modifier.weight(1f))
            BondAction("Ouvrir un canal", Color.White, primary = false, modifier = Modifier.weight(1f))
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
                bond.title,
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.TextHigh,
            )
            Text(bond.detail, style = A4LText.Data, color = A4L.TextDim)
        }
        Spacer(Modifier.width(8.dp))
        when (bond.state) {
            BondState.Ionised -> DataBadge(
                bond.state.label,
                A4L.Orange,
                background = A4L.Orange.tint(0.12f),
                border = A4L.Orange.tint(0.30f),
                radius = 7.dp,
            )
            else -> DataBadge(
                bond.state.label,
                A4L.TextBody.copy(alpha = 0.45f),
                background = Color.White.copy(alpha = 0.06f),
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
