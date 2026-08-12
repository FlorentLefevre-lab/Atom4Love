package one.astroport.atom4love.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.chat.CabinChat
import one.astroport.atom4love.chat.Medium
import one.astroport.atom4love.chat.net.P2pGroup
import one.astroport.atom4love.data.IncarnationStore
import one.astroport.atom4love.data.MultipassAccount
import one.astroport.atom4love.data.MultipassStore
import one.astroport.atom4love.data.SavedIncarnation
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.multipass.Enrollment
import one.astroport.atom4love.multipass.MultipassService
import one.astroport.atom4love.nostr.Bech32
import one.astroport.atom4love.nostr.CabinSalon
import one.astroport.atom4love.nostr.LocalRelayScout
import one.astroport.atom4love.nostr.LoveKeyForge
import one.astroport.atom4love.nostr.NostrKeys
import one.astroport.atom4love.nostr.RelayStation
import one.astroport.atom4love.proximity.CellLocator
import one.astroport.atom4love.proximity.ProximityService
import one.astroport.atom4love.ui.components.ElectronSweep
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.screens.BoardScreen
import one.astroport.atom4love.ui.screens.HelpScreen
import one.astroport.atom4love.ui.screens.IncarnationScreen
import one.astroport.atom4love.ui.screens.MultipassScreen
import one.astroport.atom4love.ui.screens.RadarScreen
import one.astroport.atom4love.ui.screens.ResonanceScreen
import one.astroport.atom4love.ui.screens.SPLASH_HOLD_MS
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.screens.SplashScreen
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/** Les cinq destinations de la barre du bas. */
enum class A4LTab(
    /** L'emoji reste en dur : un pictogramme n'est d'aucune langue. */
    val icon: String,
    @StringRes val labelRes: Int,
) {
    Radar("🌀", R.string.tab_radar),
    Board("🎴", R.string.tab_board),
    Bonds("💜", R.string.tab_bonds),
    Nucleus("⚛", R.string.tab_nucleus),
    Help("❓", R.string.tab_help),
    ;

    /**
     * La couleur de l'onglet une fois choisi. L'onglet dit **quel** accent lui
     * revient, la palette dit de quelle couleur il est à cette heure-ci — comme
     * [labelRes] dit quel mot et les ressources dans quelle langue.
     */
    val accent: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Radar, Board, Nucleus -> A4L.Cyan
            Bonds -> A4L.Mint
            Help -> A4L.Indigo
        }
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
    // Premier lancement : fiche entièrement vierge — aucune donnée d'exemple.
    var birth by remember { mutableStateOf(restored?.birth ?: BirthData.Empty) }
    var forged by remember { mutableStateOf(restored?.forged ?: false) }
    var tab by rememberSaveable { mutableStateOf(A4LTab.Radar) }

    // ── Le compte Astroport.ONE, s'il y en a un ───────────────────────────
    val context = LocalContext.current
    val multipassStore = remember { MultipassStore(context.applicationContext) }
    val enrollment = remember {
        Enrollment(scope, MultipassService(BuildConfig.ASTROPORT_USPOT), multipassStore)
    }
    val enrollStep by enrollment.step.collectAsState()
    var account by remember { mutableStateOf<MultipassAccount?>(null) }
    var showMultipass by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { account = enrollment.restore() }
    // Le coffre fait foi, à chaque étape et pas seulement au succès : la
    // création réussit parfois là où l'activation échoue — le compte existe
    // alors pour de bon, et l'écran doit le montrer plutôt qu'un formulaire
    // vide qui inviterait à en créer un second.
    LaunchedEffect(enrollStep) { account = enrollment.restore() }

    // L'identité de la station. Tant qu'aucune station ne l'a dérivée, c'est le
    // noyau provisoire — redérivé des cinq données à chaque démarrage, jamais
    // persisté. Dès qu'un MULTIPASS rend sa clé LOVE, celle-ci prend la place :
    // c'est la station qui fait autorité sur qui l'on est.
    val loveKeys = remember(account) {
        account?.takeIf { it.loveActivated }?.let { saved ->
            runCatching { NostrKeys(Bech32.decode(saved.loveNsec).second) }
                .onFailure { Log.w("Multipass", "clé LOVE illisible, retour au provisoire", it) }
                .getOrNull()
        }
    }
    // Pas de noyau, pas d'identité : une clé LOVE au coffre ne rallume pas à
    // elle seule une station dont la fiche a été dissoute.
    val keys = remember(birth, forged, loveKeys) {
        if (!forged) null else loveKeys ?: LoveKeyForge.forge(birth)
    }

    // L'antenne suit le noyau : allumée dès que la clé existe, coupée à la
    // dissolution, et avec la station quand l'activité disparaît. L'éclaireur
    // lui fait préférer le relais local du hot-spot quand il y en a un.
    val scout = remember { LocalRelayScout(context.applicationContext) }
    val relay = remember { RelayStation(scope, scout = scout) }
    // Le salon de cabine suit la même vie que l'antenne : il n'échange que
    // par le relais local, jamais par les relais publics.
    val salon = remember { CabinSalon(scope, relay.localRelay) }
    LaunchedEffect(keys) {
        // La balise dérive son jeton de présence du noyau — sans quoi elle
        // n'annonce rien qui la distingue, et le portail compte des adresses.
        ProximityService.bindIdentity(keys?.publicKey)
        if (keys != null) {
            relay.start(keys)
            salon.start(keys)
        } else {
            salon.stop()
            relay.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            salon.stop()
            relay.stop()
        }
    }
    val relayStatus by relay.status.collectAsState()

    // ── La cabine, au-dessus des onglets ──────────────────────────────────
    // Elle vivait dans l'écran Radar : changer d'onglet détruisait le moteur et
    // effaçait la conversation sans que personne ne l'ait fermée. En la logeant
    // ici, « fermer = effacer » redevient un geste — et l'indicateur du haut
    // peut dire le médium depuis n'importe quel écran.
    //
    // Le moteur ne vit plus dans la composition : elle le fermait sans que
    // personne ne l'ait demandé dès que l'activité se recréait (rotation,
    // thème, langue, fenêtres partagées). C'est [CabinHost] qui le tient
    // désormais, et l'ouverture comme la fermeture y sont des gestes.
    val cabinHost: CabinHost = viewModel()
    val cabin = cabinHost.chat
    val cabinOpen = cabinHost.open
    val closeCabin: () -> Unit = { cabinHost.close() }
    // Le Wi-Fi Direct est le seul médium qui demande une permission de plus.
    // Elle se demande ICI, au moment d'accepter la montée — pas à l'ouverture
    // de la cabine : parler à portée n'a jamais eu besoin de fabriquer un
    // réseau, et faire payer cette permission à tout le monde serait le même
    // contresens que le salon jadis adossé à la balise.
    val nearbyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> if (results.values.all { it }) cabin.enable(Medium.WIFI_DIRECT) }
    val upgrade: (Medium) -> Unit = { medium ->
        if (medium == Medium.WIFI_DIRECT && !P2pGroup.permissionsGranted(context)) {
            nearbyLauncher.launch(P2pGroup.RUNTIME_PERMISSIONS)
        } else {
            cabin.enable(medium)
        }
    }

    fun updateBirth(b: BirthData) {
        birth = b
        scope.launch { store.save(b, forged) }
    }

    fun forge() {
        forged = true
        scope.launch { store.save(birth, forged = true) }
        // Le noyau vient d'être scellé : c'est le moment où la proposition
        // d'ouvrir un compte a du sens. On atterrit derrière sur le Noyau, là
        // où la porte se retrouve — refuser ne la fait pas revenir d'elle-même.
        if (account == null) {
            tab = A4LTab.Nucleus
            showMultipass = true
        } else {
            tab = A4LTab.Radar
        }
    }

    /** La position du moment, pour rattacher le compte à une UMAP. */
    suspend fun currentCoords(): Pair<Double?, Double?> {
        val fix = CellLocator(context.applicationContext).currentFix()
        return fix?.lat to fix?.lon
    }

    Crossfade(
        targetState = forged,
        animationSpec = tween(550),
        label = "forge",
    ) { isForged ->
        if (!isForged) {
            // Avant la forge il n'y a pas encore de barre de menus : l'aide
            // s'ouvre par le « ? » de l'en-tête, en plein écran.
            var showHelp by rememberSaveable { mutableStateOf(false) }
            if (showHelp) {
                HelpScreen(modifier = modifier, onClose = { showHelp = false })
            } else {
                IncarnationScreen(
                    birth = birth,
                    onBirthChange = ::updateBirth,
                    forged = false,
                    onForge = ::forge,
                    modifier = modifier,
                    relay = relayStatus,
                    onHelp = { showHelp = true },
                )
            }
        } else if (showMultipass) {
            // Plein écran, comme l'aide avant la forge : ouvrir un compte n'est
            // pas une manœuvre qu'on mène d'un œil, entre deux onglets.
            MultipassScreen(
                step = enrollStep,
                account = account,
                onSubmit = { email, passCode ->
                    scope.launch {
                        val (lat, lon) = currentCoords()
                        enrollment.enroll(email, birth, lat, lon, passCode)
                    }
                },
                onRetryActivation = { enrollment.retryActivation(birth) },
                onReset = { enrollment.reset() },
                onClose = {
                    showMultipass = false
                    enrollment.reset()
                },
                modifier = modifier,
            )
        } else {
            // `statusBarsPadding` consomme l'encoche pour ses enfants : celui
            // que chaque écran pose déjà devient un no-op, sans double marge.
            Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                CabinLine(
                    cabin = cabin,
                    open = cabinOpen,
                    onUpgrade = upgrade,
                )
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
                            A4LTab.Radar -> RadarScreen(
                                relay = relayStatus,
                                salon = salon,
                                keys = keys,
                                cabin = cabin,
                                cabinOpen = cabinOpen,
                                onOpenCabin = { cabinHost.open(keys) },
                                onCloseCabin = closeCabin,
                            )
                            A4LTab.Board -> BoardScreen(npub = keys?.npubShort)
                            A4LTab.Bonds -> ResonanceScreen()
                            A4LTab.Nucleus -> IncarnationScreen(
                                birth = birth,
                                onBirthChange = ::updateBirth,
                                forged = true,
                                onForge = {},
                                npub = keys?.npub,
                                relay = relayStatus,
                                onMultipass = { showMultipass = true },
                                multipassActive = account?.loveActivated == true,
                                onDissolve = {
                                    // La station oublie tout : fiche vierge, retour à la forge.
                                    birth = BirthData.Empty
                                    forged = false
                                    tab = A4LTab.Radar
                                    scope.launch { store.clear() }
                                },
                            )
                            A4LTab.Help -> HelpScreen()
                        }
                    }
                    ElectronSweep(trigger = tab)
                }
                A4LNavBar(current = tab, onSelect = { tab = it })
            }
        }
    }
}

/**
 * L'indicateur de liaison, tout en haut, sous l'encoche — le même sur les cinq
 * onglets.
 *
 * Il dit **par où** la cabine parle en ce moment, parce que ça change ce qu'on
 * peut en attendre : 14 Ko/s en BLE contre 11,8 Mo/s par la station. Et quand un
 * pair a annoncé une voie plus rapide, il la propose — sans jamais l'emprunter
 * de lui-même. La cabine s'établit toujours seule en BLE ; la montée, elle, se
 * décide.
 */
@Composable
private fun CabinLine(cabin: CabinChat, open: Boolean, onUpgrade: (Medium) -> Unit) {
    val status by cabin.status.collectAsState()
    val peers by cabin.peers.collectAsState()
    val medium = status.medium
    val offered = status.offered

    Row(
        Modifier
            .fillMaxWidth()
            .background(A4L.NavBackdrop)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            when {
                !open -> A4L.TextGhost
                medium != null -> A4L.Mint
                else -> A4L.TextDim
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                !open -> stringResource(R.string.header_cabin_closed)
                // le médium ne se lit qu'une fois quelqu'un joint : dire « BLE »
                // dans le vide ferait passer une antenne allumée pour un lien
                medium == null -> stringResource(R.string.header_cabin_open_alone)
                else -> stringResource(
                    R.string.header_medium,
                    stringResource(medium.labelRes),
                    medium.short,
                )
            },
            style = A4LText.Data.copy(fontSize = 10.sp),
            color = if (open && medium != null) A4L.Mint else A4L.TextMuted,
        )
        if (open && peers.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.cabin_peers_here, peers.size),
                style = A4LText.Data.copy(fontSize = 10.sp),
                color = A4L.TextDim,
            )
        }
        Spacer(Modifier.weight(1f))
        if (open && offered != null) {
            Text(
                stringResource(R.string.header_upgrade, offered.short),
                style = A4LText.Data.copy(fontSize = 10.sp),
                color = A4L.Cyan,
                modifier = Modifier
                    .clickable { onUpgrade(offered) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        ThemeToggle()
        if (AppLocale.selectable) {
            Spacer(Modifier.width(8.dp))
            LanguagePicker()
        }
    }
}

/**
 * L'interrupteur du jour et de la nuit : une pastille, un appui.
 *
 * Il montre **ce qu'il propose**, pas où l'on est — c'est ce que fait un
 * interrupteur. Dans le jour, la lune attend qu'on la touche ; on est dans la
 * nuit, et c'est le soleil qui attend à son tour. Les drapeaux d'à côté font
 * l'inverse et c'est normal : eux sont trois, on désigne celui qu'on veut ;
 * ici il n'y a qu'un seul geste possible, et il doit se lire d'un regard.
 *
 * Le soleil est jaune et un peu plus grand que son cercle — la nuit, c'est lui
 * la lumière qu'on propose ; la lune reste dans le ton du texte, discrète comme
 * ce qu'elle promet.
 */
@Composable
private fun ThemeToggle() {
    val context = LocalContext.current
    val dark = AppTheme.dark
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(A4L.Glass)
            .border(1.dp, A4L.StrokeSoft, CircleShape)
            .clickable { AppTheme.toggle(context) }
            .semantics {
                contentDescription = context.getString(
                    if (dark) R.string.theme_to_light else R.string.theme_to_dark,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // U+FE0E force la présentation texte : sans lui, la police rend le
            // soleil en emoji coloré et la lune en glyphe, deux registres pour
            // un seul interrupteur
            if (dark) "☀︎" else "☾︎",
            style = A4LText.Data.copy(fontSize = if (dark) 18.sp else 14.sp),
            color = if (dark) A4L.Amber else A4L.TextStrong,
        )
    }
}

/**
 * Le choix de langue, dans l'en-tête : trois drapeaux, celle qui parle en
 * pleine lumière et les autres en retrait.
 *
 * Pas de menu déroulant — trois langues tiennent dans la largeur, et le geste
 * doit coûter un seul appui : on change de langue parce qu'on ne comprend pas
 * ce qui est à l'écran, ce n'est pas le moment de demander de naviguer.
 *
 * L'état se lit dans la configuration, jamais dans une variable à nous : le
 * système recrée l'activité après le choix, et il peut aussi avoir été changé
 * depuis les réglages d'Android sans passer par ici.
 */
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val current = AppLocale.shown(LocalResources.current.configuration.locales)
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        AppLanguage.entries.forEach { language ->
            val active = language == current
            Text(
                language.flag,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .clickable(enabled = !active) { AppLocale.choose(context, language) }
                    .alpha(if (active) 1f else 0.32f)
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    // le drapeau ne se lit pas à voix haute : le nom de la
                    // langue, dans cette langue, est ce qui a du sens
                    .semantics { contentDescription = language.endonym },
            )
        }
    }
}

/** Barre de navigation — quatre onglets, l'actif prend la couleur de son espace. */
@Composable
private fun A4LNavBar(current: A4LTab, onSelect: (A4LTab) -> Unit) {
    // le filet du haut se dessine hors composition : sa couleur se prend ici
    val hairline = A4L.StrokeFaint
    Row(
        Modifier
            .fillMaxWidth()
            .background(A4L.NavBackdrop)
            .drawBehind {
                drawRect(
                    color = hairline,
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
                    stringResource(entry.labelRes),
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
