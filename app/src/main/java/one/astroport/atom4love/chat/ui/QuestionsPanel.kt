package one.astroport.atom4love.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Questions
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/**
 * ❓ Les questions, avec **une** personne de la cabine.
 *
 * Troisième coup du « Qui est-ce ? », et le premier qui ait besoin d'un canal :
 * φ est public, donc deux inconnus ne peuvent rien se dire que la salle
 * n'entende, tant qu'ils n'ont pas ouvert quelque chose. Voir [Questions] pour
 * la règle du jeu et ce qu'elle ne garantit pas.
 *
 * L'écran a une seule chose à faire correctement : **dire ce qu'on donne avant
 * qu'on le donne**. Chaque attribut porte sa conséquence en clair — « votre âge
 * à dix ans près », « ramène votre date à quelques jours » — parce qu'un jeu
 * qui fait céder des données en cachant leur portée n'est pas un jeu, c'est un
 * formulaire déguisé.
 */
@Composable
fun QuestionsPanel(
    /** Ce qui s'est déjà joué avec cette personne. */
    history: List<Questions.Exchange>,
    /** Ce que notre fiche sait encore proposer — [Questions.offerable]. */
    offerable: List<Questions.Trait>,
    onAsk: (Questions.Trait) -> Unit,
    onAnswer: (Questions.Trait) -> Unit,
    onDecline: (Questions.Trait) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val owed = history.count { it.owed }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (open) "▾" else "▸",
                fontSize = 14.sp,
                color = A4L.TextMuted,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.questions_title),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
            // Une question posée attend une réponse de nous : c'est la seule
            // chose de ce panneau qui appelle un geste, elle se voit fermée.
            if (owed > 0) {
                Spacer(Modifier.width(7.dp))
                Box(
                    Modifier
                        .background(A4L.Amber.copy(alpha = 0.18f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) { Text("$owed", style = A4LText.Data.copy(fontSize = 10.sp), color = A4L.Amber) }
            }
        }

        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.questions_rule),
                    style = A4LText.Caption.copy(fontSize = 10.sp),
                    color = A4L.TextGhost,
                    modifier = Modifier.padding(bottom = 2.dp),
                )

                history.forEach { exchange ->
                    PlayedQuestion(
                        exchange = exchange,
                        onAnswer = { onAnswer(exchange.trait) },
                        onDecline = { onDecline(exchange.trait) },
                    )
                }

                when {
                    offerable.isNotEmpty() ->
                        offerable.forEach { trait -> OfferableQuestion(trait) { onAsk(trait) } }

                    history.isEmpty() -> Hint(R.string.questions_no_card)
                    else -> Hint(R.string.questions_none_left)
                }
            }
        }
    }
}

@Composable
private fun Hint(res: Int) {
    Text(stringResource(res), style = A4LText.Caption, color = A4L.TextDim)
}

/**
 * Un attribut qu'on peut proposer : son nom, ce qu'il donne, et le geste.
 *
 * Le « ce qu'il donne » n'est pas une note de bas de page, il est à la même
 * taille que le reste : c'est l'information dont on a besoin pour décider.
 */
@Composable
private fun OfferableQuestion(trait: Questions.Trait, onAsk: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(11.dp, background = A4L.GlassFaint, border = A4L.StrokeFaint)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(trait.labelRes),
                style = A4LText.Body,
                color = A4L.TextHigh,
            )
            Text(
                stringResource(trait.tellsRes),
                style = A4LText.Caption.copy(fontSize = 10.sp),
                color = A4L.TextDim,
            )
        }
        Spacer(Modifier.width(10.dp))
        Pill(stringResource(R.string.questions_ask), A4L.Cyan, onAsk)
    }
}

/** Une question déjà engagée : ce qui est retourné, et ce qui reste à faire. */
@Composable
private fun PlayedQuestion(
    exchange: Questions.Exchange,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    val settled = exchange.settled
    Column(
        Modifier
            .fillMaxWidth()
            .glass(
                11.dp,
                background = if (settled) A4L.Mint.copy(alpha = 0.07f) else A4L.GlassFaint,
                border = if (settled) A4L.Mint.copy(alpha = 0.25f) else A4L.StrokeFaint,
            )
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(exchange.trait.labelRes),
            style = A4LText.Body,
            color = A4L.TextHigh,
            fontWeight = FontWeight.SemiBold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Side(R.string.questions_you_label, exchange.trait, exchange.mine, A4L.Cyan)
            Side(R.string.questions_them_label, exchange.trait, exchange.theirs, A4L.Mint)
        }

        when {
            exchange.owed -> {
                Text(
                    stringResource(R.string.questions_asked_you),
                    style = A4LText.Caption.copy(fontSize = 10.sp),
                    color = A4L.Amber,
                )
                Text(
                    stringResource(exchange.trait.tellsRes),
                    style = A4LText.Caption.copy(fontSize = 10.sp),
                    color = A4L.TextDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Pill(stringResource(R.string.questions_answer), A4L.Mint, onAnswer)
                    Pill(stringResource(R.string.questions_decline), A4L.TextDim, onDecline)
                }
            }

            exchange.pending -> Note(R.string.questions_waiting, A4L.TextDim)
            // Un refus se lit des deux côtés, et pas du même côté : celui qui a
            // donné pour rien doit le voir, celui qui n'a pas répondu aussi.
            exchange.declined && exchange.theirs == null -> Note(R.string.questions_declined, A4L.TextDim)
            exchange.declined -> Note(R.string.questions_refused, A4L.TextDim)
        }
    }
}

@Composable
private fun Note(res: Int, color: Color) {
    Text(stringResource(res), style = A4LText.Caption.copy(fontSize = 10.sp), color = color)
}

/** Un côté de la question — le nôtre ou le sien, une valeur ou un tiret. */
@Composable
private fun Side(labelRes: Int, trait: Questions.Trait, value: Int?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            stringResource(labelRes),
            style = A4LText.Caption.copy(fontSize = 9.sp),
            color = A4L.TextGhost,
        )
        Text(
            value?.let { readable(trait, it) } ?: "—",
            style = A4LText.Data.copy(fontSize = 13.sp),
            color = if (value == null) A4L.TextGhost else color,
        )
    }
}

/**
 * La valeur en toutes lettres.
 *
 * Le sceau et la couleur ont des noms ; la tonalité, l'heure et la décennie
 * sont des nombres et le restent. Rien n'est arrondi ni enjolivé ici : ce qui
 * s'affiche est exactement ce qui a voyagé.
 */
@Composable
private fun readable(trait: Questions.Trait, value: Int): String = when (trait) {
    Questions.Trait.Tone -> "$value"
    Questions.Trait.Color -> stringResource(
        when (value) {
            0 -> R.string.trait_color_name_0
            1 -> R.string.trait_color_name_1
            2 -> R.string.trait_color_name_2
            3 -> R.string.trait_color_name_3
            else -> R.string.trait_color_name_4
        },
    )
    Questions.Trait.Decade -> stringResource(R.string.trait_decade_value, value)
    Questions.Trait.BirthHour -> stringResource(R.string.trait_hour_value, value)
    // Le KIN porte son sceau avec lui : c'est ce qu'on lit, pas le nombre nu.
    Questions.Trait.Kin ->
        KinMaya.ofNumber(value)?.let { "$value · ${KinMaya.glyphEmoji(it.glyph)}" } ?: "$value"
    // Une onde s'écrit en hertz, avec sa décimale : c'est l'écart entre les
    // deux qui fait le battement, et il se joue sur ce dixième-là.
    Questions.Trait.Bio -> stringResource(
        R.string.trait_bio_value,
        Questions.decodeBio(value),
    )
}

@Composable
private fun Pill(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(label, style = A4LText.Caption.copy(fontSize = 11.sp), color = color)
    }
}
