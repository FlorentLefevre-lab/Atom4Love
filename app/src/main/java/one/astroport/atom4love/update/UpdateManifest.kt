package one.astroport.atom4love.update

import android.os.Build
import kotlinx.serialization.Serializable
import one.astroport.atom4love.BuildConfig

/**
 * Ce qu'une version publiée déclare d'elle-même — le fichier `latest.json`.
 *
 * Un seul format, publié à deux endroits (voir [UpdateService]) : le dépôt
 * GitHub, qui fait foi, et le site de Fred, qui en est le miroir. L'app lit
 * l'un ou l'autre sans savoir lequel elle a eu : c'est le même fichier.
 *
 * Les champs inconnus sont ignorés à la lecture — une version future peut donc
 * en ajouter sans rendre le fichier illisible aux versions déjà installées.
 * C'est la seule promesse qui compte ici : **un vieil APK doit toujours savoir
 * lire le manifeste du jour**, sinon il ne saura jamais qu'il est vieux.
 */
@Serializable
data class UpdateManifest(
    /** Le seul nombre qui décide : Android refuse d'installer un code inférieur. */
    val versionCode: Int,
    /** Ce qui s'affiche à l'écran (« 0.2.0 »). */
    val versionName: String,
    /** En deçà, l'APK ne s'installera pas : autant ne rien proposer. */
    val minSdk: Int = 0,
    /** Empreinte SHA-256 de l'APK, en minuscules. Vérifiée avant d'installer. */
    val sha256: String,
    /** Taille en octets — sert à annoncer le poids avant de télécharger. */
    val sizeBytes: Long = 0,
    /** L'APK, chez GitHub. */
    val url: String,
    /** Les mêmes octets ailleurs, essayés dans l'ordre si le premier tombe. */
    val mirrors: List<String> = emptyList(),
    /** Ce que cette version change, en une phrase ou deux. */
    val notes: String = "",
    /** Date de publication, telle quelle (« 2026-08-17 »). */
    val publishedAt: String = "",
) {

    /** Toutes les adresses de l'APK, la principale d'abord. */
    val sources: List<String> get() = listOf(url) + mirrors

    /**
     * Vaut-il la peine d'être proposé à CET appareil ?
     *
     * Deux refus, et ils ne se confondent pas : une version qui n'est pas plus
     * récente n'a rien à dire, et une version qui demande un Android plus
     * récent que celui-là ne s'installerait pas — la proposer serait promettre
     * une porte qui ne s'ouvre pas.
     */
    fun isWorthOffering(
        installedCode: Int = BuildConfig.VERSION_CODE,
        sdk: Int = Build.VERSION.SDK_INT,
    ): Boolean = versionCode > installedCode && sdk >= minSdk
}
