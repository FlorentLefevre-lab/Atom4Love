package one.astroport.atom4love.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.welcomeDataStore by preferencesDataStore(name = "welcome")

/**
 * Ce que l'appareil retient des bienvenues déjà faites.
 *
 * Deux valeurs, et aucune n'est une donnée personnelle : un instant, et des
 * clés publiques déjà lues sur un relais public. Ça vit donc dans son propre
 * magasin, à côté du coffre et jamais dedans — rien ici ne mérite le Keystore,
 * et tout ici doit survivre à un redémarrage, sans quoi la fête recommence.
 */
class WelcomeStore(private val context: Context) {

    private object Keys {
        val Celebrated = stringSetPreferencesKey("celebrated")
        val LastSeenAt = longPreferencesKey("last_seen_at")
    }

    /** Entrées `<clé publique>:<createdAt>` — cf. `Welcome.remember`. */
    suspend fun celebrated(): Set<String> =
        context.welcomeDataStore.data.first()[Keys.Celebrated].orEmpty()

    /**
     * L'instant de la dernière veille, en **secondes** — le `since` d'un filtre
     * NOSTR, qui compte en secondes Unix.
     *
     * Zéro tant qu'on n'a jamais écouté : la première souscription part alors
     * sans borne basse, et c'est le filtre « récent » qui fait le tri. On ne
     * remonte pas artificiellement en arrière pour fêter des gens qu'on aurait
     * manqués il y a un mois.
     */
    suspend fun lastSeenAt(): Long =
        context.welcomeDataStore.data.first()[Keys.LastSeenAt] ?: 0L

    suspend fun save(celebrated: Set<String>, lastSeenAt: Long) {
        context.welcomeDataStore.edit { p ->
            p[Keys.Celebrated] = celebrated
            p[Keys.LastSeenAt] = lastSeenAt
        }
    }

    /**
     * Dissoudre le noyau efface aussi cette mémoire — comme [BodyStore.clear].
     * Elle ne dit rien de nous, mais elle dit qui on a vu arriver, et une
     * station qui promet d'oublier ne garde pas ça au chaud.
     */
    suspend fun clear() {
        context.welcomeDataStore.edit { it.clear() }
    }
}
