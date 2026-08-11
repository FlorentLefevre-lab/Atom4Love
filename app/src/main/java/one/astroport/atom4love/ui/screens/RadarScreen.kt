package one.astroport.atom4love.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import one.astroport.atom4love.domain.GoldbergPortal
import android.widget.Toast
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.chat.ChatSounds
import one.astroport.atom4love.chat.CabinChat
import one.astroport.atom4love.chat.ui.ChatPanel
import one.astroport.atom4love.nostr.NostrKeys
import one.astroport.atom4love.nostr.CabinSalon
import one.astroport.atom4love.nostr.RelayStation
import one.astroport.atom4love.proximity.CellLocator
import one.astroport.atom4love.proximity.NeighborRegistry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.proximity.ProximityService
import one.astroport.atom4love.ui.components.HexagonShape
import one.astroport.atom4love.ui.components.hexagonPath
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint
import kotlin.math.ceil

/** Le rituel de cabine dure 33 secondes d'immobilité (cf. CONTEXTE). */
private const val RITUAL_SECONDS = 33f

/** La cellule bouge peu : un rafraîchissement du fix toutes les 30 s suffit. */
private const val FIX_REFRESH_MS = 30_000L

/**
 * Quelle demande de permission est en vol. Un seul lanceur sert les trois :
 * deux `rememberLauncherForActivityResult` du même contrat dans un même
 * composable se sont disputé le résultat au banc, et le retour d'une demande
 * de localisation a démarré la balise. L'intention est donc portée en état.
 */
private enum class PermissionIntent { NONE, BEACON, LOCATION, CABIN }

/** Ce qu'il faut pour résoudre une cellule H3 — et rien de plus. */
private val LOCATION_PERMISSIONS = arrayOf(
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * Identifiant lisible d'une cellule H3 : l'index en hexadécimal, débarrassé de
 * la traîne de « f » des chiffres inutilisés. Affichage uniquement — jamais
 * reparsé.
 */
private fun cellHex(cell: Long): String =
    cell.toULong().toString(16).uppercase().trimEnd('F')

/**
 * L'Activity qui porte ce composable. `LocalContext` en Compose est un
 * `ContextThemeWrapper` posé sur elle : on remonte la chaîne d'emballages.
 */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Une nouvelle demande montrerait-elle encore un dialogue ? Vrai tant qu'au
 * moins une des permissions a droit à sa justification. À n'appeler qu'APRÈS un
 * refus revenu du lanceur : avant la première demande, la réponse est faux sans
 * qu'aucune impasse n'existe.
 */
private fun Context.canStillAskFor(permissions: Array<String>): Boolean {
    val activity = findActivity() ?: return true
    return permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

/** La page « Infos sur l'appli » du système, seul endroit où rouvrir une permission refusée. */
private fun appSettingsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

/**
 * 02 · Radar Phi2X — la cabine à portée et le rituel de phase.
 *
 * Le compteur tourne réellement : 33 s d'immobilité déverrouillent la cabine.
 * Un appui sur le radar relance le rituel (en attendant le vrai verrou GPS, qui
 * devra réinitialiser le compteur dès que l'utilisateur s'éloigne du centre).
 */
@Composable
fun RadarScreen(
    modifier: Modifier = Modifier,
    relay: RelayStation.Status? = null,
    salon: CabinSalon? = null,
    keys: NostrKeys? = null,
    /**
     * La cabine vit au-dessus des onglets, dans la station : son indicateur se
     * lit depuis n'importe quel écran, et changer d'onglet n'efface plus une
     * conversation que personne n'a fermée. Fermer reste un geste, ici.
     */
    cabin: CabinChat? = null,
    cabinOpen: Boolean = false,
    onOpenCabin: () -> Unit = {},
    onCloseCabin: () -> Unit = {},
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    var attempt by remember { mutableIntStateOf(0) }
    val unlocked = elapsed >= RITUAL_SECONDS

    // ── Balise de proximité : premier morceau réel de l'écran ─────────────
    val context = LocalContext.current
    val beaconRunning by ProximityService.running.collectAsStateWithLifecycle()
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
    val ownCell4d by ProximityService.advertisedCell4d.collectAsStateWithLifecycle()
    // La localisation se demande pour elle-même. Elle ne l'était auparavant que
    // par le bouton balise, ce qui obligeait à diffuser son adresse 4D en
    // continu pour obtenir un hexagone — donc pour entrer dans le salon de
    // cabine, qui n'en a pourtant aucun besoin (vérifié au banc : balise
    // coupée, le salon continue d'émettre et de recevoir).
    //
    // Un SEUL lanceur pour les deux demandes : deux lanceurs du même contrat
    // dans un même composable se sont disputé le résultat au banc, et le retour
    // d'une demande de localisation seule a démarré la balise. L'intention est
    // donc portée explicitement, pas déduite du lanceur appelé.
    var locationAttempt by remember { mutableIntStateOf(0) }
    var permissionFor by remember { mutableStateOf(PermissionIntent.NONE) }
    // Au deuxième refus, Android ne montre plus de dialogue : la demande revient
    // refusée dans la milliseconde et l'affordance devient un bouton mort, sans
    // que rien ne bouge à l'écran. Le seul recours est la page de réglages de
    // l'app. Impossible de le savoir d'avance — shouldShowRequestPermissionRationale
    // est faux AUSSI avant la première demande : seul un refus revenu de NOTRE
    // lanceur, sans rationale à montrer, prouve l'impasse.
    var locationDeadEnd by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        when (permissionFor) {
            // Rien à faire : l'effet qui veille sur `permissionFor` démarre la
            // balise dès que le Bluetooth est là. Localisation et notifications
            // restent optionnelles — sans elles, présence sans position.
            PermissionIntent.BEACON -> Unit
            // sans Bluetooth, pas de cabine : on n'ouvre pas un panneau muet
            PermissionIntent.CABIN -> if (results.values.all { it }) onOpenCabin()
            // accordée : on relance la résolution sans attendre le tour des 30 s
            // refusée : l'affordance mène-t-elle encore quelque part ?
            PermissionIntent.LOCATION ->
                if (results.values.any { it }) {
                    locationDeadEnd = false
                    locationAttempt++
                } else {
                    locationDeadEnd = !context.canStillAskFor(LOCATION_PERMISSIONS)
                }
            PermissionIntent.NONE -> Unit
        }
        permissionFor = PermissionIntent.NONE
    }
    // Le retour des réglages ne dit pas ce qui a été accordé : on re-sonde. Si
    // la permission est là, l'affordance disparaît d'elle-même ; sinon elle
    // continue de pointer vers les réglages, seul chemin qui reste.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { locationAttempt++ }

    // La balise est le socle : elle démarre d'elle-même dès que le Bluetooth
    // est accordé, et ne se coupe plus. Elle n'annonce une cellule que si la
    // localisation l'est aussi — présence sans position, sinon. `permissionFor`
    // sert de clé : tout retour de demande de permission repasse par ici.
    LaunchedEffect(beaconRunning, permissionFor) {
        if (!beaconRunning && ProximityService.corePermissionsGranted(context)) {
            ProximityService.start(context)
        }
    }

    LaunchedEffect(attempt) {
        val start = withFrameNanos { it }
        while (elapsed < RITUAL_SECONDS) {
            val now = withFrameNanos { it }
            elapsed = ((now - start) / 1_000_000_000f).coerceAtMost(RITUAL_SECONDS)
        }
    }

    // ── Le vrai fix : cellule H3, distance au centre, portail Goldberg ────
    val locator = remember { CellLocator(context.applicationContext) }
    var fix by remember { mutableStateOf<CellLocator.Fix?>(null) }
    var locationBlocker by remember { mutableStateOf<CellLocator.Blocker?>(null) }
    // Re-résout quand la balise change d'état ou quand la localisation vient
    // d'être accordée, puis toutes les 30 s.
    LaunchedEffect(beaconRunning, locationAttempt) {
        while (true) {
            fix = locator.currentFix()
            locationBlocker = if (fix == null) locator.blocker() else null
            // La permission accordée efface l'impasse : si l'utilisateur la
            // révoque ensuite depuis les réglages, Android remet son compteur
            // à zéro et redonne droit au dialogue.
            if (locationBlocker != CellLocator.Blocker.PERMISSION) locationDeadEnd = false
            delay(FIX_REFRESH_MS)
        }
    }
    val portal = fix?.let { GoldbergPortal.nearest(it.lat, it.lon) }
    val heading = rememberHeadingDegrees()

    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowRadar, A4L.Deep, centerY = 0.34f, radiusFactor = 1.2f)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {

        // ── Adresse de la tuile + cap ─────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (fix != null) A4L.Green else A4L.TextGhost)
                Spacer(Modifier.width(2.dp))
                Text(
                    // L'adresse réelle : portail Goldberg + cellule H3 du lieu.
                    if (fix != null && portal != null) {
                        "${portal.code}H${cellHex(fix!!.cell)}"
                    } else "a4l:—",
                    style = A4LText.Data.copy(fontSize = 10.sp),
                    color = A4L.TextBody,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Même témoin que sur l'écran Noyau : pendant une démo on vit
                // sur le Radar, la bascule « relais local » doit s'y voir.
                relay?.let {
                    StatusDot(if (it.online) A4L.Green else A4L.TextGhost)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        it.label,
                        style = A4LText.Data.copy(fontSize = 10.sp),
                        color = A4L.TextDim,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    heading?.let { "↑ %d°".format(it) } ?: "↑ —",
                    style = A4LText.Data.copy(fontSize = 10.sp),
                    color = A4L.TextDim,
                )
            }
        }

        // ── Titre ─────────────────────────────────────────────────────────
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text(
                if (unlocked) "Cabine déverrouillée" else "Cabine à portée",
                style = A4LText.H2,
                color = if (unlocked) A4L.Mint else A4L.TextHigh,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    fix == null -> when (locationBlocker) {
                        CellLocator.Blocker.SERVICE_OFF ->
                            "Hexagone inconnu — la Localisation du téléphone est coupée : " +
                                "réactivez-la dans les réglages rapides."
                        CellLocator.Blocker.PERMISSION -> if (locationDeadEnd) {
                            "Hexagone inconnu — la localisation a été refusée. Android " +
                                "ne la redemandera plus : elle se rouvre depuis les " +
                                "réglages de l'app. Elle n'allume aucune balise."
                        } else {
                            "Hexagone inconnu — la localisation résout votre cellule. " +
                                "Elle n'allume aucune balise."
                        }
                        null ->
                            "Hexagone inconnu — recherche de position en cours…"
                    }
                    unlocked ->
                        "Hexagone ${cellHex(fix!!.cell)} — abonnement au flux de la cabine actif."
                    else ->
                        "Hexagone ${cellHex(fix!!.cell)} — vous êtes à %.0f m du centre géométrique."
                            .format(fix!!.distanceToCenterM)
                },
                style = A4LText.Body,
                color = A4L.TextBody.copy(alpha = 0.45f),
            )
            if (fix == null && locationBlocker == CellLocator.Blocker.PERMISSION) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (locationDeadEnd) "Ouvrir les réglages de l'app" else "Accorder la localisation",
                    style = A4LText.Caption,
                    color = A4L.Mint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (locationDeadEnd) {
                                settingsLauncher.launch(appSettingsIntent(context))
                            } else {
                                permissionFor = PermissionIntent.LOCATION
                                permissionLauncher.launch(LOCATION_PERMISSIONS)
                            }
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }

        // ── Radar ─────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(318.dp)
                .padding(top = 8.dp)
                .clickable(enabled = unlocked) { elapsed = 0f; attempt++ },
            contentAlignment = Alignment.Center,
        ) {
            RadarRings(progress = elapsed / RITUAL_SECONDS)

            Box(Modifier.size(150.dp).background(A4L.Cyan.tint(0.07f), HexagonShape))
            Canvas(Modifier.size(150.dp)) {
                drawPath(
                    path = hexagonPath(size),
                    color = A4L.Cyan.tint(0.45f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // Compteur du rituel
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (unlocked) {
                    Text("⚛", fontSize = 40.sp, color = A4L.Mint)
                } else {
                    Text(
                        ceil(elapsed).toInt().toString(),
                        style = A4LText.Data.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 44.sp,
                        ),
                        color = A4L.Cyan,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (unlocked) "RITUEL ACCOMPLI" else "/ 33 S",
                    style = A4LText.Data.copy(fontSize = 10.sp, letterSpacing = 1.8.sp),
                    color = if (unlocked) A4L.Mint.copy(alpha = 0.7f) else A4L.Cyan.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (unlocked) "Toucher pour recommencer" else "Ne bougez plus",
                    style = A4LText.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = A4L.TextStrong,
                )
            }

            // Noyaux voisins qui respirent — les vrais, vus par la balise BLE.
            neighbors.take(MAX_NEIGHBOR_DOTS).forEach { neighbor ->
                key(neighbor.address) {
                    NeighborDot(neighbor = neighbor, ownCell4d = ownCell4d)
                }
            }
        }

        // ── Compteurs de la cabine ────────────────────────────────────────
        // Le salon de cabine ne vit que sur le relais local d'une station.
        val salonActive = relay?.local == true && relay.online
        val pensees = salon?.pensees?.collectAsStateWithLifecycle()?.value.orEmpty()
        var salonOpen by remember { mutableStateOf(false) }

        // ── Cabine : ce qui se dit ici, entre gens à portée ───────────────
        // Le moteur et son cycle de vie appartiennent à la station : c'est ce
        // qui permet à l'indicateur du haut de dire le médium depuis n'importe
        // quel onglet. Ne restent ici que le geste d'ouverture et les
        // permissions, qui sont affaire d'écran.
        val cabinPeers by (cabin?.peers ?: remember { MutableStateFlow(emptyList()) })
            .collectAsStateWithLifecycle()
        LaunchedEffect(fix?.cell) {
            fix?.let { salon?.setCell(cellHex(it.cell)) }
        }
        // Un seul geste d'ouverture/fermeture, partagé par le compteur « ici »
        // et par la rangée de la cabine : les deux désignent la même fenêtre.
        val toggleCabin: () -> Unit = {
            if (cabinOpen) {
                onCloseCabin()
            } else if (CabinChat.permissionsGranted(context)) {
                onOpenCabin()
            } else {
                permissionFor = PermissionIntent.CABIN
                permissionLauncher.launch(CabinChat.RUNTIME_PERMISSIONS)
            }
        }
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CabinStat(
                    if (salonActive) pensees.size.toString() else "—",
                    // « ici » appartient désormais à la cabine : l'hexagone,
                    // c'est ce qu'on n'atteint que par un relais
                    "dans l'hexagone",
                    Modifier
                        .weight(1f)
                        .clickable { salonOpen = !salonOpen },
                    accent = if (salonOpen) A4L.Green else null,
                )
                // Approximation en attendant la logique de portail D2 : les noyaux
                // qui annoncent la même cellule que la nôtre.
                CabinStat(
                    if (beaconRunning && ownCell4d != null) {
                        neighbors.count { it.cell4d == ownCell4d }.toString()
                    } else {
                        "—"
                    },
                    "dans le portail",
                    Modifier.weight(1f),
                )
                // « Ici » se compte par la cabine, pas par la balise. Ce
                // compteur lisait `neighbors` : il fallait donc diffuser une
                // annonce continue pour savoir qui est à portée, alors que la
                // balise ne sait dire que « une radio est là » quand la cabine,
                // elle, nomme des noyaux attestés (un par npub, dédoublonné —
                // voir CabinChat.peers). Chaque compteur tient désormais à
                // une seule fenêtre : le relais, la balise, la cabine.
                CabinStat(
                    if (cabinOpen) cabinPeers.size.toString() else "—",
                    "ici, sans relais",
                    Modifier
                        .weight(1f)
                        .clickable(onClick = toggleCabin),
                    accent = if (cabinOpen) A4L.Mint else null,
                )
            }
            if (salonOpen && salon != null) {
                CabinSalonPanel(salon = salon, pensees = pensees, active = salonActive)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .dashedGlass(
                        12.dp,
                        A4L.GlassFaint,
                        (if (cabinOpen) A4L.Mint else A4L.Stroke).copy(alpha = 0.2f),
                    )
                    .clickable(onClick = toggleCabin)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(if (cabinOpen) A4L.Mint else A4L.TextDim)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (cabinOpen) {
                        "Cabine ouverte — toucher pour fermer et effacer"
                    } else {
                        "Parler à ceux qui sont ici — chiffré, sans relais"
                    },
                    style = A4LText.Caption,
                    color = if (cabinOpen) A4L.Mint else A4L.TextMuted,
                )
            }
            if (cabinOpen) {
                cabin?.let { CabinDirectPanel(chat = it) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .dashedGlass(
                        12.dp,
                        A4L.GlassFaint,
                        (if (beaconRunning) A4L.Mint else A4L.Stroke).copy(alpha = 0.2f),
                    )
                    // Plus d'interrupteur : la balise est le socle, elle tourne
                    // dès qu'elle le peut. Ne reste à toucher que ce qui manque
                    // pour qu'elle le puisse.
                    .clickable(enabled = !beaconRunning) {
                        permissionFor = PermissionIntent.BEACON
                        permissionLauncher.launch(ProximityService.runtimePermissions())
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(if (beaconRunning) A4L.Mint else A4L.TextDim)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        // La cellule ne part QUE si la localisation est
                        // accordée : sans elle la balise annonce une présence,
                        // jamais une position (charge utile « cellule inconnue »).
                        beaconRunning && ownCell4d != null ->
                            "Balise active — annonce l'hexagone ${cellHex(ownCell4d!!)}"
                        beaconRunning ->
                            "Balise active — présence seule, sans position"
                        else -> "Balise de proximité — Bluetooth requis"
                    },
                    style = A4LText.Caption,
                    color = if (beaconRunning) A4L.Mint else A4L.TextMuted,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .dashedGlass(12.dp, A4L.GlassFaint, A4L.Stroke.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🌙", fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Après 22 h, cette cabine écoute l'hexagone aux antipodes.",
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Le cap de l'appareil en degrés (0 = nord), via le capteur de rotation.
 * null tant qu'aucune mesure n'est arrivée ou que l'appareil n'a pas de capteur.
 * Arrondi au degré pour ne recomposer qu'au changement visible.
 */
@Composable
private fun rememberHeadingDegrees(): Int? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val orientation = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val degrees = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                heading = degrees.toInt()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            if (sensor != null) manager.unregisterListener(listener)
        }
    }
    return heading
}

/** Cercles concentriques, balayage conique et anneau de progression du rituel. */
@Composable
private fun RadarRings(progress: Float) {
    Canvas(Modifier.size(290.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val stroke = Stroke(width = 1.dp.toPx())

        // Cercles de portée : 290 / 206 / 122 px
        drawCircle(A4L.Cyan.tint(0.09f), radius = 145.dp.toPx(), center = center, style = stroke)
        drawCircle(A4L.Cyan.tint(0.13f), radius = 103.dp.toPx(), center = center, style = stroke)
        drawCircle(A4L.Cyan.tint(0.18f), radius = 61.dp.toPx(), center = center, style = stroke)

        // Balayage conique, départ à 200° (le CSS compte depuis midi, le shader depuis 3 h)
        rotate(degrees = 200f - 90f, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to A4L.Cyan.tint(0.16f),
                    0.55f to Color.Transparent,
                    1f to Color.Transparent,
                    center = center,
                ),
                radius = 145.dp.toPx(),
                center = center,
            )
        }

        // Anneau de progression du rituel : 240 px de diamètre, 4 px d'épaisseur
        val ringStroke = Stroke(width = 4.dp.toPx())
        val ringRadius = 118.dp.toPx()
        val ringSize = Size(ringRadius * 2, ringRadius * 2)
        val ringTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
        drawArc(
            color = Color.White.copy(alpha = 0.07f),
            startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = ringTopLeft, size = ringSize, style = ringStroke,
        )
        drawArc(
            color = A4L.Cyan,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = ringTopLeft, size = ringSize, style = ringStroke,
        )
    }
}

/**
 * Au-delà, le radar deviendrait une nuée. Aucun compteur du bas ne rattrape
 * plus le débordement : ils tiennent chacun à une fenêtre nommée (relais,
 * portail, cabine), et « toutes les radios vues à la ronde » n'en est pas une.
 */
private const val MAX_NEIGHBOR_DOTS = 12

/**
 * Un vrai noyau voisin sur le radar.
 *
 * L'angle est dérivé de l'adresse radio (stable le temps de sa rotation ~15 min :
 * la pastille ne saute pas à chaque scan) — le BLE ne donne aucune direction,
 * c'est un placement de constellation, pas un cap. La distance au centre suit le
 * RSSI : signal fort = proche. La couleur dit la cellule : menthe = le même
 * hexagone que nous, ambre = un autre, indigo = cellule inconnue.
 */
@Composable
private fun BoxScope.NeighborDot(
    neighbor: NeighborRegistry.Neighbor,
    ownCell4d: Long?,
) {
    val hash = neighbor.address.hashCode()
    val angleRad = Math.toRadians(((hash % 360 + 360) % 360).toDouble())

    // RSSI −55 dBm (très proche) → bord du hexagone central ; −95 dBm → bord du radar.
    val fraction = ((-55 - neighbor.rssi).toFloat() / 40f).coerceIn(0f, 1f)
    val radius = 52.dp + 88.dp * fraction

    val color = when {
        neighbor.cell4d == null -> A4L.Indigo
        neighbor.cell4d == ownCell4d && ownCell4d != null -> A4L.Mint
        else -> A4L.Amber
    }
    // Période de respiration propre à chaque noyau, pour désynchroniser la nuée.
    val periodMillis = 2400 + ((hash ushr 16) % 800)

    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis / 2),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(
        Modifier
            .align(Alignment.Center)
            .offset(
                x = radius * cos(angleRad).toFloat(),
                y = radius * sin(angleRad).toFloat(),
            )
            .size(if (neighbor.cell4d != null && neighbor.cell4d == ownCell4d) 9.dp else 7.dp)
            .alpha(0.25f + 0.45f * t)
            .scale(1f + 0.06f * t)
            .background(color, CircleShape),
    )
}

/** Un compteur de la cabine : un grand nombre, un libellé. */
@Composable
private fun CabinStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Column(
        modifier
            .glass(
                radius = 12.dp,
                background = accent?.tint(0.07f) ?: A4L.GlassSoft,
                border = accent?.tint(0.24f) ?: A4L.StrokeSoft,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = A4LText.Metric, color = accent ?: A4L.TextHigh.copy(alpha = 0.88f))
        Text(
            label,
            style = A4LText.Caption.copy(fontSize = 11.sp),
            color = accent?.copy(alpha = 0.65f) ?: A4L.TextMuted,
        )
    }
}

/**
 * Le canal direct de la cabine : ceux qui sont à portée radio, en Noise
 * chiffré, sans relais. Rien de ce qui se dit ici n'en sort — c'est la
 * contrepartie étanche du salon d'hexagone.
 *
 * Hauteur bornée : l'écran est déjà dans un `verticalScroll`, et une liste
 * paresseuse ne se mesure pas dans une hauteur infinie.
 */
@Composable
private fun CabinDirectPanel(chat: CabinChat) {
    val context = LocalContext.current
    val status by chat.status.collectAsStateWithLifecycle()
    val messages by chat.messages.collectAsStateWithLifecycle()
    val sounds = remember { ChatSounds() }

    LaunchedEffect(chat) {
        chat.chimes.collect { chime ->
            when (chime) {
                CabinChat.Chime.SENT -> sounds.send()
                CabinChat.Chime.RECEIVED -> sounds.receive()
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(12.dp, background = A4L.GlassFaint, border = A4L.Mint.copy(alpha = 0.18f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val peers by chat.peers.collectAsStateWithLifecycle()
        Text(
            buildString {
                when {
                    peers.isNotEmpty() -> append("${peers.size} ici")
                    // un pair sans noyau incarné n'a rien à attester : il est
                    // bien là, mais il n'y a pas d'identité à montrer
                    status.links > 0 -> append("quelqu'un est là, sans identité déclarée")
                    else -> append("personne à portée")
                }
                status.lastError?.let { append("  ·  $it") }
            },
            style = A4LText.Caption,
            color = if (status.links > 0) A4L.Mint else A4L.TextMuted,
        )
        peers.forEach { peer ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(A4L.Mint)
                Spacer(Modifier.width(8.dp))
                Text(peer.short, style = A4LText.Data, color = A4L.TextHigh)
            }
        }
        // un lien vivant sans attestation : le pair n'a pas de noyau incarné,
        // donc rien à afficher de lui — mais il est bien là, et le taire
        // laisserait croire qu'on est seul
        if (status.unattestedLinks > 0 && peers.isNotEmpty()) {
            Text(
                "et ${status.unattestedLinks} lien(s) sans identité déclarée",
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
        ChatPanel(
            messages = messages,
            canSend = status.links > 0,
            placeholder = "ici, chiffré…",
            // le vide n'a pas le même sens selon qu'on est seul ou pas : dire
            // « personne à portée » alors que quelqu'un est là se contredirait
            // avec la présence affichée juste au-dessus
            emptyHint = if (status.links > 0) {
                "Personne n'a encore rien dit. Ce qui se dira ici ne partira sur " +
                    "aucun relais, et s'effacera en fermant la cabine."
            } else {
                "Personne à portée pour l'instant. La cabine trouve seule les noyaux " +
                    "proches dont la cabine est ouverte. Rien de ce qui se dit ici ne " +
                    "part sur un relais, et tout s'efface en fermant."
            },
            onSendText = { text -> chat.sendText(text) },
            onSendImage = { uri -> chat.sendImage(uri) },
            onSendFile = { uri -> chat.sendFile(uri) },
            onOpen = { message ->
                message.file?.let { file ->
                    runCatching {
                        context.startActivity(Attachments.viewIntent(context, file, message.mime))
                    }
                }
            },
            onDownload = { message ->
                message.file?.let { file ->
                    val ok = Attachments.saveToDownloads(context, file, message.name, message.mime)
                    Toast.makeText(
                        context,
                        if (ok) "Enregistré dans Téléchargements" else "Échec de l'enregistrement",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier.height(400.dp),
        )
    }
}

/**
 * Le salon de cabine, déplié sous les compteurs : les pensées éphémères du
 * lieu, et un champ pour y déposer la sienne. Fermé (message explicatif)
 * tant que l'antenne n'est pas accrochée au relais local d'une station.
 */
@Composable
private fun CabinSalonPanel(
    salon: CabinSalon,
    pensees: List<CabinSalon.Pensee>,
    active: Boolean,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(12.dp, background = A4L.GlassFaint, border = A4L.Green.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "SALON DE CABINE",
            style = A4LText.Data.copy(fontSize = 10.sp, letterSpacing = 1.7.sp),
            color = A4L.Green.copy(alpha = 0.75f),
        )
        if (!active) {
            Text(
                "Salon fermé — il s'ouvre quand la station du lieu est à portée " +
                    "(pastille « relais local »). Rien ne part sur Internet.",
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
            return@Column
        }
        if (pensees.isEmpty()) {
            Text(
                "Personne n'a encore rien déposé ici. Les pensées sont éphémères : " +
                    "elles vivent le temps du salon, rien n'est archivé.",
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
        pensees.takeLast(SALON_VISIBLE_PENSEES).forEach { pensee ->
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (pensee.mine) "moi · ${pensee.author}" else pensee.author,
                    style = A4LText.Data.copy(fontSize = 9.sp),
                    color = if (pensee.mine) A4L.Mint.copy(alpha = 0.8f) else A4L.TextDim,
                )
                Text(pensee.text, style = A4LText.Caption, color = A4L.TextBody)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .glass(8.dp, background = A4L.GlassSoft, border = A4L.StrokeSoft)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                textStyle = A4LText.Caption.copy(color = A4L.TextHigh),
                cursorBrush = SolidColor(A4L.Mint),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text("une pensée pour ce lieu…", style = A4LText.Caption, color = A4L.TextGhost)
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (sending) "…" else "déposer",
                style = A4LText.Caption,
                color = if (draft.isBlank() || sending) A4L.TextGhost else A4L.Mint,
                modifier = Modifier
                    .clickable(enabled = draft.isNotBlank() && !sending) {
                        scope.launch {
                            sending = true
                            if (salon.send(draft)) draft = ""
                            sending = false
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 9.dp),
            )
        }
    }
}

/** Le salon montre les dernières pensées sans envahir l'écran du Radar. */
private const val SALON_VISIBLE_PENSEES = 12
