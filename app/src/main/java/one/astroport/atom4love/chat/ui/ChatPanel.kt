package one.astroport.atom4love.chat.ui

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.chat.ChatKind
import one.astroport.atom4love.chat.ChatMessage
import one.astroport.atom4love.chat.ChatStatus
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * Panneau de causerie réutilisable — indépendant du transport : le POC BLE
 * lui donne des [ChatMessage] et récupère des intentions d'envoi ; le futur
 * chat Noise fera pareil. Bulles texte/image/fichier avec progression,
 * barre d'émojis, sélecteurs de photo et de fichier.
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    canSend: Boolean,
    placeholder: String,
    emptyHint: String,
    onSendText: (String) -> Boolean,
    onSendImage: (Uri) -> Unit,
    onSendFile: (Uri) -> Unit,
    onOpen: (ChatMessage) -> Unit,
    onDownload: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var emojiOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onSendImage) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(onSendFile) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (messages.isEmpty()) {
                item { Text(emptyHint, style = A4LText.Caption, color = A4L.TextMuted) }
            }
            items(messages) { message -> MessageBubble(message, onOpen, onDownload) }
        }

        if (emojiOpen) {
            EmojiBar { emoji -> draft += emoji }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlyphButton("😊", enabled = true) { emojiOpen = !emojiOpen }
            GlyphButton("🖼️", enabled = canSend) {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            GlyphButton("📎", enabled = canSend) { filePicker.launch("*/*") }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .glass(12.dp, background = A4L.Glass, border = A4L.StrokeFaint)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                textStyle = A4LText.Body.copy(color = A4L.TextHigh),
                cursorBrush = SolidColor(A4L.Mint),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(placeholder, style = A4LText.Body, color = A4L.TextGhost)
                    }
                    inner()
                },
            )
            Button(
                onClick = { if (onSendText(draft)) draft = "" },
                enabled = canSend && draft.isNotBlank(),
            ) { Text("Envoyer") }
        }
    }
}

// ── Bulles ────────────────────────────────────────────────────────────────

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onOpen: (ChatMessage) -> Unit,
    onDownload: (ChatMessage) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .glass(
                    radius = 14.dp,
                    background = if (message.mine) A4L.Mint.tint(0.08f) else A4L.Glass,
                    border = if (message.mine) A4L.Mint.tint(0.25f) else A4L.Stroke,
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                if (message.mine) "moi" else "pair ${message.from}",
                style = A4LText.Data,
                color = if (message.mine) A4L.Mint else A4L.TextDim,
            )
            // l'affichage suit le contenu réel (mime), pas le bouton utilisé
            // pour l'envoyer : une photo envoyée « en fichier » reste une photo
            when {
                message.kind == ChatKind.TEXT ->
                    Text(message.text, style = A4LText.Body, color = A4L.TextHigh)
                message.kind == ChatKind.IMAGE || message.mime.startsWith("image/") ->
                    ImageContent(message, onOpen)
                message.mime.startsWith("audio/") && message.file != null ->
                    AudioContent(message)
                else -> FileContent(message, onOpen)
            }
            if (message.kind != ChatKind.TEXT && message.file != null) {
                Text(
                    "⬇ enregistrer",
                    style = A4LText.Caption,
                    color = A4L.Mint.tint(0.8f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDownload(message) }
                        .padding(vertical = 2.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    timeFormat.format(Date(message.atMs)),
                    style = A4LText.Data,
                    color = A4L.TextGhost,
                )
                StatusMark(message)
            }
        }
    }
}

@Composable
private fun StatusMark(message: ChatMessage) {
    val pct = (message.progress * 100).toInt()
    when (message.status) {
        ChatStatus.SENDING -> Text("↑ $pct %", style = A4LText.Data, color = A4L.TextDim)
        ChatStatus.SENT -> Text("✓", style = A4LText.Data, color = A4L.TextDim)
        ChatStatus.DELIVERED -> Text("✓✓", style = A4LText.Data, color = A4L.Mint)
        ChatStatus.RECEIVING -> Text("↓ $pct %", style = A4LText.Data, color = A4L.TextDim)
        ChatStatus.RECEIVED -> Unit
        ChatStatus.FAILED -> Text("échec", style = A4LText.Data, color = A4L.Red)
    }
}

@Composable
private fun ImageContent(message: ChatMessage, onOpen: (ChatMessage) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val file = message.file
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = message.name,
            contentScale = ContentScale.Inside,
            modifier = Modifier
                .heightIn(max = 260.dp)
                .clip(shape)
                .clickable { onOpen(message) },
        )
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .dashedGlass(10.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (message.mine) "image en envoi…" else "image en réception…",
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
            if (message.status == ChatStatus.SENDING || message.status == ChatStatus.RECEIVING) {
                TransferBar(message.progress)
            }
        }
    }
}

@Composable
private fun FileContent(message: ChatMessage, onOpen: (ChatMessage) -> Unit) {
    Row(
        Modifier.clickable(enabled = message.file != null) { onOpen(message) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📎", fontSize = 20.sp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                message.name.ifBlank { "fichier" },
                style = A4LText.ItemTitle,
                color = A4L.TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Attachments.humanSize(message.sizeBytes),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
            if (message.status == ChatStatus.SENDING || message.status == ChatStatus.RECEIVING) {
                TransferBar(message.progress)
            }
        }
    }
}

/** Mini-lecteur pour les pièces audio — un MediaPlayer par bulle visible. */
@Composable
private fun AudioContent(message: ChatMessage) {
    val file = message.file ?: return
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (playing) "⏸" else "▶",
            fontSize = 24.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    runCatching {
                        if (playing) {
                            player.pause()
                            playing = false
                        } else {
                            if (!prepared) {
                                player.setDataSource(file.path)
                                player.prepare()
                                player.setOnCompletionListener { playing = false }
                                prepared = true
                            }
                            player.start()
                            playing = true
                        }
                    }
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                message.name.ifBlank { "audio" },
                style = A4LText.ItemTitle,
                color = A4L.TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Attachments.humanSize(message.sizeBytes),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
    }
}

@Composable
private fun TransferBar(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp),
        color = A4L.Mint,
        trackColor = A4L.Glass,
    )
}

// ── Saisie ────────────────────────────────────────────────────────────────

private val EMOJIS = listOf(
    "❤️", "😂", "👍", "😮", "😢", "🔥", "✨", "🎶",
    "🍻", "😍", "🙏", "👋", "🤝", "🌙", "⚛️", "💫",
)

@Composable
private fun EmojiBar(onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(12.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EMOJIS.forEach { emoji ->
            Text(
                emoji,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(emoji) }
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun GlyphButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        glyph,
        fontSize = 20.sp,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .glass(10.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
    )
}
