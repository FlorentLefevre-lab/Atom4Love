package one.astroport.atom4love.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.Wave

private val Context.incarnationDataStore by preferencesDataStore(name = "incarnation")

/** Ce que la station retrouve au réveil : la fiche, et si le noyau est scellé. */
data class SavedIncarnation(val birth: BirthData, val forged: Boolean)

/**
 * Persistance de l'incarnation. On ne stocke JAMAIS la clé elle-même — c'est le
 * principe de la clé LOVE : elle se redérive des cinq données de naissance à
 * chaque démarrage. Seules la fiche et son état (scellée ou non) survivent.
 */
class IncarnationStore(private val context: Context) {

    private object Keys {
        val Year = intPreferencesKey("birth_year")
        val Month = intPreferencesKey("birth_month")
        val Day = intPreferencesKey("birth_day")
        val Hour = intPreferencesKey("birth_hour")
        val Minute = intPreferencesKey("birth_minute")
        val PlaceName = stringPreferencesKey("birth_place_name")
        val Lat = doublePreferencesKey("birth_lat")
        val Lon = doublePreferencesKey("birth_lon")
        val Wave = stringPreferencesKey("birth_wave")
        val WeightKg = floatPreferencesKey("birth_weight_kg")
        val Forged = booleanPreferencesKey("forged")
    }

    /** null tant que rien n'a jamais été saisi sur cet appareil. */
    suspend fun load(): SavedIncarnation? {
        val p = context.incarnationDataStore.data.first()
        val birth = BirthData(
            year = p[Keys.Year],
            month = p[Keys.Month],
            day = p[Keys.Day],
            hour = p[Keys.Hour],
            minute = p[Keys.Minute],
            placeName = p[Keys.PlaceName] ?: "",
            lat = p[Keys.Lat],
            lon = p[Keys.Lon],
            wave = p[Keys.Wave]?.let { name -> Wave.entries.firstOrNull { it.name == name } },
            weightKg = p[Keys.WeightKg],
        )
        // Une fiche même partielle se restaure — on ne perd jamais une saisie en cours.
        if (birth == BirthData.Empty) return null
        return SavedIncarnation(birth = birth, forged = p[Keys.Forged] ?: false)
    }

    /** Dissolution du noyau : la station oublie tout — la fiche se redérive
     *  à l'identique si les cinq mêmes données sont ressaisies un jour. */
    suspend fun clear() {
        context.incarnationDataStore.edit { it.clear() }
    }

    suspend fun save(birth: BirthData, forged: Boolean) {
        context.incarnationDataStore.edit { p ->
            fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T?) {
                if (value == null) p.remove(key) else p[key] = value
            }
            set(Keys.Year, birth.year)
            set(Keys.Month, birth.month)
            set(Keys.Day, birth.day)
            set(Keys.Hour, birth.hour)
            set(Keys.Minute, birth.minute)
            p[Keys.PlaceName] = birth.placeName
            set(Keys.Lat, birth.lat)
            set(Keys.Lon, birth.lon)
            set(Keys.Wave, birth.wave?.name)
            set(Keys.WeightKg, birth.weightKg)
            p[Keys.Forged] = forged
        }
    }
}
