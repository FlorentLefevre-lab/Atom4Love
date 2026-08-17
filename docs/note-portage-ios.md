# Porter Atom4Love sur iPhone

*17 août 2026 — état des lieux et contraintes de plateforme*

---

## Ce que dit le dépôt aujourd'hui

Atom4Love **n'est pas un projet multiplateforme**. `settings.gradle.kts` ne déclare
qu'un module, `include(":composeApp")`, et `composeApp/build.gradle.kts` applique le
plugin `android.application`, pas `kotlin.multiplatform`. Une seule chose a bougé,
le 17 août : les répertoires portent désormais les noms qu'attend KMP —
`androidMain/kotlin` au lieu de `main/java`, `androidUnitTest` et
`androidInstrumentedTest` au lieu de `test` et `androidTest`, et `commonMain`,
`commonTest`, `iosMain` existent, vides. Le bloc `sourceSets` du build les
raccorde à l'unique variante Android ; le jour où le plugin `kotlin.multiplatform`
est appliqué, ce bloc disparaît et pas un fichier ne se déplace.

C'est un décor, pas une cible : rien ne compile encore hors d'Android, et rien
n'empêche un `import android.*` de se glisser dans `commonMain`.

L'interface est **Compose, mais pas Compose Multiplatform** :

| | |
|---|---|
| `androidx.compose.*` — Jetpack Compose | 35 fichiers |
| `org.jetbrains.compose.*` — Compose Multiplatform | 0 fichier |
| Layouts XML | 0 |

Le plugin appliqué, `org.jetbrains.kotlin.plugin.compose`, est le compilateur
Compose : il est commun aux deux et ne tranche rien. Ce qui tranche, ce sont les
dépendances — BOM `androidx.compose`, `material3`, `activity-compose`,
`navigation-compose`, `material3.adaptive` — toutes côté AndroidX, donc Android
seul.

C'est la bonne nouvelle du dossier : une interface **100 % Compose et 0 % XML**
se porte vers Compose Multiplatform sans réécriture des écrans. La syntaxe et les
concepts sont identiques ; le travail porte sur les imports et sur les
composants absents côté JetBrains.

## Couplage à Android, par sous-paquet

Sur 110 fichiers Kotlin, **51 importent `android.*`**.

| Paquet | Fichiers | Touchent Android | Portabilité |
|---|---|---|---|
| `domain/` | 10 | 1 | φ, KIN, dérivation — porte tel quel |
| `multipass/` | 3 | 1 | porte tel quel |
| `noise/` | 3 | 0 | le wrapper est propre ; voir plus bas |
| `proximity/` | 11 | 3 | protocole partageable, transport à abstraire |
| `nostr/` | 15 | 8 | idem |
| `chat/` | 17 | 6 | idem |
| `ui/` | 30 | 13 | migrable vers Compose Multiplatform |
| `data/` | 7 | 6 | Room → SQLDelight ou Room KMP |
| `geo/` | 3 | 3 | à réécrire par plateforme |
| `update/` | 3 | 3 | **disparaît sur iOS** |

Le découpage est favorable : l'assise identitaire — dérivation de clé LOVE,
φ/KIN, MULTIPASS, protocole NOSTR — est justement la partie la moins couplée.

## ~~Le coût caché : Noise~~ — FAIT le 17 août 2026

`com/southernstorm/noise/` contenait **29 fichiers `.java`, zéro Kotlin**, et
Java ne compile pas vers Kotlin/Native. C'était un préalable au partage, pas une
option — et il ne dépendait d'aucune décision iOS.

**Les 29 fichiers sont portés** (10 587 lignes de Kotlin, zéro `.java` restant).
Le détail est dans `composeApp/src/androidMain/kotlin/com/southernstorm/noise/PROVENANCE.md`.
Les trois points qui comptent pour la suite :

- une transcription littérale n'aurait rien donné — quatre classes héritaient de
  `java.security.MessageDigest` et deux exceptions de `javax.crypto` traversaient
  l'API. Tout est remplacé, et **les substitutions tiennent dans un seul
  fichier**, `Platform.kt` ;
- **une seule classe reste liée à la JVM** : `AESGCMOnCtrCipherState`, qui
  n'existe que pour déléguer AES à `javax.crypto`. Elle restera dans
  `androidMain` ; côté iOS il n'y a rien à écrire, la fabrique retombe seule sur
  l'implémentation pure. Rien chez nous n'appelle AES-GCM ;
- l'unique couture de plateforme restante est **la source d'aléa**
  (`SecureRandomSource`), qui deviendra un `expect`/`actual`.

Le filet : la copie Java intacte de l'amont vit dans les tests, et
`PortageDifferentielTest` compare les deux implémentations **octet par octet** —
primitives, handshake XX complet à clés éphémères fixées, échange New Hope de
bout en bout. 349 tests verts.

⚠ Ce test-là ne pourra pas monter dans `commonTest` : il dépend de sources Java.

---

## Les quatre médiums face à iOS

| Médium | iOS | Détail |
|---|---|---|
| BLE — balise + chat chiffré | ⚠ dégradé | voir ci-dessous |
| Wi-Fi Direct — groupe 5 GHz, 15,5 Mo/s | ✗ | l'API n'existe pas. Multipeer Connectivity repose sur AWDL, propriétaire, Apple↔Apple |
| Bluetooth classique / RFCOMM | ✗ | non exposé aux apps tierces ; réservé au programme MFi, qui certifie du matériel |
| Relais NOSTR — l'hexagone | ✓ | du réseau, aucun obstacle |

### Le point dur : la balise

CoreBluetooth permet d'émettre en périphérique, mais avec trois contraintes qui
touchent directement le protocole.

**Pas de Manufacturer Specific Data.** `startAdvertising` n'accepte que deux
clés — nom local et UUID de service. Aucune charge utile arbitraire. L'identité
doit être encodée dans un UUID de service 128 bits : 16 octets, contournement
connu et viable, mais qui impose de revoir la trame.

**Le champ TX Power (AD 0x0A) n'est pas réglable.** iOS l'écrit lui-même.
L'échelle en atténuation, calibrée à 65 dB à 1 m, perd sa référence ; il faudra
une calibration par modèle d'iPhone.

**En arrière-plan, les UUID de service basculent dans l'« overflow area »**, une
zone propriétaire Apple. Un autre iPhone qui scanne cet UUID précis la voit ;
**Android ne la voit pas du tout.** Concrètement : iPhone en poche = invisible
aux appareils Android. Or la balise « tourne tout le temps » — c'est exactement
ce cas d'usage.

Résultat : une balise iOS qui fonctionne app au premier plan, écran allumé, et
disparaît sinon.

---

## La piste Wi-Fi Aware, creusée

Apple a ouvert Wi-Fi Aware (NAN) dans iOS 26, sous contrainte réglementaire — la
Commission européenne l'a imposé dans ses spécifications d'interopérabilité DMA.
iPhone 12 et ultérieurs. C'était le seul pont peer-to-peer haut débit
théoriquement commun aux deux plateformes. **Elle ne tient pas.**

### L'interop iOS↔Android est cassée, pas seulement immature

- **Android publie → iOS ne découvre pas.** Log Apple :
  `Discovery: Dropping event, missing DCEA attribute`. Les trames Android
  n'exposent pas l'attribut SDEA/DCEA qu'iOS exige.
- **iOS publie → Android découvre, puis l'appairage échoue.** Le code PIN ne
  s'affiche pas, et l'authentification retourne
  `status 15 — authentication rejected because of challenge failure`.
- **Blocage de fond : Apple ne publie pas la dérivation de la clé PASN EPK à
  partir du PIN à 6 chiffres.** Sans cette spécification, le côté Android ne peut
  pas être implémenté correctement. Ce n'est pas un défaut à attendre, c'est une
  pièce manquante du protocole.

Le test le plus parlant : un **Samsung S25**, matériel récent annoncé Wi-Fi Aware
4.0, échoue. La réponse officielle d'Apple (DTS) renvoie le développeur vers son
fournisseur — *« if you've determined that your other vendor's device doesn't meet
the requirements in Accessory Design Guidelines for Apple Devices, that's
something you'll have to discuss with that vendor »*. Discussions de juin puis
septembre 2025 ; les retours de mi-2026 décrivent toujours un chemin de données
NAN en chantier.

### Même réparée, ce n'est pas une balise

Deux caractéristiques structurelles, celles-là documentées et stables.

**L'arrière-plan.** Wi-Fi Aware se comporte comme toute API réseau iOS : la
connexion tient tant que l'app s'exécute et **se ferme dès qu'elle est
suspendue**. Le framework ramasse en plus agressivement les connexions inactives
— quelques minutes suffisent.

**L'appairage.** Il passe par `DeviceDiscoveryUI` : sélecteur système, choix de
l'appareil par l'utilisateur, code PIN à 6 chiffres. Pas de découverte anonyme,
pas de reconnaissance passive — l'inverse exact de l'annonce BLE.

Côté Android, enfin, le support dépend du firmware constructeur : la présence de
l'API n'implique rien, `isAvailable()` peut être faux sur un appareil récent.
L'A5 2016 sous LineageOS 17.1, certainement pas.

### Ce qu'elle éclaire quand même

Le modèle d'appairage de Wi-Fi Aware — sélecteur, PIN échangé de vive voix,
premier plan des deux côtés — est très exactement **la cabine** : présence
physique, geste délibéré, en dernier. Si Apple publiait la dérivation PASN, ce
serait le bon transport pour la cabine, jamais pour la balise. À surveiller, pas
à parier dessus.

---

## Ce qui reste atteignable

L'app iOS complète possible est celle-ci :

- **à parité** — hexagone NOSTR, MULTIPASS, dérivation de clé LOVE, φ/KIN,
  Oracle, match et super match, honneur aux nouveaux ;
- **dégradé** — cabine BLE : iPhone↔iPhone en arrière-plan, iPhone↔Android au
  premier plan seulement ;
- **absent** — les gros transferts (vidéo, pièces jointes 10 Mo) faute de
  Wi-Fi Direct, et le RFCOMM.

Le médium haut débit iOS↔Android réaliste est celui qui a déjà sauvé l'essai
croisé du 12 août : **le réseau Wi-Fi du lieu**, en infrastructure, avec
Bonjour / `NWListener` côté iOS et NSD côté Android. Multiplateforme sans
réserve. Pas de pair-à-pair, mais la mesure existe : le réseau du lieu rattrape
le BLE quand il échoue.

Côté iOS, le jeu se réduit donc à **BLE (premier plan) + LAN + relais NOSTR**.

## Structure de dépôt visée

```
Atom4Love/
├── settings.gradle.kts              # include(":shared", ":composeApp")
├── shared/src/
│   ├── commonMain/kotlin/one/astroport/atom4love/
│   │   ├── domain/  multipass/  noise/  nostr/    # le socle partagé
│   │   └── data/                                  # SQLDelight ou Room KMP
│   ├── androidMain/     # BLE, Wi-Fi Direct, RFCOMM, geo — actual
│   ├── iosMain/         # CoreBluetooth, Network.framework, CoreLocation — actual
│   └── commonTest/
├── composeApp/          # UI Compose Multiplatform + hôte Android
└── iosApp/              # projet Xcode, hôte de la vue Compose
```

Le transport est abstrait derrière `expect`/`actual`, une implémentation par
médium et par plateforme — pour que Wi-Fi Aware puisse s'y glisser plus tard sans
toucher au reste.

**Substitutions imposées :** Hilt → Koin · Room → SQLDelight ou Room KMP ·
`material-icons-extended` → icônes embarquées · `material3.adaptive` → sans
équivalent, à refaire.

## Les livrables

| | Android | iOS |
|---|---|---|
| Produit par | Gradle | Xcode / `xcodebuild` |
| Gradle sort | `composeApp-release.apk`, `.aab` | `shared.xcframework` seulement |
| Livrable | APK / AAB | `.xcarchive` → `.ipa` |
| Distribution | GitHub + miroir, empreinte vérifiée | **TestFlight puis App Store** |

Chaîne iOS en deux étages : `./gradlew assembleSharedXCFramework` produit la
bibliothèque, puis `xcodebuild archive` et `-exportArchive` produisent l'app.

**L'équivalent de l'APK n'existe pas sur iOS.** Le `.ipa` lui ressemble comme
fichier, mais il exige une signature et un profil de provisionnement liés à des
UDID (ad-hoc, 100 appareils) ou un compte Enterprise. Conséquence directe : le
mécanisme actuel de mise à jour — téléchargement, vérification d'empreinte,
passage à l'installeur — **n'a pas de contrepartie iOS**, et le paquet `update/`
disparaît.

Rangement des artefacts, à peupler par une tâche Gradle `Copy` côté Android et
par `-exportPath` côté iOS :

```
dist/
├── android/   atom4love-x.y.z.apk · .aab · mapping.txt
└── ios/       Atom4Love-x.y.z.ipa · .app.dSYM.zip
```

`dist/` et les `build/` hors du dépôt ; les secrets de signature — `.jks`,
`.p12`, profils — hors du dépôt **et** hors des sources. Côté iOS, la pratique
établie est `fastlane match` ou un dépôt de certificats séparé.

Dernier point d'organisation : la CI iOS impose un runner macOS. Un second job
sur `macos-latest`, certificats injectés par secrets, s'ajoute au job Linux
actuel.

---

## Ordre de marche

1. ~~**Réécrire Noise en Kotlin**~~ — **FAIT le 17/08** : 29 fichiers, 10 587
   lignes, vérifiées octet par octet contre le Java d'origine.
2. **Trancher la cible fonctionnelle iOS** au vu des contraintes ci-dessus :
   app complète au sens dégradé, ou hexagone et MULTIPASS sans la cabine radio.
3. **Extraire `shared/`** — `domain`, `multipass`, `nostr`, `noise` d'abord, qui
   ne touchent presque pas Android.
4. **Abstraire le transport** derrière `expect`/`actual`.
5. **Migrer l'UI** vers Compose Multiplatform, en traitant les composants absents.

---

### Sources

- [heise — Apple intègre Wi-Fi Aware sur ordre de l'UE](https://www.heise.de/en/news/Peer-to-peer-WLAN-by-order-of-the-EU-Apple-integrates-Wi-Fi-Aware-10446649.html)
- [Apple Developer Forums — Wi-Fi Aware between iOS 26 and Android device](https://developer.apple.com/forums/thread/790195)
- [Apple Developer Forums — Wi-Fi Aware can't pair with Android Device](https://developer.apple.com/forums/thread/801280)
- [Apple Developer Forums — Wi-Fi Aware in the app's background execution mode](https://developer.apple.com/forums/thread/787570)
- [Apple Developer Forums — Wi-Fi Aware device support](https://developer.apple.com/forums/thread/787775)
- [Apple Developer Forums — Device pairing with DeviceDiscoveryUI](https://developer.apple.com/forums/thread/792143)
- [Apple — DeviceDiscoveryUI](https://developer.apple.com/documentation/DeviceDiscoveryUI)
- [Android Developers — Wi-Fi Aware overview](https://developer.android.com/develop/connectivity/wifi/wifi-aware)
- [AOSP — Wi-Fi Aware](https://source.android.com/docs/core/connect/wifi-aware)
