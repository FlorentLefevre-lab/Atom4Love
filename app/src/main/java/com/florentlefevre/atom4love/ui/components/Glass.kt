package com.florentlefevre.atom4love.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.florentlefevre.atom4love.ui.theme.A4L
import com.florentlefevre.atom4love.ui.theme.A4LText
import com.florentlefevre.atom4love.ui.theme.tint

/**
 * Surface vitrée : un fond translucide + un liseré d'un blanc à peine visible.
 * C'est la brique de base de toutes les cartes de la maquette.
 */
fun Modifier.glass(
    radius: Dp,
    background: Color = A4L.GlassSoft,
    border: Color = A4L.StrokeSoft,
    borderWidth: Dp = 1.dp,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .background(background, shape)
        .border(borderWidth, border, shape)
}

/** Variante en pointillés — emplacement vide, indice contextuel. */
fun Modifier.dashedGlass(
    radius: Dp,
    background: Color = A4L.GlassFaint,
    border: Color = A4L.Stroke,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .background(background, shape)
        .drawBehind {
            drawRoundRect(
                color = border,
                cornerRadius = CornerRadius(radius.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                    ),
                ),
            )
        }
}

/**
 * Fond d'écran : un halo radial posé au-dessus du noir de la station.
 * `centerY` est exprimé en fraction de la hauteur, comme le `at 50% 0%` du CSS.
 */
fun Modifier.screenBackground(
    glow: Color,
    base: Color = A4L.Deep,
    centerY: Float = 0f,
    radiusFactor: Float = 1.35f,
): Modifier = drawBehind {
    drawRect(base)
    drawRect(
        Brush.radialGradient(
            colors = listOf(glow, base),
            center = Offset(size.width / 2f, size.height * centerY),
            radius = size.width * radiusFactor,
        ),
    )
}

/** Tracé d'un hexagone pointe en haut, inscrit dans [size]. */
fun hexagonPath(size: Size): Path = Path().apply {
    moveTo(size.width * 0.5f, 0f)
    lineTo(size.width, size.height * 0.25f)
    lineTo(size.width, size.height * 0.75f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.75f)
    lineTo(0f, size.height * 0.25f)
    close()
}

/** Hexagone pointe en haut — la maille du polyèdre de Goldberg. */
val HexagonShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(hexagonPath(size))
}

/** Label de section : mono, capitales, très espacé. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = A4L.TextFaint) {
    Text(
        text = text.uppercase(),
        style = A4LText.SectionLabel,
        color = color,
        modifier = modifier,
    )
}

/** Point d'état avec son halo — connecté, relay, présence. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier
            .size(size * 2.6f)
            .drawBehind {
                drawCircle(color = color.copy(alpha = 0.22f), radius = this.size.minDimension / 2f)
                drawCircle(color = color, radius = this.size.minDimension / 2f * 0.385f)
            },
    )
}

/** Puce de filtre / d'état. */
@Composable
fun A4LChip(
    label: String,
    selected: Boolean = false,
    accent: Color = A4L.Cyan,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = A4LText.Caption,
        fontWeight = if (selected) FontWeight.SemiBold else null,
        color = if (selected) accent else A4L.TextBody.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .glass(
                radius = 9.dp,
                background = if (selected) accent.tint(0.10f) else A4L.GlassSoft.copy(alpha = 0.05f),
                border = if (selected) accent.tint(0.34f) else A4L.Stroke.copy(alpha = 0.10f),
            )
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/** Petite pastille de données en chasse fixe — « ⚡ 42,0 ẑen », « COVALENT », « N2 ». */
@Composable
fun DataBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = color.tint(0.08f),
    border: Color? = color.tint(0.32f),
    radius: Dp = 10.dp,
) {
    Text(
        text = label,
        style = A4LText.Data.copy(fontSize = 9.sp),
        color = color,
        maxLines = 1,
        modifier = modifier
            .glass(
                radius = radius,
                background = background,
                border = border ?: Color.Transparent,
                borderWidth = if (border == null) 0.dp else 1.dp,
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Une ligne « libellé ······ valeur » du panneau « Calculé pour vous ». */
@Composable
fun ComputedRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = A4L.TextHigh.copy(alpha = 0.8f),
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(label, style = A4LText.Body, color = A4L.TextBody.copy(alpha = 0.45f))
        Text(
            text = value,
            style = A4LText.Data.copy(fontSize = 11.5.sp),
            color = valueColor,
            maxLines = 1,
        )
    }
}
