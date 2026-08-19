package one.astroport.atom4love.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.pseudoDataStore by preferencesDataStore(name = "pseudo")

/**
 * Le nom sous lequel on paraît — celui que les autres lisent à la place du npub.
 *
 * ## Pourquoi il a son propre magasin
 *
 * Pour la même raison que [BodyStore] : **ce qui se change ne dort jamais à
 * côté de ce qui est scellé.** La fiche d'incarnation fait la clé — cinq
 * données, un SALT, un npub, et plus rien ne bouge. Le pseudo ne fait rien du
 * tout : il n'entre dans aucun sel, ne dérive aucune clé, et se change autant
 * de fois qu'on veut sans que personne ne perde de vue qui l'on est. Les ranger
 * ensemble inviterait un jour à les mêler dans une même dérivation, et ce jour-là
 * changer de nom changerait d'identité.
 *
 * ## Ce qu'il remplace, et ce qu'il ne cache pas
 *
 * Il remplace le npub **à l'affichage**, partout où une personne en désigne une
 * autre. Il ne le remplace pas dans le protocole : l'attestation
 * ([one.astroport.atom4love.noise.NoiseVouch]) continue de lier la clé NOSTR à
 * la clé Noise, et c'est elle qui dit qui parle. Le pseudo n'est donc pas une
 * identité de plus, c'est une **étiquette** posée sur une identité déjà prouvée.
 *
 * ⚠ Il se déclare, il ne se prouve pas. Deux personnes peuvent choisir le même
 * mot, et rien ne l'empêche — c'est le npub attesté qui les sépare, en dessous,
 * exactement comme avant. Ce qui change n'est pas la sécurité de la rencontre,
 * c'est ce qu'on lit : `npub1u9v…eqx2` ne se retient pas, ne se prononce pas à
 * voix haute dans un bar, et ne se reconnaît pas d'un écran à l'autre.
 */
class PseudoStore(private val context: Context) {

    private object Keys {
        val Pseudo = stringPreferencesKey("pseudo")
    }

    /** Vide tant que l'assistant n'en a pas demandé un. */
    suspend fun load(): String = context.pseudoDataStore.data.first()[Keys.Pseudo].orEmpty()

    suspend fun save(pseudo: String) {
        val clean = Pseudo.clean(pseudo)
        context.pseudoDataStore.edit { p ->
            if (clean.isEmpty()) p.remove(Keys.Pseudo) else p[Keys.Pseudo] = clean
        }
    }

    /**
     * Dissoudre le noyau efface le nom avec. Même promesse que le corps : la
     * station oublie, et un nom qui survivrait au noyau reparaîtrait sur la
     * fiche du suivant.
     */
    suspend fun clear() {
        context.pseudoDataStore.edit { it.clear() }
    }
}

/** Les règles du nom — pures, donc testables sans Android. */
object Pseudo {

    /**
     * Assez pour se distinguer dans une salle. Deux caractères laisseraient
     * passer « A », qui ne désigne personne.
     */
    const val MIN_LENGTH = 2

    /**
     * ⚠ **Ce plafond est une contrainte de radio, pas de goût.** Le nom voyage
     * dans une trame scellée sur un lien BLE, dont la charge utile tombe à une
     * douzaine d'octets au MTU plancher : un nom long se fragmenterait, pour un
     * mot que l'écran tronquerait de toute façon dans une ligne de conversation.
     * Vingt-quatre caractères tiennent dans une rangée de liste sur le plus
     * étroit des téléphones, accents compris.
     */
    const val MAX_LENGTH = 24

    /**
     * Ce qu'on garde de ce qui a été tapé.
     *
     * Les espaces de tête et de queue partent — ils ne se voient pas et font
     * deux noms là où l'œil n'en lit qu'un. Les retours à la ligne et les
     * tabulations deviennent des espaces : un nom tient sur une ligne, et un
     * collage depuis une autre application en apporte régulièrement.
     */
    fun clean(raw: String): String =
        raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
            .trim()
            .take(MAX_LENGTH)

    /** Un pseudo qu'on a le droit de garder. */
    fun isValid(raw: String): Boolean = clean(raw).length >= MIN_LENGTH

    /**
     * **Deux personnes peuvent choisir le même pseudo. Voici ce qui se passe.**
     *
     * Rien ne l'empêche, et rien ne doit l'empêcher : un pseudo se déclare, il
     * ne s'attribue pas. Il n'y a pas de registre, pas de première arrivée, pas
     * d'autorité qui distribue les mots — et il n'en faut pas. Quelqu'un qui
     * s'appelle comme un autre n'usurpe rien, parce que **le pseudo n'a jamais
     * été l'identité** : celle-ci est la clé NOSTR, prouvée au handshake par
     * [one.astroport.atom4love.noise.NoiseVouch], et deux homonymes ont deux
     * clés différentes. La sécurité de la rencontre ne bouge pas d'un pouce.
     *
     * Ce qui bouge, c'est l'écran : deux lignes « Marie » dans une liste ne se
     * distinguent plus, et c'est un vrai défaut — on écrirait à la mauvaise
     * personne.
     *
     * ⚠ **La règle : quand le pseudo ne suffit plus, l'identité reparaît — pour
     * ceux-là seulement, et juste assez.** Les quatre derniers caractères du
     * npub séparent les homonymes ; tous les autres gardent leur pseudo nu. On
     * ne remet pas la clé sur tous les écrans pour un cas qui n'arrive presque
     * jamais, et on ne laisse pas non plus deux inconnus porter le même mot.
     *
     * Quatre caractères de base32 font un million de combinaisons — largement
     * de quoi séparer les quelques homonymes d'une salle, et trop peu pour
     * qu'on prétende lire une clé dedans.
     *
     * [who] associe un npub (`npub1…`) au pseudo déclaré, null quand il n'y en
     * a pas. Le résultat associe le même npub à **ce que l'écran doit écrire**,
     * null quand la personne ne s'est pas nommée — c'est alors à l'écran de
     * choisir le mot, dans sa langue.
     */
    fun labels(who: Map<String, String?>): Map<String, String?> {
        val shared = who.values
            .filterNotNull()
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        return who.mapValues { (npub, name) ->
            when {
                name == null -> null
                name !in shared -> name
                // `npub1…eqx2` : la queue, jamais la tête — les premiers
                // caractères sont le préfixe bech32 et le même pour tout le
                // monde, ils ne sépareraient rien.
                else -> "$name · ${npub.takeLast(DISAMBIGUATION)}"
            }
        }
    }

    /** Combien de caractères de clé il faut pour séparer deux homonymes. */
    const val DISAMBIGUATION = 4
}
