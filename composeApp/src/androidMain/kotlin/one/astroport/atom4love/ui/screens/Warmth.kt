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
internal enum class Warmth(val lossAtMost: Int, val glyph: String) {
    /** À bout de bras. La chaleur a fini son travail, le rythme prend le relais. */
    Touching(57, "🎯"),   // < 0,5 m
    Burning(67, "🔥"),    // 0,5 – 1,2 m
    Hot(76, "🌶"),        // 1,2 – 2,5 m
    Warm(81, "🌡"),       // 2,5 – 4 m
    Cool(88, "❄"),        // 4 – 7 m, jusqu'au plancher du récepteur
    Cold(Int.MAX_VALUE, "🧊"),
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
         * L'atténuation à un mètre, en décibels. **65 dB, mesuré le 15/08.**
         *
         * Elle se déduit de deux relevés du même soir : les deux appareils
         * annoncent **−7 dBm** (`ADVERTISE_TX_POWER_MEDIUM`, le défaut
         * d'Android), et s'entendent à **−72 dBm** posés à un mètre l'un de
         * l'autre. L'air, les antennes et la table en mangent donc 65.
         *
         * ⚠ C'est très loin des ~40 dB de l'espace libre à 2,4 GHz, et ce n'est
         * pas une erreur : une antenne de téléphone est mauvaise, l'appareil
         * était posé à plat sur un plan qui réfléchit, et un corps se tenait à
         * côté. On garde le nombre mesuré, pas celui de la théorie — c'est dans
         * cette pièce-là que le jeu se joue.
         *
         * Pour réétalonner : deux appareils à un mètre, `adb logcat | grep
         * "rssi="`, médiane du RSSI et lecture du `tx=` de la même ligne ;
         * l'atténuation est leur différence.
         */
        const val PATH_LOSS_AT_ONE_METRE = 65.0

        /**
         * La puissance qu'on **suppose** à un pair qui ne l'annonce pas.
         *
         * −7 dBm, la valeur des deux appareils du banc, qui est aussi le défaut
         * d'Android. C'est une supposition, et elle vaut ce que vaut une
         * supposition : un téléphone qui crie plus fort se croira plus proche.
         * D'où le champ dans l'annonce — voir [ProximityEngine].
         */
        const val ASSUMED_TX_POWER_DBM = -7

        /**
         * L'exposant d'atténuation. 2,0 en espace libre, 2,7 dans une salle
         * meublée avec des gens dedans — c'est le cas d'usage, et les corps
         * absorbent la bande des 2,4 GHz mieux que les murs.
         */
        const val PATH_LOSS_EXPONENT = 2.7

        /** Ce qu'il faut dépasser pour quitter un état. Voir le mot sur l'hystérésis. */
        private const val HYSTERESIS_DB = 2

        /**
         * L'atténuation du trajet, en décibels : ce que l'émetteur a mis dans
         * l'air, moins ce que nous en entendons.
         *
         * **C'est la seule grandeur comparable d'un appareil à l'autre.** Un
         * RSSI seul ne dit rien : deux téléphones à un mètre, l'un émettant à
         * −4 dBm et l'autre à −20, s'entendent 16 dB différemment — soit un
         * facteur 4 sur la distance déduite. C'est pour ça que toute l'échelle
         * ci-dessus est graduée en atténuation depuis le 15/08, et non plus en
         * RSSI comme elle l'était le matin même.
         */
        fun pathLoss(rssi: Int, txPowerDbm: Int?): Int =
            (txPowerDbm ?: ASSUMED_TX_POWER_DBM) - rssi

        /** L'état correspondant à un signal, sans mémoire. */
        fun of(rssi: Int, txPowerDbm: Int?): Warmth {
            val loss = pathLoss(rssi, txPowerDbm)
            return entries.first { loss <= it.lossAtMost }
        }

        /**
         * L'état suivant, connaissant le précédent.
         *
         * On ne quitte [previous] que si l'atténuation a franchi la frontière de
         * [HYSTERESIS_DB] de plus : se rapprocher d'un cran demande 2 dB de
         * moins que le seuil visé, s'en éloigner demande 2 dB de plus que celui
         * qu'on occupe. Une frontière traversée en aller-retour ne fait donc
         * plus clignoter le mot.
         */
        fun of(rssi: Int, txPowerDbm: Int?, previous: Warmth?): Warmth {
            if (previous == null) return of(rssi, txPowerDbm)
            val loss = pathLoss(rssi, txPowerDbm)
            val candidate = of(rssi, txPowerDbm)
            if (candidate == previous) return previous
            return if (candidate.ordinal < previous.ordinal) {
                // Il fait plus chaud : exiger d'être franchement en dessous.
                if (loss <= candidate.lossAtMost - HYSTERESIS_DB) candidate else previous
            } else {
                // Il fait plus froid : exiger d'être franchement au-dessus.
                if (loss > previous.lossAtMost + HYSTERESIS_DB) candidate else previous
            }
        }

        /**
         * La distance en mètres, déduite de l'atténuation.
         *
         * `d = 10 ^ ((atténuation − A₁) / (10·n))`, le modèle log-distance. À
         * prendre pour ce qu'il est : à l'intérieur, l'erreur va couramment du
         * simple au double. Le nombre sert à décider si l'on traverse la salle
         * ou si l'on regarde autour de soi, pas à désigner une chaise.
         *
         * ⚠ À nourrir avec le RSSI **lissé**. Sur du brut, les 18 dB d'amplitude
         * mesurés le 15/08 font varier le résultat d'un facteur 4,6 pour deux
         * appareils posés qui ne bougent pas.
         */
        fun metres(rssi: Int, txPowerDbm: Int?): Double =
            10.0.pow(
                (pathLoss(rssi, txPowerDbm) - PATH_LOSS_AT_ONE_METRE) /
                    (10.0 * PATH_LOSS_EXPONENT),
            )

        /**
         * Au-delà, le modèle rend encore des nombres mais plus d'information :
         * on est au plancher du récepteur, et ce qui reste est du bruit. Réglé
         * sur la portée utile mesurée, pas sur ce que la formule tolère.
         */
        const val USEFUL_RANGE_METRES = 8.0
    }
}
