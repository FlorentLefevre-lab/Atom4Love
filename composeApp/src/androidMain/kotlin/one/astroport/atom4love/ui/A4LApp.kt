package one.astroport.atom4love.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.res.pluralStringResource
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import one.astroport.atom4love.proximity.PresenceAlert
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
import one.astroport.atom4love.chat.ChatEngine
import one.astroport.atom4love.chat.Medium
import one.astroport.atom4love.data.BodyStore
import one.astroport.atom4love.data.Pseudo
import one.astroport.atom4love.data.PseudoStore
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
import one.astroport.atom4love.nostr.HexagonSalon
import one.astroport.atom4love.nostr.Certificate
import one.astroport.atom4love.nostr.Contacts
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
import one.astroport.atom4love.trial.Trial
import one.astroport.atom4love.trial.TrialStore
import one.astroport.atom4love.ui.components.ElectronSweep
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.chat.Conversations
import one.astroport.atom4love.journal.Journal
import one.astroport.atom4love.journal.JournalRecorder
import one.astroport.atom4love.ui.screens.BoardScreen
import one.astroport.atom4love.ui.screens.ChatsScreen
import one.astroport.atom4love.ui.screens.ConversationScreen
import one.astroport.atom4love.ui.screens.JournalScreen
import one.astroport.atom4love.ui.screens.MapScreen
import one.astroport.atom4love.ui.screens.RadioSection
import one.astroport.atom4love.ui.screens.WorldLocked
import one.astroport.atom4love.ui.screens.HelpScreen
import one.astroport.atom4love.ui.screens.SettingsScreen
import one.astroport.atom4love.ui.screens.IncarnationScreen
import one.astroport.atom4love.ui.screens.MultipassScreen
import one.astroport.atom4love.ui.screens.SPLASH_HOLD_MS
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.screens.SplashScreen
import one.astroport.atom4love.ui.screens.TrialWall
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * Les quatre destinations de la barre du bas — **ici, à qui, ailleurs, soi**.
 *
 * ## Comment on en est arrivé là
 *
 * Il y en a eu six, puis trois, et l'histoire de chaque départ vaut d'être
 * gardée : **Aide** et **Réglages** ne sont pas des lieux mais des poignées, et
 * vivent dans la ligne d'en-tête ; **Résonance** montrait trois liaisons écrites
 * en dur et a été supprimée le 15/08 faute d'en avoir de vraies ; **Radar** et
 * **Constellation** avaient été réunies sous une seule entrée « Carte », à deux
 * segments — ici, et le monde.
 *
 * Ce dernier arrangement est celui qui vient de tomber, et pour deux raisons
 * indépendantes qui pointaient dans le même sens.
 *
 * **Le monde est un onglet, pas un segment.** Il est fermé tant qu'une station
 * n'a pas activé la clé LOVE, et un segment fermé au milieu d'un écran ne se
 * voit pas : il faut d'abord aller sur la Carte pour découvrir qu'il existe une
 * porte, et qu'elle est close. Un onglet **cadenassé dans la barre** est visible
 * en permanence, depuis n'importe où — on sait que le monde existe avant d'avoir
 * voulu y entrer, ce qui est exactement l'intention d'origine.
 *
 * **Le Radar n'était pas un lieu.** Retirée sa liste de résonances — qui était
 * le Plateau, en moins bien —, il ne restait qu'un état d'appareil et trois
 * compteurs. Ça se met en tête de ce que ça conditionne ; c'est aujourd'hui
 * [one.astroport.atom4love.ui.screens.RadioSection], au sommet du Plateau.
 *
 * La place ainsi libérée revient à ce qui n'en avait aucune : **les
 * conversations**. Elles vivaient dans une destination sans onglet, qu'on
 * atteignait par une rangée au fond d'un écran — c'est-à-dire que la chose pour
 * laquelle on ouvre cette application était le seul endroit qu'on ne pouvait pas
 * désigner du pouce.
 *
 * ## L'ordre, qui n'est pas libre
 *
 * Du plus proche au plus lointain, et soi en bout : ce qui est **ici**, ceux à
 * **qui** l'on parle, ce qui se passe **ailleurs**, et **soi**. C'est le même
 * mouvement que les trois compteurs du Plateau, et il place le Plateau — celui
 * qu'on ouvre en entrant dans une salle — sous le pouce, à gauche.
 */
enum class A4LTab(
    /** L'emoji reste en dur : un pictogramme n'est d'aucune langue. */
    val icon: String,
    @StringRes val labelRes: Int,
) {
    /**
     * Le Plateau : la radio, les cartes à portée, et le geste qui part en
     * chercher une. C'est l'écran d'entrée — celui qu'on ouvre en poussant la
     * porte d'un café.
     */
    Board("🎴", R.string.tab_board),

    /** À qui l'on parle. Voir [one.astroport.atom4love.ui.screens.ChatsScreen]. */
    Chats("💬", R.string.tab_chats),

    /**
     * Le monde, **sous cadenas jusqu'au MULTIPASS**.
     *
     * ⚠ Sa place est tenue en permanence, fermée ou non — jamais ajoutée au
     * moment de l'activation. Une barre qui passe de trois à quatre entrées
     * déplacerait les trois autres sous le pouce, et le ferait précisément
     * pendant que quelqu'un lit l'écran qui vient de s'ouvrir.
     */
    World("🌍", R.string.tab_world),

    /** Soi : la fiche scellée, le nom, la porte d'Astroport.ONE. */
    Nucleus("⚛", R.string.tab_nucleus),
    ;

    /**
     * La couleur de l'onglet une fois choisi. L'onglet dit **quel** accent lui
     * revient, la palette dit de quelle couleur il est à cette heure-ci — comme
     * [labelRes] dit quel mot et les ressources dans quelle langue.
     */
    val accent: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Board -> A4L.Violet
            Chats -> A4L.Mint
            World -> A4L.Indigo
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
    var tab by rememberSaveable { mutableStateOf(A4LTab.Board) }
    // ⚠ `place` vivait ici : le segment Ici|Monde de l'ancienne Carte. Les deux
    // segments sont devenus deux onglets, il n'y a plus de vue à retenir.

    // ── Le compte Astroport.ONE, s'il y en a un ───────────────────────────
    val context = LocalContext.current

    // ── Le corps d'aujourd'hui ────────────────────────────────────────────
    // Son propre magasin, chargé après coup : contrairement à la fiche, rien ne
    // dépend de lui au démarrage — ni la clé, ni l'antenne, ni la balise. Le
    // splash n'a donc aucune raison de l'attendre.
    val bodyStore = remember { BodyStore(context.applicationContext) }
    var body by remember { mutableStateOf(BodyMetrics.Empty) }
    LaunchedEffect(Unit) { body = bodyStore.load() }
    // Le nom qu'on se donne. Même vie que le corps : son propre magasin, chargé
    // après coup, et rien au démarrage n'en dépend — ni la clé, ni l'antenne,
    // ni la balise. Le splash n'a donc pas à l'attendre.
    val pseudoStore = remember { PseudoStore(context.applicationContext) }
    var pseudo by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { pseudo = pseudoStore.load() }
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
    val contacts = remember(scope) { Contacts(scope) }
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
    val salon = remember { HexagonSalon(scope, relay.localRelay) }

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
                    // ⚠ On ne retient que ce qui a **effectivement paru**.
                    // Retenir une arrivée restée muette — permission refusée —
                    // la condamnerait : accorder les notifications ensuite ne
                    // la rattraperait jamais. Vu sur le Pixel le 16/08.
                    val shown = due.filter { welcomeNotifier.celebrate(it) }
                    if (shown.isEmpty()) return@collect
                    memory = Welcome.remember(memory, shown, now)
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
    // thème, langue, fenêtres partagées). C'est [ChatHost] qui le tient
    // désormais, et l'ouverture comme la fermeture y sont des gestes.
    val cabinHost: ChatHost = viewModel()
    val cabin = cabinHost.chat
    val cabinOpen = cabinHost.open
    /**
     * ⚠ **La radio n'a plus de geste d'ouverture, et c'est le changement le
     * plus profond de cette refonte.**
     *
     * La cabine s'ouvrait : on touchait une rangée, les liens se nouaient, une
     * salle s'ouvrait, et la refermer effaçait tout. Ce geste portait une
     * promesse — rien ne se garde — mais il portait aussi un malentendu : tant
     * que personne ne l'avait fait, **personne n'était joignable**, et deux
     * personnes dans la même pièce pouvaient s'être cherchées sans qu'aucune
     * radio ne se parle. « Il faut que l'autre ait ouvert sa cabine » était
     * écrit dans l'aide ; c'est le genre de phrase qu'on n'a pas le droit
     * d'écrire.
     *
     * Depuis que chaque personne a son fil, il n'y a plus rien à ouvrir : la
     * radio parle dès que la clé existe et que le Bluetooth est accordé — les
     * deux mêmes conditions que la balise, demandées dans le même dialogue. La
     * promesse, elle, ne bouge pas : elle est devenue un geste au bas des
     * conversations, là où l'on voit ce qu'on efface.
     */
    // ⚠ **À chaque reprise, et non à chaque changement d'état.**
    //
    // La première écriture veillait sur `keys` et sur l'état de la balise, en
    // pariant que l'un des deux basculerait au bon moment. Vu sur la tablette le
    // 19/08 : après un passage en arrière-plan et une mise à mort par le
    // gestionnaire de ZUI, l'application revenait avec « radio éteinte » et rien
    // pour la rallumer — les deux valeurs surveillées étaient déjà dans leur
    // état final quand la composition est revenue, donc plus aucun basculement à
    // attendre. Et aucun geste ne rattrapait ça : la radio n'en a plus.
    //
    // `repeatOnLifecycle(STARTED)` retourne la question dans le bon sens. Ce
    // n'est pas « quand quelque chose change », c'est **« tant que l'application
    // est à l'écran, la radio doit être allumée »** — une invariante, vérifiée à
    // chaque retour au premier plan. Rouvrir ne coûte rien quand c'est déjà
    // ouvert : `open()` sort à sa première ligne.
    LaunchedEffect(keys, lifecycleOwner) {
        if (keys == null) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (ChatEngine.permissionsGranted(context)) cabinHost.open(keys)
        }
    }

    /**
     * La position du moment.
     *
     * Elle sert à deux choses qui n'ont rien à voir : rattacher un compte à une
     * UMAP au moment de l'inscription, et savoir si l'on a quitté le lieu de la
     * forge ([Trial]). Une seule lecture pour les deux — remonter le locator
     * jusqu'ici évite qu'un écran en fabrique un second.
     */
    suspend fun currentCoords(): Pair<Double?, Double?> {
        val fix = CellLocator(context.applicationContext).currentFix()
        return fix?.lat to fix?.lon
    }

    // ── La période d'essai ────────────────────────────────────────────────
    //
    // ⚠ **Elle décide du seul moment où l'application réclame quelque chose.**
    // Tout le reste est proposé et refusable ; cette porte-là se referme. Voir
    // [one.astroport.atom4love.trial.Trial] pour la règle et ce qu'elle ne fait
    // pas — notamment : sans position, la proposition ne vient jamais.
    val trialStore = remember { TrialStore(context.applicationContext) }
    var trialOrigin by remember { mutableStateOf<Trial.Origin?>(null) }
    var trialDeclined by remember { mutableStateOf(false) }
    LaunchedEffect(forged) {
        if (!forged) {
            trialOrigin = null
            trialDeclined = false
            return@LaunchedEffect
        }
        trialOrigin = trialStore.origin()
        trialDeclined = trialStore.declined()
    }
    // Le départ se pose au premier lancement suivant la forge, et se **complète**
    // dès que la position se résout : la forge arrive souvent avant le premier
    // fix. [TrialStore.begin] ne déplace jamais la date — sinon chaque fix
    // repousserait l'horloge et la proposition n'arriverait jamais.
    LaunchedEffect(forged, trialOrigin?.lat) {
        if (!forged) return@LaunchedEffect
        if (trialOrigin?.lat != null) return@LaunchedEffect
        val (lat, lon) = currentCoords()
        trialStore.begin(lat, lon, System.currentTimeMillis())
        trialOrigin = trialStore.origin()
    }
    // ⚠ **Une seule évaluation, à l'ouverture** — et non une veille continue.
    // Le déclencheur est « la personne revient à l'application après être
    // partie » : le regarder en permanence ferait surgir un formulaire de compte
    // pendant qu'elle marche, ce qui est exactement le mauvais moment. La clé
    // `forged` suffit : la composition se refait au lancement.
    var trialDue by remember { mutableStateOf(false) }
    LaunchedEffect(forged, trialOrigin, worldUnlocked) {
        if (!forged || worldUnlocked || trialOrigin == null) return@LaunchedEffect
        val (lat, lon) = currentCoords()
        trialDue = Trial.isDue(trialOrigin, lat, lon, System.currentTimeMillis())
    }
    // La proposition s'ouvre d'elle-même, une fois. La refuser ferme le MULTIPASS
    // ET pose le mur ; l'accepter mène au formulaire, et c'est le coffre du
    // compte qui fera foi ensuite.
    LaunchedEffect(trialDue, trialDeclined) {
        if (trialDue && !trialDeclined && overlay == Overlay.None) {
            overlay = Overlay.Multipass
        }
    }
    // L'essai est fini et la porte a été refusée : le mur couvre tout, sauf le
    // Noyau. On ne prend jamais quelqu'un en otage de ses propres données.
    val walled = forged && trialDue && trialDeclined && !worldUnlocked

    // ── Le journal ────────────────────────────────────────────────────────
    //
    // Monté ici, une fois, au-dessus des onglets : il doit écrire même quand
    // personne ne le regarde. Le poser dans son propre écran ne l'aurait rempli
    // que pendant qu'on le lit — c'est-à-dire jamais avec ce qui vient de se
    // passer.
    JournalRecorder(chat = cabin, relayOnline = relayStatus.online)

    /** Le fil ouvert en plein écran, null quand on est dans la liste. */
    var openPeer by rememberSaveable { mutableStateOf<String?>(null) }

    // ── Les conversations, rangées par personne ───────────────────────────
    //
    // Dérivées, jamais stockées : le moteur tient une liste de messages et une
    // liste de pairs, et un message porte la clé de son correspondant. Voir
    // [one.astroport.atom4love.chat.Conversations] pour ce que ce choix coûte et
    // rapporte.
    val cabinPeers by cabin.peers.collectAsStateWithLifecycle()
    val cabinMessages by cabin.messages.collectAsStateWithLifecycle()
    /**
     * Ceux qu'on a retenus depuis le Monde — voir [Conversations.of].
     *
     * ⚠ En mémoire, comme le reste des conversations : ce qui ne se garde pas
     * ne se garde nulle part, y compris la liste de qui l'on a voulu joindre.
     * Un fichier de gens retenus serait un carnet d'adresses, c'est-à-dire
     * exactement la chose que « tout s'efface » promet de ne pas être. Ce qui
     * se garde vraiment existe déjà et se dit : c'est **Suivre**, qui écrit un
     * kind 3 signé sur le relais et qu'on décide ligne par ligne.
     */
    var pinnedPeers by rememberSaveable { mutableStateOf(emptySet<String>()) }

    /**
     * Les pseudos déjà entendus — voir [Conversations.of].
     *
     * ⚠ Il ne se vide jamais de lui-même : un nom appris reste su tant que la
     * station vit. C'est peu de mémoire (un mot par personne croisée) et ça
     * évite le contresens d'oublier quelqu'un parce qu'il a franchi une porte.
     */
    var knownNames by remember { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(cabinPeers) {
        val fresh = cabinPeers.mapNotNull { peer -> peer.display?.let { peer.npub to it } }
        if (fresh.isNotEmpty()) knownNames = knownNames + fresh
    }

    val conversations = remember(cabinPeers, cabinMessages, pinnedPeers, openPeer, knownNames) {
        Conversations.of(
            peers = cabinPeers,
            messages = cabinMessages,
            // ⚠ **Le fil qu'on est en train de LIRE est épinglé tant qu'il est
            // ouvert.** Sans ça, un pair qui s'éloigne pendant qu'on regarde sa
            // conversation vide la faisait disparaître de la liste — donc
            // l'écran se refermait tout seul, sans un mot, et le geste de retour
            // suivant sortait de l'application. Vu à l'écran le 19/08. Une porte
            // franchie par quelqu'un d'autre ne referme pas l'écran qu'on lit.
            pinned = pinnedPeers + setOfNotNull(openPeer),
            remembered = knownNames,
        )
    }
    // ⚠ On retrouve le fil par sa CLÉ à chaque recomposition, on ne le garde
    // pas. Un `Conversation` est un instantané : le retenir figerait la conversation
    // au moment où on l'a ouverte, et les messages suivants n'y entreraient
    // jamais. Le fil disparaît de la liste — plus de messages, plus de pair —,
    // l'écran se referme de lui-même, ce qui est le comportement voulu quand on
    // vient d'effacer.
    val openConversation = conversations.firstOrNull { it.peerHex == openPeer }

    /** Le journal ouvert en plein écran, à la place qu'occupait la cabine. */
    var journalShown by rememberSaveable { mutableStateOf(false) }
    // Ce que la fiche saura répondre au jeu des questions. Rien ne part de
    // là — c'est un geste par question, et il coûte la même réponse.
    //
    // ⚠ `cabin` fait partie des clés, et ce n'est pas décoratif : fermer la
    // cabine y installe une instance NEUVE (ChatHost.close), et une liaison
    // faite sur l'ancienne ne la suivrait pas. Sans cette clé, une cabine
    // rouverte ne savait plus rien répondre.
    //
    // ⚠ Une seconde liaison vivait ici, `cabin.bindResonance(Phi2X.omegaBio(…))`.
    // Partie le 15/08 avec Watson — le jeu compte cinq questions, toutes lues
    // dans la fiche, et plus aucune n'a besoin d'être jointe du dehors.
    LaunchedEffect(cabin, birth) { cabin.bindTraits(birth) }
    // ⚠ `cabin` fait partie des clés pour la même raison que ci-dessus : fermer
    // installe une instance NEUVE, qui ne saurait plus comment on s'appelle.
    // Le nom se relie **avant** l'ouverture — un handshake déjà engagé garderait
    // le silence, et le pair nous lirait « sans nom » jusqu'au lien suivant.
    LaunchedEffect(cabin, pseudo) { cabin.bindPseudo(pseudo) }
    /**
     * **Fermer efface** — la promesse de la cabine, tenue telle quelle.
     *
     * Le moteur repart neuf : messages, fils et pièces jointes reçues
     * disparaissent de l'appareil. Ce qui change est le moment où l'on peut le
     * faire : ce n'était pas un geste mais la sortie d'un lieu, si bien qu'on
     * effaçait une conversation en croyant refermer un écran. C'est maintenant
     * une décision qu'on prend en regardant la liste de ce qu'elle emporte.
     *
     * La radio se rallume aussitôt derrière — l'effet d'ouverture veille sur le
     * cycle de vie, pas sur un basculement : on efface ce qui s'est dit, on ne
     * se coupe pas du monde.
     */
    val eraseConversations: () -> Unit = {
        cabinHost.close()
        openPeer = null
    }
    // ⚠ **Trois choses ont disparu d'ici, et elles tenaient ensemble** : le
    // lanceur de permissions du Wi-Fi Direct, la montée `enable` et le choix
    // forcé `select`. Elles n'ont plus de destinataire depuis que la bêta ne
    // nomme qu'une voie locale ([Medium.inBeta]) — un écran qui ne propose
    // aucune montée n'a personne à qui demander une permission de plus.
    //
    // Le moteur, lui, garde les quatre voies et leurs deux méthodes : ce n'est
    // pas du code mort, c'est du code sans porte. La porte se rouvre en une
    // ligne, dans `Medium.inBeta`, le jour où la bêta s'élargit.

    fun updateBirth(b: BirthData) {
        birth = b
        scope.launch { store.save(b, forged) }
    }

    fun updateBody(b: BodyMetrics) {
        body = b
        scope.launch { bodyStore.save(b) }
    }

    fun updatePseudo(name: String) {
        pseudo = Pseudo.clean(name)
        scope.launch { pseudoStore.save(pseudo) }
    }

    fun forge() {
        forged = true
        scope.launch { store.save(birth, forged = true) }
        // ⚠ **La forge n'ouvre plus le MULTIPASS**, et c'est une décision de
        // Florent du 19/08. Elle le faisait aussitôt, sur le seul fait qu'aucun
        // compte n'existait : quelqu'un qui venait de saisir sa date de
        // naissance recevait un formulaire de compte avant d'avoir vu une
        // carte, une conversation ou un seul écran de l'application. On lui
        // demandait de régulariser une situation qu'il n'avait pas encore eue.
        //
        // La proposition attend maintenant que la **première expérience ait eu
        // lieu** — c'est le GPS qui le dit, quand on a quitté le lieu et qu'on
        // rouvre l'application. Voir `trial/`.
        //
        // On atterrit donc sur le Plateau, à l'endroit où il y a quelque chose à
        // voir : la balise s'allume, les cartes paraissent, et c'est ça qu'on
        // vient de fabriquer.
        tab = A4LTab.Board
    }


    // ── Une carte se montre, et on est ailleurs ───────────────────────────
    //
    // Le voisinage remonte ici, au lieu de rester dans la page des onglets : la
    // ligne d'en-tête et la bannière en ont besoin partout, cabine comprise.
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()

    // Le compte de ce qui est **jouable** à portée : même prédicat que la main
    // du Plateau, à la lettre — un pair sans signature est là sans rien avoir
    // montré, et ne donne aucune carte.
    val cardsInRange = remember(neighbors) {
        neighbors
            .distinctBy { it.identity }
            .count { it.signature != ProximityPayload.Signature.Unknown }
    }
    var presenceBanner by remember { mutableStateOf(false) }
    var cardsBefore by remember { mutableIntStateOf(0) }
    var lastPresenceMs by remember { mutableStateOf(0L) }
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }
    }

    // ⚠ Même règle que la notification système ([PresenceAlert]) : la
    // transition seule, et un temps de garde. Une seule règle pour les deux, ou
    // l'un sonnerait quand l'autre se tait et personne ne saurait plus quoi
    // attendre de l'application.
    LaunchedEffect(cardsInRange) {
        val now = System.currentTimeMillis()
        val fire = PresenceAlert.shouldAnnounce(cardsBefore, cardsInRange, lastPresenceMs, now)
        cardsBefore = cardsInRange
        // Rien à annoncer à qui regarde déjà le Plateau : il l'a sous les yeux.
        if (!fire || tab == A4LTab.Board) return@LaunchedEffect
        lastPresenceMs = now
        // Une seule secousse, courte. Elle sert à faire lever les yeux, pas à
        // réclamer — quelqu'un est peut-être au milieu d'une phrase.
        vibrator?.vibrate(VibrationEffect.createOneShot(35L, 160))
        presenceBanner = true
    }
    // ⚠ **Plus de minuterie : le bandeau reste jusqu'à ce qu'on le referme.**
    // Il durait six secondes, « de quoi le lire deux fois ». C'était vrai pour
    // qui regardait l'écran, et faux pour tous les autres : une nouvelle qui
    // s'efface toute seule pendant qu'on parle à quelqu'un n'a pas été donnée.
    //
    // La seule chose qui le retire sans geste est **qu'il devienne faux** :
    // plus personne à portée, plus rien à annoncer. Ça n'est pas une minuterie
    // déguisée — c'est la salle qui s'est vidée.
    LaunchedEffect(cardsInRange) {
        if (cardsInRange == 0) presenceBanner = false
    }

    // ── Ce qui attend d'être lu ───────────────────────────────────────────
    //
    // ⚠ **Une date de lecture par personne, en mémoire, et rien de plus.**
    // Marquer chaque message « lu » demanderait de retoucher la liste du moteur
    // à chaque coup d'œil ; retenir *quand* on a regardé un fil suffit, et se
    // range en un nombre par correspondant. Ça vit le temps de la station, comme
    // les conversations elles-mêmes : ce qui ne se garde pas n'a pas de non-lus
    // à garder non plus.
    var readAt by remember { mutableStateOf(emptyMap<String, Long>()) }
    // Le fil ouvert se lit en continu — y compris ce qui arrive pendant qu'on
    // le regarde. La clé `cabinMessages` n'est pas décorative : sans elle, un
    // message reçu la conversation ouverte resterait compté comme en attente.
    LaunchedEffect(openPeer, cabinMessages) {
        openPeer?.let { readAt = readAt + (it to System.currentTimeMillis()) }
    }
    val unreadTotal = remember(conversations, readAt) {
        conversations.sumOf { conversation ->
            val since = readAt[conversation.peerHex] ?: 0L
            conversation.messages.count { !it.mine && it.atMs > since }
        }
    }
    // L'invitation ne se lève qu'au PASSAGE de zéro à un : elle dit « il y a
    // quelque chose », pas « il y en a un de plus ». Un bandeau qui se relève à
    // chaque message ferait de la conversation un harcèlement.
    var unreadBanner by remember { mutableStateOf(false) }
    var unreadBefore by remember { mutableIntStateOf(0) }
    LaunchedEffect(unreadTotal) {
        if (unreadTotal > 0 && unreadBefore == 0 && tab != A4LTab.Chats) {
            vibrator?.vibrate(VibrationEffect.createOneShot(35L, 160))
            unreadBanner = true
        }
        if (unreadTotal == 0) unreadBanner = false
        unreadBefore = unreadTotal
    }


    Box(Modifier.fillMaxSize()) {

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
                        pseudo = pseudo,
                        onPseudoChange = ::updatePseudo,
                        relay = relayStatus,
                        onHelp = { showHelp = true },
                        onSettings = { showSettings = true },
                    )
                }
            }
        } else if (openConversation != null) {
            // Une conversation prend l'écran entier, barre du bas comprise —
            // même montage que la cabine dont elle hérite, et pour la même
            // raison : la liste doit céder sa hauteur au clavier, et la place
            // réservée à la barre laissait 64 dp de vide sous la saisie.
            Column(modifier.fillMaxSize().background(A4L.Deep)) {
                // ⚠ Le retour QUITTE la conversation, il n'efface rien. Un
                // geste de retour distrait ne doit jamais emporter un échange.
                // Le seul geste qui efface est au bas des conversations, là où
                // l'on voit ce qu'on efface.
                BackHandler { openPeer = null }
                ConversationScreen(
                    conversation = openConversation,
                    chat = cabin,
                    keys = keys,
                    contacts = contacts,
                    onBack = { openPeer = null },
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (journalShown) {
            // Le journal a exactement la place, et le geste, de l'ancienne
            // cabine : plein écran depuis une rangée du Plateau, et le retour
            // referme. Voir [one.astroport.atom4love.journal.Journal].
            Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                RadioLine(
                    cabin = cabin,
                    open = cabinOpen,
                    onHelp = { overlay = Overlay.Help },
                    onSettings = { overlay = Overlay.Settings },
                    cardsInRange = cardsInRange,
                    onOpenBoard = {
                        journalShown = false
                        tab = A4LTab.Board
                    },
                )
                BackHandler { journalShown = false }
                JournalScreen(
                    onClose = { journalShown = false },
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (overlay != Overlay.None) {
            // Plein écran, comme l'aide avant la forge : ce qui s'ouvre ici est
            // ce dont la barre a été débarrassée. Un lieu où l'on va se garde
            // dans la barre ; une chose qu'on consulte et qu'on referme, non.
            Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                /**
                 * ⚠ **Refermer le MULTIPASS ne veut pas dire la même chose
                 * avant et après la première expérience.**
                 *
                 * Avant, c'est « pas maintenant » : l'application continue
                 * entière, et c'est ce qu'elle a toujours fait. Après — quand la
                 * proposition est due —, c'est la fin de l'essai, et le mur se
                 * pose. Le geste est le même ; seul son moment lui donne son
                 * poids, et c'est pour ça que le refus se lit ici, à la
                 * fermeture, plutôt que dans un bouton « non merci » qui aurait
                 * dû exister en deux versions.
                 */
                val close = {
                    if (overlay == Overlay.Multipass && trialDue && !trialDeclined) {
                        trialDeclined = true
                        scope.launch { trialStore.decline() }
                    }
                    overlay = Overlay.None
                }
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
            Column(modifier.fillMaxSize().background(A4L.Deep).statusBarsPadding()) {
                RadioLine(
                    cabin = cabin,
                    open = cabinOpen,
                    onHelp = { overlay = Overlay.Help },
                    onSettings = { overlay = Overlay.Settings },
                    // Pas sur le Plateau lui-même : on y a les cartes sous les
                    // yeux, un compte de plus ne dirait rien.
                    cardsInRange = if (tab == A4LTab.Board) 0 else cardsInRange,
                    onOpenBoard = { tab = A4LTab.Board },
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
                        when {
                            // ⚠ Le Noyau reste ouvert, toujours : il porte la
                            // fiche, le nom, les mesures et la dissolution. On
                            // ne prend jamais quelqu'un en otage de ses propres
                            // données — un mur qui couvrirait ça ferait de
                            // l'application un ravisseur, pas un service.
                            walled && t != A4LTab.Nucleus -> TrialWall(
                                onOpenMultipass = {
                                    trialDeclined = false
                                    scope.launch { trialStore.reconsider() }
                                    overlay = Overlay.Multipass
                                },
                            )
                            else -> when (t) {
                            A4LTab.Board -> BoardScreen(
                                npub = keys?.npubShort,
                                birth = birth,
                                radio = { title ->
                                    RadioSection(
                                        relay = relayStatus,
                                        salon = salon,
                                        reachable = conversations.count { it.inRange },
                                        onOpenJournal = { journalShown = true },
                                        title = title,
                                    )
                                },
                            )
                            A4LTab.Chats -> ChatsScreen(
                                conversations = conversations,
                                onOpen = { openPeer = it.peerHex },
                                onErase = eraseConversations,
                            )
                            A4LTab.World ->
                                // La clé rendue par la station, jamais celle
                                // qu'on dérive ici : le monde est fait de ses
                                // certificats.
                                if (worldUnlocked) {
                                    MapScreen(
                                        birth = birth,
                                        keys = keys,
                                        shared = constellation,
                                        onOpenChat = { pubkey ->
                                            pinnedPeers = pinnedPeers + pubkey
                                            openPeer = pubkey
                                            tab = A4LTab.Chats
                                        },
                                    )
                                } else {
                                    WorldLocked()
                                }
                            A4LTab.Nucleus -> IncarnationScreen(
                                birth = birth,
                                onBirthChange = ::updateBirth,
                                forged = true,
                                onForge = {},
                                body = body,
                                onBodyChange = ::updateBody,
                                pseudo = pseudo,
                                onPseudoChange = ::updatePseudo,
                                npub = keys?.npub,
                                relay = relayStatus,
                                // ⚠ **La porte du compte n'existe que lorsqu'elle
                                // a été méritée.** Elle était en bas du Noyau en
                                // permanence : il suffisait d'y descendre, le
                                // premier jour, pour se voir demander une adresse
                                // e-mail avant d'avoir croisé personne. Elle
                                // paraît maintenant dans trois cas seulement —
                                // la proposition est due (le GPS dit que la
                                // première expérience a eu lieu), elle a été
                                // refusée et le mur est posé, ou le compte existe
                                // déjà et la ligne dit qu'il est actif. C'est la
                                // même règle que le cadenas du Monde : on ne
                                // court-circuite pas la fin du jeu.
                                onMultipass = if (trialDue || account != null) {
                                    { overlay = Overlay.Multipass }
                                } else {
                                    null
                                },
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
                                    // Le nom part avec le reste : il ne dit
                                    // rien de la clé, mais il dit comment on
                                    // s'appelait — et le suivant n'a pas à
                                    // hériter de ça.
                                    pseudo = ""
                                    forged = false
                                    tab = A4LTab.Board
                                    scope.launch {
                                        store.clear()
                                        bodyStore.clear()
                                        loveKeyStore.clear()
                                        pseudoStore.clear()
                                        // Un noyau neuf n'a pas d'histoire :
                                        // l'essai reprend au premier jour, et
                                        // le journal ne garde pas le souvenir
                                        // de la radio du précédent.
                                        trialStore.clear()
                                        Journal.clear()
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
                    }
                    ElectronSweep(trigger = tab)
                }
                A4LNavBar(
                    current = tab,
                    onSelect = { tab = it },
                    // ⚠ **Le Plateau ne s'endort plus.** Il s'éteignait tant
                    // qu'aucun sceau n'était à portée, du temps où il n'était
                    // qu'un jeu au milieu de la barre. C'est aujourd'hui
                    // l'écran d'entrée, celui qui porte l'état de la radio :
                    // l'éteindre reviendrait à griser la porte par laquelle on
                    // apprend justement qu'il n'y a personne.
                    //
                    // Ce qui s'endort, ce sont les **conversations** — il n'y
                    // en a aucune tant que personne n'est passé — et le monde
                    // reste cadenassé jusqu'au MULTIPASS.
                    awake = { entry ->
                        when (entry) {
                            A4LTab.Chats -> conversations.isNotEmpty()
                            A4LTab.World -> worldUnlocked
                            else -> true
                        }
                    },
                    // Le cadenas plutôt qu'un onglet grisé : un onglet éteint se
                    // lit « en panne », un cadenas se lit « pas encore ». C'est
                    // le mot exact qui décidait déjà de l'ancien segment.
                    locked = { it == A4LTab.World && !worldUnlocked },
                    badge = { if (it == A4LTab.Chats) unreadTotal else 0 },
                )
            }
        }
    }

    // ⚠ **En BAS, pas en haut.** Elle se posait sous l'encoche, à l'autre bout
    // de l'écran des deux pouces — il fallait remonter la main pour la refermer
    // ou l'ouvrir, sur un téléphone qu'on tient d'une main dans un bar. En bas,
    // au-dessus de la barre de menus, elle tombe là où le pouce est déjà.
    //
    // Elle reste **par-dessus tout** : elle doit se voir y compris quand une
    // conversation occupe l'écran entier, barre comprise. Elle n'attrape pas les
    // gestes qui ne la visent pas.
    // ⚠ **Deux bandeaux, un seul à la fois, et le message passe devant.**
    // Une carte qui se montre est une occasion ; un message qui attend est
    // quelqu'un qui a parlé. Les empiler ferait deux rangées au-dessus de la
    // barre ; les laisser se disputer la place ferait clignoter l'écran. Le
    // message gagne, et la présence attend qu'il soit lu ou refermé.
    UnreadBanner(
        visible = unreadBanner,
        count = unreadTotal,
        onOpen = {
            unreadBanner = false
            openPeer = null
            journalShown = false
            tab = A4LTab.Chats
        },
        onDismiss = { unreadBanner = false },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    PresenceBanner(
        visible = presenceBanner && !unreadBanner,
        count = cardsInRange,
        onOpen = {
            presenceBanner = false
            openPeer = null
            journalShown = false
            tab = A4LTab.Board
        },
        onDismiss = { presenceBanner = false },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }
}

/**
 * De quoi laisser passer la barre de menus sous la bannière.
 *
 * Ses 64 dp, plus une respiration. Quand la barre n'est pas là — dans une
 * conversation, dans le journal —, la bannière flotte simplement un peu plus
 * haut : mieux vaut ça qu'un calcul qui dépendrait de l'écran affiché derrière.
 */
private val PRESENCE_BANNER_LIFT = 76.dp

/**
 * « Une carte se montre » — la seule chose que l'application s'autorise à dire
 * à quelqu'un qui fait autre chose.
 *
 * ⚠ Elle annonce une **présence**, jamais une recherche. « Quelqu'un vous
 * cherche » nommerait le chercheur dès qu'il n'y a que deux personnes dans la
 * pièce, et ruinerait le silence sur lequel repose tout le consentement de
 * `Rendezvous`. Ce qui est dit ici resterait vrai si personne ne cherchait.
 *
 * Elle s'efface d'elle-même : rien à balayer, rien à refuser. Quelqu'un au
 * milieu d'une phrase ne doit pas avoir à s'occuper d'elle.
 */
/**
 * 💬 « Un message vous attend » — la seule chose que l'application dise à
 * quelqu'un qui regarde ailleurs, en dehors d'une carte qui se montre.
 *
 * ⚠ **Elle annonce une attente, jamais un contenu.** Ni le nom de qui a écrit,
 * ni le début du message : ce bandeau se pose par-dessus tout, y compris sur un
 * téléphone posé sur une table, et rien de ce qui s'est dit en privé n'a à s'y
 * lire. Le compte suffit à faire lever les yeux — c'est tout ce qu'on lui
 * demande.
 *
 * Elle ne se lève qu'au **passage de zéro à un** et ne se referme que d'un
 * geste, ou quand il n'y a plus rien à lire. Même règle que la présence : une
 * nouvelle qui s'efface toute seule n'a pas été donnée.
 */
@Composable
private fun UnreadBanner(
    visible: Boolean,
    count: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeLabel = stringResource(R.string.presence_banner_close)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = PRESENCE_BANNER_LIFT)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(A4L.Mint.tint(0.22f))
                .border(1.dp, A4L.Mint.tint(0.45f), RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("💬", fontSize = 17.sp)
            Spacer(Modifier.width(11.dp))
            Text(
                pluralStringResource(R.plurals.unread_banner, count.coerceAtLeast(1), count),
                style = A4LText.Body.copy(fontSize = 13.sp),
                color = A4L.TextHigh,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.unread_banner_open),
                style = A4LText.Body.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Mint,
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(A4L.Mint.tint(0.30f))
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 14.sp, color = A4L.TextHigh)
            }
        }
    }
}

@Composable
private fun PresenceBanner(
    visible: Boolean,
    count: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Lu dans la composition et non dans le rappel : une ressource lue depuis un
    // lambda ne serait pas réévaluée si la langue change sous l'application.
    val closeLabel = stringResource(R.string.presence_banner_close)
    AnimatedVisibility(
        visible = visible,
        // Elle vient du bas maintenant, et y repart : une bannière qui entre par
        // le haut alors qu'elle s'affiche en bas se lit comme une chute.
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = PRESENCE_BANNER_LIFT)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(A4L.Violet.tint(0.22f))
                .border(1.dp, A4L.Violet.tint(0.45f), RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🎴", fontSize = 17.sp)
            Spacer(Modifier.width(11.dp))
            Text(
                pluralStringResource(R.plurals.presence_banner, count.coerceAtLeast(1), count),
                style = A4LText.Body.copy(fontSize = 13.sp),
                color = A4L.TextHigh,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.presence_banner_open),
                style = A4LText.Body.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Violet,
            )
            Spacer(Modifier.width(6.dp))
            // ⚠ **Une vraie cible, pas une ponctuation.** La croix faisait 13 sp
            // dans 4 dp de marge : un glyphe posé au bout d'une phrase, qu'on
            // touchait de travers et qui ouvrait le Plateau au lieu de fermer.
            // Maintenant qu'aucune minuterie ne referme la bannière à sa place,
            // c'est le SEUL geste qui la retire — il lui faut la taille d'un
            // bouton, et son propre fond pour se détacher du lavis violet.
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(A4L.Violet.tint(0.30f))
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 14.sp, color = A4L.TextHigh)
            }
        }
    }
}

/**
 * L'état de la radio, tout en haut, sous l'encoche — le même sur tous les
 * onglets.
 *
 * ⚠ **Il disait par où la cabine parlait, et il n'a plus qu'une réponse.** Tant
 * qu'il y avait quatre voies, la question valait d'être posée : 14 Ko/s en BLE
 * contre 11,8 Mo/s par la station, ça change ce qu'on peut attendre d'un envoi.
 * La bêta n'en garde qu'une ([Medium.inBeta]) — la ligne ne nomme donc plus un
 * médium, elle dit l'état : éteinte, allumée et seule, allumée avec quelqu'un.
 * Le sélecteur et la proposition de montée sont partis avec les trois autres
 * voies ; un menu à une entrée n'est pas un choix, c'est un obstacle.
 *
 * ⚠ **Et le mot « cabine » est parti avec.** Il désignait ici une salle qu'on
 * ouvrait et refermait ; il désigne surtout, chez Fred, **Cabine-33** — un autre
 * projet, cité tel quel dans nos pages d'aide. Deux choses différentes sous le
 * même nom, dans la même constellation de projets, ne pouvaient pas tenir. Ce
 * que l'utilisateur lit est donc « chat » là où l'on parle à quelqu'un, et
 * « radio » là où l'appareil écoute — deux mots, pour les deux choses que la
 * cabine confondait.
 */
@Composable
private fun RadioLine(
    cabin: ChatEngine,
    open: Boolean,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    /**
     * Combien de cartes se montrent à portée, et de quoi aller les voir.
     *
     * ⚠ **C'est le seul endroit de l'application qui est sur TOUS les écrans**,
     * cabine comprise. Sans ça, quelqu'un en pleine conversation ne saurait
     * jamais qu'il y a de quoi jouer à côté de lui : la barre du bas disparaît
     * dans la cabine, et une notification système ne se voit pas quand on est
     * déjà dans l'application. Florent l'a dit en une phrase — *« les gens ne
     * penseront pas à aller dans Plateau s'ils sont en train de chatter »*.
     *
     * Une **présence**, jamais une recherche : ce compte serait le même si
     * personne ne cherchait personne.
     */
    cardsInRange: Int = 0,
    onOpenBoard: (() -> Unit)? = null,
) {
    val status by cabin.status.collectAsState()
    val peers by cabin.peers.collectAsState()
    val medium = status.medium

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
        // ⚠ **Deux états, et rien de plus : allumée, éteinte.**
        //
        // Cette ligne a porté successivement le médium en service, « personne à
        // portée », « quelqu'un est là » et le compte des pairs. Chacun était
        // exact, et l'ensemble disait sur TOUS les écrans ce qui a désormais sa
        // place ailleurs : qui est là se lit au compteur 📍 du Plateau, ce qui
        // attend se lit à la pastille des Chats. Une ligne présente partout doit
        // dire ce qu'aucun écran ne dit — l'état de la machine — et se taire sur
        // le reste, sinon elle double tout sans rien ajouter.
        Text(
            stringResource(if (open) R.string.header_radio_on else R.string.header_radio_off),
            style = A4LText.Data.copy(fontSize = 10.sp),
            color = if (open && medium != null) A4L.Mint else A4L.TextMuted,
        )
        Spacer(Modifier.weight(1f))
        if (cardsInRange > 0 && onOpenBoard != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(A4L.Violet.tint(0.16f))
                    .clickable(onClick = onOpenBoard)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎴", fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    cardsInRange.toString(),
                    style = A4LText.Data.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = A4L.Violet,
                )
            }
            Spacer(Modifier.width(8.dp))
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
 * ⚠ **La liste des quatre voies vivait ici**, dans une pastille à chevron de la
 * ligne d'en-tête : chaque entrée nommait un médium, disait ce que le choix
 * coûtait, et quitter son propre groupe Wi-Fi Direct demandait même une
 * confirmation — parce que le départ de l'hôte dissout la voie pour tout le
 * monde.
 *
 * Elle est partie avec les trois voies qu'elle servait ([Medium.inBeta]). Ce
 * n'était pas un mauvais composant, c'était un composant sans objet : un menu
 * qui ne propose qu'une entrée déjà active n'offre pas un choix, il ajoute un
 * geste. Le moteur, lui, sait toujours router sur quatre rangs — `git` garde le
 * menu pour le jour où la bêta les rouvre.
 */

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
    /**
     * Une porte fermée qu'on doit voir fermée.
     *
     * Le cadenas se pose **sur le pictogramme**, pas à la place du libellé : le
     * mot reste lisible, parce qu'il faut savoir de quoi la porte est la porte.
     * Un onglet cadenassé reste touchable — l'écran derrière explique lui-même
     * comment l'ouvrir, ce qui vaut mieux qu'un bouton qui refuse le doigt sans
     * un mot.
     */
    locked: (A4LTab) -> Boolean = { false },
    /**
     * Ce qui attend d'être lu sur cet onglet. Zéro : rien ne se dessine.
     *
     * ⚠ **En haut à GAUCHE du pictogramme**, et non à droite comme le veut
     * l'habitude des systèmes. La barre porte quatre entrées de largeur égale ;
     * une pastille à droite du 💬 tomberait dans la gouttière qui le sépare du
     * 🌍 et se lirait comme appartenant aux deux. À gauche elle est adossée au
     * signe qu'elle qualifie, et rien d'autre n'occupe ce coin.
     */
    badge: (A4LTab) -> Int = { 0 },
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
            // ⚠ **Le voile ne couvre plus la pastille.** Il était posé sur la
            // colonne entière : un onglet non choisi passe à 40 %, et le compteur
            // de messages en attente s'effaçait avec lui — or c'est précisément
            // sur un onglet où l'on n'est PAS qu'il doit se voir. Le voile
            // s'applique donc au pictogramme et au mot, un par un ; la pastille
            // garde son rouge plein.
            val veil = when {
                // Là où l'on est, on est : un onglet endormi qu'on a choisi
                // reste pleinement lisible.
                selected -> 1f
                !lit -> 0.16f
                else -> 0.4f
            }
            Column(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable { onSelect(entry) }
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.TopStart) {
                    Text(
                        if (locked(entry)) "🔒" else entry.icon,
                        fontSize = 16.sp,
                        // ⚛ est un glyphe monochrome : sans teinte explicite il
                        // se perdrait, là où 🎴 💬 🌍 portent leurs propres
                        // couleurs.
                        color = if (selected) entry.accent else A4L.TextStrong,
                        modifier = Modifier
                            .alpha(veil)
                            .padding(start = 9.dp, top = 5.dp),
                    )
                    val waiting = badge(entry)
                    if (waiting > 0) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(A4L.Red)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                // Au-delà, le nombre exact n'apprend plus rien
                                // et la pastille cesse d'être une pastille.
                                if (waiting > 9) "9+" else waiting.toString(),
                                style = A4LText.Data.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = A4L.Deep,
                            )
                        }
                    }
                }
                Text(
                    stringResource(entry.labelRes),
                    style = A4LText.Tab.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) entry.accent else A4L.TextStrong,
                    modifier = Modifier
                        .alpha(veil)
                        .padding(top = 4.dp),
                )
            }
        }
    }
}
