package one.astroport.atom4love.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Les visages qu'on nous a montrés — **un par personne, le dernier**.
 *
 * ## Pourquoi ce registre existe, à côté des messages
 *
 * Un selfie de reconnaissance arrive comme une pièce jointe et vit, à ce
 * titre, dans la liste des messages du moteur. Ça suffit à l'afficher tant que
 * la causerie tourne — et ça ne suffit à rien d'autre : la liste se vide quand
 * la cabine se ferme, et un moteur neuf ne sait plus rien de ce qu'on lui a
 * montré il y a deux minutes.
 *
 * Demandé par Florent le 20/08 : **garder le visage en mémoire, associé à la
 * personne, pour pouvoir le réafficher.** D'où ce registre, tenu par identité
 * (la clé publique du pair) et non par message.
 *
 * ## Ce qu'il garde, et ce qu'il ne garde pas
 *
 * ⚠ **Le dernier visage écrase le précédent.** Quelqu'un qui se remontre a
 * changé de lumière, d'angle ou de pièce : c'est cette photo-là qui sert à le
 * reconnaître maintenant. Un album de tous les visages qu'on a croisés serait
 * exactement ce que le projet refuse de fabriquer.
 *
 * ⚠ **Rien ne descend sur le disque ici.** Le registre est une mémoire de
 * PROCESSUS : il meurt avec l'application, comme la cabine. Le fichier, lui,
 * vit dans `files/chat/` le temps de la session et part avec
 * [Attachments.wipe]. « Fermer = effacer » reste vrai.
 */
object Faces {

    /** Un visage, et le nom qu'il portait quand il est arrivé. */
    data class Face(
        val file: File,
        /** Le pseudo au moment de l'arrivée — null s'il ne s'était pas encore nommé. */
        val pseudo: String?,
        val atMs: Long = System.currentTimeMillis(),
    )

    private val _faces = MutableStateFlow<Map<String, Face>>(emptyMap())

    /** Par clé publique NOSTR hexadécimale du pair. */
    val faces: StateFlow<Map<String, Face>> = _faces.asStateFlow()

    /**
     * Un visage vient d'arriver — il prend la place de celui d'avant.
     *
     * Rend `true` si c'est une nouveauté (fichier différent), de quoi lever le
     * bandeau une fois et une seule.
     */
    fun show(peerHex: String, file: File, pseudo: String?): Boolean {
        val known = _faces.value[peerHex]
        if (known?.file?.path == file.path) {
            // Même photo, pseudo peut-être appris depuis : on complète sans
            // rien annoncer.
            if (pseudo != null && known.pseudo != pseudo) {
                _faces.update { it + (peerHex to known.copy(pseudo = pseudo)) }
            }
            return false
        }
        _faces.update { it + (peerHex to Face(file, pseudo)) }
        return true
    }

    /** Le pseudo arrive toujours après l'attestation : on complète quand il tombe. */
    fun name(peerHex: String, pseudo: String) {
        val known = _faces.value[peerHex] ?: return
        if (known.pseudo == pseudo) return
        _faces.update { it + (peerHex to known.copy(pseudo = pseudo)) }
    }

    /** La station oublie tout : les visages partent avec le reste. */
    fun clear() {
        _faces.value = emptyMap()
    }
}
