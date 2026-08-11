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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import one.astroport.atom4love.domain.GoldbergPortal
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
 * Identifiant lisible d'une cellule H3 : l'index en hexadécimal, débarrassé de
 * la traîne de « f » des chiffres inutilisés. Affichage uniquement — jamais
 * reparsé.
 */
private fun cellHex(cell: Long): String =
    cell.toULong().toString(16).uppercase().trimEnd('F')

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
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    var attempt by remember { mutableIntStateOf(0) }
    val unlocked = elapsed >= RITUAL_SECONDS

    // ── Balise de proximité : premier morceau réel de l'écran ─────────────
    val context = LocalContext.current
    val beaconRunning by ProximityService.running.collectAsStateWithLifecycle()
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
    val ownCell4d by ProximityService.advertisedCell4d.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Localisation et notifications sont optionnelles ; seul le Bluetooth bloque.
        if (ProximityService.corePermissionsGranted(context)) {
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
    // Re-résout quand la balise change d'état (la permission de localisation
    // vient peut-être d'être accordée), puis toutes les 30 s.
    LaunchedEffect(beaconRunning) {
        while (true) {
            fix = locator.currentFix()
            locationBlocker = if (fix == null) locator.blocker() else null
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
                        CellLocator.Blocker.PERMISSION ->
                            "Hexagone inconnu — accordez la localisation (via la balise) " +
                                "pour résoudre votre cellule."
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
        LaunchedEffect(fix?.cell) {
            fix?.let { salon?.setCell(cellHex(it.cell)) }
        }
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CabinStat(
                    if (salonActive) pensees.size.toString() else "—",
                    "pensées ici",
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
                CabinStat(
                    if (beaconRunning) neighbors.size.toString() else "—",
                    "noyaux proches",
                    Modifier.weight(1f),
                    accent = A4L.Mint,
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
                        (if (beaconRunning) A4L.Mint else A4L.Stroke).copy(alpha = 0.2f),
                    )
                    .clickable {
                        if (beaconRunning) {
                            ProximityService.stop(context)
                        } else {
                            permissionLauncher.launch(ProximityService.runtimePermissions())
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(if (beaconRunning) A4L.Mint else A4L.TextDim)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (beaconRunning) {
                        "Balise active — toucher pour couper"
                    } else {
                        "Activer la balise de proximité"
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

/** Au-delà, le radar deviendrait une nuée : les compteurs du bas font le total. */
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
