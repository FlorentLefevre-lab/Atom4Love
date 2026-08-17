# `commonTest` — les tests sans plateforme

Vide, et déjà branché : `build.gradle.kts` déclare `src/commonTest/kotlin` comme
source des tests unitaires Android. Un test posé ici tourne donc dès maintenant
sur JVM, et tournera sur iOS le jour où la cible existe.

Y vont les tests qui ne demandent rien d'Android : dérivation de clé LOVE, phase
φ, KIN, portails Goldberg, trames du protocole. Les tests qui ont besoin d'un
`Context`, de Room ou d'un serveur restent dans
[`../androidUnitTest`](../androidUnitTest).

⚠ Rappel de banc : les tests **instrumentés** désinstallent l'app et effacent le
noyau scellé de l'appareil. Rien de tel ici — `commonTest` et `androidUnitTest`
tournent sur JVM, sans toucher au téléphone.
