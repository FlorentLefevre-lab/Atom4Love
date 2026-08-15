package one.astroport.atom4love.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
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
 * Les seuils sont ceux d'une salle : au-delà de −55 dBm on est à quelques pas,
 * en dessous de −85 on est aux limites de la portée.
 */
internal enum class Warmth(val rssiAtLeast: Int, val glyph: String) {
    Burning(-55, "🔥"),
    Warm(-70, "🌡"),
    Cool(-85, "❄"),
    Cold(Int.MIN_VALUE, "🧊"),
    ;

    val labelRes: Int
        get() = when (this) {
            Burning -> R.string.board_warmth_burning
            Warm -> R.string.board_warmth_warm
            Cool -> R.string.board_warmth_cool
            Cold -> R.string.board_warmth_cold
        }

    val color: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            Burning -> A4L.Orange
            Warm -> A4L.Amber
            Cool -> A4L.Cyan
            Cold -> A4L.TextDim
        }

    companion object {
        fun of(rssi: Int): Warmth = entries.first { rssi >= it.rssiAtLeast }
    }
}
