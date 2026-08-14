package one.astroport.atom4love.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.nostr.Bech32
import one.astroport.atom4love.nostr.LoveKeyForge
import one.astroport.atom4love.nostr.NostrKeys

private val Context.loveKeyDataStore by preferencesDataStore(name = "love_key")

/**
 * La clé du noyau, gardée entre deux démarrages.
 *
 * **C'est une rupture avec le principe d'origine**, et elle se paie : jusqu'ici
 * la station ne rangeait jamais sa clé, elle la redérivait des données de
 * naissance à chaque lancement, et c'était la meilleure preuve que la clé LOVE
 * ne tient qu'à la fiche. Mais la vraie dérivation d'Astroport.ONE coûte
 * 1 200 000 tours de PBKDF2 et 8 Mo de scrypt : la refaire à chaque ouverture
 * ferait attendre plusieurs secondes devant un écran vide, à chaque fois, pour
 * retrouver le même nombre.
 *
 * Ce qui est rangé l'est donc sous [DeviceVault] — AES-256-GCM, clé non
 * exportable de l'Android Keystore. Le fichier du DataStore, copié ailleurs,
 * ne rend rien. Et le principe survit là où il compte : **rien n'est
 * irremplaçable ici**. Si le coffre ne s'ouvre plus (restauration sur un autre
 * appareil, verrou d'écran réinitialisé), la clé se redérive de la fiche comme
 * avant — quelques secondes, une seule fois.
 */
class LoveKeyStore(private val context: Context) {

    private object Keys {
        val Nsec = stringPreferencesKey("love_nsec_sealed")
        val Fingerprint = stringPreferencesKey("birth_fingerprint")
    }

    /**
     * La clé de [birth] : rendue telle quelle si elle est déjà en coffre,
     * dérivée puis rangée sinon.
     *
     * L'empreinte de la fiche est stockée à côté : elle change dès qu'un
     * caractère du SALT ou du PEPPER change, et une clé qui ne correspond plus
     * à la fiche courante est jetée plutôt que rendue. Sans elle, dissoudre un
     * noyau puis en forger un autre ressortirait l'ancienne identité.
     *
     * Appeler hors du fil principal : la dérivation prend quelques secondes.
     */
    suspend fun loadOrDerive(birth: BirthData): NostrKeys = withContext(Dispatchers.Default) {
        val fingerprint = LoveKeyForge.fingerprint(birth)
        cached(fingerprint)?.let { return@withContext it }
        val keys = LoveKeyForge.forge(birth)
        runCatching {
            val sealed = DeviceVault.seal(keys.nsec)
            context.loveKeyDataStore.edit { p ->
                p[Keys.Nsec] = sealed
                p[Keys.Fingerprint] = fingerprint
            }
        }
        keys
    }

    /** La clé en coffre, si elle existe et correspond encore à la fiche. */
    private suspend fun cached(fingerprint: String): NostrKeys? {
        val p = context.loveKeyDataStore.data.first()
        if (p[Keys.Fingerprint] != fingerprint) return null
        val sealed = p[Keys.Nsec] ?: return null
        // Un coffre qui ne s'ouvre plus n'est pas une erreur : la fiche
        // redonne la clé. Idem pour un nsec devenu illisible.
        return runCatching {
            DeviceVault.open(sealed)?.let { NostrKeys(Bech32.decode(it).second) }
        }.getOrNull()
    }

    /** Dissolution du noyau : la clé rangée part avec la fiche. */
    suspend fun clear() {
        context.loveKeyDataStore.edit { it.clear() }
    }
}
