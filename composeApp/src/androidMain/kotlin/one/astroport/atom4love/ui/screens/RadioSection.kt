package one.astroport.atom4love.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.astroport.atom4love.R
import one.astroport.atom4love.chat.ChatEngine
import one.astroport.atom4love.nostr.HexagonSalon
import one.astroport.atom4love.nostr.RelayStation
import one.astroport.atom4love.proximity.CellLocator
import one.astroport.atom4love.proximity.ProximityService
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/** La cellule bouge peu : un rafraîchissement du fix toutes les 30 s suffit. */
private const val FIX_REFRESH_MS = 30_000L

/**
 * Quelle demande de permission est en vol. Un seul lanceur sert les deux :
 * deux `rememberLauncherForActivityResult` du même contrat dans un même
 * composable se sont disputé le résultat au banc, et le retour d'une demande de
 * localisation a démarré la balise. L'intention est donc portée en état.
 *
 * ⚠ Il y en avait trois — la troisième, `CABIN`, demandait les permissions au
 * moment d'ouvrir la conversation commune. Elle est partie avec elle : les
 * conversations n'ont plus de geste d'ouverture, elles existent dès qu'un pair
 * est attesté, et ce pair n'est attesté que si la balise tourne déjà. La seule
 * porte est donc celle de la balise, et il n'y en a plus qu'une à tenir.
 */
private enum class PermissionIntent { NONE, BEACON, LOCATION }

/**
 * Tout ce que la radio demande, **en un seul dialogue**.
 *
 * ⚠ La balise et les conversations n'exigent pas le même jeu : l'une veut
 * annoncer et balayer, l'autre veut en plus se connecter (`BLUETOOTH_CONNECT`).
 * Tant que parler demandait un geste d'ouverture, chacune posait sa question au
 * moment où elle en avait besoin. Ce geste n'existe plus — la radio parle dès
 * qu'elle le peut —, donc il n'y a plus de second moment où demander : les deux
 * jeux se réunissent ici, et une seule autorisation ouvre tout.
 *
 * `distinct` n'est pas cosmétique : les deux listes partagent `ADVERTISE` et
 * `SCAN`, et Android affiche une entrée par permission demandée.
 */
private val RADIO_PERMISSIONS: Array<String> =
    (ProximityService.runtimePermissions() + ChatEngine.RUNTIME_PERMISSIONS)
        .distinct()
        .toTypedArray()

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
/**
 * Le nom du portail, tel qu'on le lit dans son carreau.
 *
 * ⚠ **Le `88` de tête ne dit rien de l'endroit** : c'est le marqueur de mode
 * et de résolution d'un index H3, le même pour toute cellule de résolution 8,
 * partout sur Terre. Le retirer est aussi neutre que le `FFFF…` de queue que
 * [cellHex] enlève déjà — et ça n'est pas un caprice : à 13 sp dans un tiers de
 * largeur, l'A5 coupait le **dernier** caractère et affichait `881FB5B86` pour
 * `881FB5B861`. Un identifiant tronqué est un identifiant faux, et deux
 * personnes du même portail auraient pu lire deux noms différents.
 *
 * ⚠ À n'utiliser que pour l'écran. La chaîne qui part sur le relais — le tag
 * `h` du salon — reste [cellHex], entière : ce qui circule ne se raccourcit
 * pas pour tenir dans une bulle.
 */
internal fun portalLabel(cell: Long): String = cellHex(cell).removePrefix("88")

internal fun cellHex(cell: Long): String =
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

/** La page « Infos sur l'appli », seul endroit où rouvrir une permission refusée. */
private fun appSettingsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

/**
 * 📡 L'état de la radio, en tête du Plateau.
 *
 * ## D'où ça vient
 *
 * De l'écran **Radar**, qui était un onglet à lui seul et n'en est plus un. Il
 * montrait la balise, la cellule, trois compteurs, le salon d'hexagone, une
 * rangée pour ouvrir la cabine et — juste en dessous — **la liste des résonances
 * à portée**. Cette dernière était le Plateau, en moins bien : les mêmes
 * voisins, triés par le même k, sans les cartes ni le jeu. Deux écrans disaient
 * la même chose de la même salle, et il fallait choisir lequel regarder.
 *
 * Ce qui restait après avoir retiré le doublon ne faisait plus un lieu : un état
 * d'appareil, quelques compteurs, une porte. Ce n'est pas une destination, c'est
 * un **en-tête** — et sa place est au-dessus de ce qu'il conditionne. Sans
 * balise, pas de voisin, pas de carte, pas de conversation : la ligne qui dit
 * « il manque le Bluetooth » se lit maintenant **avant** tout ce qu'elle empêche,
 * et non dans un onglet d'à côté que personne n'ouvrait.
 *
 * ## L'ordre des trois blocs, et il n'est pas libre
 *
 * 1. **le portail** — où l'on est, et l'état de la balise avec lui ;
 * 2. **le titre des compteurs** ;
 * 3. **les compteurs** : ici (la radio) puis les relais (le lointain), du plus
 *    proche au plus lointain, dans le sens de la lecture.
 *
 * ⚠ **Le journal n'est plus ici.** Il a occupé la deuxième place, puis un
 * tiroir dépliable, avant de monter dans l'en-tête de la station le 20/08 —
 * la seule ligne qui existe sur TOUS les écrans. Ce qu'on veut relire quand on
 * doute ne doit pas s'atteindre depuis un seul onglet.
 *
 * Les cartes suivent, en dessous, dans le Plateau. On lit donc : est-ce que ça
 * marche, ce que ça a fait, puis jusqu'où ça porte et qui est là.
 */
/**
 * Quelqu'un de joignable, tel que le carreau « Ici » le déroule : de quoi
 * l'écrire, et de quoi ouvrir sa conversation.
 *
 * Le nom est déjà passé par la règle des homonymes — c'est celui de
 * [one.astroport.atom4love.chat.Conversations] — et il est null quand la
 * personne ne s'est pas nommée : à l'écran de choisir le mot, dans sa langue.
 */
data class HereEntry(val peerHex: String, val name: String?)

@Composable
fun RadioSection(
    relay: RelayStation.Status?,
    /**
     * Les relais un par un, avec leur adresse — la liste que déroule le
     * carreau 🕸️. Vide en aperçu, où l'antenne n'existe pas.
     */
    relays: List<RelayStation.Relay> = emptyList(),
    salon: HexagonSalon?,
    /** Combien de conversations sont joignables — le compteur « ici ». */
    reachable: Int,
    /**
     * **Qui** est joignable, dans l'ordre de la liste des Chats — le pseudo de
     * chacun, null pour qui ne s'est pas nommé (l'écran choisit alors le mot,
     * dans sa langue).
     *
     * ⚠ Ce sont les AUTRES : la liste déroulante y ajoute « vous » en tête,
     * pour que sa longueur soit exactement le nombre affiché. Un compteur qui
     * se compte et une liste qui s'oublie diraient deux choses.
     */
    here: List<HereEntry> = emptyList(),
    /**
     * Ouvrir la conversation de quelqu'un, par sa clé publique hexadécimale.
     *
     * ⚠ **Le compteur devient une porte.** Voir un pseudo dans la liste et ne
     * pas pouvoir le toucher serait un cul-de-sac : on saurait qui est là, et
     * il faudrait repartir de l'onglet d'à côté pour lui parler. Demandé par
     * Florent le 20/08.
     */
    onOpenPeer: (String) -> Unit = {},
    /**
     * Aller voir où l'on est, sur la carte — latitude, longitude.
     *
     * ⚠ **La position ne quitte pas l'appareil pour autant.** Elle passe d'un
     * écran à l'autre dans la même composition ; ce qui part dans l'air reste
     * la cellule, et elle seule ([CellLocator]).
     */
    onOpenPosition: (Double, Double) -> Unit = { _, _ -> },
    /**
     * L'adaptateur Bluetooth du téléphone est-il allumé ?
     *
     * ⚠ **Ce n'est pas la même question que [ProximityService.running].** Le
     * service reste vivant quand l'interrupteur système se coupe sous lui : il
     * a démarré, il tourne, et il ne peut plus rien. Sans ce booléen, la rangée
     * annoncerait un portail et une balise active pendant que la radio est
     * morte — c'est exactement ce que l'en-tête faisait jusqu'au 20/08.
     */
    bluetoothOn: Boolean = true,
    /**
     * Un médium porte réellement du trafic — le troisième état du point.
     *
     * Éteint (aucune radio), allumé sans lien, allumé avec un lien vivant : ces
     * trois-là étaient dans le point de l'en-tête, et ils descendent ensemble.
     * « Si le point tient au Bluetooth et à la balise, il descend avec eux » —
     * Florent, 20/08.
     */
    linked: Boolean = false,
    modifier: Modifier = Modifier,
    /**
     * Le titre de l'écran, rendu **entre la balise et les compteurs**.
     *
     * ⚠ Ce n'est pas une coquetterie d'API : la balise doit passer au-dessus du
     * titre, et pourtant elle ne peut pas quitter cette section. Le lanceur de
     * permissions est **unique et partagé** entre la balise et la localisation —
     * deux `rememberLauncherForActivityResult` du même contrat dans un même
     * composable se sont disputé le résultat au banc, et le retour d'une demande
     * de localisation a démarré la balise. Couper la section en deux
     * recréerait exactement ce défaut.
     *
     * Le titre traverse donc la section au lieu que la section se coupe autour
     * de lui. La balise se lit avant le nom de l'écran, ce qui est l'ordre juste
     * : ce qui conditionne tout se lit avant ce qu'il conditionne.
     */
    title: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val beaconRunning by ProximityService.running.collectAsStateWithLifecycle()
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
    val ownCell4d by ProximityService.advertisedCell4d.collectAsStateWithLifecycle()
    /**
     * ⚠ **Sur un Android d'avant 12, la position ne fait pas que nommer la
     * cellule : sans elle, le scan ne remonte rien.** La ligne « la
     * localisation résout votre cellule » se lisait alors comme un confort
     * qu'on peut refuser — et l'appareil ne voyait plus personne, sans un mot.
     * Voir [ProximityService.scanBlind].
     */
    val scanBlind by ProximityService.scanBlind.collectAsStateWithLifecycle()

    // La localisation se demande pour elle-même : la balise annonce une
    // présence sans elle, et l'exiger pour allumer la radio reviendrait à faire
    // payer une position à qui veut seulement parler à côté de lui.
    var locationAttempt by remember { mutableIntStateOf(0) }
    var permissionFor by remember { mutableStateOf(PermissionIntent.NONE) }
    // Au deuxième refus, Android ne montre plus de dialogue : la demande revient
    // refusée dans la milliseconde et l'affordance devient un bouton mort, sans
    // que rien ne bouge à l'écran. Le seul recours est la page de réglages.
    // Impossible de le savoir d'avance — `shouldShowRequestPermissionRationale`
    // est faux AUSSI avant la première demande : seul un refus revenu de NOTRE
    // lanceur, sans justification à montrer, prouve l'impasse.
    var locationDeadEnd by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        when (permissionFor) {
            // Rien à faire : l'effet qui veille sur `permissionFor` démarre la
            // balise dès que le Bluetooth est là.
            PermissionIntent.BEACON -> Unit
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
    // Le retour des réglages ne dit pas ce qui a été accordé : on re-sonde.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { locationAttempt++ }

    // La balise est le socle : elle démarre d'elle-même dès que le Bluetooth est
    // accordé, et ne se coupe plus.
    LaunchedEffect(beaconRunning, permissionFor) {
        if (!beaconRunning && ProximityService.corePermissionsGranted(context)) {
            ProximityService.start(context)
        }
    }

    val locator = remember { CellLocator(context.applicationContext) }
    var fix by remember { mutableStateOf<CellLocator.Fix?>(null) }
    var locationBlocker by remember { mutableStateOf<CellLocator.Blocker?>(null) }
    // La précision de la position refusée, pour la DIRE : « ± 1 400 m » se
    // comprend, « pas de position » serait faux — elle est là, elle est floue.
    var impreciseM by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(beaconRunning, locationAttempt) {
        while (true) {
            fix = locator.currentFix()
            locationBlocker = if (fix == null) locator.blocker() else null
            impreciseM = locator.lastImpreciseM
            // La permission accordée efface l'impasse : si elle est révoquée
            // ensuite depuis les réglages, Android remet son compteur à zéro et
            // redonne droit au dialogue.
            if (locationBlocker != CellLocator.Blocker.PERMISSION) locationDeadEnd = false
            delay(FIX_REFRESH_MS)
        }
    }

    val salonActive = relay?.local == true && relay.online
    val pensees = salon?.pensees?.collectAsStateWithLifecycle()?.value.orEmpty()
    var salonOpen by remember { mutableStateOf(false) }
    var relaysOpen by remember { mutableStateOf(false) }
    var hereOpen by remember { mutableStateOf(false) }
    LaunchedEffect(fix?.cell) { fix?.let { salon?.setCell(cellHex(it.cell)) } }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── Le portail, et l'état de la balise avec lui ────────────────────
        //
        // ⚠⚠ **La rangée « Balise active » n'existe plus ; elle est ici.**
        // Tranché par Florent le 20/08, et c'est cohérent : la balise n'avait
        // plus rien à dire d'elle-même depuis qu'elle ne s'allume ni ne s'éteint
        // à la main. Ce qu'elle avait à dire, c'était **où l'on est** — et
        // quand elle ne peut pas le dire, pourquoi.
        //
        // ⚠ **Elle garde le geste de permission, et ce n'est pas négociable.**
        // Cette rangée est la seule porte vers [RADIO_PERMISSIONS] de toute
        // l'application, notifications comprises. La dissoudre sans reprendre
        // son `clickable` rendrait le Bluetooth inaccordable à qui l'a refusé
        // une fois — exactement la faute corrigée le 19/08 pour les
        // notifications. **Toute permission garde une porte atteignable.**
        // La radio est allumée quand la puce est allumée ET que le service a
        // le droit de s'en servir. L'une sans l'autre ne porte rien.
        val radioOn = bluetoothOn && beaconRunning
        Column(
            Modifier
                .fillMaxWidth()
                .dashedGlass(
                    12.dp,
                    A4L.GlassFaint,
                    (if (radioOn) A4L.Mint else A4L.Stroke).copy(alpha = 0.2f),
                )
                .clickable(enabled = !beaconRunning) {
                    permissionFor = PermissionIntent.BEACON
                    permissionLauncher.launch(RADIO_PERMISSIONS)
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ⚠ **Trois états, trois couleurs de feu**, demandées par
                // Florent le 20/08 : rouge la radio éteinte, orange allumée
                // mais sans personne au bout, vert dès qu'un lien porte
                // vraiment. L'en-tête les disait en gris sur gris — ce qui
                // revenait à ne les dire qu'à qui les cherchait.
                StatusDot(
                    when {
                        !radioOn -> A4L.Red
                        linked -> A4L.Green
                        else -> A4L.Amber
                    },
                )
                Spacer(Modifier.width(10.dp))
                Text("⛩️", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                if (!bluetoothOn) {
                    // ⚠ Une phrase à part, et pas celle des permissions : ici
                    // rien n'a été refusé, c'est l'interrupteur du téléphone
                    // qui est retombé. Les deux se corrigent ailleurs et
                    // autrement, les confondre enverrait chercher au mauvais
                    // endroit.
                    Text(
                        stringResource(R.string.radar_bluetooth_off),
                        style = A4LText.Caption,
                        color = A4L.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                } else if (beaconRunning) {
                    StatLabel(stringResource(R.string.radar_stat_in_portal), A4L.TextMuted)
                    Spacer(Modifier.width(6.dp))
                    // Le code EST la porte : le toucher porte la carte sur la
                    // position d'où il a été calculé. Sans fix, il n'y a rien
                    // à montrer et rien ne se touche.
                    Text(
                        ownCell4d?.let { portalLabel(it) } ?: "—",
                        style = A4LText.Metric.copy(fontSize = 14.sp),
                        color = if (ownCell4d != null) A4L.Mint else A4L.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                fix?.let { f ->
                                    Modifier.clickable { onOpenPosition(f.lat, f.lon) }
                                } ?: Modifier,
                            )
                            .padding(vertical = 2.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.radar_beacon_needs_bluetooth),
                        style = A4LText.Caption,
                        color = A4L.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ⚠ **Pourquoi il n'y a pas de portail se lit SOUS le portail.**
            // Ces trois lignes vivaient sous le titre des compteurs, à deux
            // blocs de l'endroit qu'elles expliquaient — on lisait « — » ici et
            // la raison quinze centimètres plus bas. Déplacé par Florent le
            // 20/08. La balise éteinte les tait : ce qui manque alors n'est pas
            // la position, c'est le Bluetooth, et une question à la fois.
            if (radioOn && fix == null) {
                Text(
                    when (locationBlocker) {
                        CellLocator.Blocker.SERVICE_OFF ->
                            stringResource(R.string.radar_hex_unknown_service_off)
                        CellLocator.Blocker.PERMISSION -> if (locationDeadEnd) {
                            stringResource(R.string.radar_hex_unknown_dead_end)
                        } else {
                            stringResource(R.string.radar_hex_unknown_permission)
                        }
                        CellLocator.Blocker.APPROXIMATE ->
                            stringResource(R.string.radar_hex_unknown_approximate)
                        CellLocator.Blocker.IMPRECISE -> stringResource(
                            R.string.radar_hex_unknown_imprecise,
                            (impreciseM ?: 0f).toInt(),
                        )
                        null -> stringResource(R.string.radar_hex_unknown_searching)
                    },
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                )
                // La conséquence, et elle n'est pas la même partout : ce qui
                // n'est qu'une cellule manquante sur un téléphone récent est
                // une salle entière qu'on ne voit pas sur un ancien.
                if (scanBlind) {
                    Text(
                        stringResource(R.string.radar_scan_needs_location),
                        style = A4LText.Caption,
                        color = A4L.TextMuted,
                    )
                }
                // ⚠ L'approximative garde une porte, et ce n'est pas la même
                // phrase : redemander [LOCATION_PERMISSIONS] fait poser à
                // Android sa question « passer à la position précise ? ».
                val locationAsk = locationBlocker == CellLocator.Blocker.PERMISSION ||
                    locationBlocker == CellLocator.Blocker.APPROXIMATE
                if (locationAsk) {
                    Text(
                        stringResource(
                            when {
                                locationBlocker == CellLocator.Blocker.APPROXIMATE ->
                                    R.string.radar_grant_precise
                                locationDeadEnd -> R.string.radar_open_app_settings
                                else -> R.string.radar_grant_location
                            },
                        ),
                        style = A4LText.Caption,
                        color = A4L.Mint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                // `locationDeadEnd` n'est vrai que sous
                                // PERMISSION : la boucle l'efface pour tout
                                // autre motif, l'approximative comprise.
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
        }

        title()

        // ── Les trois fenêtres, de la plus proche à la plus lointaine ──────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // « Ici » se compte par les conversations joignables, pas par la
            // balise : elles nomment des noyaux attestés, un par personne, là où
            // la balise ne sait dire que « une radio est là ».
            //
            // ⚠ **On se compte.** Ces trois cases nomment des LIEUX — ici, le
            // portail, l'hexagone — et l'on est dans le lieu qu'on regarde.
            // Trois téléphones sur une table affichaient « 2 », chacun comptant
            // les deux autres : trois personnes présentes, trois écrans qui
            // disent deux. Tranché par Florent le 20/08, sur le banc à trois.
            //
            // La couleur, elle, ne suit pas le nombre mais la rencontre : elle
            // s'allume quand quelqu'un d'AUTRE est là, sinon « 1 · ici » aurait
            // la teinte de ce qui vient d'arriver alors qu'on est seul.
            Box(Modifier.weight(1f)) {
            RadioStat(
                glyph = "📍",
                value = if (beaconRunning) (reachable + 1).toString() else "—",
                label = stringResource(R.string.radar_stat_here_no_relay),
                // Les trois blocs portent maintenant le même dessin : titre en
                // haut contre le bord gauche, pictogramme en face, valeur
                // dessous. Un carreau qui range ses parties autrement se lit
                // comme s'il disait autre chose.
                labelOnTop = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { hereOpen = !hereOpen },
                accent = if (reachable > 0) A4L.Mint else null,
            )
            // ⚠ **Le nombre ne dit pas QUI, et c'est la première question.**
            // Même geste que le carreau des relais, à côté : le compteur
            // s'ouvre sur ce qu'il compte. Demandé par Florent le 20/08.
            DropdownMenu(
                expanded = hereOpen,
                onDismissRequest = { hereOpen = false },
                containerColor = A4L.Deep,
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.radar_here_you),
                            style = A4LText.Caption,
                            color = A4L.TextMuted,
                        )
                    },
                    leadingIcon = { StatusDot(A4L.Mint, size = 5.dp) },
                    onClick = { hereOpen = false },
                )
                here.forEach { entry ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                entry.name ?: stringResource(R.string.chat_from_unnamed),
                                style = A4LText.Caption,
                                color = if (entry.name != null) A4L.Mint else A4L.TextMuted,
                            )
                        },
                        leadingIcon = { StatusDot(A4L.Mint, size = 5.dp) },
                        trailingIcon = { Text("💬", fontSize = 12.sp) },
                        onClick = { hereOpen = false; onOpenPeer(entry.peerHex) },
                    )
                }
                if (here.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.radar_here_alone),
                                style = A4LText.Caption,
                                color = A4L.TextMuted,
                            )
                        },
                        onClick = { hereOpen = false },
                    )
                }
            }
            }
            // ⚠ **Ce carreau comptait des pensées et compte des relais.**
            // Tranché par Florent le 20/08. Son tiret avait deux causes qu'on
            // ne distinguait pas — pas de relais, ou un relais qui ne rapporte
            // rien — et la première commande la seconde : sans relais joignable
            // il n'y a rien à savoir du lointain, quoi qu'il s'y passe. Le
            // carreau dit donc d'abord ce qui porte, et le compte des pensées
            // se lit dans le salon, qu'un doigt ouvre toujours ici.
            // ⚠ **Le compteur ne dit pas LEQUEL, et c'est ce qu'on veut savoir.**
            // « 1/1 » vaut pour le relais par défaut d'Internet comme pour la
            // passerelle d'une station à trois mètres ; seules les adresses les
            // distinguent. Le carreau les déroule, les vivantes en vert.
            // Demandé par Florent le 20/08.
            //
            // ⚠ **Le salon garde sa porte.** Elle était le geste de ce
            // carreau ; sans cette dernière ligne du menu, l'hexagone
            // deviendrait inatteignable — même règle que pour les permissions.
            Box(Modifier.weight(1f)) {
            RadioStat(
                glyph = "🕸️",
                value = relay?.let { "${it.connected}/${it.total}" } ?: "—",
                label = stringResource(R.string.radar_stat_relays_active),
                // Même dessin que le portail : ces deux carreaux-là portent un
                // état, pas une population, et un état se présente avant de se
                // lire. Le carreau « ici » garde son grand nombre en tête —
                // c'est le seul qui compte des gens.
                labelOnTop = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { relaysOpen = !relaysOpen },
                accent = when {
                    salonOpen -> A4L.Green
                    relay?.online == true -> A4L.Green
                    else -> null
                },
            )
            DropdownMenu(
                expanded = relaysOpen,
                onDismissRequest = { relaysOpen = false },
                containerColor = A4L.Deep,
            ) {
                relays.forEach { r ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                r.url,
                                style = A4LText.Data.copy(fontSize = 11.sp),
                                color = if (r.online) A4L.Green else A4L.TextMuted,
                            )
                        },
                        leadingIcon = {
                            StatusDot(if (r.online) A4L.Green else A4L.TextGhost, size = 5.dp)
                        },
                        onClick = { relaysOpen = false },
                    )
                }
                if (relays.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.radar_no_relay),
                                style = A4LText.Caption,
                                color = A4L.TextMuted,
                            )
                        },
                        onClick = { relaysOpen = false },
                    )
                }
                if (salonActive) {
                    HorizontalDivider(color = A4L.StrokeSoft)
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.radar_open_salon),
                                style = A4LText.Caption,
                                color = A4L.Cyan,
                            )
                        },
                        leadingIcon = { Text("🕸️", fontSize = 13.sp) },
                        onClick = { relaysOpen = false; salonOpen = !salonOpen },
                    )
                }
            }
            }
        }

        if (salonOpen && salon != null) {
            SalonPanel(salon = salon, pensees = pensees, active = salonActive)
        }

    }
}

/**
 * Un compteur : un pictogramme, un grand nombre, un libellé.
 *
 * ⚠ **Le pictogramme est dedans, en haut à gauche — pas à côté du nombre.**
 * Posé sur la ligne du chiffre, il se serait lu comme une unité (« 3 🚪 ») ;
 * au-dessus, il coiffe la bulle et dit de quelle fenêtre on parle avant même
 * qu'on lise le libellé. Les trois se comparent alors d'un coup d'œil, du plus
 * proche au plus lointain.
 *
 * Il ne prend **jamais l'accent** : la couleur dit si la fenêtre est vivante,
 * le pictogramme dit laquelle. Deux informations dans un seul signe, et l'on ne
 * saurait plus laquelle on lit.
 *
 * ## Les trois signes, et pourquoi ceux-là
 *
 * ⚠ Le premier jeu — 👥 🚪 💭 — nommait des **choses** et pas des **portées** :
 * des gens, une porte, des pensées. Or ces trois bulles ne comptent pas des
 * natures différentes, elles comptent la même chose à trois distances. Le signe
 * doit donc dire la distance.
 *
 *  - **📍 ici** — la punaise d'un point sur une carte : ce qui est à portée
 *    d'antenne, sans rien entre nous ;
 *  - **⛩️ le portail** — un seuil, une arche : ce que compte cette bulle est ce
 *    qui annonce **la même adresse 4D** que nous, donc ce qui se tient du même
 *    côté d'un passage. Ni une porte (🚪, un objet domestique, essayé et
 *    écarté), ni un vortex (🌪️, essayé aussi : ça dit l'énergie, pas le
 *    seuil) — une arche ne s'ouvre ni ne se ferme, elle marque un lieu ;
 *  - **🕸️ l'hexagone** — un maillage : ce qui n'arrive que par un relais, donc
 *    par un réseau et non par une portée.
 *
 * ⚠ Le 🌀 — le vrai vortex — était le premier candidat pour le portail : il est
 * déjà l'**Alternance** de l'Oracle, quinze centimètres plus bas sur le même
 * écran. Un même glyphe qui dit deux choses sur une seule page est exactement
 * l'incohérence qu'on chasse ici.
 */
/**
 * Le titre d'un carreau : **gras, souligné, deux-points, contre le bord
 * gauche**. Demandé par Florent le 20/08, et valable partout — c'est ce qui
 * fait qu'on lit une étiquette et pas une phrase. Le deux-points vit dans les
 * chaînes traduites : le français met une espace devant, l'anglais non, et
 * c'est le genre de détail qu'un `+ " :"` en Kotlin détruit dans deux langues
 * sur trois.
 */
@Composable
private fun StatLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        // ⚠ **Le trait passe sous le mot, pas sous le deux-points.** Souligner
        // la chaîne entière fait courir le trait sous la ponctuation, et l'œil
        // lit alors un mot d'une lettre de plus. D'où l'annotation plutôt qu'un
        // `textDecoration` sur tout le style — et d'où le deux-points séparé
        // des libellés dans les ressources : le français met une espace devant,
        // l'anglais non, ce qu'un `+ " :"` en Kotlin détruirait.
        buildAnnotatedString {
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(text) }
            append(stringResource(R.string.stat_label_suffix))
        },
        style = A4LText.Caption.copy(
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun RadioStat(
    glyph: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    /** Un chiffre se lit gros ; un nom de dix caractères, non. */
    valueStyle: TextStyle = A4LText.Metric,
    /** Ce qui conditionne la valeur, sous elle — l'état du relais pour 🕸️. */
    footer: (@Composable () -> Unit)? = null,
    /**
     * Le libellé passe **au-dessus**, à gauche du pictogramme.
     *
     * Pour un carreau dont la valeur n'est pas un nombre mais un nom : un
     * chiffre se comprend seul et se fait annoncer après, un identifiant a
     * besoin d'être présenté avant d'être lu. Demandé par Florent le 20/08
     * pour le portail.
     */
    labelOnTop: Boolean = false,
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
        // ⚠ **En haut à DROITE.** À gauche, le pictogramme s'alignait sur le
        // chiffre et sur le libellé : trois choses dans la même colonne, dont
        // deux se lisent et une se regarde. À droite il quitte la colonne de
        // lecture et devient ce qu'il est — l'étiquette de la bulle, posée dans
        // son coin, que l'œil prend d'un balayage sans traverser le texte.
        Row(
            Modifier.fillMaxWidth(),
            // Le titre contre le bord gauche, le pictogramme contre le droit :
            // l'œil trouve l'étiquette là où il trouve toutes les autres.
            horizontalArrangement = if (labelOnTop) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.End
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (labelOnTop) {
                // ⚠ **Un mot, parce qu'un carreau fait un tiers d'écran.**
                // « Portail Géo. » tenait sur le Pixel et sortait en
                // « Portail … » sur l'A5 (360 dp de large, pictogramme
                // compris). Le libellé a été raccourci plutôt que la police
                // rabotée ; les deux lignes restent autorisées comme filet,
                // pour une police système agrandie.
                StatLabel(
                    label,
                    accent?.copy(alpha = 0.65f) ?: A4L.TextMuted,
                    Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(glyph, fontSize = 19.sp)
        }
        Text(
            value,
            style = valueStyle,
            color = accent ?: A4L.TextHigh.copy(alpha = 0.88f),
            maxLines = 1,
            // ⚠ Sans ça, une valeur trop longue se coupe **en silence** : l'A5
            // a affiché un identifiant amputé de son dernier caractère sans que
            // rien ne le signale. Les points de suite le diraient.
            overflow = TextOverflow.Ellipsis,
        )
        if (!labelOnTop) {
            StatLabel(label, accent?.copy(alpha = 0.65f) ?: A4L.TextMuted)
        }
        footer?.invoke()
    }
}

/**
 * Le salon d'hexagone, déplié sous les compteurs : les pensées éphémères du
 * lieu, et un champ pour y déposer la sienne. Fermé — avec sa phrase — tant que
 * l'antenne n'est pas accrochée au relais local d'une station.
 */
@Composable
private fun SalonPanel(
    salon: HexagonSalon,
    pensees: List<HexagonSalon.Pensee>,
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
                style = A4LText.Caption.copy(fontWeight = FontWeight.SemiBold),
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

/** Le salon montre les dernières pensées sans envahir le haut du Plateau. */
private const val SALON_VISIBLE_PENSEES = 12
