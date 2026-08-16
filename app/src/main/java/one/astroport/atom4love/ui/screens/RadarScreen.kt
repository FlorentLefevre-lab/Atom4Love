package one.astroport.atom4love.ui.screens

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
import android.widget.Toast
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import one.astroport.atom4love.R
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.chat.CabinChat
import one.astroport.atom4love.chat.CabinError
import one.astroport.atom4love.chat.ChatSounds
import one.astroport.atom4love.chat.Medium
import one.astroport.atom4love.chat.ui.ChatPanel
import one.astroport.atom4love.chat.ui.QuestionsPanel
import one.astroport.atom4love.domain.GoldbergPortal
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.domain.Questions
import one.astroport.atom4love.domain.Wave
import one.astroport.atom4love.nostr.CabinSalon
import one.astroport.atom4love.nostr.NostrKeys
import one.astroport.atom4love.nostr.RelayStation
import one.astroport.atom4love.proximity.CellLocator
import one.astroport.atom4love.proximity.NeighborRegistry
import one.astroport.atom4love.proximity.ProximityPayload
import one.astroport.atom4love.proximity.ProximityService
import one.astroport.atom4love.ui.components.HexagonShape
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.hexagonPath
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint


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
 * Le compteur tourne réellement : 33 s d'immobilité déverrouillent la cabine, et
 * un appui sur le radar relance le rituel.
 *
 * **Il manque toujours le lieu, et pas faute d'avoir essayé.** `miz.html` écrit
 * « 33 secondes immobile à moins de 50 m du centre de l'hexagone » — mais son
 * hexagone est celui du **rendez-vous**, déduit des deux phases φ, un point
 * qu'on va rejoindre exprès à deux. Nous ne savons calculer que la cellule H3
 * où l'on se tient déjà, et son centre n'est le rendez-vous de personne : en
 * résolution 8 il est à 460 m d'arête, donc presque toujours à plus de 50 m —
 * un verrou posé dessus fermerait la cabine partout. Tant que l'hexagone de
 * rencontre n'est pas calculé, le rituel reste une durée et rien d'autre.
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
    /** Forcer la voie porteuse — même porte que l'entête, permissions comprises. */
    onSelectMedium: (Medium) -> Unit = {},
    cabinOpen: Boolean = false,
    onOpenCabin: () -> Unit = {},
    onCloseCabin: () -> Unit = {},
    /**
     * Revenir dans une cabine déjà ouverte, sans rien fermer. Le retour système
     * quitte la destination sans y toucher : il faut donc un chemin pour y
     * retourner, et ce n'est pas le même geste que fermer.
     */
    onEnterCabin: () -> Unit = {},
) {

    // ── Balise de proximité : premier morceau réel de l'écran ─────────────
    val context = LocalContext.current
    val beaconRunning by ProximityService.running.collectAsStateWithLifecycle()
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
    val ownCell4d by ProximityService.advertisedCell4d.collectAsStateWithLifecycle()
    val ownSignature by ProximityService.signature.collectAsStateWithLifecycle()
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

    // ⚠ **Le rituel des 33 secondes vivait ici** : un compteur qui descendait
    // pendant qu'on se tenait immobile, et un « RITUEL ACCOMPLI » au bout. Il
    // jouait aussi un binaural. Les deux sont partis le 15/08, sur décision de
    // Florent.
    //
    // Il ne commandait rien : ni l'abonnement à l'hexagone (qui tient au relais
    // du lieu, et à lui seul), ni la cabine, ni la balise. Il ne changeait que
    // deux mots de l'entête et affichait un décompte — une preuve de présence
    // qui ne prouvait rien à personne, puisque rien ne la vérifiait.

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


    // Le défilement de l'écran est tenu ici, et non posé au vol : ouvrir la
    // cabine doit amener la conversation sous les yeux. Sur un écran de 677 dp
    // elle naît sous la ligne de flottaison, derrière le disque du radar.
    val pageScroll = rememberScrollState()

    // ── La cabine ouverte prend l'écran ───────────────────────────────────
    //
    // Elle vivait dans la page, en panneau de hauteur fixe, et la page se
    // faisait défiler de force pour l'amener sous les yeux. Deux défauts en
    // sortaient : la hauteur était devinée (58 % de l'écran), et **le clavier
    // recouvrait la rangée de saisie** — il fallait le refermer pour atteindre
    // Envoyer, puisque rien dans un îlot figé au milieu d'un défilement ne peut
    // céder la place à l'IME.
    //
    // En destination, la forme est celle de toutes les conversations : la liste
    // prend ce qui reste, la saisie se pose dessous, et `imePadding` la remonte
    // au-dessus du clavier. La page du radar reste composée derrière — fermer la
    // cabine rend l'écran là où on l'avait laissé.
    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowRadar, A4L.Deep, centerY = 0.34f, radiusFactor = 1.2f)
            .statusBarsPadding()
            // Avant `verticalScroll`, et l'ordre compte : la fenêtre de
            // défilement doit rétrécir de la hauteur du clavier, faute de quoi
            // le champ du salon reste sous lui sans qu'aucun geste ne l'en
            // sorte. Après, on n'aurait fait qu'ajouter du vide en bas.
            .imePadding()
            .verticalScroll(pageScroll),
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

        // Le flux de l'hexagone ne passe QUE par le relais du lieu, jamais par
        // un relais public : sans lui, il n'y a pas d'abonnement, quoi qu'ait
        // accompli le rituel. Calculé ici et non plus bas, parce que l'entête
        // juste en dessous l'annonce.
        val salonActive = relay?.local == true && relay.online

        // ── Titre ─────────────────────────────────────────────────────────
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text(
                stringResource(R.string.radar_cabin_in_range),
                style = A4LText.H2,
                color = A4L.TextHigh,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    fix == null -> when (locationBlocker) {
                        CellLocator.Blocker.SERVICE_OFF ->
                            stringResource(R.string.radar_hex_unknown_service_off)
                        CellLocator.Blocker.PERMISSION -> if (locationDeadEnd) {
                            stringResource(R.string.radar_hex_unknown_dead_end)
                        } else {
                            stringResource(R.string.radar_hex_unknown_permission)
                        }
                        null ->
                            stringResource(R.string.radar_hex_unknown_searching)
                    }
                    // L'abonnement tient au relais du lieu, et à lui seul —
                    // c'est ce que le rituel laissait croire qu'il commandait.
                    salonActive ->
                        stringResource(R.string.radar_hex_subscribed, cellHex(fix!!.cell))
                    else ->
                        stringResource(
                            R.string.radar_hex_distance,
                            cellHex(fix!!.cell),
                            fix!!.distanceToCenterM,
                        )
                },
                style = A4LText.Body,
                color = A4L.TextBody.copy(alpha = 0.45f),
            )
            if (fix == null && locationBlocker == CellLocator.Blocker.PERMISSION) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        if (locationDeadEnd) {
                            R.string.radar_open_app_settings
                        } else {
                            R.string.radar_grant_location
                        },
                    ),
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

        // L'état de la cabine : par où elle parle, ce qu'elle accepte, et qui
        // tient le groupe Wi-Fi Direct. Lu avant le radar, parce que les
        // glyphes de médium vivent à côté du cadran.
        val cabinStatus by (cabin?.status ?: remember { MutableStateFlow(CabinChat.Status()) })
            .collectAsStateWithLifecycle()

        // ── Compteurs de la cabine ────────────────────────────────────────
        // Le salon de cabine ne vit que sur le relais local d'une station.
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
        // ⚠ Ouvre ou **revient**, jamais ne ferme. Fermer une cabine efface la
        // conversation : ce geste-là ne se déclenche que dans la cabine, où l'on
        // voit ce qu'on efface. D'ici on ne fait qu'y entrer.
        val toggleCabin: () -> Unit = {
            if (cabinOpen) {
                onEnterCabin()
            } else if (CabinChat.permissionsGranted(context)) {
                onOpenCabin()
            } else {
                permissionFor = PermissionIntent.CABIN
                permissionLauncher.launch(CabinChat.RUNTIME_PERMISSIONS)
            }
        }

        // ── Les voies, et les trois fenêtres ──────────────────────────────
        //
        // ⚠ Deux choses ont disparu de ce bloc. Un cadran de 318 dp d'abord —
        // cercles concentriques, balayage conique, anneau de progression — qui
        // tournait joliment sans rien dire : aucun cercle ne portait de
        // distance, aucun angle de direction. Puis **le compteur du rituel des
        // 33 secondes**, le 15/08. Reste ce qui informe.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Par où la cabine parle. Les trois voies, pas trois positions :
            // le BLE reste ouvert quand le Wi-Fi porte, et l'ensemble ne fait
            // que grandir. Chacune dit donc son propre état.
            MediumGlyphs(status = cabinStatus, open = cabinOpen)

            // Les trois fenêtres, de la plus proche à la plus lointaine : ici
            // (la cabine, à portée d'antenne), le portail (ceux qui annoncent
            // notre cellule), l'hexagone (ce qu'on n'atteint que par un relais).
            //
            // Elles étaient empilées à droite du cadran, faute de largeur.
            // Le cadran parti, elles reprennent la ligne — et l'éloignement se
            // lit maintenant de gauche à droite, dans le sens de la lecture.
            // Le reproche d'alors (« l'ordre inverse se lisait sans rien dire »)
            // portait sur l'ordre, pas sur la rangée.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // « Ici » se compte par la cabine, pas par la balise : elle
                // nomme des noyaux attestés, un par npub, là où la balise ne
                // sait dire que « une radio est là ».
                CabinStat(
                    if (cabinOpen) cabinPeers.size.toString() else "—",
                    stringResource(R.string.radar_stat_here_no_relay),
                    Modifier
                        .weight(1f)
                        .clickable(onClick = toggleCabin),
                    accent = if (cabinOpen) A4L.Mint else null,
                )
                // Approximation en attendant la logique de portail D2 : les
                // noyaux qui annoncent la même cellule que la nôtre. Comptés
                // par jeton de présence et non par adresse — une adresse qui
                // tourne pendant que l'ancienne survit à son TTL faisait
                // compter deux fois le même appareil.
                CabinStat(
                    if (beaconRunning && ownCell4d != null) {
                        NeighborRegistry.countIn(neighbors, ownCell4d).toString()
                    } else {
                        "—"
                    },
                    stringResource(R.string.radar_stat_in_portal),
                    Modifier.weight(1f),
                )
                CabinStat(
                    if (salonActive) pensees.size.toString() else "—",
                    stringResource(R.string.radar_stat_in_hexagon),
                    Modifier
                        .weight(1f)
                        .clickable { salonOpen = !salonOpen },
                    accent = if (salonOpen) A4L.Green else null,
                )
            }
        }

        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Qui tient le groupe Wi-Fi Direct. En rouge, et ce n'est pas une
            // alarme : c'est le poids du rôle. Le propriétaire EST le point
            // d'accès, et son départ dissout le groupe pour tout le monde, là
            // où un client qui s'en va ne retire que lui-même. Quand c'est
            // nous, la phrase le dit — on part rarement en sachant qu'on
            // emporte la voie des autres.
            // Le groupe vient de se refermer. En ambre et non en rouge : rien
            // n'est cassé, quelqu'un est parti — et le BLE n'a jamais cessé de
            // porter la cabine. Le bandeau reste jusqu'à ce qu'on le touche :
            // une nouvelle qui s'efface toute seule n'a pas été donnée.
            cabinStatus.groupClosedBy?.let { who ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(12.dp, A4L.Amber.tint(0.08f), A4L.Amber.tint(0.28f))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { cabin?.dismissGroupClosed() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(A4L.Amber)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.radar_group_closed, who),
                            style = A4LText.Caption,
                            color = A4L.Amber,
                        )
                        // La voie de repli se propose ICI, au moment où on la
                        // perd — pas seulement en petit dans le bandeau du
                        // haut, où l'œil ne va pas quand il vient de lire une
                        // mauvaise nouvelle.
                        //
                        // Le réseau du lieu, et lui seul. Afficher `offered`
                        // tel quel proposait « repasser en Wi-Fi P2P » — c'est
                        // à dire revenir à ce qui venait de se fermer, sans
                        // personne en face pour rouvrir un groupe : la cabine
                        // propose Direct dès qu'aucune station n'est joignable,
                        // ce qui est vrai et illisible. Faute de station
                        // offerte, on ne propose rien et le bandeau se contente
                        // de dire ce qui s'est passé.
                        // Deux issues, et il faut les deux : reprendre le réseau
                        // du lieu quand il est là, ou rouvrir un groupe soi-même.
                        // N'en proposer qu'une laissait sans porte de sortie qui
                        // n'a pas de box en commun avec le pair restant.
                        // Une issue ne se propose que s'il reste quelqu'un à
                        // rejoindre. Quand le pair a fermé sa cabine et pas
                        // seulement son groupe, il n'y a plus de voie vers
                        // personne : offrir « repasser en Wi-Fi AP » promettait
                        // une reconnexion impossible, qui finissait en
                        // « injoignable ».
                        val someoneLeft = cabinPeers.isNotEmpty()
                        if (someoneLeft && Medium.WIFI_STATION in cabinStatus.reachable) {
                            Text(
                                stringResource(
                                    R.string.radar_group_fallback,
                                    Medium.WIFI_STATION.short,
                                ),
                                style = A4LText.Caption,
                                color = A4L.Cyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        // la nouvelle a été lue, et on agit
                                        // dessus : la garder à l'écran ferait
                                        // douter que le geste ait porté
                                        cabin?.dismissGroupClosed()
                                        onSelectMedium(Medium.WIFI_STATION)
                                    }
                                    .padding(vertical = 2.dp),
                            )
                        }
                        if (someoneLeft && Medium.WIFI_DIRECT in cabinStatus.reachable) {
                            Text(
                                stringResource(R.string.radar_group_reopen),
                                style = A4LText.Caption,
                                color = A4L.Cyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        cabin?.dismissGroupClosed()
                                        onSelectMedium(Medium.WIFI_DIRECT)
                                    }
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                    Text("✕", style = A4LText.Data, color = A4L.Amber.copy(alpha = 0.7f))
                }
            }
            cabinStatus.groupHost?.let { host ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(12.dp, A4L.Red.tint(0.06f), A4L.Red.tint(0.22f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(A4L.Red)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when (host) {
                            is CabinChat.GroupHost.Self ->
                                stringResource(R.string.radar_group_host_self)
                            is CabinChat.GroupHost.Peer ->
                                stringResource(R.string.radar_group_host_peer, host.short)
                        },
                        style = A4LText.Caption,
                        color = A4L.Red,
                    )
                }
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
                    stringResource(
                        if (cabinOpen) {
                            R.string.radar_cabin_row_return
                        } else {
                            R.string.radar_cabin_row_closed
                        },
                    ),
                    style = A4LText.Caption,
                    color = if (cabinOpen) A4L.Mint else A4L.TextMuted,
                )
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
                            stringResource(R.string.radar_beacon_with_cell, cellHex(ownCell4d!!))
                        beaconRunning ->
                            stringResource(R.string.radar_beacon_presence_only)
                        else -> stringResource(R.string.radar_beacon_needs_bluetooth)
                    },
                    style = A4LText.Caption,
                    color = if (beaconRunning) A4L.Mint else A4L.TextMuted,
                )
            }
            // Ce que la balise entend des autres, une fois traduit : le panneau
            // ne paraît que s'il y a quelque chose à dire, et disparaît avec le
            // dernier voisin signé.
            ResonancePanel(neighbors = neighbors, own = ownSignature)
        }

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * La cabine quand elle est ouverte : tout l'écran, et rien d'autre.
 *
 * Une seule rangée au-dessus, celle par où l'on sort — sans elle on ne saurait
 * plus d'où l'on vient ni comment refermer. En dessous, [CabinDirectPanel]
 * prend ce qui reste.
 *
 * L'encoche du clavier est consommée **ici**, sur la colonne entière, et non sur
 * la rangée de saisie : c'est la colonne qui doit rétrécir quand le clavier
 * monte, pour que la liste cède sa place et que la saisie remonte avec elle.
 * Posée plus bas, elle n'aurait fait qu'ajouter du vide sous un bloc déjà hors
 * d'atteinte.
 *
 * Elle est montée dans [one.astroport.atom4love.ui.A4LApp], au même rang que
 * l'Aide et les Réglages, et **couvre la barre de menus**. Rendue à l'intérieur
 * de l'onglet, elle s'arrêtait au-dessus de la barre — dont la place restait
 * réservée derrière le clavier, laissant 64 dp de vide entre la saisie et les
 * touches. Mesuré à l'écran, pas déduit.
 */
@Composable
internal fun CabinDestination(
    chat: CabinChat,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(A4L.Deep)
            .screenBackground(A4L.GlowRadar, A4L.Deep, centerY = 0.34f, radiusFactor = 1.2f)
            // Les deux, et dans cet ordre : `imePadding` consomme l'encoche du
            // clavier, `navigationBarsPadding` n'ajoute ensuite que ce qui
            // reste — zéro quand le clavier est là (il couvre déjà la barre),
            // la hauteur des boutons système quand il n'y est pas. Sans le
            // second, la rangée de saisie passait sous les boutons du système.
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)
                .dashedGlass(12.dp, A4L.GlassFaint, A4L.Mint.copy(alpha = 0.3f))
                .clickable(onClick = onClose)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(A4L.Mint)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.radar_cabin_row_open),
                style = A4LText.Caption,
                color = A4L.Mint,
            )
        }
        CabinDirectPanel(
            chat = chat,
            modifier = Modifier
                .weight(1f)
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
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
    // L'angle tenait à l'adresse, qui tourne toutes les 20 à 40 s : le point
    // sautait alors ailleurs sur le radar sans que personne n'ait bougé.
    val hash = neighbor.identity.hashCode()
    val angleRad = Math.toRadians(((hash % 360 + 360) % 360).toDouble())

    // RSSI −55 dBm (très proche) → bord du hexagone central ; −95 dBm → bord du radar.
    val fraction = ((-55 - neighbor.rssi).toFloat() / 40f).coerceIn(0f, 1f)
    val radius = 52.dp + 88.dp * fraction

    val color = when {
        neighbor.cell4d == null -> A4L.Indigo
        neighbor.cell4d == ownCell4d -> A4L.Mint
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

/**
 * Les résonances lues dans l'air — un voisin par ligne, la plus forte en tête.
 *
 * Ce panneau ne dit jamais **qui** : l'annonce ne porte pas de npub, et c'est
 * exprès. Il dit comment deux ondes se croiseraient si elles se rencontraient —
 * la polarité, le sceau maya, et le k de Fred. Un voisin resté à une ancienne
 * version de l'annonce n'a pas de ligne : il compte toujours dans le portail,
 * il n'a simplement rien signé.
 */
@Composable
private fun ResonancePanel(
    neighbors: List<NeighborRegistry.Neighbor>,
    own: ProximityPayload.Signature,
) {
    // Une ligne par personne, comme les points : deux adresses d'un même jeton
    // sont un seul appareil qui vient de changer de visage.
    //
    // ⚠ Tri sur [Phi2X.resonanceK] seul — même règle qu'au Plateau, et ici elle
    // décide de qui est COUPÉ par le `take` qui suit. Ne pas remonter les 🤝
    // avant les ⚡ : k vaut 1 en phase ET en opposition, et une opposition
    // parfaite sortirait de la liste alors qu'elle est un maximum de résonance.
    // Ce qui doit tomber en bas, c'est le quart de tour (k = 0,5).
    val signed = neighbors
        .distinctBy { it.identity }
        .filter { it.signature != ProximityPayload.Signature.Unknown }
        .sortedByDescending { neighbor ->
            val theirs = neighbor.signature.phase
            if (own.phase != null && theirs != null) Phi2X.resonanceK(own.phase, theirs) else -1.0
        }
        .take(MAX_NEIGHBOR_DOTS)
    if (signed.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .glass(12.dp, A4L.GlassFaint, A4L.Cyan.tint(0.18f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SectionLabel(stringResource(R.string.radar_resonance_title))
        signed.forEach { neighbor ->
            key(neighbor.identity) {
                ResonanceRow(signature = neighbor.signature, own = own)
            }
        }
        Text(
            stringResource(
                // Sans notre propre phase il n'y a rien à comparer, et la
                // raison en est réparable : c'est le lieu de naissance qui
                // manque. Le dire ici plutôt que d'aligner des tirets muets.
                if (own.phase == null) {
                    R.string.radar_resonance_mine_missing
                } else {
                    R.string.radar_resonance_hint
                },
            ),
            style = A4LText.Caption.copy(fontSize = 10.sp),
            color = A4L.TextGhost,
        )
    }
}

/** Une résonance : la polarité, le sceau, et k. */
@Composable
private fun ResonanceRow(
    signature: ProximityPayload.Signature,
    own: ProximityPayload.Signature,
) {
    val k = own.phase?.let { mine ->
        signature.phase?.let { theirs -> Phi2X.resonanceK(mine, theirs) }
    }
    val singular = own.phase != null && signature.phase != null &&
        Phi2X.isOpticalSingularity(own.phase, signature.phase)
    val strong = k != null && k >= Phi2X.SUPER_COHERENCE_K
    val accent = if (strong) A4L.Mint else A4L.Cyan

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // La polarité de l'autre, dans le glyphe que la fiche lui a donné.
        Text(
            signature.sex?.let { sex -> Wave.entries.firstOrNull { it.sex == sex }?.symbol } ?: "·",
            style = A4LText.Data.copy(fontSize = 15.sp),
            color = accent,
            modifier = Modifier.width(22.dp),
        )
        Text(
            KinMaya.glyphName(signature.glyph) ?: "—",
            style = A4LText.Data.copy(fontSize = 12.sp),
            color = A4L.TextBody,
            modifier = Modifier.weight(1f),
        )
        if (singular) {
            Text(
                stringResource(R.string.radar_resonance_singularity),
                style = A4LText.Caption.copy(fontSize = 10.sp),
                color = A4L.Mint,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            // Trois décimales, comme Fred les écrit : k se lit à la troisième,
            // deux ondes voisines partagent les deux premières.
            k?.let {
                stringResource(R.string.radar_resonance_k, String.format(Locale.getDefault(), "%.3f", it))
            } ?: stringResource(R.string.radar_resonance_no_phase),
            style = A4LText.Data.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (k == null) A4L.TextGhost else accent,
        )
    }
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
 * paresseuse ne se mesure pas dans une hauteur infinie. Bornée **à l'écran**
 * et non à un nombre fixe : 400 dp tenaient sur les 1000 dp de la tablette et
 * poussaient le champ de saisie sous la barre de navigation du Pixel, qui n'en
 * a que 677 — on y tapait à l'aveugle, après avoir fait défiler toute la page.
 */
@Composable
private fun CabinDirectPanel(chat: CabinChat, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status by chat.status.collectAsStateWithLifecycle()
    val messages by chat.messages.collectAsStateWithLifecycle()
    val sounds = remember { ChatSounds() }
    val refusal by chat.refusal.collectAsStateWithLifecycle()
    // Lus dans la composition, pas dans le rappel : une ressource lue depuis un
    // lambda ne serait pas réévaluée si la langue change sous l'application.
    val savedLabel = stringResource(R.string.cabin_saved_to_downloads)
    val saveFailedLabel = stringResource(R.string.cabin_save_failed)

    // Une pièce refusée mérite un dialogue : le sélecteur système vient de se
    // refermer, et sans lui il ne se passerait visiblement rien.
    refusal?.let { refused ->
        AlertDialog(
            onDismissRequest = { chat.dismissRefusal() },
            confirmButton = {
                Text(
                    stringResource(R.string.cabin_refused_ok),
                    style = A4LText.Caption,
                    color = A4L.Mint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { chat.dismissRefusal() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            title = {
                Text(
                    stringResource(
                        when (refused) {
                            is CabinChat.Refusal.TooBig -> R.string.cabin_too_big_title
                            is CabinChat.Refusal.VideoNeedsWifi -> R.string.cabin_video_wifi_title
                        },
                    ),
                    style = A4LText.H2,
                    color = A4L.TextHigh,
                )
            },
            text = {
                // Deux phrases entières, jamais des bribes recollées : l'ordre
                // des mots n'est pas le même d'une langue à l'autre, et
                // « au-delà des » seul ne se traduit pas.
                val medium = stringResource(refused.medium.labelRes)
                val res = LocalResources.current
                Text(
                    when (refused) {
                        is CabinChat.Refusal.TooBig -> buildString {
                            val limit = Attachments.humanSize(res, refused.limit)
                            append(
                                if (refused.bytes > 0) {
                                    stringResource(
                                        R.string.cabin_too_big_sized,
                                        refused.name,
                                        Attachments.humanSize(res, refused.bytes),
                                        limit,
                                        medium,
                                    )
                                } else {
                                    stringResource(
                                        R.string.cabin_too_big_unsized,
                                        refused.name,
                                        limit,
                                        medium,
                                    )
                                },
                            )
                            // le plafond radio n'est pas une limite technique mais
                            // une limite d'attente : 10 Mo en BLE, c'est un quart
                            // d'heure
                            if (refused.medium == Medium.BLE) {
                                append("\n\n")
                                append(
                                    stringResource(
                                        R.string.cabin_too_big_ble_advice,
                                        Attachments.humanSize(res, CabinChat.MAX_TRANSFER_STREAM),
                                    ),
                                )
                            }
                        }
                        // Ni un plafond ni une panne : la règle. Une vidéo part
                        // telle qu'elle a été filmée, et la radio ne la porte pas.
                        is CabinChat.Refusal.VideoNeedsWifi ->
                            if (refused.name.isBlank()) {
                                stringResource(R.string.cabin_video_wifi_unnamed, medium)
                            } else {
                                stringResource(
                                    R.string.cabin_video_wifi_named,
                                    refused.name,
                                    medium,
                                )
                            }
                    },
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            },
            containerColor = A4L.Deep,
        )
    }

    LaunchedEffect(chat) {
        chat.chimes.collect { chime ->
            when (chime) {
                CabinChat.Chime.SENT -> sounds.send()
                CabinChat.Chime.RECEIVED -> sounds.receive()
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .glass(12.dp, background = A4L.GlassFaint, border = A4L.Mint.copy(alpha = 0.18f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val peers by chat.peers.collectAsStateWithLifecycle()
        Text(
            buildString {
                when {
                    peers.isNotEmpty() ->
                        append(stringResource(R.string.cabin_peers_here, peers.size))
                    // un pair sans noyau incarné n'a rien à attester : il est
                    // bien là, mais il n'y a pas d'identité à montrer
                    status.links > 0 -> append(stringResource(R.string.cabin_someone_unnamed))
                    else -> append(stringResource(R.string.cabin_nobody_in_range))
                }
                status.lastError?.let { append("  ·  ${it.text()}") }
            },
            style = A4LText.Caption,
            color = if (status.links > 0) A4L.Mint else A4L.TextMuted,
        )
        val exchanges by chat.exchanges.collectAsStateWithLifecycle()
        val answerable by chat.answerable.collectAsStateWithLifecycle()
        peers.forEach { peer ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(A4L.Mint)
                    Spacer(Modifier.width(8.dp))
                    Text(peer.short, style = A4LText.Data, color = A4L.TextHigh)
                }
                // Le jeu se joue avec quelqu'un, pas dans une salle : il vit
                // sous la personne, et chaque partie n'appartient qu'aux deux.
                val history = exchanges[peer.npub].orEmpty()
                QuestionsPanel(
                    history = history.sortedBy { it.trait.ordinal },
                    offerable = Questions.offerable(answerable, history),
                    onAsk = { trait -> chat.ask(peer.npub, trait) },
                    onAnswer = { trait -> chat.answer(peer.npub, trait) },
                    onDecline = { trait -> chat.decline(peer.npub, trait) },
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        // un lien vivant sans attestation : le pair n'a pas de noyau incarné,
        // donc rien à afficher de lui — mais il est bien là, et le taire
        // laisserait croire qu'on est seul
        if (status.unattestedLinks > 0 && peers.isNotEmpty()) {
            Text(
                pluralStringResource(
                    R.plurals.cabin_unattested_links,
                    status.unattestedLinks,
                    status.unattestedLinks,
                ),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
        ChatPanel(
            messages = messages,
            canSend = status.links > 0,
            placeholder = stringResource(R.string.cabin_chat_placeholder),
            // ⚠ Deux phrases se relayaient ici selon qu'un pair était là ou
            // non, et **toutes deux redisaient l'entête** — « personne à
            // portée » y est déjà écrit deux lignes plus haut, avec le compte
            // des pairs. Une seule reste, celle qui n'est écrite nulle part
            // ailleurs : ce que devient ce qu'on dit. Elle s'efface au premier
            // message, entrant comme sortant (`messages.isEmpty()` dans
            // `ChatPanel`).
            emptyHint = stringResource(R.string.cabin_empty),
            onSendText = { text -> chat.sendText(text) },
            onSendImage = { uri -> chat.sendImage(uri) },
            onSendFile = { uri -> chat.sendFile(uri) },
            onCancel = { message -> chat.cancelSend(message.id) },
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
                        if (ok) savedLabel else saveFailedLabel,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            // Plus aucune hauteur devinée : la conversation prend ce que la
            // destination lui laisse, et rétrécit d'elle-même quand le clavier
            // monte. C'est tout l'intérêt d'avoir quitté la page qui défile.
            modifier = Modifier.weight(1f),
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
            stringResource(R.string.salon_title),
            style = A4LText.Data.copy(fontSize = 10.sp, letterSpacing = 1.7.sp),
            color = A4L.Green.copy(alpha = 0.75f),
        )
        if (!active) {
            Text(
                stringResource(R.string.salon_closed),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
            return@Column
        }
        if (pensees.isEmpty()) {
            Text(
                stringResource(R.string.salon_empty),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
        pensees.takeLast(SALON_VISIBLE_PENSEES).forEach { pensee ->
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (pensee.mine) {
                        stringResource(R.string.salon_mine, pensee.author)
                    } else {
                        pensee.author
                    },
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
                        Text(
                            stringResource(R.string.salon_draft_placeholder),
                            style = A4LText.Caption,
                            color = A4L.TextGhost,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (sending) "…" else stringResource(R.string.salon_post),
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

/**
 * Un [CabinError] mis en mots, ici et maintenant. Le moteur a dit lequel ;
 * c'est cette ligne qui choisit la langue, et elle change avec elle.
 */
@Composable
private fun CabinError.text(): String =
    if (args.isEmpty()) {
        stringResource(messageRes)
    } else {
        stringResource(messageRes, *args.toTypedArray())
    }

/**
 * Les trois voies de la cabine, en glyphes, à côté du radar.
 *
 * Ce sont les **symboles génériques de Material**, pas les marques déposées du
 * Bluetooth SIG ni de la Wi-Fi Alliance : celles-là ont des conditions d'usage
 * réservées aux produits certifiés. Le rune et les arcs disent la même chose et
 * se reconnaissent aussi bien.
 *
 * Quatre états, parce que les médiums se cumulent :
 *  - **engagé** — c'est lui qui porte le trafic en ce moment (plein, menthe) ;
 *  - **ouvert** — accepté, mais un plus rapide sert (menthe éteinte) ;
 *  - **proposé** — un pair l'annonce, il reste à l'accepter (cyan) ;
 *  - **hors d'atteinte** — personne ne l'offre (fantôme).
 */
@Composable
private fun MediumGlyphs(
    status: CabinChat.Status,
    open: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        // Le cadran n'a pas de marge à lui : sans ce retrait, les glyphes se
        // collaient 16 dp plus à gauche que tout le reste de l'écran.
        modifier.padding(start = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Medium.entries.forEach { medium ->
            val inUse = open && status.medium == medium
            val enabled = open && medium in status.enabled
            val offered = open && status.offered == medium
            // Le bleu du Bluetooth SIG, ou la marque monochrome de la Wi-Fi
            // Alliance — la seconde suit le thème, elle n'a pas de couleur.
            // Les deux Bluetooth partagent la marque : c'est la même radio et
            // le même bleu du SIG, seule la couche au-dessus change.
            val bluetooth = medium == Medium.BLE || medium == Medium.BT_CLASSIC
            val brand = if (bluetooth) A4L.BluetoothBrand else A4L.WifiBrand
            Icon(
                imageVector = when (medium) {
                    Medium.BLE -> Icons.Filled.Bluetooth
                    // Le même pictogramme que le BLE, et c'est voulu : dans une
                    // ligne où l'on cherche par où ça passe, ce qui compte est
                    // « Bluetooth » — le nom court sous l'icône dit lequel.
                    Medium.BT_CLASSIC -> Icons.Filled.Bluetooth
                    // les arcs : on est client d'un point d'accès
                    Medium.WIFI_STATION -> Icons.Filled.Wifi
                    // les arcs qui rayonnent d'un point : le groupe est à nous
                    Medium.WIFI_DIRECT -> Icons.Filled.WifiTethering
                },
                // le nom de la technologie, d'aucune langue — comme l'indicateur du haut
                contentDescription = medium.short,
                // Engagé : la couleur de la marque, vérifiée à la source. Le
                // bleu du Bluetooth SIG (#0082FC) ne change pas d'heure ; la
                // marque Wi-Fi Alliance, elle, est monochrome — blanche sur
                // fond sombre, noire sur fond clair — donc elle suit le thème.
                // Les autres états restent dans la palette de la station : une
                // marque dit « ceci marche », pas « ceci est proposé ».
                tint = when {
                    // engagé : la marque, pleine
                    inUse -> brand
                    // proposé : un accent de la station, parce que c'est une
                    // invitation à toucher, pas un état de la technologie
                    offered -> A4L.Cyan
                    // ouvert : la même marque, éteinte. Un gris neutre disait
                    // « rien ici », alors que la technologie est bel et bien
                    // allumée — et il se confondait avec l'état suivant.
                    enabled -> brand.copy(alpha = 0.38f)
                    else -> A4L.TextGhost
                },
                modifier = Modifier.size(if (inUse) 22.dp else 18.dp),
            )
        }
    }
}
