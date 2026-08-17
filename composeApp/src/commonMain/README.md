# `commonMain` — le code sans plateforme

Vide pour l'instant, et déjà branché : `build.gradle.kts` déclare
`src/commonMain/kotlin` comme source du build Android. Un fichier posé ici
compile donc tout de suite, et compilera pour iOS le jour où la cible existe,
sans bouger.

## Ce qui vient ici, et dans quel ordre

L'ordre suit le couplage à Android mesuré le 17 août 2026 — le moins couplé
d'abord, parce qu'il se déplace sans rien casser.

| Paquet | Fichiers | Touchent Android | |
|---|---|---|---|
| `noise/` | 3 | 0 | après réécriture des 29 `.java` amont en Kotlin |
| `domain/` | 10 | 1 | φ, KIN, dérivation de clé LOVE |
| `multipass/` | 3 | 1 | |
| `proximity/` | 11 | 3 | le protocole, pas le transport |
| `nostr/` | 15 | 8 | le protocole, pas le transport |

Le reste — `geo/`, `data/`, `update/`, `ui/` — demande soit une substitution de
bibliothèque, soit une implémentation par plateforme.

## La règle

Rien ici ne doit importer `android.*` ni `androidx.*`. Ce qui a besoin d'une
plateforme se déclare `expect` ici et s'implémente `actual` dans
[`../androidMain`](../androidMain) et [`../iosMain`](../iosMain).

Tant que le module ne compile que pour Android, rien ne fait respecter cette
règle : le compilateur accepte un import Android posé ici. C'est le seul point
de vigilance de la structure actuelle.

Voir `docs/note-portage-ios.md` pour l'état complet du dossier.
