# Consignes de travail — Atom4Love

## Protocole de debug UI (ordre obligatoire)

Cet ordre n'est pas une préférence : chaque échelon coûte dix fois le
précédent, et la journée du 20/08 s'est passée à en payer le prix à l'envers.
On descend d'un cran **seulement** quand le cran du dessus ne peut pas répondre.

1. **Reproduire par test Robolectric/Compose (`testDebugUnitTest`) — par
   défaut.** Un test dit la même chose qu'un écran, en deux secondes, sans
   appareil et sans main. Il reste ensuite dans le dépôt : le défaut ne peut
   plus revenir sans être vu.

2. **Sinon : deep link ou `DebugSeedActivity` + lecture `logcat` tag `A4L`.**
   Atteindre l'état directement plutôt que le rejouer à la main. Naviguer
   depuis l'écran d'accueil pour arriver au troisième écran, c'est cinq gestes
   qui peuvent tous rater — et qui ratent.

3. **Interaction écran : uniquement via l'arbre d'accessibilité MCP, jamais de
   tap par coordonnées.** Un bouton se touche par son **nom**.
   ⚠ Mesuré : des coordonnées devinées ont ouvert le tiroir d'applications, les
   réglages Bien-être numérique, et un onglet au hasard — sur le téléphone de
   quelqu'un. Elles cassent à la première retouche de mise en page, et elles
   mentent quand l'écran a défilé. Un élément sans libellé doit **recevoir un
   `contentDescription`** : il est de toute façon invisible à un lecteur
   d'écran. Voir `tools/bench-drive.sh`.

4. **Screenshot : dernier recours, et seulement pour un problème visuel**
   (alignement, thème, débordement). Une capture ne prouve pas qu'un bouton
   répond, ne dit pas pourquoi, et coûte un aller-retour par idée.

5. **Jamais de `clean`. Un seul variant : `assembleDebug`.** Le build
   incrémental tient ; `clean` rachète cinq minutes d'attente contre rien.
   Compiler `release` pour vérifier un écran, c'est compiler deux fois.

## Le banc

- **Après chaque build : `tools/bench-install.sh`.** Il pose l'APK sur **tous**
  les appareils en parallèle, vérifie par **SHA-256 de l'installé** — pas par
  `versionName`, qui ne distingue pas deux builds du même commit — et **sort en
  erreur** si un appareil du banc manque.
- Après un `install -r`, compter **30 à 60 s** pour que les liens attestés se
  refassent. Ce n'est pas un défaut, c'est le temps qu'il faut.
- ⚠ **Le banc ment sur le Wi-Fi** : les appareils partagent une box, ce que la
  rue n'offre jamais. Ce qui doit marcher en radio se teste en radio.
