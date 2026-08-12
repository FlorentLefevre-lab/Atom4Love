package one.astroport.atom4love.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * Les langues que l'application sait parler, et le drapeau qui les désigne dans
 * l'en-tête.
 *
 * Le nom reste l'**endonyme** : « Español » s'écrit pareil que l'interface soit
 * en français ou en anglais. On cherche sa propre langue dans une liste, pas la
 * traduction de son nom — un hispanophone perdu dans une IHM française doit
 * reconnaître le sien du premier coup d'œil.
 *
 * Le drapeau est un raccourci **assumé, et faux si on le prend au mot** :
 * l'anglais n'appartient pas au Royaume-Uni, ni l'espagnol à l'Espagne. Il est
 * là pour se repérer d'un regard, pas pour dire d'où vient une langue.
 *
 * Cette liste doit rester d'accord avec `res/xml/locales_config.xml` : c'est
 * lui qu'Android lit, celle-ci ne fait que l'offrir à l'écran.
 */
enum class AppLanguage(
    /** Le code BCP-47, celui des dossiers `values-xx/` et de `locales_config`. */
    val tag: String,
    val flag: String,
    val endonym: String,
) {
    FR("fr", "🇫🇷", "Français"),
    EN("en", "🇬🇧", "English"),
    ES("es", "🇪🇸", "Español"),
    ;

    companion object {
        /** Celle de `values/` — la langue source, servie quand rien ne colle. */
        val Source = FR

        fun forTag(tag: String?): AppLanguage? =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
    }
}

/**
 * Le choix de langue **par application**, celui d'Android 13+.
 *
 * On ne tient aucun réglage à nous : la préférence vit dans le système. Elle se
 * relit dans Réglages › Applis › Atom4Love › Langue, elle survit à une mise à
 * jour, et elle ne fait pas une deuxième source de vérité à côté de celle que
 * l'utilisateur peut déjà changer sans nous.
 *
 * En dessous d'API 33, ce choix n'existe pas : l'application suit la langue du
 * téléphone. L'en-tête n'y montre alors aucun drapeau — mieux vaut ne rien
 * offrir qu'offrir un geste sans effet.
 */
object AppLocale {

    val selectable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * La langue réellement **affichée**, pas celle qui a été demandée : c'est
     * la configuration résolue qu'on lit, celle dont les ressources sortent.
     */
    fun shown(locales: LocaleList): AppLanguage {
        for (i in 0 until locales.size()) {
            AppLanguage.forTag(locales[i].language)?.let { return it }
        }
        return AppLanguage.Source
    }

    /**
     * Le système applique le changement et recrée l'activité de lui-même : rien
     * à propager à la main, la composition repart avec les bonnes ressources.
     */
    fun choose(context: Context, language: AppLanguage) {
        if (!selectable) return
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            LocaleList.forLanguageTags(language.tag)
    }
}
