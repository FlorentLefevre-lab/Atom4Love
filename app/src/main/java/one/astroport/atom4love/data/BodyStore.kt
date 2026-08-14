package one.astroport.atom4love.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import one.astroport.atom4love.domain.BodyMetrics

private val Context.bodyDataStore by preferencesDataStore(name = "body")

/**
 * Persistance des mesures du corps — son propre magasin, à côté de celui de
 * l'incarnation et jamais dedans.
 *
 * Ce n'est pas une commodité de rangement, c'est la règle rendue structurelle :
 * la fiche d'incarnation est scellée et fait la clé, ces deux mesures changent
 * avec le corps et ne font que l'onde biologique. Les mêler dans un même
 * enregistrement inviterait tôt ou tard à les mêler dans un même SALT.
 */
class BodyStore(private val context: Context) {

    private object Keys {
        val HeightCm = intPreferencesKey("current_height_cm")
        val WeightKg = floatPreferencesKey("current_weight_kg")
    }

    /** [BodyMetrics.Empty] tant que rien n'a été mesuré sur cet appareil. */
    suspend fun load(): BodyMetrics {
        val p = context.bodyDataStore.data.first()
        return BodyMetrics(heightCm = p[Keys.HeightCm], weightKg = p[Keys.WeightKg])
    }

    suspend fun save(body: BodyMetrics) {
        context.bodyDataStore.edit { p ->
            body.heightCm?.let { p[Keys.HeightCm] = it } ?: p.remove(Keys.HeightCm)
            body.weightKg?.let { p[Keys.WeightKg] = it } ?: p.remove(Keys.WeightKg)
        }
    }

    /**
     * Dissoudre le noyau efface aussi le corps. La station promet d'oublier ;
     * garder au chaud la taille et le poids de quelqu'un qui vient de tout
     * effacer serait tenir cette promesse à moitié.
     */
    suspend fun clear() {
        context.bodyDataStore.edit { it.clear() }
    }
}
