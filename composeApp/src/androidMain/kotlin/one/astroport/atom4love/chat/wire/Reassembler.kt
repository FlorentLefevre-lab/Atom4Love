package one.astroport.atom4love.chat.wire

import java.util.zip.CRC32

/**
 * Réassemble les fragments reçus. Un seul flux est élu par id de message :
 * le premier START gagne, le flux jumeau arrivé par l'autre connexion du
 * double lien croisé est ignoré en silence. GATT garantissant l'ordre par
 * lien, les fragments d'un flux élu doivent arriver séquentiellement — tout
 * écart (index inattendu, débordement, CRC) abandonne le flux.
 *
 * **Rien n'est mis en tampon ici.** Les octets partent au fil de l'eau dans un
 * [Sink] que l'appelant choisit : la mémoire pour le texte, le disque pour une
 * pièce jointe. Allouer `ByteArray(totalBytes)` à l'arrivée du premier fragment
 * plafonnait les transferts à ce qu'un téléphone peut tenir en mémoire, et le
 * multipliait par le nombre de pairs qui envoient en même temps.
 *
 * Le CRC se calcule au passage, fragment par fragment — c'est du protocole, il
 * reste donc ici et non dans la destination.
 *
 * Pure JVM, horloge et destination injectables : testable hors appareil.
 */
class Reassembler(
    private val maxBytes: Int,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val staleAfterMs: Long = 30_000,
    /** Où ranger un flux, décidé d'après ce que son START annonce. */
    private val sinkFor: (ChatFrame.Start) -> Sink = { MemorySink(it.totalBytes) },
) {

    sealed interface Event {
        data class Started(val from: String, val start: ChatFrame.Start) : Event
        data class Progress(val msgId: Int, val receivedBytes: Int, val totalBytes: Int) : Event
        class Completed(
            val from: String,
            val start: ChatFrame.Start,
            val payload: Payload,
        ) : Event

        data class Failed(val msgId: Int, val reason: String, val ackStatus: Int? = null) : Event
    }

    private class Stream(
        val from: String,
        val start: ChatFrame.Start,
        val sink: Sink,
        var lastSeenMs: Long,
    ) {
        val crc = CRC32()
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
        // l'émetteur renonce : le flux s'arrête ici, et son partiel avec lui —
        // pas d'accusé en retour, il n'attend plus rien
        is ChatFrame.Cancel -> streams[frame.msgId]?.let {
            abandon(frame.msgId, "envoi annulé par l'expéditeur")
        }
        is ChatFrame.Handshake -> null // le handshake appartient au lien, pas aux flux
        is ChatFrame.Sealed -> null // déjà ouvert par le lien avant d'arriver ici
        is ChatFrame.Address -> null // affaire de médium, pas de flux
        is ChatFrame.Question -> null // un coup du jeu, pas du contenu
        is ChatFrame.Name -> null // une étiquette posée sur le lien, pas du contenu
        is ChatFrame.Ping -> null // un signe de vie du lien, pas du contenu
        is ChatFrame.Group -> null // idem : une invitation, pas du contenu
        is ChatFrame.Bye -> null // affaire de lien, pas de flux
    }

    private fun onStart(from: String, start: ChatFrame.Start): Event? {
        if (finished.contains(start.msgId)) return null
        // le premier START gagne — flux jumeau du double lien comme doublon
        // du même lien (réémission) sont ignorés, jamais d'écrasement
        if (streams.containsKey(start.msgId)) return null
        if (start.totalBytes <= 0 || start.totalBytes > maxBytes) {
            // refusé avant d'ouvrir quoi que ce soit : rien à abandonner
            remember(start.msgId)
            return Event.Failed(
                start.msgId,
                "taille refusée (${start.totalBytes} o)",
                ChatFrames.ACK_ABORT,
            )
        }
        streams[start.msgId] = Stream(from, start, sinkFor(start), nowMs())
        return Event.Started(from, start)
    }

    private fun onData(from: String, frame: ChatFrame.Data): Event? {
        val stream = streams[frame.msgId] ?: return null
        if (stream.from != from) return null // fragment du flux jumeau
        stream.lastSeenMs = nowMs()
        // doublon exact du dernier fragment accepté (réémission) : ignoré
        if (frame.index == stream.nextIndex - 1) return null
        if (frame.index != stream.nextIndex) {
            return abandon(
                frame.msgId,
                "fragment ${frame.index} inattendu (attendu ${stream.nextIndex})",
            )
        }
        if (stream.offset + frame.chunk.size > stream.start.totalBytes) {
            return abandon(frame.msgId, "débordement du contenu annoncé", ChatFrames.ACK_ABORT)
        }
        if (!stream.sink.write(frame.chunk, frame.chunk.size)) {
            return abandon(frame.msgId, "écriture impossible", ChatFrames.ACK_ABORT)
        }
        stream.crc.update(frame.chunk)
        stream.offset += frame.chunk.size
        stream.nextIndex++
        if (stream.offset < stream.start.totalBytes) {
            return Event.Progress(frame.msgId, stream.offset, stream.start.totalBytes)
        }
        streams.remove(frame.msgId)
        remember(frame.msgId)
        // le CRC avant la clôture : rien ne sert de nommer un fichier faux
        if (stream.crc.value.toInt() != stream.start.crc32) {
            stream.sink.abort()
            return Event.Failed(frame.msgId, "CRC invalide", ChatFrames.ACK_CRC)
        }
        val payload = stream.sink.finish()
            ?: return Event.Failed(frame.msgId, "clôture impossible", ChatFrames.ACK_ABORT)
        return Event.Completed(stream.from, stream.start, payload)
    }

    /** Retire le flux, ne laisse rien derrière lui, et rend son échec. */
    private fun abandon(msgId: Int, reason: String, ackStatus: Int? = null): Event.Failed {
        streams.remove(msgId)?.sink?.abort()
        remember(msgId)
        return Event.Failed(msgId, reason, ackStatus)
    }

    /** Nombre de flux en cours de réassemblage. */
    fun activeStreams(): Int = streams.size

    /**
     * Abandonne les flux muets depuis [staleAfterMs] — lien mort, pair parti.
     * Le fichier partiel part avec, sans attendre la fermeture de la cabine :
     * personne ne réclamera plus ces octets-là.
     */
    fun prune(): List<Event.Failed> {
        val now = nowMs()
        val stale = streams.filterValues { now - it.lastSeenMs > staleAfterMs }.keys.toList()
        return stale.map { msgId -> abandon(msgId, "transfert interrompu") }
    }

    /** La cabine ferme : plus rien n'aboutira, et rien ne doit rester. */
    fun abortAll() {
        streams.values.forEach { it.sink.abort() }
        streams.clear()
    }

    private fun remember(msgId: Int) {
        finished.addLast(msgId)
        if (finished.size > FINISHED_MAX) finished.removeFirst()
    }

    private companion object {
        const val FINISHED_MAX = 128
    }
}
