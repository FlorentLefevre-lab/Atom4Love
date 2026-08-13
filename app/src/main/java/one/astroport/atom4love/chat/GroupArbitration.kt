package one.astroport.atom4love.chat

/**
 * Qui garde son groupe Wi-Fi Direct quand deux pairs en ont ouvert un chacun.
 *
 * La collision est réelle et n'a rien d'exotique : la cabine propose la montée
 * **aux deux appareils au même instant** dès qu'aucune station commune n'est
 * utilisable. Les deux personnes voient donc l'invitation apparaître ensemble,
 * et rien n'empêche les deux de toucher dans la même seconde. Aucune n'a encore
 * reçu l'invitation de l'autre : les deux hébergent, et l'on obtient
 * exactement ce que le code voulait éviter — chacun maître du sien, personne
 * chez l'autre.
 *
 * Le départage ne coûte **aucun message** : les deux npub sont déjà connus des
 * deux côtés depuis l'attestation Noise. Chacun applique la même comparaison et
 * tombe sur la même réponse, donc exactement un des deux cède.
 *
 * Pourquoi le npub et non « le meilleur appareil » : la bande est déjà demandée
 * en 5 GHz par celui qui héberge, quel qu'il soit ([one.astroport.atom4love.chat.net.P2pGroup]),
 * et le débit d'un lien reste le minimum des deux radios. Élire le plus
 * capable coûterait un champ de protocole pour un gain non mesuré — et
 * demanderait de toute façon ce départage-ci pour deux appareils identiques.
 *
 * Pure JVM : testable hors appareil, ce qui est le moins pour une règle dont
 * les deux moitiés doivent s'accorder sans se parler.
 */
object GroupArbitration {

    /**
     * Vrai si **nous** devons céder notre groupe et rejoindre celui de [peer].
     *
     * Le plus petit npub garde le sien — le sens n'a aucune importance, seul
     * compte que les deux appliquent le même. Une clé absente ou vide ne
     * tranche rien : on garde ce qu'on a plutôt que de tout lâcher sur une
     * comparaison qui ne veut rien dire.
     */
    fun shouldYield(mine: String?, peer: String?): Boolean {
        if (mine.isNullOrBlank() || peer.isNullOrBlank()) return false
        // égalité : ce serait le même noyau des deux côtés, donc pas deux
        // appareils en collision. Personne ne cède, personne ne boucle.
        return peer < mine
    }
}
