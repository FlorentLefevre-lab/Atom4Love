package one.astroport.atom4love.chat

import java.io.File

enum class ChatKind { TEXT, IMAGE, FILE }

enum class ChatStatus { SENDING, SENT, DELIVERED, RECEIVING, RECEIVED, FAILED }

/**
 * Un message affiché par le panneau de causerie — indépendant du transport :
 * le POC BLE d'aujourd'hui et le chat Noise de demain produisent les mêmes.
 */
data class ChatMessage(
    val id: Int,
    val mine: Boolean,
    /** Étiquette de l'expéditeur (« moi » ou suffixe d'adresse radio). */
    val from: String,
    val kind: ChatKind,
    val status: ChatStatus,
    val text: String = "",
    /** Copie locale de la pièce jointe (émise ou reçue), null tant qu'elle n'existe pas. */
    val file: File? = null,
    val name: String = "",
    val mime: String = "",
    val sizeBytes: Int = 0,
    /** Avancement du transfert, 0..1 (SENDING/RECEIVING). */
    val progress: Float = 0f,
    val atMs: Long = System.currentTimeMillis(),
)
