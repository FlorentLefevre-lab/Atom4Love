package one.astroport.atom4love.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import one.astroport.atom4love.multipass.Atom4LoveActivation
import one.astroport.atom4love.multipass.MultipassResponse

private val Context.multipassDataStore by preferencesDataStore(name = "multipass")

/**
 * Le compte Astroport.ONE de la station, tel qu'elle l'a rendu.
 *
 * Rien ici ne se recalcule : contrairement au noyau — cinq données qui
 * redérivent la clé provisoire à volonté — un MULTIPASS est fabriqué par la
 * station, et ces clés-là sont les seules copies que l'appareil en détient.
 */
@Serializable
data class MultipassAccount(
    val email: String,
    val station: String,
    val npub: String,
    val hex: String,
    val nsec: String,
    val g1pub: String,
    val pass: String,
    val ssss: String,
    val isOrigin: Boolean,
    /** Vide tant qu'ATOM4LOVE n'a pas été activé sur ce compte. */
    val loveNpub: String = "",
    val loveHex: String = "",
    val loveNsec: String = "",
    /**
     * Ce que la station calcule et que cet appareil ne sait pas refaire : le KIN
     * du Tzolkin et la phase personnelle. Ils arrivaient avec l'activation et
     * repartaient avec le corps de réponse — gardés ici, faute de pouvoir les
     * dériver. 0 signifie « pas encore rendus », l'activation étant refusée à
     * ce jour.
     */
    val kinNum: Int = 0,
    val personalPhase: Double = 0.0,
) {
    /** La clé LOVE définitive est arrivée : le noyau provisoire peut céder la place. */
    val loveActivated: Boolean get() = loveNsec.isNotEmpty()
}

/**
 * Rangement du compte : un DataStore comme le reste de la station, mais dont
 * l'unique entrée est scellée par [DeviceVault] — donc illisible hors de cet
 * appareil.
 *
 * L'incarnation, elle, tient dans un DataStore en clair : ses cinq données ne
 * sont un secret pour personne et se ressaisissent. Le `nsec` du MULTIPASS
 * n'est ni redérivable ni ressaisissable — il ouvre les portefeuilles ẐEN du
 * compte. D'où le coffre, et d'où le fait qu'une dissolution du noyau ne doit
 * jamais l'effacer en silence.
 *
 * Le compte est scellé d'un bloc plutôt que champ par champ : l'email et la
 * station en disent déjà long sur qui est là, et un seul scellement garantit
 * qu'on ne relise jamais un compte à moitié écrit.
 */
class MultipassStore(context: Context) : AccountVault {

    private companion object {
        const val TAG = "Vault"
        val Sealed = stringPreferencesKey("account")
    }

    private val app = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    /** null tant qu'aucun MULTIPASS n'a été créé depuis cet appareil. */
    override suspend fun load(): MultipassAccount? {
        val sealed = app.multipassDataStore.data.first()[Sealed] ?: return null
        val plain = DeviceVault.open(sealed) ?: return null
        return runCatching { json.decodeFromString<MultipassAccount>(plain) }
            .onFailure { Log.w(TAG, "compte illisible", it) }
            .getOrNull()
    }

    suspend fun save(account: MultipassAccount) {
        val sealed = DeviceVault.seal(json.encodeToString(account))
        app.multipassDataStore.edit { it[Sealed] = sealed }
    }

    /** Le compte tel que la station vient de le créer, sans clé LOVE encore. */
    override suspend fun save(station: String, response: MultipassResponse) = save(
        MultipassAccount(
            email = response.email,
            station = station,
            npub = response.npub,
            hex = response.hex,
            nsec = response.nsec,
            g1pub = response.g1pub,
            pass = response.pass,
            ssss = response.ssss,
            isOrigin = response.isOrigin,
        ),
    )

    /**
     * La clé LOVE rendue par l'activation vient se poser sur le compte existant.
     * Sans compte enregistré, il n'y a rien à compléter : l'appel est ignoré.
     */
    override suspend fun saveLove(activation: Atom4LoveActivation): MultipassAccount? {
        val current = load() ?: return null
        val updated = current.copy(
            loveNpub = activation.loveNpub,
            loveHex = activation.loveHex,
            loveNsec = activation.loveNsec,
            kinNum = activation.kinNum,
            personalPhase = activation.personalPhase,
        )
        save(updated)
        return updated
    }

    /** Oublier le compte sur cet appareil — le MULTIPASS, lui, vit sur la station. */
    suspend fun clear() {
        app.multipassDataStore.edit { it.clear() }
    }
}
