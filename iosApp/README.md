# `iosApp` — l'hôte Xcode

Vide. Ce répertoire tient la place du projet Xcode qui hébergera l'app iOS ;
il ne contient rien tant que la cible iOS n'est pas ouverte, parce qu'un
`.xcodeproj` ne se fabrique pas à la main de façon utile.

## Ce qu'il contiendra

```
iosApp/
├── iosApp.xcodeproj
├── iosApp/                  # sources Swift : point d'entrée, hôte de la vue Compose
│   ├── iOSApp.swift
│   └── Info.plist
└── Configuration/Config.xcconfig
```

Le projet Xcode ne compile pas de Kotlin : il consomme le `shared.xcframework`
que Gradle produit depuis `composeApp/`. Chaîne en deux étages —

```sh
./gradlew assembleSharedXCFramework          # la bibliothèque
xcodebuild archive   -scheme iosApp -archivePath build/iosApp.xcarchive
xcodebuild -exportArchive -archivePath build/iosApp.xcarchive \
           -exportOptionsPlist ExportOptions.plist -exportPath build/ipa
```

## Ce qui change pour la diffusion

**Il n'y a pas d'équivalent de l'APK.** Le `.ipa` exige une signature et un
profil de provisionnement liés à des UDID (ad-hoc, 100 appareils) ou un compte
Enterprise ; la voie normale est TestFlight puis l'App Store. Le paquet
`update/` — téléchargement, vérification d'empreinte, passage à l'installeur —
n'a pas de contrepartie et disparaît côté iOS.

La CI devra gagner un job sur `macos-latest` : `xcodebuild` n'existe que là.

Prérequis, contraintes de plateforme et arbitrages : `docs/note-portage-ios.md`.
