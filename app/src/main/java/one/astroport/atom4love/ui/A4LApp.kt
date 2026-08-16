package one.astroport.atom4love.ui

import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.chat.CabinChat
import one.astroport.atom4love.chat.Medium
import one.astroport.atom4love.chat.net.P2pGroup
import one.astroport.atom4love.data.BodyStore
import one.astroport.atom4love.data.LoveKeyStore
import one.astroport.atom4love.data.IncarnationStore
import one.astroport.atom4love.data.MultipassAccount
import one.astroport.atom4love.data.MultipassStore
import one.astroport.atom4love.data.SavedIncarnation
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.BodyMetrics
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.multipass.Enrollment
import one.astroport.atom4love.multipass.MultipassService
import one.astroport.atom4love.nostr.Bech32
import one.astroport.atom4love.nostr.CabinSalon
import one.astroport.atom4love.nostr.Certificate
import one.astroport.atom4love.nostr.LocalRelayScout
import one.astroport.atom4love.nostr.NostrKeys
import one.astroport.atom4love.nostr.RelayStation
import one.astroport.atom4love.proximity.CellLocator
import one.astroport.atom4love.proximity.ProximityPayload
import one.astroport.atom4love.nostr.Constellation
import one.astroport.atom4love.nostr.Welcome
import one.astroport.atom4love.nostr.WelcomeNotifier
import one.astroport.atom4love.data.WelcomeStore
import one.astroport.atom4love.proximity.ProximityService
import one.astroport.atom4love.ui.components.ElectronSweep
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.screens.BoardScreen
import one.astroport.atom4love.ui.screens.HelpScreen
import one.astroport.atom4love.ui.screens.SettingsScreen
import one.astroport.atom4love.ui.screens.CabinDestination
import one.astroport.atom4love.ui.screens.IncarnationScreen
import one.astroport.atom4love.ui.screens.MultipassScreen
import one.astroport.atom4love.ui.screens.PlaceView
import one.astroport.atom4love.ui.screens.SPLASH_HOLD_MS
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.screens.SplashScreen
import one.astroport.atom4love.ui.screens.StationScreen
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog

/**
 * Les deux destinations de la barre du bas : dehors, et soi.
 *
 * Il y en avait six. Quatre sont parties, chacune pour une raison différente :
 * - **Radar** et **Constellation** n'étaient pas deux endroits mais deux façons
 *   de regarder qui est là — elles sont les deux segments de [A4LTab.Map]
 *   (`StationScreen`).
 * - **Aide** et **Réglages** ne sont pas des lieux, ce sont des poignées : elles
 *   vivent dans la ligne d'en-tête, à côté du thème et des langues.
 * - **Plateau** a repris sa place au milieu depuis qu'un sceau à portée l'allume.
 * - **Résonance** montrait trois liaisons écrites en dur. Elle a longtemps
 *   attendu dans le dépôt d'avoir de vraies liaisons à montrer ; **supprimée le
 *   15/08** avec le reste du code mort — une maquette qu'on ne peut pas
 *   atteindre n'attend rien, elle encombre. `git` la garde si elle revient.
 */
enum class A4LTab(
    /** L'emoji reste en dur : un pictogramme n'est d'aucune langue. */
    val icon: String,
    @StringRes val labelRes: Int,
) {
    Map("🌍", R.string.tab_map),

    /**
     * Le Plateau, **au milieu** : le lieu, ceux qui y sont, soi — du dehors
     * vers le dedans, et la meilleure place du pouce pour l'échelon qu'on veut
     * voir monter.
     *
     * Il était sorti de la barre parce qu'« il n'a pas de partie, et un onglet
     * promet un lieu où l'on revient ». Il en a une depuis qu'un sceau à portée
     * l'allume. Sa place est **tenue en permanence** et non ajoutée à l'arrivée
     * d'un voisin : une barre qui passe de deux à trois entrées déplacerait les
     * deux autres sous le pouce, au moment précis où quelqu'un entre dans la
     * pièce. Elle s'éteint, elle ne disparaît pas.
     */
    Board("🎴", R.string.tab_board),
    Nucleus("⚛", R.string.tab_nucleus),
    ;

    /**
     * La couleur de l'onglet une fois choisi. L'onglet dit **quel** accent lui
     * revient, la palette dit de quelle couleur il est à cette heure-ci — comme
     * [labelRes] dit quel mot et les ressources dans quelle langue.
     */
    val accent: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Map -> A4L.Mint
            Board -> A4L.Violet
            Nucleus -> A4L.Cyan
        }
}

/** Ce qui s'ouvre par-dessus la station, en plein écran, et se referme. */
private enum class Overlay { None, Multipass, Help, Settings }

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
    var tab by rememberSaveable { mutableStateOf(A4LTab.Map) }
    // La Carte s'ouvre sur ce qui est à portée : c'est là qu'on vit. Le monde
    // n'est l'accueil qu'une fois, au sortir de la forge — voir la constellation
    // où l'on ne figure pas encore est ce qui donne envie d'y entrer.
    var place by rememberSaveable { mutableStateOf(PlaceView.Here) }

    // ── Le compte Astroport.ONE, s'il y en a un ───────────────────────────
    val context = LocalContext.current

    // ── Le corps d'aujourd'hui ────────────────────────────────────────────
    // Son propre magasin, chargé après coup : contrairement à la fiche, rien ne
    // dépend de lui au démarrage — ni la clé, ni l'antenne, ni la balise. Le
    // splash n'a donc aucune raison de l'attendre.
    val bodyStore = remember { BodyStore(context.applicationContext) }
    var body by remember { mutableStateOf(BodyMetrics.Empty) }
    LaunchedEffect(Unit) { body = bodyStore.load() }
    val multipassStore = remember { MultipassStore(context.applicationContext) }
    val enrollment = remember {
        Enrollment(scope, MultipassService(BuildConfig.ASTROPORT_USPOT), multipassStore)
    }
    val enrollStep by enrollment.step.collectAsState()
    var account by remember { mutableStateOf<MultipassAccount?>(null) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
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
    //
    // La dérivation ne tient plus dans une composition : elle refait la chaîne
    // complète d'Astroport.ONE — 1 200 000 tours de PBKDF2 puis scrypt —, soit
    // quelques secondes la toute première fois. Elle part donc en arrière-plan,
    // et le coffre la rend instantanément aux démarrages suivants.
    val loveKeyStore = remember { LoveKeyStore(context.applicationContext) }
    var derivedKeys by remember { mutableStateOf<NostrKeys?>(null) }
    LaunchedEffect(birth, forged) {
        derivedKeys = if (forged) loveKeyStore.loadOrDerive(birth) else null
    }
    /**
     * Le ménage d'une identité abandonnée — ceinture et bretelles.
     *
     * Une clé provisoire a pu publier un certificat dans la constellation, du
     * temps où le monde s'ouvrait sans MULTIPASS. Quand la station rend enfin
     * sa clé LOVE, cet atome-là devient un fantôme : le même être humain, signé
     * d'un nom qu'il n'emploie plus, à côté du sien. On le retire une fois,
     * sans rien demander — ce n'est pas une décision, c'est du rangement.
     *
     * **Ça ne devrait jamais avoir lieu** : depuis que le monde est réservé au
     * MULTIPASS, une clé provisoire n'a plus accès à la publication. C'est un
     * filet pour les installations d'avant cette règle, et pour le jour où
     * cette règle bougerait.
     *
     * On lit d'abord le **nom public** de la provisoire — sans ouvrir le coffre,
     * sans rien dériver — et on ne va chercher la clé privée que si le relais
     * dit qu'il y a effectivement quelque chose à retirer. Le coffre effacé sert
     * de mémoire : plus de nom, plus rien à faire. Et s'il n'a pas répondu, on
     * garde tout et on réessaie au prochain démarrage.
     */
    val certificate = remember(scope) { Certificate(scope) }
    LaunchedEffect(loveKeys, forged, birth) {
        val station = loveKeys ?: return@LaunchedEffect
        if (!forged) return@LaunchedEffect
        val abandoned = loveKeyStore.provisionalPublicKey(birth) ?: return@LaunchedEffect
        if (abandoned == station.publicKeyHex) return@LaunchedEffect
        val outcome = certificate.retire(abandoned) { loveKeyStore.loadOrDerive(birth) }
        Log.d("Nostr", "ménage de la clé provisoire : $outcome")
        if (outcome !is Certificate.Retirement.Unreachable) loveKeyStore.clear()
    }

    // La clé de la station prime dès qu'elle existe : c'est elle qui fait
    // autorité sur qui l'on est, celle d'ici ne fait que la précéder.
    val keys = if (!forged) null else loveKeys ?: derivedKeys

    // L'antenne suit le noyau : allumée dès que la clé existe, coupée à la
    // dissolution, et avec la station quand l'activité disparaît. L'éclaireur
    // lui fait préférer le relais local du hot-spot quand il y en a un.
    val scout = remember { LocalRelayScout(context.applicationContext) }
    val relay = remember { RelayStation(scope, scout = scout) }
    // Le salon de cabine suit la même vie que l'antenne : il n'échange que
    // par le relais local, jamais par les relais publics.
    val salon = remember { CabinSalon(scope, relay.localRelay) }

    // ── La veille de la constellation ─────────────────────────────────────
    //
    // Elle vit ici et non dans la Carte, parce qu'une arrivée ne se produit pas
    // quand on regarde : rater une bienvenue parce qu'on était sur le Noyau
    // n'aurait aucun sens. La Carte reçoit la même instance, pour qu'une
    // lecture et une veille ne fassent pas deux sockets vers le même relais.
    val constellation = remember(scope) { Constellation(scope) }
    val welcomeStore = remember(context) { WelcomeStore(context.applicationContext) }
    val welcomeNotifier = remember(context) { WelcomeNotifier(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val worldUnlocked = account?.loveActivated == true

    // ⚠ Deux conditions, et aucune n'est décorative. `worldUnlocked` : le relais
    // public ne se lit pas sans clé LOVE activée — pas de MULTIPASS, pas de
    // constellation, donc pas de veille. `repeatOnLifecycle(STARTED)` : la
    // socket s'ouvre quand l'application paraît et se referme quand elle
    // disparaît. Ce qui s'est publié entre-temps revient au `since` de la
    // reprise ; tenir une socket ouverte toute la journée pour l'apprendre dix
    // minutes plus tôt ne le vaut pas.
    LaunchedEffect(worldUnlocked, keys?.publicKeyHex) {
        if (!worldUnlocked) return@LaunchedEffect
        welcomeNotifier.ensureChannel()
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // La mémoire est relue à chaque reprise et tenue ici : la consulter
            // sur disque à chaque arrivée serait une écriture pour une lecture.
            var memory = welcomeStore.celebrated()
            constellation.watch(welcomeStore.lastSeenAt())
            try {
                constellation.arrivals.collect { atom ->
                    val now = System.currentTimeMillis()
                    val due = Welcome.toCelebrate(
                        atoms = listOf(atom),
                        alreadyCelebrated = Welcome.keysOf(memory),
                        myPubkey = keys?.publicKeyHex,
                        nowMs = now,
                    )
                    if (due.isEmpty()) return@collect
                    due.forEach(welcomeNotifier::celebrate)
                    memory = Welcome.remember(memory, due, now)
                    welcomeStore.save(memory, now / 1000)
                }
            } finally {
                constellation.stopWatching()
            }
        }
    }
    LaunchedEffect(keys) {
        // La balise dérive son jeton de présence du noyau — sans quoi elle
        // n'annonce rien qui la distingue, et le portail compte des adresses.
        ProximityService.bindIdentity(keys?.publicKey)
    }
    // La signature suit la fiche, pas la clé : le sceau existe dès la date, la
    // phase dès le lieu — bien avant la forge. Ce qui se croise à portée
    // d'antenne n'a jamais eu besoin d'un compte.
    LaunchedEffect(birth) {
        ProximityService.bindResonance(
            ProximityPayload.Signature(
                sex = birth.wave?.sex,
                glyph = KinMaya.of(birth)?.glyph,
                phase = Phi2X.personalPhase(birth),
            ),
        )
    }
    LaunchedEffect(keys) {
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
    // Ouverte et **affichée** sont deux choses. Le retour système quitte la
    // destination sans fermer la cabine ; celle-ci continue de tenir ses liens,
    // et l'on y revient par la rangée du radar.
    var cabinShown by rememberSaveable { mutableStateOf(false) }
    // Ce que la fiche saura répondre au jeu des questions. Rien ne part de
    // là — c'est un geste par question, et il coûte la même réponse.
    //
    // ⚠ `cabin` fait partie des clés, et ce n'est pas décoratif : fermer la
    // cabine y installe une instance NEUVE (CabinHost.close), et une liaison
    // faite sur l'ancienne ne la suivrait pas. Sans cette clé, une cabine
    // rouverte ne savait plus rien répondre.
    //
    // ⚠ Une seconde liaison vivait ici, `cabin.bindResonance(Phi2X.omegaBio(…))`.
    // Partie le 15/08 avec Watson — le jeu compte cinq questions, toutes lues
    // dans la fiche, et plus aucune n'a besoin d'être jointe du dehors.
    LaunchedEffect(cabin, birth) { cabin.bindTraits(birth) }
    // Fermer efface : la destination se retire avec, sinon on resterait devant
    // une conversation qui n'existe plus.
    val closeCabin: () -> Unit = { cabinHost.close(); cabinShown = false }
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

    /**
     * Forcer la voie porteuse. Même porte que [upgrade] pour la permission :
     * ouvrir un groupe Wi-Fi Direct la demande, et l'écran est le seul endroit
     * d'où l'on peut encore l'accorder.
     */
    val selectMedium: (Medium) -> Unit = { medium ->
        if (medium == Medium.WIFI_DIRECT && !P2pGroup.permissionsGranted(context)) {
            nearbyLauncher.launch(P2pGroup.RUNTIME_PERMISSIONS)
        } else {
            cabin.select(medium)
        }
    }

    fun updateBirth(b: BirthData) {
        birth = b
        scope.launch { store.save(b, forged) }
    }

    fun updateBody(b: BodyMetrics) {
        body = b
        scope.launch { bodyStore.save(b) }
    }

    fun forge() {
        forged = true
        scope.launch { store.save(birth, forged = true) }
        // La Carte reste sur « Ici » : le monde est fermé tant que la station
        // n'a pas rendu la clé LOVE, et atterrir sur une porte close n'accueille
        // personne. (J'avais posé l'inverse le 15/08 — Florent a tranché.)
        // C'est en revanche le moment où la proposition d'ouvrir un compte a du sens.
        // On atterrit derrière sur le Noyau, là où la porte se retrouve —
        // refuser ne la fait pas revenir d'elle-même.
        if (account == null) {
            tab = A4LTab.Nucleus
            overlay = Overlay.Multipass
        } else {
            tab = A4LTab.Map
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
            // s'ouvre par le « ? » de l'en-tête, en plein écran — et les
            // réglages par le rouage à côté, depuis que la langue et la lumière
            // n'ont plus d'étape à elles.
            var showHelp by rememberSaveable { mutableStateOf(false) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            if (showSettings) {
                Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        modifier = Modifier.weight(1f),
                        onClose = { showSettings = false },
                        body = body,
                        onBodyChange = ::updateBody,
                    )
                }
            } else if (showHelp) {
                // L'aide d'avant la forge s'ouvre par-dessus l'assistant : elle
                // garde donc les mêmes réglages, sinon on les perdrait
                // justement en allant chercher de quoi comprendre.
                Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                    BackHandler { showHelp = false }
                    HelpScreen(
                        modifier = Modifier.weight(1f),
                        onClose = { showHelp = false },
                        // Avant la forge il n'y a pas de barre de menus : le
                        // bas de la fenêtre, c'est ce pied-là.
                        atWindowBottom = true,
                    )
                }
            } else {
                // `statusBarsPadding` ici rend celui de l'écran inoffensif :
                // la barre de réglages prend l'encoche, l'assistant se pose
                // dessous — c'est le montage de la station, à l'identique.
                Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                    IncarnationScreen(
                        birth = birth,
                        onBirthChange = ::updateBirth,
                        forged = false,
                        onForge = ::forge,
                        modifier = Modifier.weight(1f),
                        body = body,
                        onBodyChange = ::updateBody,
                        relay = relayStatus,
                        onHelp = { showHelp = true },
                        onSettings = { showSettings = true },
                    )
                }
            }
        } else if (cabinOpen && cabinShown) {
            // La cabine ouverte est une destination, au même rang que l'Aide et
            // les Réglages : plein écran, **par-dessus la barre de menus**.
            //
            // Elle vivait dans la page du radar, en panneau de hauteur fixe que
            // la page faisait défiler de force. Le clavier recouvrait alors la
            // rangée de saisie — il fallait le refermer pour atteindre Envoyer.
            // Ici la liste prend ce qui reste et cède au clavier ; et couvrir la
            // barre supprime les 64 dp de vide que sa place réservée laissait
            // sous la saisie.
            Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                // La ligne de cabine reste au-dessus, comme sur les onglets :
                // c'est elle qui porte le sélecteur de voie (BT / réseau du lieu
                // / point à point), le compteur de pairs et les deux poignées.
                // La destination l'avait emportée avec la barre du haut — et
                // c'est justement dans la cabine qu'on veut changer de voie,
                // puisque c'est là que le débit se sent.
                CabinLine(
                    cabin = cabin,
                    open = cabinOpen,
                    onUpgrade = upgrade,
                    onSelect = selectMedium,
                    onHelp = { overlay = Overlay.Help },
                    onSettings = { overlay = Overlay.Settings },
                )
                // ⚠ Le retour QUITTE LA VUE, il ne ferme rien. Fermer une
                // cabine efface la conversation — un geste de retour distrait
                // emporterait l'échange. La cabine reste ouverte derrière, ses
                // liens tiennent, et la rangée du radar y ramène. Le seul geste
                // qui efface est celui de la rangée du haut, dans la cabine,
                // là où l'on voit ce qu'on efface.
                BackHandler { cabinShown = false }
                CabinDestination(
                    chat = cabin,
                    onClose = closeCabin,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (overlay != Overlay.None) {
            // Plein écran, comme l'aide avant la forge : ce qui s'ouvre ici est
            // ce dont la barre a été débarrassée. Un lieu où l'on va se garde
            // dans la barre ; une chose qu'on consulte et qu'on referme, non.
            Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                val close = { overlay = Overlay.None }
                // Ces écrans ne sont plus des onglets : le geste de retour du
                // système doit les refermer, pas quitter la station. Le
                // MULTIPASS remet en plus son inscription à zéro, comme sa ✕.
                BackHandler {
                    if (overlay == Overlay.Multipass) enrollment.reset()
                    close()
                }
                when (overlay) {
                    Overlay.Multipass -> MultipassScreen(
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
                            close()
                            enrollment.reset()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Overlay.Help -> HelpScreen(
                        modifier = Modifier.weight(1f),
                        onClose = close,
                        atWindowBottom = true,
                    )
                    Overlay.Settings -> SettingsScreen(
                        modifier = Modifier.weight(1f),
                        onClose = close,
                        body = body,
                        onBodyChange = ::updateBody,
                    )
                    Overlay.None -> Unit // impossible : la branche l'exclut
                }
            }
        } else {
            // `statusBarsPadding` consomme l'encoche pour ses enfants : celui
            // que chaque écran pose déjà devient un no-op, sans double marge.
            // Les voisins que la balise entend : c'est ce qui réveille le
            // Plateau dans la barre du bas.
            val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
            Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                CabinLine(
                    cabin = cabin,
                    open = cabinOpen,
                    onUpgrade = upgrade,
                    onSelect = selectMedium,
                    onHelp = { overlay = Overlay.Help },
                    onSettings = { overlay = Overlay.Settings },
                    pickerAlways = tab == A4LTab.Map,
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
                            A4LTab.Map -> StationScreen(
                                view = place,
                                onSelectView = { place = it },
                                // La clé rendue par la station, jamais celle
                                // qu'on dérive ici : le monde est fait de ses
                                // certificats.
                                worldUnlocked = worldUnlocked,
                                constellation = constellation,
                                onOpenMultipass = { overlay = Overlay.Multipass },
                                birth = birth,
                                relay = relayStatus,
                                salon = salon,
                                keys = keys,
                                cabin = cabin,
                                onSelectMedium = selectMedium,
                                cabinOpen = cabinOpen,
                                onOpenCabin = { cabinHost.open(keys); cabinShown = true },
                                onEnterCabin = { cabinShown = true },
                                onCloseCabin = closeCabin,
                            )
                            A4LTab.Board -> BoardScreen(
                                npub = keys?.npubShort,
                                birth = birth,
                            )
                            A4LTab.Nucleus -> IncarnationScreen(
                                birth = birth,
                                onBirthChange = ::updateBirth,
                                forged = true,
                                onForge = {},
                                body = body,
                                onBodyChange = ::updateBody,
                                npub = keys?.npub,
                                relay = relayStatus,
                                onMultipass = { overlay = Overlay.Multipass },
                                multipassActive = account?.loveActivated == true,
                                // ⚠ Le bouton vers le Plateau a vécu ici tant que
                                // le Plateau n'avait pas de place à lui. Il en a
                                // une depuis `ec67778`, au milieu de la barre :
                                // deux chemins vers le même lieu, dont l'un se
                                // trouve en descendant sous la fiche, ne valent
                                // pas mieux qu'un seul qui se voit.
                                onDissolve = {
                                    // La station oublie tout : fiche vierge, retour à la
                                    // forge — et les mesures du corps partent avec, sans
                                    // quoi la promesse d'oubli serait tenue à moitié.
                                    birth = BirthData.Empty
                                    body = BodyMetrics.Empty
                                    forged = false
                                    tab = A4LTab.Map
                                    place = PlaceView.Here
                                    scope.launch {
                                        store.clear()
                                        bodyStore.clear()
                                        loveKeyStore.clear()
                                        // Elle ne dit rien de nous, mais elle dit qui
                                        // l'on a vu arriver — et la garder ferait
                                        // manquer les bienvenues au noyau suivant,
                                        // qui n'a jamais fêté personne.
                                        welcomeStore.clear()
                                    }
                                },
                            )
                        }
                    }
                    ElectronSweep(trigger = tab)
                }
                A4LNavBar(
                    current = tab,
                    onSelect = { tab = it },
                    // Un sceau à portée réveille le Plateau. Pas « quelqu'un
                    // ici » : un pair sans signature est là sans rien avoir
                    // montré, et ne donne aucune carte. Même prédicat que la
                    // main du Plateau, à la lettre.
                    awake = { entry ->
                        entry != A4LTab.Board || neighbors
                            .distinctBy { it.identity }
                            .any { it.signature != ProximityPayload.Signature.Unknown }
                    },
                )
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
private fun CabinLine(
    cabin: CabinChat,
    open: Boolean,
    onUpgrade: (Medium) -> Unit,
    onSelect: (Medium) -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    /**
     * Montrer le sélecteur de voie même cabine fermée. Vrai sur la **Carte** :
     * c'est l'écran des liaisons, et savoir par où ça passera — ou le choisir
     * d'avance — s'y lit comme un état du lieu, pas comme un réglage de trafic.
     * Ailleurs il reste lié à l'ouverture : sur le Noyau ou le Plateau, une
     * liste de voies ne désignerait rien.
     */
    pickerAlways: Boolean = false,
) {
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
                // le médium ne se lit qu'une fois quelqu'un joint : dire « BT »
                // dans le vide ferait passer une antenne allumée pour un lien
                medium == null -> stringResource(R.string.header_cabin_open_alone)
                // Le nom court seul : dans cette ligne on ne cherche pas une
                // description, on cherche lequel des trois porte en ce moment.
                // La phrase longue (« par le réseau du lieu ») vit là où il y a
                // la place de l'expliquer — les dialogues de refus.
                else -> stringResource(R.string.header_medium, medium.short)
            },
            style = A4LText.Data.copy(fontSize = 10.sp),
            color = if (open && medium != null) A4L.Mint else A4L.TextMuted,
        )
        // Choisir soi-même par où ça passe, plutôt que d'attendre qu'on le
        // propose.
        //
        // ⚠ La liste ne s'ouvrait QUE cabine ouverte, au motif qu'« hors cabine
        // il n'y a pas de trafic à router ». Vrai pour le trafic, faux pour la
        // personne : sur la Carte on veut savoir par où ça passera avant
        // d'ouvrir, et pouvoir le décider. Le choix se garde et s'applique à
        // l'ouverture — `cabin.select` s'adresse à l'instance vivante, ouverte
        // ou non.
        if (open || pickerAlways) MediumPicker(status = status, onSelect = onSelect)
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
        // Les deux poignées descendues de la barre du bas. Elles se rangent
        // AVANT le thème et les langues : celles-là sont toujours au même bout
        // de la ligne, sur tous les écrans, et c'est à ça qu'on les retrouve.
        HeaderButton("❓", R.string.tab_help, A4L.Indigo, onHelp)
        Spacer(Modifier.width(5.dp))
        // U+FE0F : sans lui, U+2699 tombe sur la police TEXTE du système, qui
        // le dessine en cercle à rayons — une roue de bateau, pas un engrenage.
        HeaderButton("⚙️", R.string.tab_settings, A4L.TextStrong, onSettings)
    }
}

/**
 * Une poignée de l'en-tête — même pastille que l'interrupteur jour/nuit, pour
 * qu'on lise du premier coup que ce sont des gestes de même nature : on les
 * touche, quelque chose s'ouvre, on referme et on est revenu où l'on était.
 */
@Composable
private fun HeaderButton(
    glyph: String,
    @StringRes labelRes: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    val label = stringResource(labelRes)
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(A4L.Glass)
            .border(1.dp, A4L.StrokeSoft, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = A4LText.Data.copy(fontSize = 12.sp), color = tint)
    }
}


/**
 * La liste des voies, à côté du mode actif — pour forcer celle qu'on veut.
 *
 * Elle n'annonce pas trois positions exclusives, parce que les médiums se
 * cumulent : **le BT reste dessous quoi qu'on choisisse**, c'est la porte
 * d'entrée et la seule qui atteste un inconnu. Choisir revient à dire par où
 * doit passer le trafic — ce qui ferme les voies plus rapides, pas la porte.
 *
 * Une voie que personne n'offre reste dans la liste, éteinte : la voir absente
 * n'apprend rien, la voir hors d'atteinte dit qu'elle existe et qu'il manque
 * quelqu'un en face.
 */
@Composable
private fun MediumPicker(status: CabinChat.Status, onSelect: (Medium) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // Quitter un groupe qu'on tient le referme POUR TOUS. Ça ne se fait pas
    // d'un doigt distrait — d'où la seule confirmation de cette liste, réservée
    // au cas où le geste engage quelqu'un d'autre que soi.
    var confirmLeaving by remember { mutableStateOf<Medium?>(null) }

    confirmLeaving?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmLeaving = null },
            containerColor = A4L.Deep,
            title = {
                Text(
                    stringResource(R.string.medium_leave_group_title),
                    style = A4LText.H2,
                    color = A4L.TextHigh,
                )
            },
            text = {
                Text(
                    stringResource(R.string.medium_leave_group_body, target.short),
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            },
            confirmButton = {
                Text(
                    stringResource(R.string.medium_leave_group_ok),
                    style = A4LText.Caption,
                    color = A4L.Red,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            confirmLeaving = null
                            onSelect(target)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            dismissButton = {
                Text(
                    stringResource(R.string.medium_leave_group_cancel),
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { confirmLeaving = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }

    Box {
        Text(
            if (open) "▴" else "▾",
            style = A4LText.Data.copy(fontSize = 10.sp),
            color = A4L.Cyan,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = A4L.Deep,
        ) {
            Medium.entries.forEach { medium ->
                val active = status.medium == medium
                // Le moteur dit lesquels sont atteignables — pas `offered`,
                // qui ne nomme que le prochain pas.
                val reachable = medium in status.reachable
                // Ce que ce choix coûte ou apporte, sous le nom. Une liste qui
                // ne dit que « BT / Wi-Fi AP / Wi-Fi P2P » laisse deviner, et
                // ces trois-là n'ont pas du tout les mêmes conséquences.
                val consequence = when {
                    !reachable -> R.string.medium_note_unreachable
                    medium == Medium.BLE -> R.string.medium_note_ble
                    medium == Medium.WIFI_STATION -> R.string.medium_note_station
                    // hôte ou invité, ce n'est pas le même engagement — et ça se
                    // sait AVANT d'entrer, par l'invitation reçue
                    status.groupHost != null || status.groupInvited ->
                        R.string.medium_note_direct_join
                    else -> R.string.medium_note_direct_host
                }
                DropdownMenuItem(
                    enabled = reachable && !active,
                    onClick = {
                        open = false
                        // On ne quitte pas son propre groupe sans le savoir.
                        if (status.groupHost is CabinChat.GroupHost.Self &&
                            medium.rank < Medium.WIFI_DIRECT.rank
                        ) {
                            confirmLeaving = medium
                        } else {
                            onSelect(medium)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                medium.short,
                                style = A4LText.Data.copy(fontSize = 11.sp),
                                color = when {
                                    active -> A4L.Mint
                                    reachable -> A4L.TextBody
                                    else -> A4L.TextGhost
                                },
                            )
                            Text(
                                stringResource(consequence),
                                style = A4LText.Caption.copy(fontSize = 10.sp),
                                color = if (reachable) A4L.TextMuted else A4L.TextGhost,
                            )
                        }
                    },
                    trailingIcon = {
                        Text(
                            when {
                                active -> "●"
                                reachable -> ""
                                // hors d'atteinte : personne ne l'offre
                                else -> "—"
                            },
                            style = A4LText.Data.copy(fontSize = 10.sp),
                            color = if (active) A4L.Mint else A4L.TextGhost,
                        )
                    },
                )
            }
        }
    }
}

/** Barre de navigation — l'onglet actif prend la couleur de son espace. */
@Composable
private fun A4LNavBar(
    current: A4LTab,
    onSelect: (A4LTab) -> Unit,
    /**
     * Un onglet endormi n'a rien à montrer pour l'instant. Il reste touchable :
     * l'écran qui s'ouvre dit lui-même pourquoi il est vide, ce qui vaut mieux
     * qu'un bouton qui refuse le doigt sans un mot.
     */
    awake: (A4LTab) -> Boolean = { true },
) {
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
            val lit = awake(entry)
            Column(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable { onSelect(entry) }
                    .alpha(
                        when {
                            // Là où l'on est, on est : un onglet endormi qu'on
                            // a choisi reste pleinement lisible.
                            selected -> 1f
                            !lit -> 0.16f
                            else -> 0.4f
                        },
                    )
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
