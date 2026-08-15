package one.astroport.atom4love.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.BodyMetrics
import one.astroport.atom4love.domain.Wave
import one.astroport.atom4love.ui.theme.A4L
import kotlin.math.pow

/**
 * L'indice de masse corporelle, et la silhouette qu'on en dessine.
 *
 * ⚠ **Rien de tout ceci n'est de Fred, et rien n'entre dans une clé.** Le SALT
 * ne connaît que la date, le lieu, le sexe et le poids de NAISSANCE ; la taille
 * et le poids d'aujourd'hui ne servent plus qu'à ce dessin depuis que Watson a
 * quitté le dépôt (15/08). On peut le retirer demain sans qu'un seul npub bouge.
 *
 * Pourquoi un dessin plutôt que deux nombres : parce que deux nombres qu'on
 * vient de saisir ne se vérifient pas — 183 cm et 104 kg se lisent pareil que
 * 138 cm et 140 kg. Une silhouette, si : on voit du premier coup d'œil qu'elle
 * ne nous ressemble pas, et on remonte corriger avant de sceller.
 */
object Bmi {

    /** `kg / m²`, ou null tant qu'il manque une des deux mesures. */
    fun of(body: BodyMetrics): Float? {
        val h = body.heightCm ?: return null
        val w = body.weightKg ?: return null
        if (h <= 0) return null
        return w / (h / 100f).pow(2)
    }

    /**
     * Les tranches de l'OMS, telles quelles.
     *
     * Elles portent des mots, et des mots pèsent : l'écran les dit sans les
     * commenter, et rappelle à côté que rien de tout cela n'entre dans la clé.
     * Un seuil n'est pas un jugement, et l'app n'a pas d'avis sur les corps.
     */
    enum class Band(
        /** Borne haute, exclue. La dernière n'en a pas. */
        val below: Float,
        @StringRes val labelRes: Int,
    ) {
        Thin(18.5f, R.string.bmi_band_thin),
        Normal(25f, R.string.bmi_band_normal),
        Over(30f, R.string.bmi_band_over),
        Obese(35f, R.string.bmi_band_obese),
        Severe(Float.MAX_VALUE, R.string.bmi_band_severe),
        ;

        val color: Color
            @Composable @ReadOnlyComposable get() = when (this) {
                Thin -> A4L.Cyan
                Normal -> A4L.Mint
                Over -> A4L.Gold
                Obese -> A4L.Amber
                Severe -> A4L.Orange
            }

        companion object {
            fun of(bmi: Float): Band = entries.first { bmi < it.below }
        }
    }

    /**
     * L'IMC du dessin quand aucune mesure n'a été donnée. 22 est le milieu de
     * la tranche normale : la silhouette neutre n'est celle de personne, et
     * surtout elle ne suggère rien.
     */
    const val NEUTRAL = 22f
}

/**
 * Une silhouette stylisée, large comme l'IMC la fait et taillée comme le sexe
 * la taille.
 *
 * Le tracé n'a rien de médical : c'est un pictogramme dont **une seule** mesure
 * varie, la largeur. La taille du dessin ne suit PAS les centimètres — deux
 * silhouettes côte à côte doivent se comparer sur leur corpulence, et une
 * personne petite ne mérite pas un dessin plus petit.
 *
 * ⚠ Dessin maison, et assumé : la règle de la maison est de partir d'un
 * composant Material quand il en existe un. Il n'existe pas de silhouette
 * humaine dans M3, et un `Icon` figé ne saurait pas s'élargir.
 *
 * @param sex la polarité de la fiche — l'épaule et la hanche s'inversent avec elle
 * @param bmi l'indice, ou null pour la silhouette neutre
 */
@Composable
fun Silhouette(
    sex: Wave?,
    bmi: Float?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val female = sex == Wave.Octave
    // De la maigreur à l'obésité sévère, la largeur va de 0,70 à 1,55 : assez
    // pour que deux tranches voisines se distinguent, pas assez pour caricaturer.
    val k = (0.70f + ((bmi ?: Bmi.NEUTRAL) - 15f) / 25f * 0.85f).coerceIn(0.70f, 1.55f)

    Canvas(modifier) {
        // Le dessin vit dans un repère de 100 × 175, mis à l'échelle du cadre
        // reçu — les nombres ci-dessous se lisent donc comme des proportions.
        val u = minOf(size.width / 100f, size.height / 175f)
        val ox = (size.width - 100f * u) / 2f
        val oy = (size.height - 175f * u) / 2f
        fun x(v: Float) = ox + v * u
        fun y(v: Float) = oy + v * u

        // ── Ce que la corpulence élargit, et dans quel ordre ───────────────
        //
        // ⚠ La première version étirait tout du même facteur. Un IMC de 36 y
        // donnait des épaules de lutteur : le dessin disait « costaud » là où
        // il fallait lire « corpulent ». Un corps ne grossit pas ainsi — la
        // taille prend l'essentiel, la hanche suit, l'épaule bouge à peine.
        val ks = 1f + (k - 1f) * 0.30f   // épaules
        val kw = 1f + (k - 1f) * 1.35f   // taille, le vrai signal
        val kh = 1f + (k - 1f) * 0.85f   // hanches

        val shoulder = (if (female) 13.5f else 17.0f) * ks
        val waist = (if (female) 10.0f else 12.5f) * kw
        val hip = (if (female) 15.5f else 13.0f) * kh

        // La tête grossit à peine : un corps qui double de largeur ne double
        // pas de crâne, et l'oublier donne un bonhomme de neige. Elle est
        // **détachée** du tronc, comme sur un pictogramme de porte — c'est ce
        // qui rend la forme lisible quand elle ne fait qu'un ongle de haut.
        val headR = 9.5f * (1f + (k - 1f) * 0.22f)
        val head = Path().apply {
            addOval(
                Rect(
                    left = x(50f - headR),
                    top = y(17f - headR),
                    right = x(50f + headR),
                    bottom = y(17f + headR),
                ),
            )
        }

        // ── Le tronc, d'un seul trait : épaule, taille, hanche ─────────────
        val torso = Path().apply {
            moveTo(x(50f - shoulder), y(42f))
            cubicTo(x(50f - shoulder), y(50f), x(50f - waist), y(56f), x(50f - waist), y(66f))
            cubicTo(x(50f - waist), y(78f), x(50f - hip), y(80f), x(50f - hip), y(94f))
            lineTo(x(50f + hip), y(94f))
            cubicTo(x(50f + hip), y(80f), x(50f + waist), y(78f), x(50f + waist), y(66f))
            cubicTo(x(50f + waist), y(56f), x(50f + shoulder), y(50f), x(50f + shoulder), y(42f))
            // L'épaule remonte chercher le cou.
            cubicTo(
                x(50f + shoulder), y(34f),
                x(50f + shoulder * 0.45f), y(31f),
                x(50f), y(31f),
            )
            cubicTo(
                x(50f - shoulder * 0.45f), y(31f),
                x(50f - shoulder), y(34f),
                x(50f - shoulder), y(42f),
            )
            close()
        }

        /**
         * Un membre : un fuseau qui va de large à étroit, le bout arrondi. Il
         * part **dans** le tronc et s'en écarte en descendant ; l'union fait le
         * reste, et le creux qui apparaît entre les deux est ce qui fait lire
         * « quelqu'un » plutôt qu'une masse.
         */
        fun limb(x1: Float, y1: Float, w1: Float, x2: Float, y2: Float, w2: Float) =
            Path().apply {
                moveTo(x(x1 - w1), y(y1))
                lineTo(x(x2 - w2), y(y2))
                arcTo(
                    rect = Rect(
                        left = x(x2 - w2), top = y(y2 - w2),
                        right = x(x2 + w2), bottom = y(y2 + w2),
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -180f,
                    forceMoveTo = false,
                )
                lineTo(x(x1 + w1), y(y1))
                close()
            }

        // ⚠ L'écart des bras se mesure sur **le corps**, jamais sur l'IMC.
        // Calculé sur l'IMC, il était dépassé par la taille dès qu'elle
        // grossissait vraiment : à 36, les bras se ressoudaient au tronc et la
        // silhouette redevenait un bloc. Parti de la largeur maximale, le creux
        // est garanti à toutes les corpulences.
        val widest = maxOf(shoulder, waist, hip)
        val armW = 4.2f * (1f + (k - 1f) * 0.55f)
        val handX = widest + ARM_GAP + armW * 0.72f
        val armLeft = limb(50f - shoulder + armW * 0.55f, 40f, armW, 50f - handX, 92f, armW * 0.72f)
        val armRight = limb(50f + shoulder - armW * 0.55f, 40f, armW, 50f + handX, 92f, armW * 0.72f)

        // Même principe pour l'entrejambe : la cuisse est une fraction de la
        // hanche, donc le V s'ouvre en même temps que le corps s'élargit.
        val thigh = hip * 0.40f
        val legX = hip * 0.52f
        val ankle = 4.4f * (1f + (k - 1f) * 0.40f)
        val legLeft = limb(50f - legX, 88f, thigh, 50f - legX * 0.92f, 156f, ankle)
        val legRight = limb(50f + legX, 88f, thigh, 50f + legX * 0.92f, 156f, ankle)

        // Une seule forme, réunie : membres et tronc se recouvrent, et deux
        // remplissages translucides superposés y feraient une tache plus sombre
        // à chaque jointure. La tête reste à part — c'est voulu.
        var trunk = torso
        listOf(armLeft, armRight, legLeft, legRight).forEach { part ->
            trunk = Path().apply { op(trunk, part, PathOperation.Union) }
        }

        listOf(trunk, head).forEach { part ->
            drawPath(part, color.copy(alpha = 0.20f))
            drawPath(part, color, style = Stroke(width = 2.0f * u))
        }
    }
}

/** Le jour entre le bras et le flanc, en unités du repère. Jamais moins. */
private const val ARM_GAP = 3.0f
