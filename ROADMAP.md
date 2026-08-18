<p align="center">
  <b>Français</b> · <a href="ROADMAP.en.md">English</a>
</p>

# Feuille de route

> **Légende** — ✅ fait · 🚧 en cours · 📋 prévu · 💡 exploratoire
>
> Pas de dates : Atom4Love avance sur du temps bénévole. L'ordre compte, le
> calendrier non. L'état de l'existant est décrit dans le [README](README.md),
> section « État du projet » — cette page regarde devant.

## v0.2.2 — où nous en sommes

Alpha publiée. Le parcours complet tient sur appareils réels : forge du noyau,
dérivation des clés, radar BLE, cabine chiffrée. Voir le README pour le détail.

Deux décisions d'architecture restent ouvertes, et ce sont les plus
structurantes du projet :

- 🚧 **Rotation temporelle D2** — la v0 diffuse l'index H3 statique
  (`CellRotation.None`). La formule n'est pas arrêtée.
- 🚧 **Confiance au premier contact** — la cabine chiffre et atteste, mais rien
  n'empêche encore quelqu'un de se présenter pour un autre au tout premier
  croisement.

---

## Étape 1 — Extraction du noyau multiplateforme 📋

Le dépôt est un **module unique `:composeApp`**, avec une arborescence déjà
nommée `commonMain` / `androidMain` / `iosMain` mais dont Android seul compile.
Le nommage anticipe le portage ; le build reste à convertir.

L'objectif est d'isoler dans un module `:shared` réellement multiplateforme tout
ce qui n'est pas interface, pour qu'une seconde façade puisse s'y brancher.

**Déjà multiplateforme, rien à faire :**

- ✅ `secp256k1-kmp` (ACINQ) — les courbes elliptiques sont déjà KMP
- ✅ Le portage Noise en Kotlin pur (29 fichiers) — aucune dépendance Android
- ✅ La chaîne de dérivation PBKDF2 → scrypt → SHA-256 → secp256k1
- ✅ Les calculs D1 (phase personnelle) et D4 (oracle Tzolkin) — arithmétique pure

**À migrer, par difficulté croissante :**

| Actuel | Cible | Difficulté |
|---|---|---|
| OkHttp | Ktor | Faible |
| Room, DataStore | SQLDelight ou Room KMP + multiplatform-settings | Moyenne |
| Hilt | Koin (ou injection manuelle) | Moyenne |
| WorkManager | `expect`/`actual` par plateforme | Moyenne |
| Keystore Android | `expect`/`actual` ↔ Keychain iOS | Élevée |
| H3 4.4.0 (AAR Java patché) | cinterop vers la lib C sur iOS | Élevée |

**Tâches :**

- 📋 Convertir le build Gradle en build KMP, déclarer les cibles `iosArm64` et
  `iosSimulatorArm64`
- 📋 Découper `:composeApp` en `:shared` + `:androidApp`
- 📋 Migrer les dépendances ci-dessus
- 📋 Exposer l'API partagée sous une forme confortable en Swift
  (`suspend` → `async/await`, classes scellées → énumérations)
- 📋 Faire tourner les tests JVM existants en `commonTest`

---

## Étape 2 — Client iOS 📋

**Nous cherchons quelqu'un pour porter et tenir cette partie.** Voir le ticket
`[HELP WANTED]` épinglé.

Ce n'est pas un simple habillage SwiftUI : la couche transport doit être
repensée. Les quatre médiums de la cabine ne se transposent pas.

| Médium Android | Situation sur iOS |
|---|---|
| BLE advertising / scan | Possible, mais l'annonce de 17 octets ne passe pas telle quelle ; l'arrière-plan est fortement bridé (zone d'overflow, service UUID escamoté) |
| Bluetooth classique RFCOMM | Inaccessible hors programme MFi — à remplacer |
| Wi-Fi du lieu (sockets TCP) | Transposable |
| Wi-Fi Direct | N'existe pas — MultipeerConnectivity / AWDL est le voisin le plus proche |

- 📋 Cible `iosApp` consommant le framework `:shared`
- 📋 Repenser l'annonce de proximité dans les contraintes de CoreBluetooth,
  **sans casser l'interopérabilité avec les appareils Android** — c'est le
  cœur du problème
- 📋 Choisir le remplaçant du couple RFCOMM / Wi-Fi Direct
- 📋 Interface SwiftUI : Carte (Ici / Le monde), Plateau, Noyau, cabine
- 📋 Stockage du noyau scellé dans le Keychain
- 📋 Distribution TestFlight
- 💡 App Store — la question de la compatibilité AGPL avec les conditions
  d'Apple est connue et non triviale. F-Droid reste la référence côté Android.

---

## Étape 3 — Écosystème 💡

- 💡 Inclusion F-Droid, avec IzzyOnDroid comme relais intermédiaire
- 💡 Cible desktop (Compose Multiplatform)
- 💡 Rotation D2 arrêtée et déployée des deux côtés
- 💡 Intégration plus profonde avec les stations Astroport.ONE

---

## Où l'aide est la plus utile

| Domaine | Compétences | Charge |
|---|---|---|
| **Client iOS** | Swift, SwiftUI, CoreBluetooth | Importante — cherche un porteur |
| **Transport iOS** | CoreBluetooth, MultipeerConnectivity | Élevée, et c'est le nœud |
| Extraction KMP | Kotlin, Gradle, Ktor | Moyenne |
| H3 en cinterop | C, Kotlin/Native | Petite, mais bloquante |
| Rotation D2 | Géométrie, cryptographie | Ouverte à la discussion |
| Couverture NIP | NOSTR | Petite, parallélisable |
| Traductions, accessibilité | fr / en / es, Compose | Petite, accueillante |

Voir [CONTRIBUTING.md](CONTRIBUTING.md). Les commits se signent (`git commit -s`,
DCO), il n'y a pas de CLA, et le code restera sous AGPL-3.0.
