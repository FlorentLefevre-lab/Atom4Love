package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.journal.Journal
import one.astroport.atom4love.ui.components.glass
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
fun JournalScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val entries by Journal.entries.collectAsStateWithLifecycle()
    // Lu dans la composition et non dans le rappel : une ressource lue depuis un
    // lambda ne serait pas réévaluée si la langue change sous l'application.
    val backLabel = stringResource(R.string.journal_close)
    // ⚠ **Le format MOYEN, pas le court : il porte les secondes.**
    //
    // Le court donnait 19:09, et deux lignes de la même minute devenaient
    // indiscernables — or c'est précisément ce que ce journal montre. Un
    // balayage BLE fait paraître trois cartes en deux secondes ; sans les
    // secondes, l'ordre des lignes était la seule chose qui disait laquelle est
    // arrivée d'abord, et un ordre ne se lit pas, il se déduit.
    //
    // Toujours celui du système : 19:09:23 ici, 7:09:23 PM ailleurs. Retenu hors
    // de la boucle — le construire par ligne coûterait un objet par évènement à
    // chaque défilement.
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
            // ⚠ **Une porte de sortie visible.** Le journal se refermait par le
            // seul geste système, ce qui suffit à qui le connaît et laisse les
            // autres devant une page dont ils ne voient pas comment sortir. Le
            // chevron est celui de la conversation, au même endroit et de la
            // même taille : deux plein-écrans, un seul geste pour en revenir.
            // ⚠ Le chevron est **hors du flux** (largeur nulle, dessiné
            // par-dessus) pour que le titre tombe au milieu de l'ÉCRAN et non au
            // milieu de la place qui reste. Sans ça, « Journal de bord » se
            // décalait vers la droite de la moitié du chevron — ce qui se voit
            // dès qu'on le compare aux autres titres de l'application.
            Box(Modifier.width(0.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    "‹",
                    style = A4LText.Title.copy(fontSize = 26.sp),
                    color = A4L.TextStrong,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .semantics { contentDescription = backLabel }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 🧾 : une bande imprimée, ligne après ligne — le signe le plus
                // proche de ce qu'est un journal d'évènements. Le même que la
                // rangée qui ouvre cet écran, sinon la porte et la pièce ne
                // porteraient pas le même nom.
                Text("🧾", fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.journal_title),
                    style = A4LText.Title,
                    color = A4L.TextHigh,
                )
            }
        }
        // ⚠ **La phrase d'introduction est partie.** « Ce que la radio fait,
        // pendant qu'elle le fait. Rien ne s'y garde. » — c'était juste, et
        // c'était une notice : elle expliquait au-dessus de lignes qui se
        // suffisent, et poussait le premier évènement d'un cran vers le bas à
        // chaque ouverture. Ce que le journal est se lit en le lisant.
        Spacer(Modifier.height(8.dp))

        JournalList(Modifier.weight(1f))
        if (entries.isNotEmpty()) CloseRow(onClose)
    }
}

/**
 * **Fermer**, en toutes lettres et en bas de page.
 *
 * ⚠ Le chevron du haut ne suffisait pas. Il est juste — c'est le même geste que
 * dans une conversation — mais il se lit comme une décoration tant qu'on ne l'a
 * pas essayé, et il est à l'autre bout de l'écran du pouce qui vient de faire
 * défiler une liste. Un mot, à l'endroit où la lecture s'arrête, ne demande rien
 * à personne.
 *
 * Les deux coexistent sans se contredire : le chevron sert à qui revient tout de
 * suite, le bouton à qui a lu jusqu'au bout.
 */
@Composable
private fun CloseRow(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
            .height(46.dp)
            .glass(12.dp, A4L.GlassSoft, A4L.StrokeSoft)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.journal_close),
            style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = A4L.TextStrong,
        )
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
/**
 * **Les lignes du journal, et rien autour** — ni titre, ni porte de sortie.
 *
 * Extrait de [JournalScreen] le 20/08 pour que le **tiroir** du Plateau et le
 * plein écran montrent exactement la même chose : deux rendus séparés d'un même
 * journal auraient divergé au premier format de date changé d'un seul côté.
 *
 * Il lit [Journal] lui-même — c'est un objet global, il n'y a rien à faire
 * traverser à qui l'affiche.
 */
@Composable
fun JournalList(modifier: Modifier = Modifier) {
    val entries by Journal.entries.collectAsStateWithLifecycle()
    // ⚠ **Le format MOYEN, pas le court : il porte les secondes.** Le court
    // donnait 19:09, et deux lignes de la même minute devenaient
    // indiscernables — or c'est précisément ce que ce journal montre. Retenu
    // hors de la boucle : le construire par ligne coûterait un objet par
    // évènement à chaque défilement.
    val clock = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }

    if (entries.isEmpty()) {
        Box(
            modifier
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
        return
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(entries, key = { it.seq }) { entry ->
            JournalRow(entry = entry, time = clock.format(Date(entry.atMs)))
        }
    }
}

@Composable
private fun JournalRow(entry: Journal.Entry, time: String) {
    val (color, label) = entry.render()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ⚠ **Pas de largeur imposée.** La colonne faisait 46 dp, taillée pour
        // « 19:13 » ; avec les secondes, « 19:13:50 » passait à la ligne et
        // chaque évènement occupait deux lignes. Une largeur en dur ne survit ni
        // à un format qui s'allonge, ni à une langue qui écrit « 7:13:50 PM ».
        //
        // La police est à chasse fixe : toutes les heures d'une même langue font
        // donc la même largeur, et la colonne s'aligne d'elle-même sans qu'on
        // ait à la mesurer.
        Text(
            time,
            style = A4LText.Data.copy(fontSize = 11.sp),
            color = A4L.TextDim,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(end = 12.dp),
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
