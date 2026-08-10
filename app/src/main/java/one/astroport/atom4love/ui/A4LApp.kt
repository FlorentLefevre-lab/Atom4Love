package one.astroport.atom4love.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.nostr.LoveKeyForge
import one.astroport.atom4love.ui.screens.BoardScreen
import one.astroport.atom4love.ui.screens.IncarnationScreen
import one.astroport.atom4love.ui.screens.RadarScreen
import one.astroport.atom4love.ui.screens.ResonanceScreen
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/** Les quatre destinations de la barre du bas. */
enum class A4LTab(val icon: String, val label: String, val accent: Color) {
    Radar("🌀", "Radar", A4L.Cyan),
    Board("🎴", "Plateau", A4L.Cyan),
    Bonds("💜", "Résonance", A4L.Mint),
    Nucleus("⚛", "Noyau", A4L.Cyan),
}

/**
 * Le parcours complet : on forge d'abord son noyau (aucune barre de navigation,
 * il n'y a rien à visiter tant qu'on n'a pas de clé), puis la station s'ouvre sur
 * ses trois espaces. L'onglet « Noyau » ramène à la fiche d'incarnation, désormais
 * scellée.
 */
@Composable
fun A4LApp(modifier: Modifier = Modifier) {
    var birth by remember { mutableStateOf(BirthData.Sample) }
    var forged by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(A4LTab.Radar) }

    // Les clés NOSTR se redérivent des données d'incarnation à chaque forge —
    // c'est le principe de la clé LOVE, rien à persister.
    val keys = remember(birth, forged) { if (forged) LoveKeyForge.forge(birth) else null }

    if (!forged) {
        IncarnationScreen(
            birth = birth,
            onBirthChange = { birth = it },
            forged = false,
            onForge = { forged = true; tab = A4LTab.Radar },
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().background(A4L.Deep)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                A4LTab.Radar -> RadarScreen()
                A4LTab.Board -> BoardScreen(npub = keys?.npubShort)
                A4LTab.Bonds -> ResonanceScreen()
                A4LTab.Nucleus -> IncarnationScreen(
                    birth = birth,
                    onBirthChange = { birth = it },
                    forged = true,
                    onForge = {},
                    npub = keys?.npub,
                )
            }
        }
        A4LNavBar(current = tab, onSelect = { tab = it })
    }
}

/** Barre de navigation — quatre onglets, l'actif prend la couleur de son espace. */
@Composable
private fun A4LNavBar(current: A4LTab, onSelect: (A4LTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(A4L.NavBackdrop)
            .drawBehind {
                drawRect(
                    color = A4L.StrokeFaint,
                    size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                )
            }
            .navigationBarsPadding(),
    ) {
        A4LTab.entries.forEach { entry ->
            val selected = entry == current
            Column(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable { onSelect(entry) }
                    .alpha(if (selected) 1f else 0.4f)
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    entry.icon,
                    fontSize = 16.sp,
                    // ⚛ est un glyphe monochrome : sans teinte explicite il se
                    // perdrait, là où 🌀 🎴 💜 portent leurs propres couleurs.
                    color = if (selected) entry.accent else A4L.TextStrong,
                )
                Text(
                    entry.label,
                    style = A4LText.Tab.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) entry.accent else A4L.TextStrong,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
