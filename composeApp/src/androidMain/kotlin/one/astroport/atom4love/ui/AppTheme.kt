package one.astroport.atom4love.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * La lumière sous laquelle on regarde la station : la nuit d'origine, ou le
 * jour. Un interrupteur, pas un menu — il n'y a que deux heures possibles.
 *
 * **Le réglage est à nous**, contrairement à la langue qui vit dans le système
 * ([AppLocale]) : Android n'offre pas de thème par application, seulement un
 * thème global qu'on ne veut pas piloter depuis ici.
 *
 * Il dort dans des `SharedPreferences` et non dans le DataStore du reste du
 * projet, pour une raison précise : **il se lit au tout premier frame**, avant
 * que quoi que ce soit soit dessiné. Un DataStore ne se lit qu'en suspendant ;
 * l'écran s'ouvrirait dans la nuit puis basculerait au jour sous les yeux de
 * qui a choisi le jour. Une préférence d'affichage se lit d'un bloc ou ne se lit
 * pas — les données de l'incarnation, elles, restent au DataStore.
 */
object AppTheme {

    private const val PREFS = "apparence"
    private const val KEY_DARK = "sombre"

    /**
     * L'état vivant, partagé par l'activité qui peint les barres système et par
     * l'interrupteur de l'en-tête. Chargé une fois, gardé en mémoire ensuite :
     * la préférence ne change que par le geste qui est juste là.
     */
    private var loaded = false
    var dark by mutableStateOf(false)
        private set

    /** **Le jour tant que rien n'a été choisi** : c'est la station à l'ouverture. */
    fun load(context: Context) {
        if (loaded) return
        dark = prefs(context).getBoolean(KEY_DARK, false)
        loaded = true
    }

    fun toggle(context: Context) {
        dark = !dark
        loaded = true
        prefs(context).edit().putBoolean(KEY_DARK, dark).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
