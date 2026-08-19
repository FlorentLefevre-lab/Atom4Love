package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.journal.Journal
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import java.text.DateFormat
import java.util.Date

/**
 * 📡 Le journal — ce que la radio fait, pendant qu'elle le fait.
 *
 * Il occupe la place, et le geste, de l'ancienne conversation commune : même
 * rangée pour l'ouvrir, même plein écran, même retour qui referme. Ce qui
 * change est ce qu'on y lit — non plus ce que la salle se dit, mais ce que
 * l'appareil fait. Voir [Journal] pour la règle qui décide de ce qui entre.
 *
 * ## Trois partis pris d'affichage
 *
 * **Le plus récent en haut.** Une conversation se lit du haut vers le bas parce
 * qu'on suit un fil ; un journal se consulte, et ce qu'on vient consulter est ce
 * qui vient de se passer. Descendre pour trouver le présent serait une marche
 * de plus à chaque ouverture.
 *
 * **Une heure par ligne, jamais une durée.** « il y a 3 min » oblige à
 * recalculer tout l'écran à chaque seconde et vieillit sous les yeux quand on
 * ne regarde pas. Une heure absolue reste vraie.
 *
 * **Pas de niveaux, pas de filtres, pas de recherche.** Ce n'est pas un outil de
 * diagnostic : c'est une fenêtre. Le jour où il faudrait y chercher quelque
 * chose, ce serait le signe qu'il en dit trop.
 */
@Composable
fun JournalScreen(modifier: Modifier = Modifier) {
    val entries by Journal.entries.collectAsStateWithLifecycle()
    // Le format court du système, dans la langue et la convention du téléphone :
    // 14:32 ici, 2:32 PM ailleurs. Retenu hors de la boucle — le construire par
    // ligne coûterait un objet par événement à chaque défilement.
    val clock = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(
        modifier
            .fillMaxSize()
            .background(A4L.Deep)
            .screenBackground(A4L.GlowRadar, A4L.Deep, centerY = 0.05f, radiusFactor = 1.3f)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📡", fontSize = 13.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(R.string.journal_title),
                style = A4LText.Title,
                color = A4L.TextHigh,
            )
        }
        Text(
            stringResource(R.string.journal_intro),
            style = A4LText.Caption,
            color = A4L.TextMuted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )

        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .dashedGlass(15.dp)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.journal_empty),
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entries, key = { it.seq }) { entry ->
                JournalRow(entry = entry, time = clock.format(Date(entry.atMs)))
            }
        }
    }
}

/**
 * Une ligne : l'heure, une pastille de couleur, la phrase.
 *
 * ⚠ **La couleur porte le sens, le texte ne le répète pas.** Menthe pour ce qui
 * s'allume, ambre pour ce qui s'éteint, violet pour une carte, or pour une
 * rencontre. C'est ce qui permet de balayer la colonne des pastilles sans lire
 * une ligne — et le texte reste en encre ordinaire, jamais dans l'accent, parce
 * qu'une couleur d'accent ne fait pas une encre (règle du Plateau, tenue ici).
 */
@Composable
private fun JournalRow(entry: Journal.Entry, time: String) {
    val (color, label) = entry.render()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            time,
            style = A4LText.Data.copy(fontSize = 11.sp),
            color = A4L.TextDim,
            modifier = Modifier.width(46.dp),
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(11.dp))
        Text(label, style = A4LText.Caption, color = A4L.TextBody)
    }
}

/**
 * La valeur devient une phrase — **ici et nulle part ailleurs**.
 *
 * Le journal n'enregistre que des nombres et des drapeaux ; toute la langue vit
 * dans cette fonction. Changer de langue relit donc l'historique entier dans la
 * nouvelle, sans qu'une ligne ait été réécrite au moment où elle est arrivée.
 */
@Composable
private fun Journal.Entry.render(): Pair<androidx.compose.ui.graphics.Color, String> = when (this) {
    is Journal.Entry.Beacon -> if (on) {
        A4L.Mint to stringResource(R.string.journal_beacon_on)
    } else {
        A4L.Amber to stringResource(R.string.journal_beacon_off)
    }

    is Journal.Entry.Cell -> if (cell4d != null) {
        A4L.Mint to stringResource(R.string.journal_cell, cellHex(cell4d))
    } else {
        // Ce n'est pas une panne : la balise annonce une présence, simplement
        // sans position. La couleur le dit — ambre, pas rouge.
        A4L.Amber to stringResource(R.string.journal_cell_none)
    }

    is Journal.Entry.CardSeen -> A4L.Violet to if (percent != null) {
        stringResource(R.string.journal_card_seen_pct, sealName(glyph), percent)
    } else {
        stringResource(R.string.journal_card_seen, sealName(glyph))
    }

    is Journal.Entry.CardGone -> A4L.TextDim to
        stringResource(R.string.journal_card_gone, sealName(glyph))

    is Journal.Entry.Meeting -> A4L.Gold to
        stringResource(R.string.journal_meeting, sealName(glyph))

    is Journal.Entry.Peer -> if (joined) {
        A4L.Cyan to stringResource(
            R.string.journal_peer_joined,
            name ?: stringResource(R.string.chat_from_unnamed),
        )
    } else {
        A4L.TextDim to stringResource(
            R.string.journal_peer_left,
            name ?: stringResource(R.string.chat_from_unnamed),
        )
    }

    is Journal.Entry.Relay -> if (online) {
        A4L.Green to stringResource(R.string.journal_relay_on)
    } else {
        A4L.Amber to stringResource(R.string.journal_relay_off)
    }
}

/** Le nom du sceau, ou le mot qui dit qu'il n'y en a pas. */
@Composable
private fun sealName(glyph: Int?): String =
    KinMaya.glyphName(glyph) ?: stringResource(R.string.board_no_seal)

// ⚠ **La même fonction que le Plateau, pas une seconde qui lui ressemble.**
// Celle d'ici écrivait `Long.toHexString` brut : le journal disait
// `881fb5b861fffff` pendant que la balise, deux écrans plus loin, disait
// `881FB5B861` de la même cellule. Vu à l'écran le 19/08. Une adresse qu'on
// compare d'un appareil à l'autre ne peut pas avoir deux orthographes.
