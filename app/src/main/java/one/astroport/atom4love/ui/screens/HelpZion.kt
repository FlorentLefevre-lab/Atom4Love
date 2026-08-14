package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/**
 * 05c · Zion — les mécanismes de la station, tels que Made In Zion les publie.
 *
 * Le troisième onglet de l'aide ne dit pas ce que fait cette application : il
 * dit le monde dans lequel elle travaille. Sphère de Goldberg, Tzolkin, corps
 * comme antenne, forge des clés — quatre sujets qui expliquent les nombres
 * qu'on voit passer partout ailleurs (φ, k, le KIN, 429,62 Hz, le npub).
 *
 * **Tout vient de `u.copylaradio.com/earth/miz.html`**, textes ET planches. Les
 * onze SVG sont ceux de la page, repris tels quels dans `assets/miz/` par
 * `tools/extract_miz_svg.py` — pas redessinés : une planche redessinée dirait
 * ce que nous avons compris, celle-ci dit ce que Fred a écrit. Le script leur
 * retire seulement leur fond noir et rouvre leur fenêtre là où elle rognait.
 *
 * Chaque planche s'ouvre en grand d'un toucher : à la largeur d'un téléphone,
 * un schéma de 800 unités portant 28 étiquettes se regarde, il ne se lit pas.
 */
@Composable
internal fun ZionTab() {
    // Un chargeur à part : le singleton de Coil ne connaît pas le SVG, et le
    // projet n'a pas de classe Application où l'enseigner. Onze petites
    // planches locales — le cache par défaut suffit largement.
    val context = LocalContext.current.applicationContext
    val loader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // Les proportions se lisent dans les fichiers, elles ne sont pas recopiées
    // ici : le script rouvre les fenêtres à ce que chaque planche dessine, et
    // deux nombres tenus à la main auraient vieilli dès la première reprise.
    val ratios = remember(context) {
        PLATES.associateWith { asset ->
            runCatching {
                val head = context.assets.open("miz/$asset.svg").use { it.readBytes() }
                    .decodeToString()
                val box = VIEW_BOX.find(head)!!.groupValues[1].trim().split(Regex("\\s+"))
                box[2].toFloat() / box[3].toFloat()
            }.getOrDefault(DEFAULT_RATIO)
        }
    }

    // La planche ouverte en grand, s'il y en a une.
    var zoomed by remember { mutableStateOf<Zoomed?>(null) }
    zoomed?.let { open ->
        MizViewer(
            asset = open.asset,
            ratio = ratios[open.asset] ?: DEFAULT_RATIO,
            description = open.description,
            loader = loader,
            onClose = { zoomed = null },
        )
    }

    val show: (String, String) -> Unit = { asset, description ->
        zoomed = Zoomed(asset, description)
    }

    ZionGroup(
        title = stringResource(R.string.zion_group_goldberg),
        intro = stringResource(R.string.zion_goldberg_intro),
    ) {
        MizFigure(
            "goldberg_sphere", ratios, loader, show,
            R.string.zion_sphere_label, R.string.zion_sphere_body,
        )
        MizFigure(
            "goldberg_phase", ratios, loader, show,
            R.string.zion_phase_label, R.string.zion_phase_body,
        )
        MizFigure(
            "goldberg_precession", ratios, loader, show,
            R.string.zion_precession_label, R.string.zion_precession_body,
        )
        MizFigure(
            "goldberg_rendezvous", ratios, loader, show,
            R.string.zion_rendezvous_label, R.string.zion_rendezvous_body,
        )
        MizFrieze(
            "goldberg_trust_path", ratios, loader, show,
            R.string.zion_trust_path_caption, height = 120.dp,
        )
        HelpPanel(
            listOf(
                answer(R.string.zion_antiscraping_label, R.string.zion_antiscraping_body),
                answer(R.string.zion_cooperative_label, R.string.zion_cooperative_body),
                answer(R.string.zion_binaural_anchor_label, R.string.zion_binaural_anchor_body),
            ),
        )
    }

    ZionGroup(
        title = stringResource(R.string.zion_group_maya),
        intro = stringResource(R.string.zion_maya_intro),
    ) {
        MizFigure(
            "tzolkin_cycle", ratios, loader, show,
            R.string.zion_tzolkin_label, R.string.zion_tzolkin_body,
        )
        MizFigure(
            "tzolkin_oracle", ratios, loader, show,
            R.string.zion_oracle_label, R.string.zion_oracle_body,
        )
        HelpPanel(
            listOf(
                answer(R.string.zion_maya_universal_label, R.string.zion_maya_universal_body),
                answer(R.string.zion_maya_routing_label, R.string.zion_maya_routing_body),
                answer(R.string.zion_maya_filter_label, R.string.zion_maya_filter_body),
                answer(R.string.zion_maya_moons_label, R.string.zion_maya_moons_body),
                answer(R.string.zion_maya_tone_label, R.string.zion_maya_tone_body),
            ),
        )
    }

    ZionGroup(
        title = stringResource(R.string.zion_group_body),
        intro = stringResource(R.string.zion_body_intro),
    ) {
        MizFigure(
            "body_water_antenna", ratios, loader, show,
            R.string.zion_water_label, R.string.zion_water_body,
        )
        MizFigure(
            "body_mudras", ratios, loader, show,
            R.string.zion_mudras_label, R.string.zion_mudras_body,
        )
        MizFrieze(
            "body_binaural", ratios, loader, show,
            R.string.zion_binaural_caption, height = 84.dp,
        )
        HelpPanel(
            listOf(
                answer(R.string.zion_water_freq_label, R.string.zion_water_freq_body),
                answer(R.string.zion_mudra_kin_label, R.string.zion_mudra_kin_body),
                answer(R.string.zion_cabin33_label, R.string.zion_cabin33_body),
            ),
        )
    }

    ZionGroup(
        title = stringResource(R.string.zion_group_forge),
        intro = stringResource(R.string.zion_forge_intro),
    ) {
        // La plus large des planches, et la plus dense : 28 étiquettes sur
        // 800 unités. Réduite à la largeur d'un téléphone elle ne serait plus
        // qu'une frise grise — elle se parcourt du doigt, et s'ouvre en grand.
        MizFrieze(
            "forge_pipeline", ratios, loader, show,
            R.string.zion_forge_caption, height = 210.dp,
        )
        HelpPanel(
            listOf(
                answer(R.string.zion_forge_pbkdf2_label, R.string.zion_forge_pbkdf2_body),
                answer(R.string.zion_forge_scrypt_label, R.string.zion_forge_scrypt_body),
                answer(R.string.zion_forge_nostr_label, R.string.zion_forge_nostr_body),
                answer(R.string.zion_forge_g1_label, R.string.zion_forge_g1_body),
            ),
        )
    }
}

/** La planche que l'on regarde en grand, et ce qu'elle montre. */
private data class Zoomed(val asset: String, val description: String)

private val PLATES = listOf(
    "goldberg_sphere", "goldberg_phase", "goldberg_precession", "goldberg_rendezvous",
    "goldberg_trust_path", "tzolkin_cycle", "tzolkin_oracle",
    "body_water_antenna", "body_mudras", "body_binaural", "forge_pipeline",
)

private val VIEW_BOX = Regex("""viewBox="([^"]+)"""")

/** Faute de pouvoir lire la fenêtre, une planche à peu près carrée. */
private const val DEFAULT_RATIO = 1.4f

private const val MAX_ZOOM = 12f
private const val DOUBLE_TAP_ZOOM = 4f

/**
 * Un sujet : son nom, la phrase qui l'ouvre, puis ses planches et ses réponses.
 *
 * Même titre que [HelpGroup] — c'est le même niveau de lecture — mais avec une
 * phrase d'accroche, que les thèmes de l'aide n'ont pas : ici on entre dans un
 * sujet qui ne parle pas de l'application, et il faut dire de quoi il parle.
 */
@Composable
private fun ZionGroup(title: String, intro: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            title.uppercase(),
            style = A4LText.SectionLabel.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                letterSpacing = 1.75.sp,
            ),
            color = A4L.TextMuted,
            modifier = Modifier.padding(start = 3.dp),
        )
        Text(
            intro,
            style = A4LText.Body,
            color = A4L.TextBody.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
        )
        content()
    }
}

/** Une planche et ce qu'elle montre, dans le verre du reste de l'aide. */
@Composable
private fun MizFigure(
    asset: String,
    ratios: Map<String, Float>,
    loader: ImageLoader,
    onOpen: (String, String) -> Unit,
    label: Int,
    body: Int,
) {
    val labelText = stringResource(label)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(14.dp)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        MizPlate(
            asset, loader, labelText,
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratios[asset] ?: DEFAULT_RATIO)
                .clickable { onOpen(asset, labelText) },
        )
        Text(labelText, style = A4LText.Body, color = A4L.TextHigh)
        Text(
            stringResource(body),
            style = A4LText.Body,
            color = A4L.TextBody.copy(alpha = 0.8f),
        )
    }
}

/**
 * Une planche trop large pour l'écran : hauteur imposée, largeur déduite, et
 * c'est le doigt qui la parcourt. La rétrécir à la largeur du téléphone
 * rendrait ses étiquettes illisibles, ce qui reviendrait à ne pas la montrer.
 */
@Composable
private fun MizFrieze(
    asset: String,
    ratios: Map<String, Float>,
    loader: ImageLoader,
    onOpen: (String, String) -> Unit,
    caption: Int,
    height: Dp,
) {
    val captionText = stringResource(caption)
    val ratio = ratios[asset] ?: DEFAULT_RATIO
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            MizPlate(
                asset, loader, captionText,
                Modifier
                    .height(height)
                    .width(height * ratio)
                    .clickable { onOpen(asset, captionText) },
            )
        }
        Text(
            captionText,
            style = A4LText.Body.copy(fontSize = 12.sp),
            color = A4L.TextMuted,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

/**
 * Le SVG lui-même, sans fond à lui.
 *
 * Le rectangle noir des planches web est retiré à l'extraction : ce qui se voit
 * derrière les traits est le verre de l'aide, dans la lumière du moment. Une
 * planche cesse ainsi d'être une vignette collée sur la page pour en devenir
 * une partie.
 */
@Composable
private fun MizPlate(
    asset: String,
    loader: ImageLoader,
    description: String,
    modifier: Modifier,
    /** Le visualiseur pose son propre fond de nuit : il garde la planche d'origine. */
    forceDark: Boolean = false,
) {
    val folder = if (forceDark || A4L.IsDark) "miz" else "miz/light"
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("file:///android_asset/$folder/$asset.svg")
            .build(),
        imageLoader = loader,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * La planche en grand : pincer pour approcher, glisser pour parcourir.
 *
 * Le fond est sombre et il ne suit pas le thème, à la différence de la planche
 * posée dans la page : ici il n'y a plus que le schéma à regarder, et ces
 * schémas ont été dessinés pour la nuit — plusieurs portent des étiquettes
 * blanches qui, sur un fond clair, ne se verraient plus.
 *
 * Le déplacement est borné à ce que l'agrandissement a fait déborder : sans
 * cela, un glissement de trop emporte la planche hors de l'écran et il ne
 * reste qu'un rectangle vide, sans moyen de la ramener.
 */
@Composable
private fun MizViewer(
    asset: String,
    ratio: Float,
    description: String,
    loader: ImageLoader,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var frame by remember { mutableStateOf(IntSize.Zero) }

        fun bound(next: Offset, atScale: Float): Offset {
            val slackX = (frame.width * (atScale - 1f) / 2f).coerceAtLeast(0f)
            val slackY = (frame.height * (atScale - 1f) / 2f).coerceAtLeast(0f)
            return Offset(
                next.x.coerceIn(-slackX, slackX),
                next.y.coerceIn(-slackY, slackY),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(VIEWER_BACKDROP)
                .onSizeChanged { frame = it }
                .pointerInput(asset) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        scale = next
                        offset = bound(offset + pan, next)
                    }
                }
                .pointerInput(asset) {
                    detectTapGestures(
                        onTap = { onClose() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = DOUBLE_TAP_ZOOM
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            MizPlate(
                asset, loader, description,
                forceDark = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            // Le toucher ferme déjà, mais rien ne le dit : la croix est là pour
            // qui n'essaie pas. Elle reste en place quand la planche s'agrandit.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f)) }
        }
    }
}

private val VIEWER_BACKDROP = Color(0xF205050C)
