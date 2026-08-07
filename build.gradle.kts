// Fichier racine : déclare les plugins sans les appliquer.
// Chaque module les active via son propre bloc `plugins { alias(...) }`.
plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin.android : intégré à AGP depuis la 9.0, ne plus le déclarer.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
