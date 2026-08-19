package one.astroport.atom4love.chat

import java.io.File

enum class ChatKind { TEXT, IMAGE, FILE }

/**
 * CANCELLED n'est pas un FAILED : rien n'a raté, quelqu'un a renoncé. Les deux
 * bouts l'affichent — celui qui a renoncé, et celui qui attendait.
 */
enum class ChatStatus { SENDING, SENT, DELIVERED, RECEIVING, RECEIVED, FAILED, CANCELLED }

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
    /**
     * **Avec qui** — la clé publique NOSTR du pair, en hexadécimal.
     *
     * ⚠ C'est ce champ qui a fait passer la cabine d'une salle à des
     * conversations. Un message n'appartenait à personne : il était dit à tous
     * ceux qui étaient là, et la liste des messages était la salle elle-même.
     * En le rattachant à un pair — le destinataire quand il part, l'expéditeur
     * quand il arrive — la même liste se relit comme autant de fils séparés,
     * sans qu'aucun octet du transport ait changé.
     *
     * null pour un message qui n'a pas de correspondant identifié : il en reste
     * un cas, l'envoi vers un lien non attesté, qu'on ne sait rattacher à
     * personne parce que personne n'a signé derrière.
     */
    val peer: String? = null,
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
