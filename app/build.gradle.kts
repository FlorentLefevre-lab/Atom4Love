import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    // kotlin.android : intégré à AGP depuis la 9.0, ne plus l'appliquer.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Signature de release : lue depuis keystore.properties (jamais versionné).
// Voir .gitignore — ce fichier contient les mots de passe du keystore.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "one.astroport.atom4love"
    compileSdk = 36

    defaultConfig {
        applicationId = "one.astroport.atom4love"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // secp256k1 et H3 embarquent des bibliothèques natives.
        // On limite les ABI aux architectures réellement utilisées.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        // Constantes de protocole ATOM4LOVE exposées via BuildConfig.
        buildConfigField("double", "PHI_DRIFT_HOURS", "14.83")
        buildConfigField("int", "GOLDBERG_PORTAL_COUNT", "12")
        buildConfigField("int", "H3_RESOLUTION", "8")
        // Port sondé sur la passerelle du Wi-Fi pour trouver le relais NOSTR
        // d'une station Astroport locale (LocalRelayScout).
        buildConfigField("int", "NOSTR_LOCAL_RELAY_PORT", "9999")
        // Guichet de la station Astroport.ONE où se crée un MULTIPASS (API
        // UPassport, dite « uSPOT »). UPlanet ORIGIN : le monde ouvert, celui
        // que le README d'Astroport.ONE appelle lui-même un bac à sable —
        // 1 Ẑen = 0,1 Ğ1, et un compte laissé à zéro y est purgé au bout de
        // sept jours. Le passage en UPlanet ẐEN demande une station dédiée.
        buildConfigField("String", "ASTROPORT_USPOT", "\"https://u.copylaradio.com\"")
        buildConfigField("boolean", "ASTROPORT_ORIGIN", "true")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            buildConfigField("String", "NOSTR_DEFAULT_RELAY", "\"wss://relay.copylaradio.com\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "NOSTR_DEFAULT_RELAY", "\"wss://relay.copylaradio.com\"")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
            )
        }
        // Réduit la taille de l'APK sans dégrader les .so natifs
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
        )
    }
}

// Schémas Room versionnés : indispensable pour tester les migrations.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // --- Socle AndroidX ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    // Deux volets côte à côte sur grand écran (tablette du banc, pliables).
    implementation(libs.bundles.adaptive)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- Hilt ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // --- Room ---
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    // --- Réseau / sérialisation ---
    implementation(libs.bundles.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    // --- Crypto NOSTR : clés MULTIPASS, signatures Schnorr BIP-340 ---
    implementation(libs.secp256k1.kmp)
    implementation(libs.secp256k1.kmp.jni.android)

    // --- Géographie : pavage hexagonal icosaédrique ---
    // AAR local patché : le libh3-java.so de com.uber:h3-android:4.4.0 omet libm
    // de ses DT_NEEDED → UnsatisfiedLinkError (« cannot locate symbol "cos" »)
    // au dlopen sur appareil. Corrigé via patchelf --add-needed libm.so sur les
    // deux ABI. À remplacer par libs.h3.android dès qu'une version upstream
    // corrigée existe (> 4.4.0).
    implementation(files("libs/h3-android-4.4.0-libm.aar"))
    implementation(libs.play.services.location)

    // --- Tâches de fond (dérive φ, synchro relais) ---
    implementation(libs.androidx.work.runtime.ktx)

    // --- Images ---
    implementation(libs.bundles.coil)

    // --- Vidéo : relecture dans la bulle (jamais de ré-encodage, cf. catalogue) ---
    implementation(libs.bundles.media3)

    // --- Tests unitaires ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Crypto NOSTR testée sur JVM (la variante Android de la lib native ne s'y charge pas).
    testImplementation(libs.secp256k1.kmp.jni.jvm)
    // H3 desktop : mêmes raisons — valide les portails Goldberg contre la vraie grille.
    testImplementation(libs.h3.jvm)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)

    // --- Tests instrumentés ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
