package one.astroport.atom4love.chat

/**
 * À quel rythme la cabine compose vers les adresses qu'elle voit passer.
 *
 * Deux faits mesurés le 2026-08-12, cabine ouverte entre les deux appareils du
 * banc, pendant que la conversation passait déjà par le Wi-Fi :
 *
 * - les adresses d'annonce tournent toutes les 20 à 40 s, si bien qu'un pair
 *   déjà relié se présente sans cesse sous un visage neuf — 8 adresses
 *   distinctes en 5 min pour un seul et même npub ;
 * - côté tablette, **8 compositions sur 8 sont mortes en `status=133`**, sur
 *   des annonces déjà périmées, dont trois vers la même adresse à 30 s
 *   d'intervalle. Zéro lien obtenu, pendant que le lien entrant et le Wi-Fi
 *   portaient tout.
 *
 * D'où les deux règles d'ici. **Composer sert à rencontrer quelqu'un de
 * nouveau ; quand on est déjà en conversation, la rencontre peut attendre** —
 * l'espacement passe de cinq secondes à une minute. Et une adresse qui refuse
 * la connexion s'éloigne à chaque refus, au lieu d'être rappelée au même
 * rythme jusqu'à ce qu'elle disparaisse.
 *
 * Ce que ça ne prétend pas faire : reconnaître un pair avant de s'y connecter.
 * L'annonce ne porte rien qui le permette, et c'est voulu. On réduit le coût
 * d'une découverte aveugle, on ne la rend pas clairvoyante.
 */
internal class DialPacing(private val capacity: Int = CAPACITY) {

    private val retryAfterMs = LinkedHashMap<String, Long>()
    private val failures = LinkedHashMap<String, Int>()
    /** null tant qu'aucune composition n'est partie : une sentinelle numérique
     *  ferait déborder la soustraction, et la toute première tentative serait
     *  refusée au lieu d'être la plus urgente. */
    private var lastDialMs: Long? = null

    /**
     * @param engaged vrai dès qu'un pair attesté est joignable — peu importe
     *   par quel lien, entrant compris : s'il nous parle, nous n'avons pas à
     *   l'appeler.
     */
    fun allow(address: String, now: Long, engaged: Boolean): Boolean {
        val spacing = if (engaged) SPACING_ENGAGED_MS else SPACING_IDLE_MS
        val last = lastDialMs
        if (last != null && now - last < spacing) return false
        val notBefore = retryAfterMs[address]
        if (notBefore != null && now < notBefore) return false
        return true
    }

    /** Une composition part : elle compte pour toutes les adresses. */
    fun dialed(now: Long) {
        lastDialMs = now
    }

    /** La connexion n'a jamais abouti : cette adresse s'éloigne un peu plus. */
    fun failed(address: String, now: Long) {
        val strikes = (failures[address] ?: 0) + 1
        remember(failures, address, strikes)
        val delay = (FAILED_BASE_MS shl (strikes - 1)).coerceAtMost(FAILED_MAX_MS)
        remember(retryAfterMs, address, now + delay)
    }

    /** Un lien qui marchait est tombé : on y revient vite, et l'ardoise est nette. */
    fun lost(address: String, now: Long) {
        failures.remove(address)
        remember(retryAfterMs, address, now + LOST_MS)
    }

    /** Pour les vérifications : le délai de grâce en cours sur une adresse. */
    fun notBefore(address: String): Long? = retryAfterMs[address]

    private fun <V> remember(map: LinkedHashMap<String, V>, key: String, value: V) {
        map.remove(key)
        map[key] = value
        while (map.size > capacity) {
            map.remove(map.keys.first())
        }
    }

    private companion object {
        /** Personne en ligne : la découverte est la seule chose qui compte. */
        const val SPACING_IDLE_MS = 5_000L

        /** Quelqu'un est là : une tentative par minute suffit pour un nouveau venu. */
        const val SPACING_ENGAGED_MS = 60_000L

        const val FAILED_BASE_MS = 30_000L
        const val FAILED_MAX_MS = 240_000L
        const val LOST_MS = 2_000L
        const val CAPACITY = 64
    }
}
