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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import one.astroport.atom4love.proximity.NeighborRegistry
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
 * 1. **La balise**, parce qu'elle conditionne tout le reste ;
 * 2. **le journal**, la porte vers ce que la radio fait — juste sous l'état de
 *    la machine, avec lequel il fait un bloc : *voilà où j'en suis, et voilà ce
 *    que j'ai fait* ;
 * 3. **le titre du jeu**, qui ouvre ce qui suit ;
 * 4. **les compteurs**, qui disent l'étendue de ce qu'on touche — ici, le
 *    portail, l'hexagone, du plus proche au plus lointain, dans le sens de la
 *    lecture.
 *
 * Les cartes suivent, en dessous, dans le Plateau. On lit donc : est-ce que ça
 * marche, ce que ça a fait, puis jusqu'où ça porte et qui est là.
 */
@Composable
fun RadioSection(
    relay: RelayStation.Status?,
    salon: HexagonSalon?,
    /** Combien de conversations sont joignables — le compteur « ici ». */
    reachable: Int,
    onOpenJournal: () -> Unit,
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
    LaunchedEffect(beaconRunning, locationAttempt) {
        while (true) {
            fix = locator.currentFix()
            locationBlocker = if (fix == null) locator.blocker() else null
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
    LaunchedEffect(fix?.cell) { fix?.let { salon?.setCell(cellHex(it.cell)) } }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── La balise ─────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .dashedGlass(
                    12.dp,
                    A4L.GlassFaint,
                    (if (beaconRunning) A4L.Mint else A4L.Stroke).copy(alpha = 0.2f),
                )
                // Plus d'interrupteur : la balise est le socle, elle tourne dès
                // qu'elle le peut. Ne reste à toucher que ce qui lui manque.
                .clickable(enabled = !beaconRunning) {
                    permissionFor = PermissionIntent.BEACON
                    permissionLauncher.launch(RADIO_PERMISSIONS)
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(if (beaconRunning) A4L.Mint else A4L.TextDim)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    beaconRunning && ownCell4d != null ->
                        stringResource(R.string.radar_beacon_with_cell, cellHex(ownCell4d!!))
                    beaconRunning -> stringResource(R.string.radar_beacon_presence_only)
                    else -> stringResource(R.string.radar_beacon_needs_bluetooth)
                },
                style = A4LText.Caption,
                color = if (beaconRunning) A4L.Mint else A4L.TextMuted,
                modifier = Modifier.weight(1f),
            )
            relay?.let {
                Spacer(Modifier.width(8.dp))
                StatusDot(if (it.online) A4L.Green else A4L.TextGhost)
                Spacer(Modifier.width(3.dp))
                Text(
                    it.label,
                    style = A4LText.Data.copy(fontSize = 10.sp),
                    color = A4L.TextDim,
                )
            }
        }

        // ── Le journal ────────────────────────────────────────────────────
        //
        // ⚠ **C'est la rangée qui ouvrait la cabine**, à la même place et avec
        // le même geste. Elle battait en orange tant que la cabine était fermée,
        // parce qu'ouvrir était le seul appel de la page et qu'il ne se voyait
        // pas. Elle ne bat plus : il n'y a plus rien à ouvrir pour parler — les
        // conversations existent d'elles-mêmes, dans leur onglet. Ce qui reste
        // ici est une fenêtre qu'on consulte, et une fenêtre n'appelle pas.
        Row(
            Modifier
                .fillMaxWidth()
                .dashedGlass(12.dp, A4L.GlassFaint, A4L.Cyan.copy(alpha = 0.22f))
                .clickable(onClick = onOpenJournal)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧾", fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.radar_journal_row),
                style = A4LText.Caption,
                color = A4L.Cyan,
                modifier = Modifier.weight(1f),
            )
            Text("›", fontSize = 15.sp, color = A4L.TextFaint)
        }

        title()

        // ── Sans position : dire pourquoi, et où le corriger ───────────────
        if (fix == null) {
            Text(
                when (locationBlocker) {
                    CellLocator.Blocker.SERVICE_OFF ->
                        stringResource(R.string.radar_hex_unknown_service_off)
                    CellLocator.Blocker.PERMISSION -> if (locationDeadEnd) {
                        stringResource(R.string.radar_hex_unknown_dead_end)
                    } else {
                        stringResource(R.string.radar_hex_unknown_permission)
                    }
                    null -> stringResource(R.string.radar_hex_unknown_searching)
                },
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
            if (locationBlocker == CellLocator.Blocker.PERMISSION) {
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

        // ── Les trois fenêtres, de la plus proche à la plus lointaine ──────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // « Ici » se compte par les conversations joignables, pas par la
            // balise : elles nomment des noyaux attestés, un par personne, là où
            // la balise ne sait dire que « une radio est là ».
            RadioStat(
                glyph = "📍",
                value = if (reachable > 0) reachable.toString() else "—",
                label = stringResource(R.string.radar_stat_here_no_relay),
                modifier = Modifier.weight(1f),
                accent = if (reachable > 0) A4L.Mint else null,
            )
            // Approximation en attendant la logique de portail D2 : les noyaux
            // qui annoncent la même cellule que la nôtre. Comptés par jeton de
            // présence et non par adresse — une adresse qui tourne pendant que
            // l'ancienne survit à son TTL faisait compter deux fois le même
            // appareil.
            RadioStat(
                glyph = "⛩️",
                value = if (beaconRunning && ownCell4d != null) {
                    NeighborRegistry.countIn(neighbors, ownCell4d).toString()
                } else {
                    "—"
                },
                label = stringResource(R.string.radar_stat_in_portal),
                modifier = Modifier.weight(1f),
            )
            RadioStat(
                glyph = "🕸️",
                value = if (salonActive) pensees.size.toString() else "—",
                label = stringResource(R.string.radar_stat_in_hexagon),
                modifier = Modifier
                    .weight(1f)
                    .clickable { salonOpen = !salonOpen },
                accent = if (salonOpen) A4L.Green else null,
            )
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
@Composable
private fun RadioStat(
    glyph: String,
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
        // ⚠ **En haut à DROITE.** À gauche, le pictogramme s'alignait sur le
        // chiffre et sur le libellé : trois choses dans la même colonne, dont
        // deux se lisent et une se regarde. À droite il quitte la colonne de
        // lecture et devient ce qu'il est — l'étiquette de la bulle, posée dans
        // son coin, que l'œil prend d'un balayage sans traverser le texte.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(glyph, fontSize = 19.sp)
        }
        Text(value, style = A4LText.Metric, color = accent ?: A4L.TextHigh.copy(alpha = 0.88f))
        Text(
            label,
            style = A4LText.Caption.copy(fontSize = 11.sp),
            color = accent?.copy(alpha = 0.65f) ?: A4L.TextMuted,
        )
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
