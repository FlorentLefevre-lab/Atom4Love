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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.astroport.atom4love.R
import one.astroport.atom4love.nostr.Constellation
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint
import kotlin.math.PI
import kotlin.math.hypot

/**
 * Le trait de côte du monde, en projection équirectangulaire.
 *
 * L'espace de travail va de 0 à 2 en abscisse et de 0 à 1 en ordonnée — deux
 * fois plus large que haut, comme la projection elle-même. Une seule échelle
 * suffit alors pour les deux axes : un cercle y reste un cercle à tous les
 * niveaux de zoom.
 */
private object WorldOutline {

    /** Une terre par entrée, trous compris, en coordonnées de travail. */
    @Volatile
    private var cached: List<Path>? = null

    fun paths(resources: Resources): List<Path> = cached ?: synchronized(this) {
        cached ?: load(resources).also { cached = it }
    }

    /**
     * `res/raw/world_land.txt` — Natural Earth 50 m, domaine public, simplifié
     * à 0,05° (soit un demi-point à l'écran au zoom maximal).
     *
     * Une **terre** par ligne ; ses anneaux séparés par `;`, l'extérieur puis
     * ses trous ; chaque anneau une suite de `Δlon,Δlat` en **centièmes de
     * degré**, le premier couple absolu. Le pas de la grille fait donc 1,1 km —
     * la version d'avant stockait au dixième de degré, soit 11 km, et c'est ce
     * qui donnait aux côtes leur allure d'escalier.
     *
     * Les anneaux qui franchissaient l'antiméridien ont été coupés à la
     * fabrication, et ceux qui enveloppent un pôle refermés par ce pôle : sans
     * ça l'Antarctique se serait rempli en travers de la carte.
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
                            val x = (lonHundredths / 100f + 180f) / 180f
                            val y = (90f - latHundredths / 100f) / 180f
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
 * La carte de la constellation : un point par noyau ayant activé sa clé LOVE,
 * posé à son adresse `a4l:` — donc à son **lieu de naissance**, au kilomètre.
 *
 * La couleur du point est sa phase φ, sur le cercle des teintes : c'est la même
 * lecture que `atomic_map.html`, où deux atomes de teinte voisine sont deux
 * ondes qui se répondent. Un noyau sans φ reste gris — il a bien activé sa clé,
 * son certificat ne dit simplement pas à quelle phase.
 *
 * La carte se déplace et se pince ; les points, eux, gardent leur taille, sans
 * quoi un zoom sur l'Europe ferait de chaque naissance une tache.
 */
@Composable
fun WorldMap(
    atoms: List<Constellation.Atom>,
    modifier: Modifier = Modifier,
    home: LatLon? = null,
    /**
     * Le calque des résidences déclarées — les 🏠 violets de sa carte. Elles
     * sont d'un autre ordre que les atomes : un lieu de naissance ne dit pas où
     * l'on dort, une résidence si. Elles se dessinent donc autrement, et ne se
     * sélectionnent pas.
     */
    homes: List<Constellation.Home> = emptyList(),
    selected: String? = null,
    onSelect: (String?) -> Unit = {},
    maxZoom: Float = 12f,
) {
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
    // Le violet des 🏠 d'`atomic_map.html` — rgba(180,100,255) chez lui.
    val residence = A4L.Violet
    val halo = A4L.TextHigh

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(RoundedCornerShape(MAP_RADIUS))
            .onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, drag, pinch, _ ->
                    val next = (zoom * pinch).coerceIn(1f, maxZoom)
                    val ratio = next / zoom
                    zoom = next
                    // Le point tenu sous les doigts y reste : sans cette
                    // correction, pincer sur l'Espagne y ramène le Groenland.
                    pan = clampPan((pan - centroid) * ratio + centroid + drag, box, next)
                }
            }
            .pointerInput(atoms, box, zoom, pan, selected) {
                detectTapGestures(
                    // Le pincement demande deux doigts ; le double appui, un
                    // seul. Au-delà du dernier cran, il ramène au monde entier
                    // — sinon on reste coincé sur un quartier.
                    onDoubleTap = { point ->
                        val next = if (zoom >= maxZoom - 0.01f) 1f else (zoom * 2.5f).coerceAtMost(maxZoom)
                        val ratio = next / zoom
                        zoom = next
                        pan = clampPan((pan - point) * ratio + point, box, next)
                    },
                    onTap = { tap ->
                        val reach = TAP_REACH.toPx()
                        val hit = atoms
                            .map { it to project(it.place.latDeg, it.place.lonDeg, box.height * zoom, pan) }
                            .filter { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) <= reach }
                            .minByOrNull { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) }
                            ?.first
                        onSelect(hit?.pubkey?.takeIf { it != selected })
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.height * zoom
            fun at(lat: Double, lon: Double) = project(lat, lon, k, pan)

            drawRect(ocean)

            // Les terres, peintes dans l'espace de travail : une seule
            // transformation pour douze cents contours. Pleines d'abord, puis
            // leur trait de côte — une silhouette se lit, un fil de fer non.
            withTransform({
                translate(pan.x, pan.y)
                scale(k, k, pivot = Offset.Zero)
            }) {
                val hairline = 1.dp.toPx() / k
                outline.forEach { path ->
                    drawPath(path, land)
                    drawPath(path, coast, style = Stroke(width = hairline))
                }
            }

            // Le quadrillage : méridiens et parallèles tous les 30°.
            for (lon in -180..180 step 30) {
                drawLine(grid, at(90.0, lon.toDouble()), at(-90.0, lon.toDouble()), 1f)
            }
            for (lat in -90..90 step 30) {
                drawLine(grid, at(lat.toDouble(), -180.0), at(lat.toDouble(), 180.0), 1f)
            }

            // Là où l'on se tient — un anneau, pas un point : ce n'est pas un
            // noyau de la constellation, c'est le point de vue.
            home?.let {
                val p = at(it.lat, it.lon)
                drawCircle(homeColor.copy(alpha = 0.18f), HOME_HALO.toPx(), p)
                drawCircle(homeColor, HOME_RING.toPx(), p, style = Stroke(width = 1.5.dp.toPx()))
            }

            // Les résidences sous les atomes : ce sont des repères, pas des
            // noyaux — un carré, pour qu'on ne les confonde à aucun zoom.
            homes.forEach { house ->
                val p = at(house.latDeg, house.lonDeg)
                if (p.x < -ATOM_MARGIN || p.x > size.width + ATOM_MARGIN) return@forEach
                if (p.y < -ATOM_MARGIN || p.y > size.height + ATOM_MARGIN) return@forEach
                val side = HOUSE_SIDE.toPx()
                drawRect(
                    color = residence.copy(alpha = 0.75f),
                    topLeft = Offset(p.x - side / 2f, p.y - side / 2f),
                    size = androidx.compose.ui.geometry.Size(side, side),
                )
            }

            atoms.forEach { atom ->
                val p = at(atom.place.latDeg, atom.place.lonDeg)
                if (p.x < -ATOM_MARGIN || p.x > size.width + ATOM_MARGIN) return@forEach
                if (p.y < -ATOM_MARGIN || p.y > size.height + ATOM_MARGIN) return@forEach
                val color = atom.phase?.let { phaseColor(it) } ?: phaseless
                drawCircle(color.copy(alpha = 0.22f), ATOM_GLOW.toPx(), p)
                drawCircle(color, ATOM_DOT.toPx(), p)
                if (atom.pubkey == selected) {
                    drawCircle(halo, SELECTED_RING.toPx(), p, style = Stroke(width = 1.5.dp.toPx()))
                }
            }
        }

        // Les deux crans, en bas à droite. Le pincement demande deux doigts et
        // une main libre ; ces boutons-là marchent d'un pouce, sur un téléphone
        // qu'on tient.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                // Le coin de la carte est arrondi à 16 dp : moins de marge et
                // il mord le bas du bouton du dessous.
                .padding(end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ZoomStep("＋", enabled = zoom < maxZoom - 0.01f) {
                val next = (zoom * ZOOM_STEP).coerceAtMost(maxZoom)
                val centre = Offset(box.width / 2f, box.height / 2f)
                pan = clampPan((pan - centre) * (next / zoom) + centre, box, next)
                zoom = next
            }
            ZoomStep("−", enabled = zoom > 1.01f) {
                val next = (zoom / ZOOM_STEP).coerceAtLeast(1f)
                val centre = Offset(box.width / 2f, box.height / 2f)
                pan = clampPan((pan - centre) * (next / zoom) + centre, box, next)
                zoom = next
            }
        }
    }
}

/** Un cran de zoom — même pastille que les poignées de l'en-tête. */
@Composable
private fun ZoomStep(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            // Un liseré cyan plutôt qu'un fond : le fond de la carte est
            // presque noir la nuit et presque blanc le jour, aucune teinte
            // unie n'y ressort des deux côtés. Le trait, si — et c'est celui
            // des côtes, il est chez lui ici.
            .background(A4L.Glass)
            .border(1.dp, A4L.Cyan.tint(0.38f), CircleShape)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = A4LText.Data.copy(fontSize = 15.sp), color = A4L.Cyan)
    }
}

/** Degrés → pixels. [k] est la hauteur du monde à l'échelle courante. */
private fun project(lat: Double, lon: Double, k: Float, pan: Offset) = Offset(
    ((lon + 180.0) / 180.0).toFloat() * k + pan.x,
    ((90.0 - lat) / 180.0).toFloat() * k + pan.y,
)

/** Le monde ne se traîne pas hors de sa fenêtre. */
private fun clampPan(candidate: Offset, box: Size, zoom: Float): Offset {
    val k = box.height * zoom
    return Offset(
        candidate.x.coerceIn(minOf(box.width - 2f * k, 0f), 0f),
        candidate.y.coerceIn(minOf(box.height - k, 0f), 0f),
    )
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

private val MAP_RADIUS: Dp = 16.dp
private val TAP_REACH: Dp = 18.dp
private val ATOM_DOT: Dp = 2.6.dp
private val ATOM_GLOW: Dp = 6.5.dp
private val SELECTED_RING: Dp = 9.dp
private val HOME_RING: Dp = 5.dp
private val HOME_HALO: Dp = 11.dp
private val HOUSE_SIDE: Dp = 5.dp

/** Une marge en pixels bruts : un point juste hors cadre garde son halo. */
private const val ATOM_MARGIN = 16f

/** Un cran de zoom. Assez franc pour qu'on sente le geste, assez doux pour viser. */
private const val ZOOM_STEP = 1.8f
