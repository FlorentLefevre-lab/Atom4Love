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

/**
 * L'instant de la compilation, en millisecondes. Rendu à la seconde dans la
 * bulle de version des Réglages.
 *
 * Un nombre et non une chaîne toute faite : la date se met en forme à
 * l'exécution, dans la langue de la personne. Une chaîne écrite ici parlerait
 * la langue de la machine qui a compilé.
 *
 * ⚠ Lu à la configuration, donc **gelé par le cache de configuration** de
 * Gradle : deux compilations qui réutilisent le cache portent le même instant.
 * Le cache tombe dès qu'un fichier de build change — et un `--rerun-tasks` ou
 * un `clean` rend toujours l'heure vraie. Pour un numéro qui sert à savoir
 * quel APK tourne sur l'appareil, c'est assez ; l'exactitude à la seconde d'un
 * build incrémental coûterait une tâche de génération à l'exécution.
 */
val buildTimeMs: Long = System.currentTimeMillis()

/**
 * Le commit court, ou « nogit » hors dépôt. Lu à la configuration : un build
 * debug doit pouvoir se nommer lui-même (voir buildTypes.debug).
 */
// `providers.exec` et non un ProcessBuilder : le cache de configuration de
// Gradle refuse tout processus lancé à la main depuis un build.gradle.
val gitShortHash: String = runCatching {
    val head = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()
    val dirty = providers.exec {
        commandLine("git", "status", "--porcelain")
    }.standardOutput.asText.get().isNotBlank()
    // Le « + » dit que le build contient du travail non commité : sans lui,
    // deux APK très différents porteraient le même numéro.
    if (dirty) "$head+" else head
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: "nogit"

android {
    namespace = "one.astroport.atom4love"
    compileSdk = 36

    // ── Arborescence Kotlin Multiplatform ────────────────────────────────────
    //
    // Le module ne compile que pour Android, mais ses répertoires portent déjà
    // les noms d'un projet KMP : `androidMain` au lieu de `main`, `kotlin` au
    // lieu de `java`, `androidUnitTest` et `androidInstrumentedTest` au lieu de
    // `test` et `androidTest`. `commonMain` et `iosMain` existent, vides.
    //
    // Ce sont exactement les noms qu'attend le plugin `kotlin.multiplatform`.
    // Le jour où on l'applique, ce bloc disparaît et pas un fichier ne bouge —
    // c'est tout l'objet de la manœuvre. Voir docs/note-portage-ios.md.
    //
    // ⚠ Un chemin de convention est perdu au passage : AGP ramasse tout seul
    // les règles R8 de `src/main/keepRules`, et ce chemin-là n'est pas
    // redéclarable ici. Le fichier a été déplacé sous `androidMain/keepRules`
    // où il n'est plus lu — sans conséquence, il ne contient que des
    // commentaires. Une vraie règle à garder irait dans proguard-rules.pro.
    //
    // ⚠⚠ `java.srcDirs` en plus de `kotlin.srcDirs`, et ce n'est pas une
    // ceinture-bretelles : les 29 fichiers `.java` de Noise vivent sous
    // `androidMain/kotlin`. Déclarés en `kotlin` SEULEMENT, le compilateur
    // Kotlin les lit pour résoudre les types — donc tout compile, et l'APK se
    // fabrique — mais javac ne les voit jamais : aucune classe n'est émise, et
    // la cabine chiffrée meurt au premier échange sur un NoClassDefFoundError.
    // Mesuré le 17/08 : APK vert, 27 tests rouges. Le jour où Noise passe en
    // Kotlin (étape 1 de docs/note-portage-ios.md), cette ligne part avec lui.
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            kotlin.srcDirs("src/commonMain/kotlin", "src/androidMain/kotlin")
            java.srcDirs("src/commonMain/kotlin", "src/androidMain/kotlin")
            res.srcDirs("src/androidMain/res")
            assets.srcDirs("src/androidMain/assets")
        }
        getByName("debug") {
            manifest.srcFile("src/androidDebug/AndroidManifest.xml")
            res.srcDirs("src/androidDebug/res")
        }
        getByName("test") {
            kotlin.srcDirs("src/commonTest/kotlin", "src/androidUnitTest/kotlin")
            // `java` en plus, pour la même raison qu'au-dessus : les tests
            // portent la copie Java de référence de Noise
            // (`com.southernstorm.noise.ref`), oracle du port Kotlin. Sans
            // cette ligne elle serait lue par kotlinc et jamais compilée par
            // javac — et le test différentiel comparerait le port à rien.
            java.srcDirs("src/commonTest/kotlin", "src/androidUnitTest/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDirs("src/androidInstrumentedTest/kotlin")
        }
    }

    defaultConfig {
        applicationId = "one.astroport.atom4love"
        // ⚠ Plancher FERME : Android 10 (API 29), on ne descend jamais en
        // dessous. C'est ce que la page de téléchargement promet, et ce que
        // l'application tient vraiment : avant Q, rejoindre un groupe Wi-Fi
        // Direct par identifiants n'existe pas (WifiP2pConfig.Builder) et
        // une pièce jointe ne peut pas être rangée dans Téléchargements.
        minSdk = 29
        targetSdk = 36
        // ⚠ Le versionCode est le SEUL nombre qu'Android regarde pour accepter
        // une mise à jour : il doit croître à chaque APK diffusé, et ne jamais
        // reculer. Tenu à la main, parce qu'un diff doit le montrer — le
        // dériver du dépôt (compte de commits) donne un nombre juste sur ce
        // poste et faux partout où le clone est superficiel.
        //
        // Un APK publié = un tag `vX.Y.Z` = un versionCode. Voir
        // `tools/release.sh`, qui refuse de publier si les trois divergent.
        versionCode = 4
        versionName = "0.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // secp256k1 et H3 embarquent des bibliothèques natives.
        // On limite les ABI aux architectures réellement utilisées.
        //
        // ⚠ x86_64 retiré le 15/08. L'artefact `h3-android` ne livre que
        // `android-arm64` et `android-arm` : il n'existe **aucun**
        // `libh3-java.so` pour x86_64, et le demander produisait un APK qui
        // s'installait sur un émulateur pour y casser au premier appel — donc
        // l'adresse `a4l:`, la balise et la carte, sans que rien n'ait prévenu
        // à la compilation. Mieux vaut ne pas s'installer que s'installer
        // cassé. Les images système « x86_64 with ARM support » (API 30+)
        // traduisent l'ARM et restent donc utilisables pour tester.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
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
        // ── Où l'on va chercher une version plus récente ──────────────────
        //
        // Ni Play Store ni F-Droid : l'APK se prend au dépôt du projet, et la
        // station de Fred en tient le miroir — il servait déjà un APK à cette
        // adresse-là, avec le bon type MIME, avant qu'on s'y mette.
        //
        // Le manifeste `latest.json` est le MÊME fichier aux deux endroits.
        // L'app essaie le premier, puis le second : peu importe lequel répond,
        // c'est l'empreinte SHA-256 qu'il porte qui dit quels octets sont bons.
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/FlorentLefevre-lab/Atom4Love/main/latest.json\"",
        )
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_MIRROR",
            "\"https://u.copylaradio.com/www/atom4love.json\"",
        )
        // La page humaine des versions : le repli quand le manifeste ne répond
        // pas, et le seul chemin pour qui préfère faire à la main.
        buildConfigField(
            "String",
            "RELEASES_URL",
            "\"https://github.com/FlorentLefevre-lab/Atom4Love/releases\"",
        )
        // L'instant de la compilation, mis en forme à l'exécution.
        buildConfigField("long", "BUILD_TIME_MS", "${buildTimeMs}L")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String

                // v2 suffit à installer (minSdk 29), et c'est tout ce qu'AGP
                // pose par défaut ici. v3 ajoute la ROTATION de clé : le jour
                // où cette clé-là devrait être remplacée, les appareils
                // accepteraient la nouvelle sans que personne désinstalle.
                // Sans v3, une clé perdue ou compromise est sans recours.
                //
                // ⚠ À poser AVANT la première version publiée : un APK déjà
                // installé ne connaît que les schémas qu'il portait.
                // Rétrocompatible par construction : le v3 est lu depuis
                // Android 9, soit avant notre plancher, et le v2 reste là
                // pour tout installeur qui ne le connaîtrait pas.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Le commit dans le numéro de version : l'onglet Réglages dit alors
            // quel APK tourne vraiment sur l'appareil. Deux builds debug se
            // ressemblent trop pour qu'on les distingue autrement, et chercher
            // un défaut dans une version qu'on n'a pas installée coûte cher.
            versionNameSuffix = "-debug-$gitShortHash"
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
