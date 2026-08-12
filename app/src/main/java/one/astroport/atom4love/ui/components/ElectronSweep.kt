package one.astroport.atom4love.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.PI
import kotlin.math.sin
import one.astroport.atom4love.ui.theme.A4L

/** Un électron du vol : sa couleur, son couloir vertical et le galbe de son arc. */
private class ElectronSpec(
    val color: Color,
    val delay: Float,        // départ décalé, en fraction du vol total
    val laneY: Float,        // couloir, en fraction de la hauteur
    val arc: Float,          // amplitude de l'arc, en fraction de la hauteur (+ creuse, − bombe)
    val radius: Float,       // rayon du noyau de l'électron, en dp
)

/**
 * Les trois électrons du vol. Une fonction et non une constante : leurs
 * couleurs viennent de la palette, qui change avec l'heure de la station.
 */
@Composable
private fun electrons() = listOf(
    ElectronSpec(A4L.Cyan, delay = 0.00f, laneY = 0.30f, arc = +0.10f, radius = 3.2f),
    ElectronSpec(A4L.Mint, delay = 0.10f, laneY = 0.52f, arc = -0.13f, radius = 2.6f),
    ElectronSpec(A4L.Violet, delay = 0.20f, laneY = 0.72f, arc = +0.08f, radius = 2.2f),
)

private const val SWEEP_MS = 620
private const val TRAIL_STEPS = 7

/**
 * Le vol d'électrons qui accompagne un changement d'écran : trois particules
 * traversent la station en arc, dans le sens de la navigation, chacune traînant
 * un sillage qui s'évanouit. À poser en overlay du contenu (ne capte aucun geste).
 */
@Composable
fun ElectronSweep(trigger: Any, modifier: Modifier = Modifier) {
    var previous by remember { mutableStateOf(trigger) }
    var forward by remember { mutableStateOf(true) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger != previous) {
            // Le sens du vol suit l'ordre des onglets (enum ordinal si disponible).
            forward = ((trigger as? Enum<*>)?.ordinal ?: 0) >= ((previous as? Enum<*>)?.ordinal ?: 0)
            previous = trigger
            progress.snapTo(0f)
            progress.animateTo(1f, tween(SWEEP_MS, easing = FastOutSlowInEasing))
        }
    }

    if (progress.value >= 1f) return

    // le Canvas dessine hors composition : la liste se construit avant
    val electrons = electrons()

    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        electrons.forEach { e ->
            // Chaque électron vole sur sa propre fenêtre temporelle décalée.
            val local = ((t - e.delay) / (1f - e.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach

            val glow = sin(local * PI).toFloat()   // né au bord, éteint au bord
            val r = e.radius * density

            fun at(f: Float): Offset {
                val x = if (forward) size.width * (f * 1.16f - 0.08f)
                else size.width * (1.08f - f * 1.16f)
                val y = size.height * (e.laneY + e.arc * sin(f * PI).toFloat())
                return Offset(x, y)
            }

            // Sillage : quelques échos de plus en plus ténus derrière la particule.
            for (i in TRAIL_STEPS downTo 1) {
                val f = local - i * 0.028f
                if (f <= 0f) continue
                val fade = (1f - i / (TRAIL_STEPS + 1f)) * glow
                drawCircle(
                    color = e.color.copy(alpha = 0.16f * fade),
                    radius = r * (1f - i * 0.09f),
                    center = at(f),
                )
            }

            // Halo puis noyau de l'électron.
            val center = at(local)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(e.color.copy(alpha = 0.30f * glow), Color.Transparent),
                    center = center,
                    radius = r * 5f,
                ),
                radius = r * 5f,
                center = center,
            )
            drawCircle(color = e.color.copy(alpha = glow), radius = r, center = center)
        }
    }
}
