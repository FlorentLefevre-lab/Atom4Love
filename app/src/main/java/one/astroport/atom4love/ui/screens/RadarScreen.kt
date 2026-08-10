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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/**
 * 02 · Radar Phi2X — la cabine à portée et le rituel de phase.
 *
 * Le compteur tourne réellement : 33 s d'immobilité déverrouillent la cabine.
 * Un appui sur le radar relance le rituel (en attendant le vrai verrou GPS, qui
 * devra réinitialiser le compteur dès que l'utilisateur s'éloigne du centre).
 */
@Composable
fun RadarScreen(modifier: Modifier = Modifier) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    var attempt by remember { mutableIntStateOf(0) }
    val unlocked = elapsed >= RITUAL_SECONDS

    // ── Balise de proximité : premier morceau réel de l'écran ─────────────
    val context = LocalContext.current
    val beaconRunning by ProximityService.running.collectAsStateWithLifecycle()
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
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
                StatusDot(A4L.Green)
                Spacer(Modifier.width(2.dp))
                Text(
                    "a4l:P02H820B7F6C",
                    style = A4LText.Data.copy(fontSize = 10.sp),
                    color = A4L.TextBody,
                )
            }
            Text("↑ 214°", style = A4LText.Data.copy(fontSize = 10.sp), color = A4L.TextDim)
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
                if (unlocked) {
                    "Hexagone 820B·7F6C — abonnement au flux de la cabine actif."
                } else {
                    "Hexagone 820B·7F6C — vous êtes à 38 m du centre géométrique."
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

            // Noyaux voisins qui respirent
            PulsingDot(A4L.Mint, 9.dp, 2400, Alignment.TopStart, x = 96.dp, y = 78.dp)
            PulsingDot(A4L.Amber, 7.dp, 3100, Alignment.TopEnd, x = (-84).dp, y = 128.dp)
            PulsingDot(A4L.Indigo, 7.dp, 2800, Alignment.BottomStart, x = 128.dp, y = (-62).dp)
        }

        // ── Compteurs de la cabine ────────────────────────────────────────
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CabinStat("12", "pensées ici", Modifier.weight(1f))
                CabinStat("3", "dans le portail", Modifier.weight(1f))
                CabinStat(
                    if (beaconRunning) neighbors.size.toString() else "—",
                    "noyaux proches",
                    Modifier.weight(1f),
                    accent = A4L.Mint,
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

/** Un noyau voisin : une pastille qui respire. */
@Composable
private fun BoxScope.PulsingDot(
    color: Color,
    dotSize: Dp,
    periodMillis: Int,
    align: Alignment,
    x: Dp,
    y: Dp,
) {
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
            .align(align)
            .offset(x = x, y = y)
            .size(dotSize)
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
