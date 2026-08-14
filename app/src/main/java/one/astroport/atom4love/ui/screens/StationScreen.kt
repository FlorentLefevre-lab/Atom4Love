package one.astroport.atom4love.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R
import one.astroport.atom4love.chat.CabinChat
import one.astroport.atom4love.chat.Medium
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.nostr.CabinSalon
import one.astroport.atom4love.nostr.NostrKeys
import one.astroport.atom4love.nostr.RelayStation
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * Les deux échelles de la Carte.
 *
 * **Ce ne sont pas deux niveaux de zoom d'une même vue, et ça ne peut pas
 * l'être.** [Here] place ses voisins par force de signal et par un angle tiré de
 * leur adresse radio — le BLE ne donne aucune direction, `RadarScreen` le dit
 * lui-même. [World] place des lieux réels. Un zoom continu de l'un à l'autre
 * ferait passer un placement de constellation pour des coordonnées ; deux
 * segments disent la vérité : ce sont deux façons de regarder, pas deux
 * distances.
 */
enum class PlaceView(@StringRes val labelRes: Int) {
    /** Ce qui est à portée d'antenne, maintenant. */
    Here(R.string.place_here),

    /** Ceux qui ont activé leur clé LOVE, où qu'ils soient nés. */
    World(R.string.place_world),
}

/**
 * 02 · La Carte — la seule destination qui regarde dehors.
 *
 * Elle a remplacé quatre onglets : le Radar et la Constellation sont ses deux
 * vues, l'Aide et les Réglages sont passés dans l'en-tête (ce ne sont pas des
 * lieux, ce sont des poignées), et le Plateau attend sa partie derrière le
 * Noyau. Restent deux destinations dans la barre : ici, et soi.
 */
@Composable
fun StationScreen(
    view: PlaceView,
    onSelectView: (PlaceView) -> Unit,
    birth: BirthData,
    modifier: Modifier = Modifier,
    relay: RelayStation.Status? = null,
    salon: CabinSalon? = null,
    keys: NostrKeys? = null,
    cabin: CabinChat? = null,
    onSelectMedium: (Medium) -> Unit = {},
    cabinOpen: Boolean = false,
    onOpenCabin: () -> Unit = {},
    onCloseCabin: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(A4L.Deep)) {
        PlaceSelector(view = view, onSelect = onSelectView)
        Box(Modifier.weight(1f)) {
            Crossfade(view, animationSpec = tween(320), label = "place") { shown ->
                when (shown) {
                    PlaceView.Here -> RadarScreen(
                        relay = relay,
                        salon = salon,
                        keys = keys,
                        cabin = cabin,
                        onSelectMedium = onSelectMedium,
                        cabinOpen = cabinOpen,
                        onOpenCabin = onOpenCabin,
                        onCloseCabin = onCloseCabin,
                    )
                    PlaceView.World -> MapScreen(birth = birth)
                }
            }
        }
    }
}

/**
 * Le choix de l'échelle — un `SegmentedButton` de Material 3, rhabillé aux
 * couleurs de la station.
 *
 * Deux segments et non deux onglets de plus : passer d'ici au monde ne change
 * pas d'endroit dans l'application, ça change de focale sur la même question —
 * qui est là.
 */
@Composable
private fun PlaceSelector(view: PlaceView, onSelect: (PlaceView) -> Unit) {
    val accent = A4L.Cyan
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
    ) {
        PlaceView.entries.forEachIndexed { index, entry ->
            val selected = entry == view
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, PlaceView.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = accent.tint(0.12f),
                    activeContentColor = accent,
                    activeBorderColor = accent.tint(0.38f),
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = A4L.TextMuted,
                    inactiveBorderColor = A4L.StrokeSoft,
                ),
                // La coche par défaut vole la place du mot et n'apprend rien de
                // plus que la couleur : le segment choisi se voit déjà.
                icon = {},
                label = {
                    Text(
                        stringResource(entry.labelRes),
                        style = A4LText.Body.copy(fontSize = 13.sp),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}
