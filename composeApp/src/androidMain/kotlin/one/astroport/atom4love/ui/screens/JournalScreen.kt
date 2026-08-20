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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalLocale
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
import java.text.SimpleDateFormat
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
fun JournalScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Les pseudos appris par les liens attestés, par jeton de présence. */
    names: Map<Int, String> = emptyMap(),
    /** Notre propre jeton : une rencontre qui NOUS concerne ne se dit pas pareil. */
    myToken: Int? = null,
) {
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

        JournalList(Modifier.weight(1f), names, myToken)
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
fun JournalList(
    modifier: Modifier = Modifier,
    names: Map<Int, String> = emptyMap(),
    myToken: Int? = null,
) {
    val entries by Journal.entries.collectAsStateWithLifecycle()
    // ⚠ **Le format MOYEN, pas le court : il porte les secondes.** Le court
    // donnait 19:09, et deux lignes de la même minute devenaient
    // indiscernables — or c'est précisément ce que ce journal montre. Retenu
    // hors de la boucle : le construire par ligne coûterait un objet par
    // évènement à chaque défilement.
    //
    // ⚠ **Et les millièmes** (Florent, 20/08) : les secondes ne suffisaient pas
    // non plus. Un balayage inscrit trois cartes dans la même seconde, et une
    // attestation suit son arrivée de neuf millièmes — l'ordre des lignes le
    // disait, l'heure le cachait. On part du motif de la langue plutôt que d'un
    // « HH:mm:ss » écrit en dur : celui d'ici, celui d'ailleurs, chacun garde le
    // sien, et les millièmes s'ajoutent après ses secondes.
    val locale = LocalLocale.current.platformLocale
    val clock = remember(locale) {
        val base = DateFormat.getTimeInstance(DateFormat.MEDIUM, locale) as? SimpleDateFormat
        val pattern = base?.toPattern()?.let { Regex("s+").replace(it) { m -> m.value + ".SSS" } }
        SimpleDateFormat(pattern ?: "HH:mm:ss.SSS", locale)
    }

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
            JournalRow(
                entry = entry,
                time = clock.format(Date(entry.atMs)),
                names = names,
                myToken = myToken,
            )
        }
    }
}

@Composable
private fun JournalRow(
    entry: Journal.Entry,
    time: String,
    names: Map<Int, String>,
    myToken: Int?,
) {
    val (color, label) = entry.render(names, myToken)
    // ⚠ **L'heure au-dessus, l'évènement en dessous** (Florent, 20/08).
    // L'heure et la pastille occupaient une colonne à gauche, et la phrase se
    // pliait dans ce qui restait : « Balise allumée — elle annonce et elle
    // écoute. » tenait sur deux lignes hautes et étroites, avec un tiers de la
    // largeur perdu en blanc à droite de l'heure. Empilées, l'heure garde la
    // place exacte de ses chiffres et la phrase prend toute la ligne.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                time,
                style = A4LText.Data.copy(fontSize = 11.sp),
                color = A4L.TextDim,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(1.dp))
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
private fun Journal.Entry.render(
    names: Map<Int, String>,
    myToken: Int?,
): Pair<androidx.compose.ui.graphics.Color, AnnotatedString> = when (this) {
    is Journal.Entry.Beacon -> if (on) {
        A4L.Mint to plain(R.string.journal_beacon_on)
    } else {
        A4L.Amber to plain(R.string.journal_beacon_off)
    }

    is Journal.Entry.Cell -> if (cell4d != null) {
        A4L.Mint to AnnotatedString(stringResource(R.string.journal_cell, cellHex(cell4d)))
    } else {
        // Ce n'est pas une panne : la balise annonce une présence, simplement
        // sans position. La couleur le dit — ambre, pas rouge.
        A4L.Amber to plain(R.string.journal_cell_none)
    }

    is Journal.Entry.CardSeen -> {
        val who = names[token]
        val label = who.orEmpty() + sealLabel(glyph, spaced = who != null)
        A4L.Violet to emphasise(
            if (percent != null) {
                stringResource(R.string.journal_card_seen_pct, label, percent)
            } else {
                stringResource(R.string.journal_card_seen, label)
            },
            who.orEmpty(),
        )
    }

    is Journal.Entry.CardGone -> {
        val who = names[token]
        A4L.TextDim to emphasise(
            stringResource(
                R.string.journal_card_gone,
                who.orEmpty() + sealLabel(glyph, spaced = who != null),
            ),
            who.orEmpty(),
        )
    }

    // ⚠ **La ligne qui parle de deux autres que soi.** Chacun est nommé comme
    // partout : le pseudo en gras s'il est connu, le sceau entre parenthèses.
    is Journal.Entry.Found -> {
        // ⚠ **Un appareil n'a pas sa propre carte dans ses voisins.** Vu à
        // l'écran le 20/08 : la tablette lisait « Droid_10 (Muluc) et (Sceau
        // inconnu) se sont trouvés » — le sceau inconnu, c'était elle. Une
        // rencontre qui nous concerne se dit donc à la deuxième personne, ce
        // qui est de toute façon la bonne façon de l'annoncer.
        val one = names[finderToken]
        val two = names[foundToken]
        val left = one.orEmpty() + sealLabel(finderGlyph, spaced = one != null)
        val right = two.orEmpty() + sealLabel(foundGlyph, spaced = two != null)
        val line = when (myToken) {
            null -> stringResource(R.string.journal_found, left, right)
            foundToken -> stringResource(R.string.journal_found_you, left)
            finderToken -> stringResource(R.string.journal_found_mine, right)
            else -> stringResource(R.string.journal_found, left, right)
        }
        A4L.Gold to buildAnnotatedString {
            val bolded = emphasise(line, one.orEmpty())
            append(bolded)
            // Le second pseudo se met en gras à son tour, sur le texte déjà
            // annoté : `emphasise` ne sait mettre qu'un mot en valeur.
            val second = two.orEmpty()
            val at = if (second.isEmpty()) -1 else line.indexOf(second, line.indexOf(right))
            if (at >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + second.length)
            }
        }
    }

    is Journal.Entry.Meeting -> {
        val who = names[token]
        A4L.Gold to emphasise(
            stringResource(
                R.string.journal_meeting,
                who.orEmpty() + sealLabel(glyph, spaced = who != null),
            ),
            who.orEmpty(),
        )
    }

    is Journal.Entry.Peer -> {
        val who = name ?: stringResource(R.string.chat_from_unnamed)
        val line = if (joined) R.string.journal_peer_joined else R.string.journal_peer_left
        val color = if (joined) A4L.Cyan else A4L.TextDim
        color to emphasise(stringResource(line, who), who)
    }

    is Journal.Entry.Relay -> if (online) {
        A4L.Green to plain(R.string.journal_relay_on)
    } else {
        A4L.Amber to plain(R.string.journal_relay_off)
    }
}

/**
 * ⚠ **Deux sortes de noms se croisent ici, et rien ne les distinguait.**
 * « Akbal » est un sceau — ce que la radio entend de n'importe qui —,
 * « Flower » est un pseudo — ce qu'une personne attestée s'est donné. Écrits
 * pareil, ils se lisaient pareil, et une carte anonyme avait l'air d'être
 * quelqu'un.
 *
 * Règle de Florent, le 20/08 : **le pseudo est l'information la plus
 * importante, donc en gras ; le type de sceau maya vient après, entre
 * parenthèses.** La forme porte la hiérarchie, sans un mot d'explication. Elle
 * vaut ici comme au Plateau (`DealtCard`), pour qu'une carte se lise pareil des
 * deux côtés.
 *
 * Le pseudo n'est connu que par le **jeton de présence** : une carte que rien
 * n'a attestée n'a que son sceau, et c'est déjà une information — elle est là,
 * on ne lui a pas encore parlé.
 */
private fun emphasise(full: String, part: String): AnnotatedString = buildAnnotatedString {
    val at = if (part.isEmpty()) -1 else full.indexOf(part)
    if (at < 0) {
        append(full)
        return@buildAnnotatedString
    }
    append(full.substring(0, at))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
    append(full.substring(at + part.length))
}

@Composable
private fun plain(id: Int): AnnotatedString = AnnotatedString(stringResource(id))

/** Le sceau, toujours entre parenthèses — voir [emphasise]. */
@Composable
private fun sealLabel(glyph: Int?, spaced: Boolean = false): String =
    (if (spaced) " " else "") + "(${sealName(glyph)})"

/** Le nom du sceau, ou le mot qui dit qu'il n'y en a pas. */
@Composable
private fun sealName(glyph: Int?): String =
    KinMaya.glyphName(glyph) ?: stringResource(R.string.board_no_seal)

// ⚠ **La même fonction que le Plateau, pas une seconde qui lui ressemble.**
// Celle d'ici écrivait `Long.toHexString` brut : le journal disait
// `881fb5b861fffff` pendant que la balise, deux écrans plus loin, disait
// `881FB5B861` de la même cellule. Vu à l'écran le 19/08. Une adresse qu'on
// compare d'un appareil à l'autre ne peut pas avoir deux orthographes.
