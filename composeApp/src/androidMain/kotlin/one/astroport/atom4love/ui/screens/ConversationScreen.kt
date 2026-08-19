package one.astroport.atom4love.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import one.astroport.atom4love.R
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.chat.ChatEngine
import one.astroport.atom4love.chat.ChatError
import one.astroport.atom4love.chat.ChatSounds
import one.astroport.atom4love.chat.Conversation
import one.astroport.atom4love.chat.ui.ChatPanel
import one.astroport.atom4love.chat.ui.QuestionsPanel
import one.astroport.atom4love.domain.Questions
import one.astroport.atom4love.nostr.Contacts
import one.astroport.atom4love.nostr.NostrKeys
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * 💬 Une conversation — deux personnes, et rien qui les déborde.
 *
 * ## Ce qu'elle garde de la cabine, et pourquoi
 *
 * Tout, sauf la salle. Le chiffrement Noise de bout en bout, l'attestation qui
 * dit qui parle, les pièces jointes avec leurs plafonds par médium, le refus
 * franc d'une vidéo que la radio ne porte pas, les accusés, l'annulation d'un
 * envoi en cours — rien de ça n'a bougé, parce que rien de ça n'était le
 * problème. Le problème était le nombre de destinataires.
 *
 * Le **jeu des questions** vient avec, et à sa place exacte : sous la personne,
 * au-dessus des messages. C'est le troisième coup du « Qui est-ce ? », celui qui
 * a besoin d'un canal, et il n'a jamais eu de sens qu'entre deux joueurs — dans
 * la salle il était déjà rangé par pair, chaque partie n'appartenant qu'aux deux.
 * Il retrouve ici son cadre naturel plutôt qu'un pli dans une liste.
 *
 * ## Ce qui se ferme quand la personne s'éloigne
 *
 * La saisie, et elle seule. Ce qui a été dit reste lisible : effacer le fil
 * parce qu'un pair a franchi une porte donnerait à une porte le pouvoir d'un
 * geste. Le bandeau du haut dit ce qui se passe, et la saisie revient d'elle-même
 * dès que la radio le retrouve — sans qu'on ait rien à rouvrir.
 *
 * ⚠ Ce qui **efface** reste un geste, et un seul : fermer les conversations
 * depuis le journal. C'est la promesse de la cabine, tenue telle quelle.
 */
@Composable
fun ConversationScreen(
    conversation: Conversation,
    chat: ChatEngine,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    keys: NostrKeys? = null,
    contacts: Contacts? = null,
) {
    val context = LocalContext.current
    val sounds = remember { ChatSounds() }
    val follows by (contacts?.state ?: remember { MutableStateFlow(emptyMap()) })
        .collectAsStateWithLifecycle()
    val savedLabel = stringResource(R.string.chat_saved_to_downloads)
    val saveFailedLabel = stringResource(R.string.chat_save_failed)

    // ── Une pièce refusée, et pourquoi ────────────────────────────────────
    //
    // ⚠ **Ce dialogue avait disparu avec le panneau de la cabine, et il ne
    // pouvait pas.** Quelqu'un vient de choisir un fichier dans un sélecteur
    // système ; celui-ci se referme, et sans un mot il ne se passe visiblement
    // rien. C'est le seul endroit de l'application où le silence serait lu comme
    // une panne — d'où un dialogue, et non une ligne d'état.
    //
    // Les deux refus ne se disent pas pareil : l'un est une question de
    // patience (un plafond de taille), l'autre une **règle** — une vidéo part
    // telle qu'elle a été filmée, jamais recompressée, et la radio ne la porte
    // pas.
    val refusal by chat.refusal.collectAsStateWithLifecycle()
    refusal?.let { refused ->
        AlertDialog(
            onDismissRequest = { chat.dismissRefusal() },
            containerColor = A4L.Deep,
            confirmButton = {
                Text(
                    stringResource(R.string.chat_refused_ok),
                    style = A4LText.Caption,
                    color = A4L.Mint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { chat.dismissRefusal() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            title = {
                Text(
                    stringResource(
                        when (refused) {
                            is ChatEngine.Refusal.TooBig -> R.string.chat_too_big_title
                            is ChatEngine.Refusal.VideoNeedsWifi -> R.string.chat_video_wifi_title
                        },
                    ),
                    style = A4LText.H2,
                    color = A4L.TextHigh,
                )
            },
            text = {
                // Deux phrases entières, jamais des bribes recollées : l'ordre
                // des mots n'est pas le même d'une langue à l'autre, et
                // « au-delà des » seul ne se traduit pas.
                val medium = stringResource(refused.medium.labelRes)
                val res = LocalResources.current
                Text(
                    when (refused) {
                        is ChatEngine.Refusal.TooBig -> buildString {
                            val limit = Attachments.humanSize(res, refused.limit)
                            append(
                                if (refused.bytes > 0) {
                                    stringResource(
                                        R.string.chat_too_big_sized,
                                        refused.name,
                                        Attachments.humanSize(res, refused.bytes),
                                        limit,
                                        medium,
                                    )
                                } else {
                                    stringResource(
                                        R.string.chat_too_big_unsized,
                                        refused.name, limit, medium,
                                    )
                                },
                            )
                        }
                        is ChatEngine.Refusal.VideoNeedsWifi ->
                            if (refused.name.isBlank()) {
                                stringResource(R.string.chat_video_wifi_unnamed, medium)
                            } else {
                                stringResource(R.string.chat_video_wifi_named, refused.name, medium)
                            }
                    },
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            },
        )
    }

    // ── Ce qui a mal tourné, sous le nom ──────────────────────────────────
    val status by chat.status.collectAsStateWithLifecycle()

    // Les cloches restent celles du moteur : elles disent qu'un message est
    // parti ou arrivé, sur n'importe quel fil. Les rendre propres à une
    // conversation demanderait de savoir laquelle est ouverte, ce que le moteur
    // n'a aucune raison d'apprendre.
    LaunchedEffect(chat) {
        chat.chimes.collect { chime ->
            when (chime) {
                ChatEngine.Chime.SENT -> sounds.send()
                ChatEngine.Chime.RECEIVED -> sounds.receive()
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(A4L.Deep)
            .screenBackground(A4L.GlowBond, A4L.Deep, centerY = 0.05f, radiusFactor = 1.2f)
            .statusBarsPadding()
            // Les deux, et dans cet ordre — même montage que la cabine : le
            // clavier rétrécit la colonne pour que la liste cède sa place, puis
            // la barre système ne prend que ce qui reste.
            .imePadding()
            .navigationBarsPadding(),
    ) {
        // ── Qui, et joignable ou non ──────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Le retour est un chevron dans l'en-tête et non une rangée en
            // travers de l'écran : on quitte une conversation cent fois par
            // soirée, et une rangée pleine largeur pour ça mangerait la hauteur
            // qui manque justement aux messages.
            Text(
                "‹",
                style = A4LText.Title.copy(fontSize = 26.sp),
                color = A4L.TextStrong,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(4.dp))
            StatusDot(if (conversation.inRange) A4L.Mint else A4L.TextDim)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    conversation.name ?: stringResource(R.string.chat_from_unnamed),
                    style = A4LText.ItemTitle,
                    color = if (conversation.name != null) A4L.TextHigh else A4L.TextMuted,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(
                        if (conversation.inRange) R.string.conv_in_range else R.string.conv_out_of_range,
                    ),
                    style = A4LText.Caption,
                    color = if (conversation.inRange) A4L.Mint else A4L.TextMuted,
                )
            }
            // « Suivre » — le seul geste de cet écran qui sorte du téléphone.
            // Il vit près du nom parce qu'il porte sur la personne, pas sur la
            // conversation : garder quelqu'un dans son carnet NOSTR survit à un
            // fil qui, lui, s'efface.
            if (keys != null && contacts != null) {
                Spacer(Modifier.width(10.dp))
                FollowChip(
                    state = follows[conversation.peerHex],
                    onClick = { contacts.follow(keys, conversation.peerHex) },
                )
            }
        }

        // ── Il est parti, et ça se dit ────────────────────────────────────
        //
        // ⚠ **Une ligne à part, pas seulement un sous-titre.** Le départ se
        // lisait dans les deux mots sous le nom — « hors de portée » — et dans
        // une pastille qui s'éteint. C'est exact et c'est trop discret : quand
        // on écrit à quelqu'un, on ne relit pas l'en-tête, on regarde le bas de
        // l'écran, et la saisie qui se ferme ressemble à une panne plutôt qu'à
        // un départ.
        //
        // ⚠ Et elle porte la **promesse de reprise**, qui est la moitié la plus
        // importante : rien n'est fini, rien n'est perdu, le fil se rouvre tout
        // seul dès que la radio le retrouve. Sans elle, l'écran annonce une
        // rupture là où il n'y a qu'une porte franchie.
        if (!conversation.inRange) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                    .glass(12.dp, A4L.Amber.tint(0.08f), A4L.Amber.tint(0.28f))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(A4L.Amber)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(
                        R.string.conv_gone,
                        conversation.name ?: stringResource(R.string.chat_from_unnamed),
                    ),
                    style = A4LText.Caption,
                    color = A4L.TextBody,
                )
            }
        }

        // ⚠ **Le dernier incident se lit ici, et nulle part ailleurs.** Le
        // moteur le porte en valeur ; c'est cette ligne qui choisit la langue.
        // Elle vivait dans l'en-tête de la salle, à côté du compte des pairs :
        // dans une conversation à deux, elle se pose sous le nom, là où l'œil
        // vient de passer.
        status.lastError?.let { error ->
            Text(
                error.text(),
                style = A4LText.Caption,
                color = A4L.Amber,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            )
        }

        // ⚠ **Le jeu des questions ne paraît plus ici.** Il y avait sa place
        // logique — troisième coup du « Qui est-ce ? », le premier qui ait
        // besoin d'un canal — et pourtant il encombrait : un pli refermé en
        // travers du haut de chaque conversation, au-dessus des messages, alors
        // qu'on vient là pour écrire. Retiré sur décision de Florent le 19/08.
        //
        // ⚠ **Rien n'est supprimé du code** : `Questions`, `QuestionsPanel`, et
        // les trois coups du moteur (`ask` / `answer` / `decline`, trame 0x0B)
        // sont intacts et testés. Ce qui manque est un écran d'où les jouer —
        // c'est une place à retrouver, pas une fonction à réécrire.

        ChatPanel(
            messages = conversation.messages,
            // ⚠ La portée, et non « des liens existent » : un lien ouvert vers
            // quelqu'un d'autre ne permet pas d'écrire ici. C'est toute la
            // différence entre une salle et une conversation.
            canSend = conversation.inRange,
            placeholder = stringResource(R.string.chat_placeholder),
            emptyHint = stringResource(R.string.chat_empty),
            onSendText = { text -> chat.sendText(text, conversation.peerHex) },
            onSendImage = { uri -> chat.sendImage(uri, conversation.peerHex) },
            onSendFile = { uri -> chat.sendFile(uri, conversation.peerHex) },
            onCancel = { message -> chat.cancelSend(message.id) },
            onOpen = { message ->
                message.file?.let { file ->
                    runCatching {
                        context.startActivity(Attachments.viewIntent(context, file, message.mime))
                    }
                }
            },
            onDownload = { message ->
                message.file?.let { file ->
                    val ok = Attachments.saveToDownloads(context, file, message.name, message.mime)
                    Toast.makeText(
                        context,
                        if (ok) savedLabel else saveFailedLabel,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )
    }
}

/**
 * « Suivre » — ajouter cette personne au carnet NOSTR (kind 3) de notre clé.
 *
 * Un seul geste, jamais automatique : la rencontre atteste le npub, elle ne
 * décide pas de le garder. Rien à scanner — la clé a été vérifiée par
 * [one.astroport.atom4love.noise.NoiseVouch] avant que ce bouton n'existe.
 *
 * ⚠ **Il a grossi, et c'est mérité.** À 10 sp dans quatre points de marge, il
 * avait la taille d'une étiquette d'état posée à côté d'un nom — or c'est le
 * seul geste de cet écran qui sorte du téléphone et qui **dure** : le fil
 * s'efface, le carnet reste. Un geste irréversible ne se touche pas du coin de
 * l'ongle.
 */
@Composable
private fun FollowChip(state: Contacts.State?, onClick: () -> Unit) {
    val (label, color, enabled) = when (state) {
        null -> Triple(stringResource(R.string.chat_follow), A4L.Mint, true)
        Contacts.State.Publishing ->
            Triple(stringResource(R.string.chat_follow_publishing), A4L.TextMuted, false)
        Contacts.State.Published ->
            Triple(stringResource(R.string.chat_following), A4L.Mint, false)
        is Contacts.State.Refused ->
            Triple(stringResource(R.string.chat_follow_retry), A4L.Cyan, true)
    }
    Text(
        label,
        style = A4LText.Data.copy(fontSize = 13.sp),
        color = color,
        modifier = Modifier
            .background(
                color.copy(alpha = 0.16f),
                androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/**
 * Un [ChatError] mis en mots, ici et maintenant. Le moteur a dit lequel ;
 * c'est cette ligne qui choisit la langue, et elle change avec elle.
 */
@Composable
private fun ChatError.text(): String =
    if (args.isEmpty()) {
        stringResource(messageRes)
    } else {
        stringResource(messageRes, *args.toTypedArray())
    }
