package one.astroport.atom4love.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import one.astroport.atom4love.ui.theme.A4L

/** Un électron : son orbite, sa vitesse relative, son départ sur l'anneau. */
private class ElectronOrbit(val ring: Int, val speed: Float, val phase: Float)

// Trois anneaux inclinés comme le logo, un seul électron par orbite —
// vitesses légèrement désaccordées pour que la figure ne se répète jamais.
private val RingAngles = listOf(-60f, 0f, 60f)
private val OrbitingElectrons = listOf(
    ElectronOrbit(ring = 0, speed = 1.00f, phase = 0.00f),
    ElectronOrbit(ring = 1, speed = 0.83f, phase = 0.38f),
    ElectronOrbit(ring = 2, speed = 1.12f, phase = 0.71f),
)

/** Une révolution de base dure ce temps-là — le calme est voulu. */
private const val ORBIT_MS = 7200
/**
 * La demi-période du cœur : il enfle en 1050 ms, dégonfle en autant. Public
 * parce que le splash y accroche la pulsation du nom — deux horloges séparées
 * auraient dérivé l'une de l'autre au bout de quelques battements.
 */
internal const val ATOM_BEAT_MS = 1050
private const val TRAIL_STEPS = 6

private val HeartDark = Color(0xFFE7325C)
private val HeartLight = Color(0xFFFF7B93)
private val ElectronCore = Color(0xFFCFF6FF)

/**
 * L'atome ATOM4LOVE dessiné en vectoriel : cœur battant en guise de noyau,
 * trois orbites elliptiques inclinées, un électron par orbite avec son sillage.
 * Remplace le GIF du logo sur le splash — même dessin, cadence maîtrisée.
 */
@Composable
fun AtomLogo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "atom")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ORBIT_MS, easing = LinearEasing)),
        label = "atom-orbit",
    )
    val beat by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ATOM_BEAT_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "atom-beat",
    )

    // le Canvas dessine hors composition : la palette se lit ici, une fois
    val cyan = A4L.Cyan

    Canvas(modifier) {
        val unit = size.minDimension
        val rx = unit * 0.47f
        val ry = unit * 0.165f

        // ── Orbites ───────────────────────────────────────────────────────
        RingAngles.forEach { deg ->
            rotate(deg, center) {
                drawOval(
                    color = cyan.copy(alpha = 0.30f),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
        }

        // ── Noyau : le cœur qui bat ───────────────────────────────────────
        val heartH = unit * 0.34f
        val pulse = 0.95f + 0.07f * beat
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(HeartDark.copy(alpha = 0.20f + 0.12f * beat), Color.Transparent),
                center = center,
                radius = heartH * 1.15f,
            ),
            radius = heartH * 1.15f,
            center = center,
        )
        scale(pulse, pulse, center) {
            drawHeart(heartH)
        }

        // ── Électrons et leurs sillages ───────────────────────────────────
        OrbitingElectrons.forEach { e ->
            val ringRad = RingAngles[e.ring] * PI.toFloat() / 180f
            val r = unit * 0.016f

            fun position(turns: Float): Offset {
                val theta = turns * 2f * PI.toFloat()
                val x = rx * cos(theta)
                val y = ry * sin(theta)
                return Offset(
                    center.x + x * cos(ringRad) - y * sin(ringRad),
                    center.y + x * sin(ringRad) + y * cos(ringRad),
                )
            }

            val turns = orbit * e.speed + e.phase
            for (i in TRAIL_STEPS downTo 1) {
                drawCircle(
                    color = cyan.copy(alpha = 0.14f * (1f - i / (TRAIL_STEPS + 1f))),
                    radius = r * (1f - i * 0.08f),
                    center = position(turns - i * 0.012f),
                )
            }
            val p = position(turns)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cyan.copy(alpha = 0.35f), Color.Transparent),
                    center = p,
                    radius = r * 4.5f,
                ),
                radius = r * 4.5f,
                center = p,
            )
            drawCircle(color = ElectronCore, radius = r, center = p)
        }
    }
}

/** Le cœur du logo : deux lobes en Bézier, dégradé rose → framboise, reflet. */
private fun DrawScope.drawHeart(h: Float) {
    val w = h * 1.1f
    val left = center.x - w / 2f
    val top = center.y - h * 0.46f

    // Tracé classique sur une grille 100 × 88, mis à l'échelle (w, h).
    fun x(u: Float) = left + u / 100f * w
    fun y(v: Float) = top + v / 88f * h

    val path = Path().apply {
        moveTo(x(50f), y(88f))
        cubicTo(x(20f), y(60f), x(0f), y(40f), x(0f), y(25f))
        cubicTo(x(0f), y(8f), x(14f), y(0f), x(27f), y(0f))
        cubicTo(x(38f), y(0f), x(46f), y(7f), x(50f), y(15f))
        cubicTo(x(54f), y(7f), x(62f), y(0f), x(73f), y(0f))
        cubicTo(x(86f), y(0f), x(100f), y(8f), x(100f), y(25f))
        cubicTo(x(100f), y(40f), x(80f), y(60f), x(50f), y(88f))
        close()
    }
    drawPath(
        path,
        brush = Brush.linearGradient(
            colors = listOf(HeartLight, HeartDark),
            start = Offset(x(20f), y(0f)),
            end = Offset(x(80f), y(88f)),
        ),
    )
    // Reflet : petite virgule claire sur le lobe gauche, comme sur le logo.
    // Il reste blanc dans les deux thèmes — c'est la lumière sur un objet rose,
    // pas une couleur d'interface.
    rotate(-28f, Offset(x(30f), y(18f))) {
        drawOval(
            color = Color.White.copy(alpha = 0.75f),
            topLeft = Offset(x(30f) - w * 0.10f, y(18f) - h * 0.045f),
            size = Size(w * 0.20f, h * 0.09f),
        )
    }
}
