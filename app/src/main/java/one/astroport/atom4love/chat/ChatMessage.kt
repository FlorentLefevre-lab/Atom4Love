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
    /**
     * Étiquette de l'expéditeur : « moi », le npub court du pair quand son
     * attestation a été vérifiée, ou à défaut un suffixe d'adresse radio.
     * [fromAttested] dit lequel des deux, car ils ne s'annoncent pas pareil.
     */
    val from: String,
    /**
     * L'étiquette est une identité attestée, pas une adresse. Une adresse
     * s'annonce « pair 36219 » ; un npub se donne tel quel, comme dans la liste
     * de ceux qui sont là. Dire « pair » devant un npub serait bavard, et
     * traiter un port comme un nom serait faux.
     */
    val fromAttested: Boolean = false,
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
