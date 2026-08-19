package one.astroport.atom4love.ui.components

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import one.astroport.atom4love.R
import one.astroport.atom4love.nostr.Constellation
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * Le trait de côte embarqué — le fond qui ne demande rien à personne.
 *
 * Il est stocké en degrés et projeté en Mercator au chargement : c'est la même
 * projection que les tuiles, sans quoi les deux fonds ne se superposeraient pas.
 */
private object WorldOutline {

    @Volatile
    private var cached: List<Path>? = null

    fun paths(resources: Resources): List<Path> = cached ?: synchronized(this) {
        cached ?: load(resources).also { cached = it }
    }

    /**
     * `res/raw/world_land.txt` — Natural Earth 50 m, domaine public, simplifié
     * à 0,05°. Une **terre** par ligne ; ses anneaux séparés par `;`, l'extérieur
     * puis ses trous ; chaque anneau une suite de `Δlon,Δlat` en centièmes de
     * degré, le premier couple absolu.
     *
     * Les anneaux qui franchissaient l'antiméridien ont été coupés à la
     * fabrication, et ceux qui enveloppent un pôle refermés par ce pôle.
     */
    private fun load(resources: Resources): List<Path> =
        resources.openRawResource(R.raw.world_land).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val path = Path()
                // Pair-impair : un trou percé dans la terre qui le contient —
                // la Caspienne reste de l'eau.
                path.fillType = PathFillType.EvenOdd
                var empty = true
                line.split(';').forEach { ring ->
                    var lonHundredths = 0
                    var latHundredths = 0
                    var first = true
                    ring.split(' ').forEach { token ->
                        val comma = token.indexOf(',')
                        if (comma > 0) {
                            lonHundredths += token.substring(0, comma).toInt()
                            latHundredths += token.substring(comma + 1).toInt()
                            val x = Mercator.x(lonHundredths / 100.0).toFloat()
                            val y = Mercator.y(latHundredths / 100.0).toFloat()
                            if (first) {
                                path.moveTo(x, y)
                                first = false
                                empty = false
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                    }
                    if (!first) path.close()
                }
                path.takeUnless { empty }
            }.toList()
        }
}

/** Un point du globe — celui d'où l'on regarde. */
data class LatLon(val lat: Double, val lon: Double)

/**
 * Où la carte doit se porter, et de combien s'en approcher.
 *
 * [request] est un numéro d'ordre, pas un booléen : deux demandes de suite vers
 * le **même** point doivent toutes les deux partir — on peut vouloir revenir où
 * l'on était après avoir traîné la carte ailleurs.
 *
 * [minScale] est un plancher, pas une consigne : si l'on est déjà plus près, on
 * ne recule pas. Se porter sur quelqu'un ne doit pas faire perdre le détail
 * qu'on était en train de regarder.
 */
data class MapFocus(val place: LatLon, val minScale: Float, val request: Int)

/**
 * **La fenêtre du monde, en bêta : l'Europe et rien d'autre.**
 *
 * ⚠ Ce n'est pas une préférence d'affichage, c'est une **borne**. La carte
 * s'ouvre dessus, ne se dézoome pas au-delà, et ne se traîne pas hors de ses
 * bords. On peut toujours **entrer** — jusqu'à la rue, comme avant.
 *
 * Pourquoi borner ce qu'on savait déjà montrer en entier : parce qu'une carte du
 * monde ouverte sur le monde ne montre rien. Les certificats de la constellation
 * se comptent aujourd'hui en dizaines, tous européens ; à l'échelle du globe, ils
 * tiennent dans un demi-centimètre carré, et le premier geste de quiconque
 * l'ouvre est de zoomer là où il vit. On lui épargne ce geste, et on lui épargne
 * surtout la question qu'il se pose entre-temps — « est-ce que c'est vide, ou
 * est-ce que je regarde au mauvais endroit ? ».
 *
 * Les bornes sont larges à dessein : de l'Atlantique à l'Oural, de la
 * Méditerranée au cap Nord. Elles ne coupent aucun pays du continent, et la
 * seule chose qu'elles retirent est l'océan.
 *
 * ⚠ **Ça ne cache rien.** Un atome hors de ces bornes est toujours lu, compté,
 * classé et présent dans la liste des résonances — il n'est simplement pas
 * dessiné. Le jour où la constellation déborde du continent, il n'y a que ces
 * quatre nombres à retirer.
 */
private object Frame {
    const val WEST = -12.0
    const val EAST = 42.0
    const val SOUTH = 33.0
    const val NORTH = 71.5

    /** Largeur et hauteur de la fenêtre, en unités Mercator (le monde vaut 1). */
    val width: Float = (Mercator.x(EAST) - Mercator.x(WEST)).toFloat()
    val height: Float = (Mercator.y(SOUTH) - Mercator.y(NORTH)).toFloat()

    val centre = LatLon(
        lat = Mercator.lat((Mercator.y(NORTH) + Mercator.y(SOUTH)) / 2.0),
        lon = (WEST + EAST) / 2.0,
    )

    /**
     * L'échelle qui fait entrer la fenêtre dans la vue — **la plus grande des
     * deux contraintes**, pas la plus petite.
     *
     * `scale` compte combien de fois la largeur de la vue le monde occupe. Pour
     * que la fenêtre remplisse la largeur, il faut `1 / width` ; pour qu'elle
     * remplisse la hauteur, `ratio / height`. On prend le **maximum** : la vue
     * est alors entièrement couverte par la fenêtre, quitte à en rogner un bord.
     * Prendre le minimum aurait laissé de l'océan sur les côtés — c'est-à-dire
     * une zone qu'on interdit de traîner et qui n'a rien à montrer.
     */
    fun fitScale(ratio: Float): Float = maxOf(1f / width, ratio / height)
}

/**
 * La carte de la constellation, en Web Mercator.
 *
 * Le fond se choisit ([Basemap]) : le trait de côte embarqué, qui ne parle à
 * personne, ou des tuiles, qui donnent la rue mais montrent à un tiers ce qu'on
 * regarde. Le trait de côte reste peint dessous en toutes circonstances — une
 * tuile qui n'arrive pas ne laisse jamais un rectangle vide.
 *
 * Les atomes qui tombent dans la **même maille** ne se sépareront à aucun zoom :
 * l'adresse `a4l:` fait 1 km et c'est tout ce que le certificat dit. Ils sont
 * donc réunis en un point qui porte leur nombre, et l'appui passe de l'un à
 * l'autre.
 */
@Composable
fun WorldMap(
    atoms: List<Constellation.Atom>,
    modifier: Modifier = Modifier,
    home: LatLon? = null,
    selected: String? = null,
    onSelect: (String?) -> Unit = {},
    basemap: Basemap = Basemap.Coastline,
    /** Où se porter, quand l'écran le demande — voir [MapFocus]. */
    focus: MapFocus? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // Vingt et un mille points à découper : hors du fil principal, sinon la
    // première ouverture de la carte se paie d'un à-coup.
    val outline by produceState(initialValue = emptyList<Path>(), resources) {
        value = withContext(Dispatchers.Default) { WorldOutline.paths(resources) }
    }

    val ocean = A4L.Ink
    val land = A4L.Cyan.copy(alpha = 0.11f)
    val coast = A4L.Cyan.copy(alpha = 0.42f)
    val grid = A4L.Stroke.copy(alpha = 0.16f)
    val phaseless = A4L.TextFaint
    val homeColor = A4L.Mint
    val halo = A4L.TextHigh
    val countInk = A4L.Deep
    val dark = A4L.IsDark

    // `scale` = combien de fois la largeur de la fenêtre le monde occupe.
    // 1 = le monde tient dans la largeur ; 2^14 ≈ la rue.
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Unspecified) }
    var box by remember { mutableStateOf(Size.Zero) }

    val tiles = remember { mutableStateMapOf<TileKey, ImageBitmap>() }
    val loader = remember(context) { SingletonImageLoader.get(context) }

    fun worldPx(s: Float = scale) = box.width * s

    /**
     * Le monde ne se traîne pas hors de **la fenêtre imposée** — voir [Frame].
     *
     * ⚠ Il bornait le monde entier : `pan` restait dans `[box − worldPx, 0]`.
     * Les bornes sont maintenant celles du continent, exprimées en pixels de la
     * vue courante. `minOf(…, max)` n'est pas décoratif : quand la fenêtre est
     * plus petite que la vue — impossible à l'échelle plancher, possible si un
     * appareil très large arrivait —, l'intervalle s'inverserait et `coerceIn`
     * lèverait. On garde alors le bord haut/gauche.
     */
    fun clamp(candidate: Offset, s: Float): Offset {
        val w = worldPx(s)
        val left = -Mercator.x(Frame.WEST).toFloat() * w
        val top = -Mercator.y(Frame.NORTH).toFloat() * w
        val right = box.width - Mercator.x(Frame.EAST).toFloat() * w
        val bottom = box.height - Mercator.y(Frame.SOUTH).toFloat() * w
        return Offset(
            candidate.x.coerceIn(minOf(right, left), left),
            candidate.y.coerceIn(minOf(bottom, top), top),
        )
    }

    fun centreOn(place: LatLon, s: Float): Offset {
        val w = worldPx(s)
        return clamp(
            Offset(
                box.width / 2f - Mercator.x(place.lon).toFloat() * w,
                box.height / 2f - Mercator.y(place.lat).toFloat() * w,
            ),
            s,
        )
    }

    fun at(lat: Double, lon: Double): Offset {
        val w = worldPx()
        val p = if (pan.isSpecified) pan else Offset.Zero
        return Offset(Mercator.x(lon).toFloat() * w + p.x, Mercator.y(lat).toFloat() * w + p.y)
    }

    /**
     * Le plancher : on ne recule pas plus loin que l'Europe.
     *
     * Il dépend de la vue, donc il ne peut pas être une constante — une tablette
     * et un téléphone n'ont pas le même rapport, et la même fenêtre géographique
     * n'y demande pas la même échelle. Zéro tant que la vue n'est pas mesurée :
     * `coerceIn(0f, MAX)` reste alors licite, et le premier cadrage arrive dès
     * que `box` est connue.
     */
    val minScale = if (box.width <= 0f) 1f else Frame.fitScale(box.height / box.width)

    // ⚠ **Premier cadrage : l'Europe, centrée** — c'était le monde entier,
    // centré sur l'équateur. La clé de l'effet est `box` et non `Unit` : la vue
    // se mesure après la première composition, et un cadrage calculé sur une
    // taille nulle plaçait la carte n'importe où.
    LaunchedEffect(box) {
        if (box.width > 0f && !pan.isSpecified) {
            scale = minScale
            pan = centreOn(Frame.centre, minScale)
        }
    }

    // Se porter quelque part : sur soi, ou sur quelqu'un qu'on vient de toucher
    // dans la liste. Le plancher d'échelle évite de reculer quand on est déjà
    // plus près que demandé.
    LaunchedEffect(focus?.request) {
        val target = focus ?: return@LaunchedEffect
        if (target.request == 0 || box.width <= 0f) return@LaunchedEffect
        val s = maxOf(scale, target.minScale).coerceAtMost(MAX_SCALE)
        scale = s
        pan = centreOn(target.place, s)
    }

    // ── Les tuiles visibles ───────────────────────────────────────────────
    val level = if (box.width <= 0f) 0 else {
        log2(worldPx() / TILE_PX).roundToInt().coerceIn(0, basemap.maxTileZoom)
    }
    LaunchedEffect(basemap, level, pan, box, scale) {
        if (!basemap.hasTiles || box.width <= 0f || !pan.isSpecified) return@LaunchedEffect
        val n = 1 shl level
        val tilePx = worldPx() / n
        val fromX = floor((-pan.x) / tilePx).toInt() - 1
        val toX = floor((box.width - pan.x) / tilePx).toInt() + 1
        val fromY = floor((-pan.y) / tilePx).toInt() - 1
        val toY = floor((box.height - pan.y) / tilePx).toInt() + 1
        for (ty in fromY..toY) {
            if (ty < 0 || ty >= n) continue
            for (tx in fromX..toX) {
                // Le monde reboucle en longitude, jamais en latitude.
                val wrapped = ((tx % n) + n) % n
                val key = TileKey(basemap, level, wrapped, ty)
                if (tiles.containsKey(key)) continue
                loadTile(context, loader, key)?.let { tiles[key] = it }
            }
        }
    }

    // Les mailles qui portent plusieurs noyaux : un point, un nombre.
    val clusters = remember(atoms) {
        atoms.groupBy { it.place.q to it.place.r }.values.toList()
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(MAP_RATIO)
            .clip(RoundedCornerShape(MAP_RADIUS))
            .onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
            // ⚠ **Le déplacement du fond a été retiré, le pincement borné.**
            //
            // Ce sont deux décisions distinctes, et la seconde découle de la
            // première. Borner le dézoom sans retirer le déplacement aurait
            // laissé traîner la carte d'un bord à l'autre du continent à
            // l'échelle plancher — c'est-à-dire glisser vers un océan vide sans
            // pouvoir reculer pour se retrouver. Les deux ensemble donnent une
            // fenêtre stable : on entre, on ressort, on est toujours au même
            // endroit.
            //
            // ⚠ `drag` n'est plus lu, mais `detectTransformGestures` reste : lui
            // seul donne le centroïde d'un pincement, et pincer doit continuer
            // de zoomer là où sont les deux doigts. Un `detectTransformGestures`
            // qui ignore la translation n'est pas un geste mort — c'est un geste
            // qui n'en fait qu'un.
            .pointerInput(box) {
                detectTransformGestures { centroid, _, pinch, _ ->
                    val floor = Frame.fitScale(box.height / box.width)
                    val next = (scale * pinch).coerceIn(floor, MAX_SCALE)
                    val ratio = next / scale
                    scale = next
                    val p = if (pan.isSpecified) pan else Offset.Zero
                    pan = clamp((p - centroid) * ratio + centroid, next)
                }
            }
            .pointerInput(clusters, box, scale, pan, selected) {
                detectTapGestures(
                    onDoubleTap = { point ->
                        val next = (scale * 2f).coerceAtMost(MAX_SCALE)
                        val ratio = next / scale
                        val p = if (pan.isSpecified) pan else Offset.Zero
                        pan = clamp((p - point) * ratio + point, next)
                        scale = next
                    },
                    onTap = { tap ->
                        val reach = TAP_REACH.toPx()
                        val hit = clusters
                            .map { it to at(it[0].place.latDeg, it[0].place.lonDeg) }
                            .filter { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) <= reach }
                            .minByOrNull { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) }
                            ?.first
                        if (hit == null) {
                            onSelect(null)
                        } else {
                            // Une maille peut en porter plusieurs, et aucun zoom
                            // ne les séparera : l'appui passe au suivant.
                            val here = hit.indexOfFirst { it.pubkey == selected }
                            onSelect(hit[(here + 1) % hit.size].pubkey)
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = worldPx()
            val p = if (pan.isSpecified) pan else Offset.Zero

            drawRect(ocean)

            // Le fond embarqué, toujours peint : une tuile qui manque laisse
            // voir la côte, jamais un trou.
            withTransform({
                translate(p.x, p.y)
                scale(w, w, pivot = Offset.Zero)
            }) {
                val hairline = 1.dp.toPx() / w
                outline.forEach { path ->
                    drawPath(path, land)
                    drawPath(path, coast, style = Stroke(width = hairline))
                }
            }

            // Les tuiles par-dessus, quand il y en a.
            if (basemap.hasTiles) {
                val n = 1 shl level
                val tilePx = w / n
                val filter = if (dark && basemap.invertsInDark) darkTileFilter() else null
                val fromX = floor((-p.x) / tilePx).toInt() - 1
                val toX = floor((size.width - p.x) / tilePx).toInt() + 1
                val fromY = floor((-p.y) / tilePx).toInt() - 1
                val toY = floor((size.height - p.y) / tilePx).toInt() + 1
                for (ty in fromY..toY) {
                    if (ty < 0 || ty >= n) continue
                    for (tx in fromX..toX) {
                        val wrapped = ((tx % n) + n) % n
                        val bitmap = tiles[TileKey(basemap, level, wrapped, ty)] ?: continue
                        // Un pixel de recouvrement : sans lui, l'arrondi laisse
                        // une couture claire entre deux tuiles.
                        val side = tilePx.roundToInt() + 1
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(
                                (tx * tilePx + p.x).roundToInt(),
                                (ty * tilePx + p.y).roundToInt(),
                            ),
                            dstSize = IntSize(side, side),
                            colorFilter = filter,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
            }

            // Le quadrillage : méridiens et parallèles tous les 30°.
            for (lon in -180..180 step 30) {
                drawLine(grid, at(Mercator.MAX_LATITUDE, lon.toDouble()), at(-Mercator.MAX_LATITUDE, lon.toDouble()), 1f)
            }
            for (lat in -60..60 step 30) {
                drawLine(grid, at(lat.toDouble(), -180.0), at(lat.toDouble(), 180.0), 1f)
            }

            home?.let {
                val q = at(it.lat, it.lon)
                drawCircle(homeColor.copy(alpha = 0.18f), HOME_HALO.toPx(), q)
                drawCircle(homeColor, HOME_RING.toPx(), q, style = Stroke(width = 1.5.dp.toPx()))
            }

            clusters.forEach { group ->
                val first = group[0]
                val q = at(first.place.latDeg, first.place.lonDeg)
                if (offScreen(q, size)) return@forEach
                val shown = group.firstOrNull { it.pubkey == selected } ?: first
                val color = shown.phase?.let { phaseColor(it) } ?: phaseless
                drawCircle(color.copy(alpha = 0.22f), ATOM_GLOW.toPx(), q)
                drawCircle(color, ATOM_DOT.toPx(), q)
                if (group.any { it.pubkey == selected }) {
                    drawCircle(halo, SELECTED_RING.toPx(), q, style = Stroke(width = 1.5.dp.toPx()))
                }
                if (group.size > 1) {
                    // Le nombre, dans une pastille : deux naissances dans le même
                    // kilomètre carré ne se sépareront jamais, autant le dire.
                    val badge = Offset(q.x + BADGE_OFFSET.toPx(), q.y - BADGE_OFFSET.toPx())
                    drawCircle(color, BADGE_RADIUS.toPx(), badge)
                    drawContext.canvas.nativeCanvas.drawText(
                        group.size.toString(),
                        badge.x,
                        badge.y + BADGE_TEXT.toPx() * 0.35f,
                        badgePaint.apply {
                            textSize = BADGE_TEXT.toPx()
                            setColor(countInk.toArgb())
                        },
                    )
                }
            }
        }

        basemap.attribution?.let { credit ->
            Text(
                credit,
                style = A4LText.Data.copy(fontSize = 8.5.sp),
                color = A4L.TextDim,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(A4L.Deep.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                // Le coin de la carte est arrondi à 16 dp : moins de marge et
                // il mord le bas du bouton du dessous.
                .padding(end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapButton("＋", enabled = scale < MAX_SCALE - 0.01f) {
                val next = (scale * 2f).coerceAtMost(MAX_SCALE)
                val centre = Offset(box.width / 2f, box.height / 2f)
                val p = if (pan.isSpecified) pan else Offset.Zero
                pan = clamp((p - centre) * (next / scale) + centre, next)
                scale = next
            }
            // Il s'arrête à l'Europe, et s'éteint là : un bouton qui reste
            // allumé sans plus rien faire laisse croire que la carte est bloquée.
            MapButton("−", enabled = scale > minScale * 1.01f) {
                val next = (scale / 2f).coerceAtLeast(minScale)
                val centre = Offset(box.width / 2f, box.height / 2f)
                val p = if (pan.isSpecified) pan else Offset.Zero
                pan = clamp((p - centre) * (next / scale) + centre, next)
                scale = next
            }
        }
    }
}

/** Une seule instance : `Paint` s'alloue cher, et `draw` passe soixante fois par seconde. */
private val badgePaint = android.graphics.Paint().apply {
    textAlign = android.graphics.Paint.Align.CENTER
    isAntiAlias = true
    isFakeBoldText = true
}

private fun offScreen(p: Offset, size: Size): Boolean =
    p.x < -ATOM_MARGIN || p.x > size.width + ATOM_MARGIN ||
        p.y < -ATOM_MARGIN || p.y > size.height + ATOM_MARGIN

/** Une commande de la carte — même pastille que les poignées de l'en-tête. */
@Composable
internal fun MapButton(glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            // Un liseré cyan plutôt qu'un fond : le fond de carte est presque
            // noir la nuit et presque blanc le jour, aucune teinte unie n'y
            // ressort des deux côtés. Le trait, si.
            .background(A4L.Glass)
            .border(1.dp, A4L.Cyan.tint(0.38f), CircleShape)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = A4LText.Data.copy(fontSize = 15.sp), color = A4L.Cyan)
    }
}

/** Le choix du fond, en trois puces. */
@Composable
fun BasemapPicker(current: Basemap, onSelect: (Basemap) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Basemap.entries.forEach { entry ->
            A4LChip(
                label = stringResource(entry.labelRes),
                selected = entry == current,
                accent = A4L.Cyan,
                modifier = Modifier.clickable { onSelect(entry) },
            )
        }
    }
}

/**
 * La teinte d'une phase — `_phiHsl()` de `atomic_map.html` : φ parcourt le
 * cercle des teintes, saturation 72 %, clarté 54 %. Deux teintes voisines sont
 * deux ondes proches ; l'écart de couleur **est** l'écart de phase.
 */
fun phaseColor(phase: Double): Color {
    val hue = ((phase / (2.0 * PI) * 360.0) % 360.0 + 360.0) % 360.0
    return Color.hsl(hue.toFloat(), 0.72f, 0.54f)
}

/**
 * **Carrée — et c'est la limite, pas un choix d'esthète.**
 *
 * En Mercator le monde entier est un carré : au repos la carte occupe toute sa
 * largeur, donc à 1:1 elle le montre en entier, des ±85° d'un bord à l'autre.
 * Aller plus haut ne montrerait rien de plus — il n'y a plus rien à montrer —
 * et laisserait deux bandes vides au-dessus et en dessous, que le calage de
 * [clampPan] ne saurait même pas remplir.
 *
 * Le premier jet était à 2:1 et n'en donnait que la bande ±66°.
 */
private const val MAP_RATIO = 1f

private val MAP_RADIUS: Dp = 16.dp
private val TAP_REACH: Dp = 18.dp
private val ATOM_DOT: Dp = 2.6.dp
private val ATOM_GLOW: Dp = 6.5.dp
private val SELECTED_RING: Dp = 9.dp
private val HOME_RING: Dp = 5.dp
private val HOME_HALO: Dp = 11.dp
private val BADGE_RADIUS: Dp = 6.5.dp
private val BADGE_OFFSET: Dp = 5.dp
private val BADGE_TEXT: Dp = 8.5.dp

/** Une marge en pixels bruts : un point juste hors cadre garde son halo. */
private const val ATOM_MARGIN = 16f

/** Le côté d'une tuile, en pixels — 256 chez tous les fournisseurs. */
private const val TILE_PX = 256f

/** Le monde tient 2^14 fois la largeur de la fenêtre : de quoi lire une rue. */
private val MAX_SCALE = 2f.pow(14)

/** Où « me recentrer » s'arrête : la ville, pas la rue. */
val RECENTRE_SCALE = 2f.pow(9)

/**
 * Où l'on s'arrête en se portant sur quelqu'un de la liste : la région.
 *
 * Plus près qu'un pays, plus loin qu'une ville — on veut voir **où** il est, pas
 * chez qui. Et comme c'est un plancher, quelqu'un qui explorait déjà une rue y
 * reste.
 */
val CONTACT_SCALE = 2f.pow(5)
