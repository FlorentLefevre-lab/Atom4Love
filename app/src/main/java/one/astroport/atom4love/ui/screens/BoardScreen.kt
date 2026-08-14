package one.astroport.atom4love.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.AtomCard
import one.astroport.atom4love.domain.Wave
import one.astroport.atom4love.domain.resonanceBetween
import one.astroport.atom4love.ui.components.A4LChip
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * La teinte d'une carte en main. Un cas et non une couleur : la main est
 * mémorisée pour toute la partie, elle ne doit pas retenir la lumière qu'il
 * faisait quand on l'a distribuée.
 */
private enum class CardAccent {
    Mint, Amber, Indigo, Neutral;

    val color: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Mint -> A4L.Mint
            Amber -> A4L.Amber
            Indigo -> A4L.Indigo
            Neutral -> A4L.TextBody
        }
}

/** Une carte en main : le noyau plus la teinte qui le distingue sur le plateau. */
private data class DealtCard(val card: AtomCard, val accent: CardAccent)

private val BOARD_FILTERS = listOf(
    R.string.board_filter_pentagon,
    R.string.board_filter_nearby,
    R.string.board_filter_contacts,
    R.string.board_filter_n2,
)

/**
 * 03 · Plateau « Qui est-ce ? »
 *
 * Votre carte est déjà posée. Toucher une carte de la main la dépose en face :
 * l'analyse se calcule aussitôt (harmonie / friction), et « Révéler » s'allume.
 * Toucher la carte déposée la reprend en main.
 */
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    npub: String? = null,
    /** Non nul quand le Plateau s'ouvre en plein écran depuis le Noyau. */
    onClose: (() -> Unit)? = null,
) {

    // Le npub réel (tronqué) une fois la clé forgée ; celui de la maquette sinon.
    val youLabel = stringResource(R.string.board_you)
    val you = remember(npub, youLabel) {
        AtomCard(kin = 168, wave = Wave.Phi, holder = youLabel, npub = npub ?: "npub1q4v…7f6c")
    }
    val hand = remember {
        mutableStateListOf(
            DealtCard(AtomCard(44, Wave.Octave, npub = "npub1h8p…2a19"), CardAccent.Mint),
            DealtCard(AtomCard(91, Wave.Phi, npub = "npub1t2m…5b80"), CardAccent.Amber),
            DealtCard(AtomCard(12, Wave.Octave, npub = "npub1c7k…9d31"), CardAccent.Indigo),
            DealtCard(AtomCard(7, Wave.Phi, npub = "npub1w9s…4e07"), CardAccent.Neutral),
        )
    }
    var placed by remember { mutableStateOf<DealtCard?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(0) }

    val resonance = placed?.let { resonanceBetween(you, it.card) }
    val harmony by animateFloatAsState(
        targetValue = (resonance?.harmony ?: 18) / 100f,
        animationSpec = tween(600),
        label = "harmony",
    )
    val friction by animateFloatAsState(
        targetValue = (resonance?.friction ?: 12) / 100f,
        animationSpec = tween(600),
        label = "friction",
    )

    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowBoard, A4L.Deep, centerY = 0.08f, radiusFactor = 1.3f)
            .statusBarsPadding(),
    ) {

        // ── En-tête ───────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎴", fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.board_title),
                    style = A4LText.Title,
                    color = A4L.TextHigh,
                )
            }
            if (onClose != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(A4L.Glass, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 13.sp, color = A4L.TextStrong) }
            } else {
                Text(
                    stringResource(R.string.board_relay_cards),
                    style = A4LText.Data.copy(fontSize = 10.sp),
                    color = A4L.TextDim,
                )
            }
        }

        // ── Filtres ───────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BOARD_FILTERS.forEachIndexed { index, label ->
                A4LChip(
                    label = stringResource(label),
                    selected = index == selectedFilter,
                    modifier = Modifier.clickable { selectedFilter = index },
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {

            // ── Les deux emplacements ─────────────────────────────────────
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoardCard(
                    card = you,
                    accent = A4L.Indigo,
                    title = youLabel,
                    subtitle = you.npub.orEmpty(),
                    modifier = Modifier.weight(1f),
                )
                val onBoard = placed
                if (onBoard == null) {
                    EmptySlot(Modifier.weight(1f))
                } else {
                    BoardCard(
                        card = onBoard.card,
                        accent = onBoard.accent.color,
                        title = stringResource(
                            if (revealed) {
                                R.string.board_nucleus_revealed
                            } else {
                                R.string.board_card_played
                            },
                        ),
                        subtitle = if (revealed) {
                            onBoard.card.npub.orEmpty()
                        } else {
                            stringResource(R.string.board_npub_hidden)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                hand.add(onBoard)
                                placed = null
                                revealed = false
                            },
                    )
                }
            }

            // ── Analyse en direct ─────────────────────────────────────────
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(stringResource(R.string.board_live_analysis))
                    Text(
                        stringResource(
                            when {
                                revealed -> R.string.board_state_revealed
                                placed != null -> R.string.board_state_ready
                                else -> R.string.board_state_waiting
                            },
                        ),
                        style = A4LText.Data,
                        color = if (placed != null) A4L.Cyan.copy(alpha = 0.6f) else A4L.TextGhost,
                    )
                }
                AnalysisBar(
                    label = stringResource(R.string.board_harmony),
                    value = resonance?.harmony,
                    fraction = harmony,
                    accent = A4L.Violet,
                )
                AnalysisBar(
                    label = stringResource(R.string.board_friction),
                    value = resonance?.friction,
                    fraction = friction,
                    accent = A4L.Orange,
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        // ── Votre main ────────────────────────────────────────────────────
        Column(
            Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                // Le Plateau a quitté la barre du bas : plus personne ne porte
                // l'encoche du système sous lui, il la prend lui-même.
                .then(if (onClose != null) Modifier.navigationBarsPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(stringResource(R.string.board_your_hand))
                Text(
                    stringResource(
                        if (placed == null) {
                            R.string.board_hint_play
                        } else {
                            R.string.board_hint_take_back
                        },
                    ),
                    style = A4LText.Caption.copy(fontSize = 11.sp),
                    color = A4L.TextDim.copy(alpha = 0.30f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                hand.forEach { dealt ->
                    HandCard(
                        dealt = dealt,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (placed == null) {
                                hand.remove(dealt)
                                placed = dealt
                            }
                        },
                    )
                }
                // La main se vide en gardant la largeur des emplacements restants.
                repeat(4 - hand.size) { Spacer(Modifier.weight(1f)) }
            }
            RevealButton(
                enabled = placed != null && !revealed,
                revealed = revealed,
                onClick = { revealed = true },
            )
        }
    }
}

/** Une carte posée sur le plateau. */
@Composable
private fun BoardCard(
    card: AtomCard,
    accent: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .aspectRatio(3f / 4.2f)
            // Le dégradé d'abord, le liseré ensuite : l'inverse le recouvrirait.
            .background(
                Brush.verticalGradient(listOf(accent.tint(0.16f), A4L.Ink.copy(alpha = 0.9f))),
                RoundedCornerShape(16.dp),
            )
            .border(1.dp, accent.tint(0.40f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(card.wave.symbol, style = A4LText.Data.copy(fontSize = 22.sp), color = accent)
            Text(
                "KIN ${card.kin}",
                style = A4LText.Data.copy(fontSize = 9.sp),
                color = accent,
                modifier = Modifier
                    .background(accent.tint(0.14f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = A4L.TextHigh,
            )
            Text(subtitle, style = A4LText.Data.copy(fontSize = 9.sp), color = A4L.TextDim)
        }
    }
}

/** L'emplacement libre, qui respire en attendant sa carte. */
@Composable
private fun EmptySlot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val t by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Reverse),
        label = "breathe",
    )
    Column(
        modifier
            .aspectRatio(3f / 4.2f)
            .dashedGlass(16.dp, A4L.GlassFaint.copy(alpha = 0.025f), A4L.Stroke.copy(alpha = 0.20f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("＋", fontSize = 20.sp, color = A4L.TextHigh, modifier = Modifier.alpha(0.45f * t))
        Text(
            stringResource(R.string.board_empty_slot),
            style = A4LText.Caption,
            color = A4L.TextDim.copy(alpha = 0.38f),
        )
    }
}

/** Une barre de l'analyse en direct. */
@Composable
private fun AnalysisBar(label: String, value: Int?, fraction: Float, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = A4LText.Body, color = A4L.TextBody.copy(alpha = 0.62f))
            Text(
                value?.let { stringResource(R.string.board_percent, it) } ?: "— —",
                style = A4LText.Data.copy(fontSize = 12.sp),
                color = if (value == null) A4L.TextDim.copy(alpha = 0.3f) else accent,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(A4L.StrokeFaint, RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.tint(0.35f), accent.tint(0.70f)),
                        ),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

/** Une carte de la main. */
@Composable
private fun HandCard(dealt: DealtCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(74.dp)
            .background(
                Brush.verticalGradient(
                    listOf(dealt.accent.color.tint(0.14f), A4L.Ink.copy(alpha = 0.85f)),
                ),
                RoundedCornerShape(11.dp),
            )
            .border(1.dp, dealt.accent.color.tint(0.30f), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            dealt.card.wave.symbol,
            style = A4LText.Data.copy(fontSize = 14.sp),
            color = dealt.accent.color,
        )
        Text(
            "KIN ${dealt.card.kin}",
            style = A4LText.Data.copy(fontSize = 8.sp),
            color = A4L.TextDim,
        )
    }
}

/** « ⚡ Révéler » — éteint tant qu'il n'y a qu'une carte sur le plateau. */
@Composable
private fun RevealButton(enabled: Boolean, revealed: Boolean, onClick: () -> Unit) {
    val accent = when {
        revealed -> A4L.Mint
        enabled -> A4L.Cyan
        else -> A4L.TextHigh
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .glass(
                radius = 14.dp,
                background = if (enabled || revealed) accent.tint(0.10f) else A4L.Glass.copy(alpha = 0.05f),
                border = if (enabled || revealed) accent.tint(0.40f) else A4L.Stroke,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (revealed) "🔓" else "⚡", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (revealed) R.string.board_revealed else R.string.board_reveal),
                style = A4LText.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = if (enabled || revealed) accent else A4L.TextDim.copy(alpha = 0.35f),
            )
        }
    }
}
