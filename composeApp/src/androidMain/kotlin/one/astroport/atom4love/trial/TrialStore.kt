package one.astroport.atom4love.trial

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.trialDataStore by preferencesDataStore(name = "trial")

/**
 * Ce que la période d'essai a besoin de retenir — **trois choses, pas une de
 * plus**.
 *
 * Le point de départ ([Trial.Origin]), et le fait que la proposition ait été
 * refusée. C'est tout. Il n'y a pas d'historique de positions, pas de compteur
 * d'ouvertures, pas de date de dernière visite : chacune de ces choses serait
 * un suivi, et aucune ne servirait à décider quoi que ce soit de plus.
 *
 * ⚠ **Le refus est retenu, l'acceptation ne l'est pas.** Ce n'est pas une
 * asymétrie d'humeur : accepter mène au MULTIPASS, et c'est le **coffre du
 * compte** ([one.astroport.atom4love.data.MultipassStore]) qui fait foi ensuite —
 * lui seul sait si la clé LOVE a été activée, et il le sait mieux que nous. Ne
 * retenir que le refus évite d'avoir deux sources de vérité sur la même
 * question.
 */
class TrialStore(private val context: Context) {

    private object Keys {
        val Lat = doublePreferencesKey("origin_lat")
        val Lon = doublePreferencesKey("origin_lon")
        val At = longPreferencesKey("origin_at")
        val Declined = booleanPreferencesKey("declined")
    }

    /** Le point de départ, ou null tant que le noyau n'a pas été forgé. */
    suspend fun origin(): Trial.Origin? {
        val p = context.trialDataStore.data.first()
        val at = p[Keys.At] ?: return null
        return Trial.Origin(lat = p[Keys.Lat], lon = p[Keys.Lon], atMs = at)
    }

    /**
     * Pose le départ, **une seule fois** : le premier appel gagne.
     *
     * La forge peut arriver avant que le GPS ait rendu un point ; l'écran
     * rappelle donc cette méthode quand la position se résout enfin, et il faut
     * qu'elle complète le départ sans en déplacer la date. Sinon, chaque fix
     * repousserait l'horloge de trois heures et la proposition n'arriverait
     * jamais — un bug qui ne se voit qu'au bout d'une soirée.
     */
    suspend fun begin(lat: Double?, lon: Double?, atMs: Long) {
        context.trialDataStore.edit { p ->
            if (p[Keys.At] == null) p[Keys.At] = atMs
            // Les coordonnées, elles, se complètent : un départ daté sans lieu
            // ne sert à rien, et le premier fix qui arrive est encore celui du
            // lieu de la forge — on ne parcourt pas deux kilomètres entre la
            // forge et la résolution d'une cellule.
            if (p[Keys.Lat] == null && lat != null) p[Keys.Lat] = lat
            if (p[Keys.Lon] == null && lon != null) p[Keys.Lon] = lon
        }
    }

    suspend fun declined(): Boolean =
        context.trialDataStore.data.first()[Keys.Declined] == true

    suspend fun decline() {
        context.trialDataStore.edit { it[Keys.Declined] = true }
    }

    /**
     * Le refus se reprend — en ouvrant le MULTIPASS depuis le mur, ou depuis le
     * Noyau. Rien n'est définitif ici : ce qui l'est, c'est le certificat qu'on
     * publie, pas la décision de ne pas encore le publier.
     */
    suspend fun reconsider() {
        context.trialDataStore.edit { it.remove(Keys.Declined) }
    }

    /** Dissoudre le noyau remet l'essai à zéro : un noyau neuf n'a pas d'histoire. */
    suspend fun clear() {
        context.trialDataStore.edit { it.clear() }
    }
}
