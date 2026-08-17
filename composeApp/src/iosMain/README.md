# `iosMain` — les implémentations Apple

Vide, et **non compilé** : le module n'a pas encore de cible iOS. Ce répertoire
existe pour que l'arborescence soit celle d'un projet KMP dès maintenant, et
pour que l'ajout de la cible ne déplace aucun fichier.

## Ouvrir la cible

Dans `composeApp/build.gradle.kts`, remplacer le plugin `android.application`
par `kotlin.multiplatform` + `android.application`, puis déclarer les cibles :

```kotlin
kotlin {
    androidTarget()
    iosX64(); iosArm64(); iosSimulatorArm64()
}
```

Le bloc `sourceSets` du build Android devient alors inutile : les noms de
répertoires sont déjà ceux que le plugin attend.

## Ce qui s'écrit ici

Les `actual` des `expect` posés dans [`../commonMain`](../commonMain) :

| Besoin | Android | iOS |
|---|---|---|
| Balise et chat de proximité | BLE | CoreBluetooth — **premier plan seulement** |
| Localisation | `geo/DeviceLocation` | CoreLocation |
| Réseau du lieu | NSD | Bonjour / `NWListener` |
| Base locale | Room | SQLDelight ou Room KMP |

## Ce qui ne s'écrira pas ici

Wi-Fi Direct et RFCOMM n'ont **aucun** équivalent iOS, et la balise permanente
non plus : en arrière-plan, les UUID de service iOS basculent dans une zone
qu'Android ne lit pas. La piste Wi-Fi Aware a été creusée le 17 août 2026 puis
écartée — Apple ne publie pas la dérivation de clé PASN, et la connexion meurt
dès que l'app est suspendue.

Le détail, les mesures et les sources : `docs/note-portage-ios.md`.
