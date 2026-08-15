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
        // Le dessin vit dans un repère de 100 × 170, mis à l'échelle du cadre
        // reçu — les nombres ci-dessous se lisent donc comme des proportions.
        val u = minOf(size.width / 100f, size.height / 170f)
        val ox = (size.width - 100f * u) / 2f
        val oy = (size.height - 170f * u) / 2f
        fun x(v: Float) = ox + v * u
        fun y(v: Float) = oy + v * u

        // Les demi-largeurs de référence, à k = 1. L'épaule domine chez l'un,
        // la hanche chez l'autre, et l'écart est **franc** : à 40 dp de haut,
        // deux points de différence ne se voient pas.
        val shoulder = (if (female) 13.5f else 19.0f) * k
        val waist = (if (female) 10.0f else 14.5f) * k
        val hip = (if (female) 19.5f else 14.0f) * k

        // La tête grossit à peine : un corps qui double de largeur ne double
        // pas de crâne, et l'oublier donne un bonhomme de neige. Elle est
        // **détachée** du tronc, comme sur un pictogramme de porte — c'est ce
        // qui rend la forme lisible quand elle ne fait qu'un ongle de haut.
        val headR = 10.5f * (1f + (k - 1f) * 0.28f)
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

        // Le tronc et les bras d'un seul trait : épaule, bras qui descend en
        // s'écartant, main, remontée jusqu'à l'aisselle, taille, hanche. Le
        // creux entre le bras et le flanc est ce qui fait lire « quelqu'un »
        // plutôt qu'une masse.
        val armFlare = 3.5f + 3.5f * (k - 1f)   // les bras s'écartent avec la corpulence
        val armW = 5.4f * k
        val handY = 88f
        val armpitY = 52f

        val body = Path().apply {
            // Épaule gauche, sous la tête.
            moveTo(x(50f - shoulder), y(40f))
            cubicTo(
                x(50f - shoulder), y(32f),
                x(50f - shoulder * 0.42f), y(29f),
                x(50f), y(29f),
            )
            cubicTo(
                x(50f + shoulder * 0.42f), y(29f),
                x(50f + shoulder), y(32f),
                x(50f + shoulder), y(40f),
            )
            // Bras droit : bord extérieur, main arrondie, bord intérieur.
            lineTo(x(50f + shoulder + armFlare), y(handY))
            quadraticTo(
                x(50f + shoulder + armFlare - armW / 2f), y(handY + 5f),
                x(50f + shoulder + armFlare - armW), y(handY),
            )
            lineTo(x(50f + waist * 0.98f), y(armpitY))
            // Flanc droit : taille puis hanche.
            cubicTo(
                x(50f + waist), y(68f),
                x(50f + hip), y(80f),
                x(50f + hip), y(97f),
            )
            lineTo(x(50f - hip), y(97f))
            // Flanc gauche, en miroir.
            cubicTo(
                x(50f - hip), y(80f),
                x(50f - waist), y(68f),
                x(50f - waist * 0.98f), y(armpitY),
            )
            // Bras gauche.
            lineTo(x(50f - shoulder - armFlare + armW), y(handY))
            quadraticTo(
                x(50f - shoulder - armFlare + armW / 2f), y(handY + 5f),
                x(50f - shoulder - armFlare), y(handY),
            )
            close()
        }

        // Les jambes d'un seul trait, avec l'entrejambe en creux : deux formes
        // séparées se décolleraient du tronc dès que la corpulence monte.
        val ankle = 4.6f * (1f + (k - 1f) * 0.5f)
        val outerL = 50f - hip * 0.92f
        val outerR = 50f + hip * 0.92f
        val legs = Path().apply {
            moveTo(x(50f - hip), y(92f))
            lineTo(x(outerL - ankle / 2f), y(162f))
            quadraticTo(x(outerL), y(167f), x(outerL + ankle), y(162f))
            lineTo(x(50f - 2.2f), y(112f))
            lineTo(x(50f + 2.2f), y(112f))
            lineTo(x(outerR - ankle), y(162f))
            quadraticTo(x(outerR), y(167f), x(outerR + ankle / 2f), y(162f))
            lineTo(x(50f + hip), y(92f))
            close()
        }

        // Une seule forme, réunie : le tronc et les jambes se recouvrent à la
        // hanche, et deux remplissages translucides superposés y feraient une
        // tache plus sombre. La tête, elle, reste à part — c'est voulu.
        val trunk = Path().apply { op(body, legs, PathOperation.Union) }

        listOf(trunk, head).forEach { part ->
            drawPath(part, color.copy(alpha = 0.20f))
            drawPath(part, color, style = Stroke(width = 2.0f * u))
        }
    }
}
