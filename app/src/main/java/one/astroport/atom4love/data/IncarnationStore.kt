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
        val year = p[Keys.Year] ?: return null
        val wave = p[Keys.Wave]?.let { name ->
            Wave.entries.firstOrNull { it.name == name }
        } ?: return null
        return SavedIncarnation(
            birth = BirthData(
                year = year,
                month = p[Keys.Month] ?: return null,
                day = p[Keys.Day] ?: return null,
                hour = p[Keys.Hour] ?: return null,
                minute = p[Keys.Minute] ?: return null,
                placeName = p[Keys.PlaceName] ?: return null,
                lat = p[Keys.Lat] ?: return null,
                lon = p[Keys.Lon] ?: return null,
                wave = wave,
                weightKg = p[Keys.WeightKg] ?: return null,
            ),
            forged = p[Keys.Forged] ?: false,
        )
    }

    suspend fun save(birth: BirthData, forged: Boolean) {
        context.incarnationDataStore.edit { p ->
            p[Keys.Year] = birth.year
            p[Keys.Month] = birth.month
            p[Keys.Day] = birth.day
            p[Keys.Hour] = birth.hour
            p[Keys.Minute] = birth.minute
            p[Keys.PlaceName] = birth.placeName
            p[Keys.Lat] = birth.lat
            p[Keys.Lon] = birth.lon
            p[Keys.Wave] = birth.wave.name
            p[Keys.WeightKg] = birth.weightKg
            p[Keys.Forged] = forged
        }
    }
}
