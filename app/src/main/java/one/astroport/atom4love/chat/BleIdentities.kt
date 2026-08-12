package one.astroport.atom4love.chat

/**
 * Qui se cache derrière une adresse BLE — appris après coup, faute de mieux.
 *
 * Une annonce de cabine ne porte que l'UUID du service : rien qui dise qui
 * annonce, et c'est voulu — un identifiant en clair dans l'air suivrait son
 * porteur de pièce en pièce. On ne peut donc savoir à qui l'on parle qu'une
 * fois connecté et le pair attesté.
 *
 * Reste que cette réponse, une fois payée, mérite d'être gardée : un pair
 * déjà joignable par un médium plus rapide n'a aucune raison d'être rappelé
 * en BLE. Sans cette mémoire, chaque passage du scan rouvrait un lien vers
 * quelqu'un à qui l'on parlait déjà en Wi-Fi, et le va-et-vient reprenait à
 * la première coupure.
 *
 * Ce que ça ne règle pas, et ne peut pas régler ici : les adresses tournent.
 * Une adresse neuve est un inconnu, et le seul moyen de savoir reste de s'y
 * connecter une fois.
 */
internal class BleIdentities(private val capacity: Int = CAPACITY) {

    private val known = LinkedHashMap<String, String>()

    /** L'attestation vient d'aboutir : cette adresse a un nom. */
    fun learn(address: String, peerHex: String) {
        known.remove(address)
        known[address] = peerHex
        while (known.size > capacity) {
            known.remove(known.keys.first())
        }
    }

    fun peerAt(address: String): String? = known[address]

    /**
     * Faut-il composer vers cette adresse ?
     *
     * Non si elle porte quelqu'un que l'on atteint déjà autrement. Un inconnu,
     * lui, se compose toujours : c'est la seule façon d'apprendre. Et si le
     * médium rapide tombe, le pair sort de [reachedBeyondBle] et redevient
     * appelable — le BLE reste la porte, on ne fait que cesser d'y frapper
     * quand quelqu'un a déjà ouvert.
     */
    fun shouldDial(address: String, reachedBeyondBle: Set<String>): Boolean {
        val who = known[address] ?: return true
        return who !in reachedBeyondBle
    }

    private companion object {
        /** Une cabine ne croise pas des milliers d'adresses ; la map reste bornée. */
        const val CAPACITY = 64
    }
}
