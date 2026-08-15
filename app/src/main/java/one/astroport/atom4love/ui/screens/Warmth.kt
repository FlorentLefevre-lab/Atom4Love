package one.astroport.atom4love.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.theme.A4L

/**
 * Combien on brûle — la seule direction que le BLE sache donner.
 *
 * La radio ne dit pas **où** est quelqu'un : elle dit à quel point son signal
 * est fort, et ça monte quand on s'en approche. C'est pauvre comme boussole, et
 * c'est exactement la mécanique d'un jeu de piste : on marche, et le téléphone
 * dit qu'on chauffe.
 *
 * Ce que la chaleur ne fera jamais, c'est le dernier mètre : arrivé devant une
 * table de six, elle a dit tout ce qu'elle savait. C'est là que
 * [one.astroport.atom4love.proximity.Rendezvous] prend le relais.
 *
 * ## Ce que le signal vaut vraiment
 *
 * Mesuré le 15/08 sur deux appareils immobiles : **18 dB d'amplitude** pour un
 * couple qui ne bougeait pas (−79 à −61 dBm, écart-type 4,2 dB). Avec les
 * quatre seuils d'alors, lus sur le RSSI brut, l'état changeait **23 fois sur
 * 42 relevés** — le jeu disait « tu brûles, tu refroidis, tu brûles » à
 * quelqu'un d'assis.
 *
 * Détailler les états sur un signal pareil les aurait rendus **moins** précis,
 * pas plus : à six paliers sur du brut, toujours 23 changements. La précision
 * vient d'abord du lissage — médiane sur cinq relevés puis exponentielle, dans
 * [one.astroport.atom4love.proximity.NeighborRegistry], qui ramène l'écart-type
 * de 4,2 à **0,9 dB** sur cette même série. Six états espacés de 5 à 10 dB
 * deviennent alors distincts, là où quatre ne l'étaient pas.
 *
 * Et l'hystérésis de 2 dB par-dessus : un état ne cède que si le signal a
 * franchi son seuil d'autant de plus. Sans elle, un pas de côté sur une
 * frontière fait clignoter le mot.
 *
 * Rejoué sur les 43 relevés d'origine : **zéro changement d'état** au lieu de
 * 23, et une distance déduite qui ne varie plus que d'un facteur 1,4 au lieu
 * de 4,6.
 */
internal enum class Warmth(val rssiAtLeast: Int, val glyph: String) {
    /** À bout de bras. La chaleur a fini son travail, le rythme prend le relais. */
    Touching(-64, "🎯"),   // < 0,5 m
    Burning(-74, "🔥"),    // 0,5 – 1,2 m
    Hot(-83, "🌶"),        // 1,2 – 2,5 m
    Warm(-88, "🌡"),       // 2,5 – 4 m
    Cool(-95, "❄"),        // 4 – 7 m, jusqu'au plancher du récepteur
    Cold(Int.MIN_VALUE, "🧊"),
    ;

    val labelRes: Int
        get() = when (this) {
            Touching -> R.string.board_warmth_touching
            Burning -> R.string.board_warmth_burning
            Hot -> R.string.board_warmth_hot
            Warm -> R.string.board_warmth_warm
            Cool -> R.string.board_warmth_cool
            Cold -> R.string.board_warmth_cold
        }

    val color: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Touching -> A4L.Mint
            Burning -> A4L.Orange
            Hot -> A4L.Gold
            Warm -> A4L.Amber
            Cool -> A4L.Cyan
            Cold -> A4L.TextDim
        }

    companion object {
        /**
         * Le RSSI mesuré à un mètre, antenne face à antenne.
         *
         * **−72 dBm, ÉTALONNÉ le 15/08** — Pixel 10 Pro et Lenovo TB350XU posés
         * à un mètre, relevés des deux côtés : le Pixel entend la tablette à
         * −71 (médiane, n = 9), la tablette entend le Pixel à −74 (n = 36).
         * Trois décibels d'écart entre les deux sens, ce qui est peu.
         *
         * La convention BLE dit −59 dBm, pour une émission à 0 dBm. Ces
         * appareils sont **13 dB en dessous** : l'annonce se fait à puissance
         * réduite, comme le font les téléphones récents pour épargner la
         * batterie. Avec −59, un pair à un mètre s'affichait **à trois mètres**.
         *
         * Conséquence qui compte plus que la correction elle-même : si le
         * plancher du récepteur est vers −95 dBm, la portée utile de cette
         * balise est d'environ **7 mètres**, pas les trente que le modèle
         * autorisait. Toute l'échelle ci-dessus a été redéployée sur 0 à 7 m.
         *
         * ⚠ C'est l'étalonnage de CE couple d'appareils. L'annonce ne porte pas
         * sa puissance d'émission — rien dans la radio ne permet de la déduire —
         * donc un téléphone qui émet plus fort se croira plus proche. C'est la
         * limite du procédé, pas un défaut de réglage. Pour réétalonner : deux
         * appareils à un mètre exactement, `adb logcat | grep rssi=`, médiane.
         */
        const val RSSI_AT_ONE_METRE = -72.0

        /**
         * L'exposant d'atténuation. 2,0 en espace libre, 2,7 dans une salle
         * meublée avec des gens dedans — c'est le cas d'usage, et les corps
         * absorbent la bande des 2,4 GHz mieux que les murs.
         */
        const val PATH_LOSS_EXPONENT = 2.7

        /** Ce qu'il faut dépasser pour quitter un état. Voir le mot sur l'hystérésis. */
        private const val HYSTERESIS_DB = 2

        /** L'état correspondant à un signal, sans mémoire. */
        fun of(rssi: Int): Warmth = entries.first { rssi >= it.rssiAtLeast }

        /**
         * L'état suivant, connaissant le précédent.
         *
         * On ne quitte [previous] que si le signal a franchi la frontière de
         * [HYSTERESIS_DB] de plus : monter d'un cran demande 2 dB au-dessus du
         * seuil visé, en descendre demande 2 dB en dessous de celui qu'on
         * occupe. Une frontière traversée en aller-retour ne fait donc plus
         * clignoter le mot.
         */
        fun of(rssi: Int, previous: Warmth?): Warmth {
            if (previous == null) return of(rssi)
            val candidate = of(rssi)
            if (candidate == previous) return previous
            return if (candidate.ordinal < previous.ordinal) {
                // Il fait plus chaud : exiger d'être franchement au-dessus.
                if (rssi >= candidate.rssiAtLeast + HYSTERESIS_DB) candidate else previous
            } else {
                // Il fait plus froid : exiger d'être franchement en dessous.
                if (rssi < previous.rssiAtLeast - HYSTERESIS_DB) candidate else previous
            }
        }

        /**
         * La distance en mètres, déduite de l'atténuation.
         *
         * `d = 10 ^ ((P₁ − rssi) / (10·n))`, le modèle log-distance. À prendre
         * pour ce qu'il est : à l'intérieur, l'erreur va couramment du simple au
         * double. Le nombre sert à décider si l'on traverse la salle ou si l'on
         * regarde autour de soi, pas à désigner une chaise.
         *
         * ⚠ À nourrir avec le RSSI **lissé**. Sur du brut, les 18 dB d'amplitude
         * mesurés le 15/08 font varier le résultat d'un facteur 4,6 pour deux
         * appareils posés qui ne bougent pas.
         */
        fun metres(rssi: Int): Double =
            10.0.pow((RSSI_AT_ONE_METRE - rssi) / (10.0 * PATH_LOSS_EXPONENT))

        /**
         * Au-delà, le modèle rend encore des nombres mais plus d'information :
         * on est au plancher du récepteur, et ce qui reste est du bruit. Réglé
         * sur la portée utile mesurée, pas sur ce que la formule tolère.
         */
        const val USEFUL_RANGE_METRES = 8.0
    }
}
