package one.astroport.atom4love.ui.screens

import java.io.File
import one.astroport.atom4love.chat.Faces
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R
import one.astroport.atom4love.chat.ChatKind
import one.astroport.atom4love.chat.Conversation
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.components.UnreadPill
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * 💬 Les conversations — une par personne, et rien d'autre.
 *
 * ## Ce que cet écran a remplacé
 *
 * **La cabine.** Une salle où tous les pairs à portée écrivaient ensemble, avec
 * un défaut qu'aucun réglage ne rattrapait : on n'écrit pas à une salle. Trois
 * personnes dans un bar produisaient un fil unique où chacun parlait à tout le
 * monde, et où « je t'ai vu tout à l'heure » ne s'adressait à personne. Le
 * transport n'a pas changé d'un octet — les mêmes trames, le même Noise, les
 * mêmes liens croisés. Ce qui a changé est qu'un message porte désormais le nom
 * de son destinataire, et que la liste se relit donc comme autant de fils
 * séparés ([one.astroport.atom4love.chat.Conversations]).
 *
 * ## Pourquoi il n'y a rien pour « démarrer une conversation »
 *
 * Parce qu'il n'y a rien à démarrer. Une personne attestée à portée **a** son
 * fil : il est là, vide, et écrire dedans est le seul geste. Un bouton
 * « nouvelle conversation » suivi d'une liste de gens serait la même liste, en
 * deux fois. Ce qui manque quand la liste est vide n'est pas un bouton, c'est
 * quelqu'un — et l'écran le dit.
 *
 * ## Le compteur de non-lus, fil par fil
 *
 * Il était écarté ici même, au motif qu'il faudrait retenir ce qui a été vu,
 * donc écrire, donc garder — ce que ces conversations promettent de ne pas
 * faire. L'objection est tombée sans qu'on y touche : **les marques de lecture
 * existent déjà** ([one.astroport.atom4love.ui.ChatHost]), une date par
 * personne, en mémoire, du même âge exactement que les fils. Rien de plus n'est
 * écrit ni gardé.
 *
 * Ce qui restait était donc un manque, pas une abstention : la pastille de
 * l'onglet disait « 3 » et pas une ligne ne disait lesquelles — il fallait
 * ouvrir les conversations une à une pour retrouver les trois messages. Chaque
 * ligne porte maintenant son compte, dans **la même pastille** que l'onglet
 * ([UnreadPill]), et la somme des lignes est ce que l'onglet annonce.
 *
 * ⚠ Ce qui n'a pas changé : **l'ordre de la liste**. Il suit le dernier mot dit
 * ([one.astroport.atom4love.chat.Conversations]), et un message non lu est par
 * construction récent — remonter les non-lus par-dessus ferait sauter un fil
 * sous le pouce au moment même où l'on va le toucher.
 */
@Composable
fun ChatsScreen(
    conversations: List<Conversation>,
    onOpen: (Conversation) -> Unit,
    /**
     * Ce qui attend d'être lu, par clé publique hexadécimale — voir
     * [one.astroport.atom4love.ui.ChatHost.unreadByPeer]. Un fil absent de la
     * carte n'a rien en attente.
     */
    unread: Map<String, Int> = emptyMap(),
    /** Les visages qu'on nous a montrés, par clé publique — voir [Faces]. */
    faces: Map<String, Faces.Face> = emptyMap(),
    /**
     * **Fermer et effacer** — la promesse que la cabine tenait par sa sortie.
     *
     * ⚠ Elle a changé de nature, pas de contenu. Fermer la cabine effaçait la
     * conversation ; comme fermer était aussi la façon de quitter l'écran, on
     * effaçait régulièrement en croyant seulement s'en aller. C'est désormais un
     * geste à part, **au bas de la liste de ce qu'il emporte** : on décide en
     * voyant. La radio, elle, se rallume aussitôt derrière — on efface ce qui
     * s'est dit, on ne se coupe pas du monde.
     */
    onErase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(A4L.Deep)
            .screenBackground(A4L.GlowBond, A4L.Deep, centerY = 0.05f, radiusFactor = 1.3f)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("💬", fontSize = 13.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(R.string.chats_title),
                style = A4LText.Title,
                color = A4L.TextHigh,
            )
        }

        if (conversations.isEmpty()) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .dashedGlass(15.dp)
                        .padding(horizontal = 18.dp, vertical = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.chats_empty),
                        style = A4LText.Body,
                        color = A4L.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    stringResource(R.string.chats_empty_hint),
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                )
            }
            return@Column
        }

        SectionLabel(
            stringResource(R.string.chats_count, conversations.count { it.inRange }),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(conversations, key = { it.peerHex }) { conversation ->
                ThreadRow(
                    conversation = conversation,
                    face = faces[conversation.peerHex]?.file,
                    unread = unread[conversation.peerHex] ?: 0,
                    onClick = { onOpen(conversation) },
                )
            }
            // ⚠ **En bas, et seulement s'il y a quelque chose à effacer.** Un
            // geste destructeur ne se met pas sous le pouce en tête de liste, et
            // proposer d'effacer le vide ferait douter de ce qui a été gardé.
            if (conversations.any { !it.empty }) {
                item {
                    EraseRow(onClick = onErase)
                }
            }
        }
    }
}

/**
 * Une personne, et la dernière chose qu'on s'est dite.
 *
 * ⚠ **La pastille dit la portée, pas l'humeur.** Menthe : joignable, on peut
 * écrire. Éteinte : la personne s'est éloignée — le fil reste lisible, la saisie
 * se ferme. C'est la seule information d'état de cet écran, et elle décide du
 * seul geste possible ; lui donner une couleur d'alarme ferait passer une porte
 * franchie pour un incident.
 */
@Composable
private fun ThreadRow(
    conversation: Conversation,
    face: File?,
    unread: Int,
    onClick: () -> Unit,
) {
    val accent = if (conversation.inRange) A4L.Mint else A4L.TextDim
    Row(
        Modifier
            .fillMaxWidth()
            .glass(
                radius = 14.dp,
                background = if (conversation.inRange) accent.tint(0.06f) else A4L.GlassSoft.copy(alpha = 0.04f),
                border = if (conversation.inRange) accent.tint(0.28f) else A4L.StrokeSoft,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ⚠ **Le visage prend la place de la pastille** quand il est arrivé
        // (Florent, 20/08) : « la photo n'est pas reprise dans les
        // discussions ». Un fil se reconnaît d'abord à qui est en face, et la
        // couleur de la pastille reste dite par l'anneau autour de la photo —
        // rien n'est perdu de l'état du lien.
        val faceBitmap = face?.let { file ->
            remember(file.path, file.lastModified()) {
                runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
            }
        }
        if (faceBitmap != null) {
            Image(
                bitmap = faceBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, accent, CircleShape),
            )
        } else {
            StatusDot(accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                conversation.name ?: stringResource(R.string.chat_from_unnamed),
                style = A4LText.ItemTitle,
                color = if (conversation.name != null) A4L.TextHigh else A4L.TextMuted,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                conversation.preview(),
                style = A4LText.Caption,
                // Ce qui n'est pas lu se lit en encre pleine : la pastille dit
                // combien, la ligne dit quoi, et une ligne grisée sous une
                // pastille rouge se contredirait elle-même.
                color = if (unread > 0) A4L.TextBody else A4L.TextMuted,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        // ⚠ **Avant le chevron, jamais à sa place.** Le chevron dit qu'on peut
        // entrer et ne dépend de rien ; la pastille dit ce qu'on y trouvera et
        // disparaît dès que c'est lu. Les échanger ferait bouger la porte au
        // rythme des messages.
        UnreadPill(unread, fontSize = 10.sp)
        if (unread > 0) Spacer(Modifier.width(10.dp))
        Text("›", fontSize = 17.sp, color = A4L.TextFaint)
    }
}

/**
 * La dernière ligne, résumée.
 *
 * Une pièce jointe se résume par **ce qu'elle est**, jamais par son nom de
 * fichier : `IMG_20260819_143205.jpg` occupe toute la largeur pour ne rien
 * apprendre, là où « une image » se lit d'un coup d'œil.
 */
@Composable
private fun Conversation.preview(): String {
    val message = last ?: return stringResource(
        if (inRange) R.string.chats_preview_new else R.string.chats_preview_out_of_range,
    )
    val body = when {
        message.kind == ChatKind.TEXT -> message.text
        message.mime.startsWith("image/") || message.kind == ChatKind.IMAGE ->
            stringResource(R.string.chats_preview_image)
        message.mime.startsWith("video/") -> stringResource(R.string.chats_preview_video)
        else -> stringResource(R.string.chats_preview_file)
    }
    return if (message.mine) stringResource(R.string.chats_preview_mine, body) else body
}

/**
 * Le geste qui efface tout, et le dit avant de le faire.
 *
 * En rouge, sans confirmation. La confirmation viendrait redemander ce qu'on
 * vient de décider en lisant une liste qu'on a sous les yeux — et surtout, ce
 * qui est effacé ne l'est jamais par surprise : la ligne nomme ce qu'elle
 * emporte, et rien de ce qu'elle emporte n'existait ailleurs qu'ici.
 */
@Composable
private fun EraseRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .glass(12.dp, A4L.Red.tint(0.06f), A4L.Red.tint(0.24f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.chats_erase),
            style = A4LText.Caption,
            color = A4L.Red,
            modifier = Modifier.weight(1f),
        )
    }
}
