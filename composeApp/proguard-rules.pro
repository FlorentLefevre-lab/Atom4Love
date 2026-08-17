# Règles R8 pour la release ATOM4LOVE.
# Complète proguard-android-optimize.txt (voir app/build.gradle.kts).

# --- Crypto NOSTR : secp256k1 charge sa bibliothèque native par JNI ---
# Les noms de classes et méthodes natives doivent survivre à l'obfuscation,
# sinon le chargement JNI échoue à l'exécution.
-keep class fr.acinq.secp256k1.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- H3 : même contrainte, pavage hexagonal via JNI ---
-keep class com.uber.h3core.** { *; }

# --- kotlinx.serialization ---
# Les sérialiseurs sont générés et référencés par réflexion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class one.astroport.atom4love.** {
    *** Companion;
}
-keepclasseswithmembers class one.astroport.atom4love.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- Modèles de données conservés pour le débogage des crashs ---
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
