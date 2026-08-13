package one.astroport.atom4love.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/** La hauteur d'un élément — la fenêtre du choix s'y accorde exactement. */
private val WHEEL_ITEM_HEIGHT = 38.dp

/**
 * Un rouleau de valeurs : la liste défile sous une fenêtre fixe, et la valeur
 * alignée dessus est celle qui est retenue.
 *
 * Material 3 ne propose aucun rouleau — son sélecteur de date est un
 * calendrier, celui d'heure un cadran. Il n'y a donc pas de composant à
 * habiller ici : le rouleau se construit sur les primitives (une `LazyColumn`
 * et son magnétisme) et n'emprunte au thème que ses couleurs et sa typographie.
 *
 * [selectedIndex] est la source de vérité : le rouleau s'y recale quand elle
 * change du dehors — un 31 ramené à 30 en changeant de mois, par exemple.
 */
@Composable
fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = WHEEL_ITEM_HEIGHT,
    visibleCount: Int = 5,
) {
    // Un nombre impair de lignes : il faut une ligne centrale, et autant de
    // vide au-dessus qu'en dessous pour que le premier et le dernier élément
    // puissent atteindre le centre.
    val rows = if (visibleCount % 2 == 0) visibleCount + 1 else visibleCount
    val pad = rows / 2
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    // La corbeille à laquelle l'effet ci-dessous doit puiser. Il n'est relancé
    // qu'au changement de liste : sans ces deux relais, il appellerait jusqu'à
    // la fin des temps la fonction composée au tout premier passage — celle qui
    // a capturé les valeurs des AUTRES rouleaux telles qu'elles étaient à
    // l'ouverture. Tourner le mois remettait ainsi le jour à sa valeur initiale.
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onSelected)

    fun centered(): Int {
        val extra = if (state.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
        return (state.firstVisibleItemIndex + extra).coerceIn(items.indices)
    }

    // La valeur ne remonte qu'une fois le rouleau reposé : pendant le
    // défilement, une ligne survolée n'est pas un choix.
    LaunchedEffect(state, items.size) {
        snapshotFlow { state.isScrollInProgress }
            .drop(1)
            .filter { !it }
            .collect {
                val index = centered()
                if (index != currentSelected) currentOnSelected(index)
            }
    }

    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && centered() != selectedIndex) {
            state.scrollToItem(selectedIndex.coerceIn(items.indices))
        }
    }

    LazyColumn(
        modifier.height(itemHeight * rows),
        state = state,
        flingBehavior = flingBehavior,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(pad) { Box(Modifier.height(itemHeight)) }
        items(items.size) { index ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    items[index],
                    style = A4LText.Data.copy(fontSize = if (selected) 17.sp else 14.sp),
                    color = if (selected) A4L.TextHigh else A4L.TextGhost,
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(pad) { Box(Modifier.height(itemHeight)) }
    }
}

/**
 * La fenêtre du choix : une bande unique derrière les rouleaux d'une même
 * ligne, à la hauteur exacte d'un élément.
 */
@Composable
private fun WheelWindow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .glass(9.dp, A4L.Cyan.tint(0.07f), A4L.Cyan.tint(0.28f)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/**
 * Les trois rouleaux d'une date de naissance : jour, mois, année.
 *
 * Le jour est borné par le mois choisi — un 31 février ne peut pas être
 * composé, là où la saisie libre le permettait.
 */
@Composable
fun BirthDateWheels(
    year: Int,
    month: Int,
    day: Int,
    yearRange: IntRange,
    onChange: (year: Int, month: Int, day: Int) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    val years = remember(yearRange) { yearRange.toList() }
    val months = remember(locale) {
        (1..12).map { Month.of(it).getDisplayName(TextStyle.SHORT, locale) }
    }
    val daysInMonth = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }
    val days = remember(daysInMonth) { (1..daysInMonth).map { "%02d".format(it) } }
    val safeDay = day.coerceIn(1, daysInMonth)

    WheelWindow(modifier) {
        WheelColumn(
            items = days,
            selectedIndex = safeDay - 1,
            onSelected = { onChange(year, month, it + 1) },
            modifier = Modifier.weight(0.9f),
        )
        WheelColumn(
            items = months,
            selectedIndex = month - 1,
            onSelected = { index ->
                val newMonth = index + 1
                val max = YearMonth.of(year, newMonth).lengthOfMonth()
                onChange(year, newMonth, safeDay.coerceAtMost(max))
            },
            modifier = Modifier.weight(1.2f),
        )
        WheelColumn(
            items = remember(years) { years.map { it.toString() } },
            selectedIndex = years.indexOf(year).coerceAtLeast(0),
            onSelected = { index ->
                val newYear = years[index]
                val max = YearMonth.of(newYear, month).lengthOfMonth()
                onChange(newYear, month, safeDay.coerceAtMost(max))
            },
            modifier = Modifier.weight(1.1f),
        )
    }
}

/**
 * Les deux rouleaux d'une heure de naissance : heures et minutes.
 *
 * Le cadran de Material vise une heure de rendez-vous, qu'on approche ; une
 * heure de naissance se lit sur un acte, au chiffre près — d'où les mêmes
 * rouleaux que la date, et la minute à l'unité.
 */
@Composable
fun BirthTimeWheels(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = remember { (0..23).map { "%02d".format(it) } }
    val minutes = remember { (0..59).map { "%02d".format(it) } }

    WheelWindow(modifier) {
        WheelColumn(
            items = hours,
            selectedIndex = hour.coerceIn(0, 23),
            onSelected = { onChange(it, minute) },
            modifier = Modifier.weight(1f),
        )
        // Deux nombres côte à côte ne disent pas une heure ; le même deux-points
        // que la ligne du formulaire les relie. Il ne défile pas : il se tient
        // sur la fenêtre du choix, à hauteur de la valeur retenue.
        Text(
            ":",
            style = A4LText.Data.copy(fontSize = 17.sp),
            color = A4L.TextHigh,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        WheelColumn(
            items = minutes,
            selectedIndex = minute.coerceIn(0, 59),
            onSelected = { onChange(hour, it) },
            modifier = Modifier.weight(1f),
        )
    }
}
