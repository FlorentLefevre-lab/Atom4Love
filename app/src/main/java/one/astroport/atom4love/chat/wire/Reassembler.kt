package one.astroport.atom4love.chat.wire

/**
 * Réassemble les fragments reçus. Un seul flux est élu par id de message :
 * le premier START gagne, le flux jumeau arrivé par l'autre connexion du
 * double lien croisé est ignoré en silence. GATT garantissant l'ordre par
 * lien, les fragments d'un flux élu doivent arriver séquentiellement — tout
 * écart (index inattendu, débordement, CRC) abandonne le flux.
 *
 * Pure JVM, horloge injectable : testable hors appareil.
 */
class Reassembler(
    private val maxBytes: Int,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val staleAfterMs: Long = 30_000,
) {

    sealed interface Event {
        data class Started(val from: String, val start: ChatFrame.Start) : Event
        data class Progress(val msgId: Int, val receivedBytes: Int, val totalBytes: Int) : Event
        class Completed(val from: String, val start: ChatFrame.Start, val bytes: ByteArray) : Event
        data class Failed(val msgId: Int, val reason: String, val ackStatus: Int? = null) : Event
    }

    private class Stream(
        val from: String,
        val start: ChatFrame.Start,
        val buffer: ByteArray,
        var lastSeenMs: Long,
    ) {
        var offset = 0
        var nextIndex = 0
    }

    private val streams = LinkedHashMap<Int, Stream>()

    /** Ids terminés (aboutis ou non) : dédoublonne les START retardataires. */
    private val finished = ArrayDeque<Int>()

    fun onFrame(from: String, frame: ChatFrame): Event? = when (frame) {
        is ChatFrame.Start -> onStart(from, frame)
        is ChatFrame.Data -> onData(from, frame)
        is ChatFrame.Ack -> null // l'acquittement se traite côté émetteur
    }

    private fun onStart(from: String, start: ChatFrame.Start): Event? {
        if (finished.contains(start.msgId)) return null
        // le premier START gagne — flux jumeau du double lien comme doublon
        // du même lien (réémission) sont ignorés, jamais d'écrasement
        if (streams.containsKey(start.msgId)) return null
        if (start.totalBytes <= 0 || start.totalBytes > maxBytes) {
            streams.remove(start.msgId)
            remember(start.msgId)
            return Event.Failed(
                start.msgId,
                "taille refusée (${start.totalBytes} o)",
                ChatFrames.ACK_ABORT,
            )
        }
        streams[start.msgId] = Stream(from, start, ByteArray(start.totalBytes), nowMs())
        return Event.Started(from, start)
    }

    private fun onData(from: String, frame: ChatFrame.Data): Event? {
        val stream = streams[frame.msgId] ?: return null
        if (stream.from != from) return null // fragment du flux jumeau
        stream.lastSeenMs = nowMs()
        // doublon exact du dernier fragment accepté (réémission) : ignoré
        if (frame.index == stream.nextIndex - 1) return null
        if (frame.index != stream.nextIndex) {
            streams.remove(frame.msgId)
            remember(frame.msgId)
            return Event.Failed(
                frame.msgId,
                "fragment ${frame.index} inattendu (attendu ${stream.nextIndex})",
            )
        }
        if (stream.offset + frame.chunk.size > stream.buffer.size) {
            streams.remove(frame.msgId)
            remember(frame.msgId)
            return Event.Failed(
                frame.msgId,
                "débordement du contenu annoncé",
                ChatFrames.ACK_ABORT,
            )
        }
        frame.chunk.copyInto(stream.buffer, stream.offset)
        stream.offset += frame.chunk.size
        stream.nextIndex++
        if (stream.offset < stream.buffer.size) {
            return Event.Progress(frame.msgId, stream.offset, stream.buffer.size)
        }
        streams.remove(frame.msgId)
        remember(frame.msgId)
        return if (ChatFrames.crc32(stream.buffer) == stream.start.crc32) {
            Event.Completed(stream.from, stream.start, stream.buffer)
        } else {
            Event.Failed(frame.msgId, "CRC invalide", ChatFrames.ACK_CRC)
        }
    }

    /** Nombre de flux en cours de réassemblage. */
    fun activeStreams(): Int = streams.size

    /** Abandonne les flux muets depuis [staleAfterMs] — lien mort, pair parti. */
    fun prune(): List<Event.Failed> {
        val now = nowMs()
        val stale = streams.filterValues { now - it.lastSeenMs > staleAfterMs }.keys.toList()
        return stale.map { msgId ->
            streams.remove(msgId)
            remember(msgId)
            Event.Failed(msgId, "transfert interrompu")
        }
    }

    private fun remember(msgId: Int) {
        finished.addLast(msgId)
        if (finished.size > FINISHED_MAX) finished.removeFirst()
    }

    private companion object {
        const val FINISHED_MAX = 128
    }
}
