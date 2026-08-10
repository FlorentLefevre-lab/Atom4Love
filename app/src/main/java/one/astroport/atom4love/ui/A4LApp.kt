package one.astroport.atom4love.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.astroport.atom4love.data.IncarnationStore
import one.astroport.atom4love.data.SavedIncarnation
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.nostr.LoveKeyForge
import one.astroport.atom4love.ui.components.ElectronSweep
import one.astroport.atom4love.ui.screens.BoardScreen
import one.astroport.atom4love.ui.screens.IncarnationScreen
import one.astroport.atom4love.ui.screens.RadarScreen
import one.astroport.atom4love.ui.screens.ResonanceScreen
import one.astroport.atom4love.ui.screens.SPLASH_HOLD_MS
import one.astroport.atom4love.ui.screens.SplashScreen
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
 * Le parcours complet : le splash (l'atome au cœur battant) couvre la
 * restauration de l'incarnation depuis le DataStore ; puis on forge son noyau
 * si ce n'est pas déjà fait, et la station s'ouvre sur ses trois espaces.
 * L'onglet « Noyau » ramène à la fiche d'incarnation, désormais scellée.
 */
@Composable
fun A4LApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { IncarnationStore(context.applicationContext) }

    var restored by remember { mutableStateOf<SavedIncarnation?>(null) }
    var storeReady by remember { mutableStateOf(false) }
    var splashHoldDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        restored = store.load()
        storeReady = true
    }
    // Le splash reste affiché un temps minimal, même si la restauration est
    // instantanée : c'est le battement d'ouverture de la station.
    LaunchedEffect(Unit) {
        delay(SPLASH_HOLD_MS)
        splashHoldDone = true
    }

    Crossfade(
        targetState = storeReady && splashHoldDone,
        animationSpec = tween(650),
        label = "splash",
    ) { ready ->
        if (!ready) {
            SplashScreen(modifier)
        } else {
            Station(store = store, restored = restored, modifier = modifier)
        }
    }
}

/** La station elle-même, une fois l'incarnation restaurée (ou vierge). */
@Composable
private fun Station(
    store: IncarnationStore,
    restored: SavedIncarnation?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var birth by remember { mutableStateOf(restored?.birth ?: BirthData.Sample) }
    var forged by remember { mutableStateOf(restored?.forged ?: false) }
    var tab by rememberSaveable { mutableStateOf(A4LTab.Radar) }

    // Les clés NOSTR se redérivent des données d'incarnation à chaque démarrage —
    // c'est le principe de la clé LOVE : seule la fiche est persistée, jamais la clé.
    val keys = remember(birth, forged) { if (forged) LoveKeyForge.forge(birth) else null }

    fun updateBirth(b: BirthData) {
        birth = b
        scope.launch { store.save(b, forged) }
    }

    fun forge() {
        forged = true
        tab = A4LTab.Radar
        scope.launch { store.save(birth, forged = true) }
    }

    Crossfade(
        targetState = forged,
        animationSpec = tween(550),
        label = "forge",
    ) { isForged ->
        if (!isForged) {
            IncarnationScreen(
                birth = birth,
                onBirthChange = ::updateBirth,
                forged = false,
                onForge = ::forge,
                modifier = modifier,
            )
        } else {
            Column(modifier.fillMaxSize().background(A4L.Deep)) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            // Le contenu glisse dans le sens de la navigation,
                            // porté par le vol d'électrons de l'overlay.
                            val forward = targetState.ordinal >= initialState.ordinal
                            (slideInHorizontally(tween(380)) { w -> if (forward) w / 12 else -w / 12 } +
                                fadeIn(tween(320, delayMillis = 40)))
                                .togetherWith(
                                    slideOutHorizontally(tween(380)) { w -> if (forward) -w / 16 else w / 16 } +
                                        fadeOut(tween(220)),
                                )
                        },
                        label = "tab",
                    ) { t ->
                        when (t) {
                            A4LTab.Radar -> RadarScreen()
                            A4LTab.Board -> BoardScreen(npub = keys?.npubShort)
                            A4LTab.Bonds -> ResonanceScreen()
                            A4LTab.Nucleus -> IncarnationScreen(
                                birth = birth,
                                onBirthChange = ::updateBirth,
                                forged = true,
                                onForge = {},
                                npub = keys?.npub,
                            )
                        }
                    }
                    ElectronSweep(trigger = tab)
                }
                A4LNavBar(current = tab, onSelect = { tab = it })
            }
        }
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
