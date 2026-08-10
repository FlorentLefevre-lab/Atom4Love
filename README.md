<p align="center">
  <img src="https://github.com/user-attachments/assets/663bee21-ba78-4f4b-bf05-3211b5cdd080"
       alt="Atom4Love" width="240" />
</p>

<h1 align="center">Atom4Love</h1>

<p align="center">
  <strong>Client Android natif pour la rencontre de proximité<br />
  sans serveur central, sans biométrie et sans traçage de position.</strong>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/agpl-3.0"><img src="https://img.shields.io/badge/Licence-AGPL%20v3-blue.svg" alt="Licence AGPL v3" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg" alt="Kotlin" /></a>
  <img src="https://img.shields.io/badge/statut-alpha%20pr%C3%A9coce-orange.svg" alt="Statut : alpha précoce" />
</p>

---

## Maquettes

> **Maquettes d'intention.** Ces écrans illustrent le parcours visé. L'application réelle en
> implémente aujourd'hui une partie — voir « État du projet » juste en dessous.

<details>
  <summary>Voir le parcours Android complet</summary>
  <p align="center">
    <img src="https://github.com/user-attachments/assets/19243fbf-1f71-4bf6-a295-212b0dffde45"
         alt="Parcours Android — maquettes" width="720" />
  </p>
</details>

---

## ⚠️ État du projet

**Stade : alpha fonctionnelle.** L'application compile, se lance et le parcours de bout en
bout fonctionne sur appareil réel : forger son identité, dériver ses clés NOSTR, diffuser son
adresse de cellule en BLE et voir apparaître les voisins physiques sur le radar. La détection
de proximité a été **validée en croisé sur deux téléphones** (août 2026). Il n'y a pas encore
de release ni d'APK publié.

### Ce qui fonctionne aujourd'hui

- **Forge de l'identité** (onglet Noyau) : saisie de la date, de l'heure et du lieu de
  naissance avec autocomplétion des communes, récapitulatif avant validation, persistance
  locale, et « dissolution du noyau » derrière une double confirmation pour tout effacer.
- **Clés NOSTR déterministes** : dérivation secp256k1 depuis les paramètres de naissance,
  npub affiché, et connexion à un relais NOSTR (en écoute seule pour l'instant — aucun
  événement n'est publié).
- **Adressage géographique réel** : cellule H3 calculée depuis la position, portail Goldberg
  dérivé du lieu de naissance.
- **Proximité BLE continue** : diffusion et scan du beacon d'adresse 4D en tâche de fond,
  registre des voisins détectés.
- **Radar en données réelles** : les pastilles sont les voisins BLE effectivement détectés
  (distance estimée par RSSI), avec le cap et la distance vers le portail.
- **Habillage** : splash animé (atome vectoriel), navigation à cinq onglets (Radar, Tableau,
  Liens, Noyau, Aide) avec transitions, écran d'aide et aide contextuelle avant la forge.

### Ce qui reste ouvert

- **Rotation temporelle D2** : la v0 diffuse l'index H3 statique, sans rotation
  (`CellRotation.None`). Assumé tant que la formule D2 n'est pas arrêtée : la portée BLE
  (~10–100 m) borne l'information révélée à des appareils déjà physiquement voisins.
- **Échange NOSTR** : le npub n'est pas encore inclus dans le payload de proximité ; aucun
  premier échange chiffré n'est implémenté. Les questions de protocole ouvertes sont
  consignées dans [`docs/note-protocole-questions.md`](docs/note-protocole-questions.md).
- **Synthèse sonore collective (D5)** : pas encore commencée.
- Caractérisation de la portée RSSI en conditions réelles.

Les décisions d'architecture les plus structurantes (rotation D2, protocole d'échange) sont
encore ouvertes : c'est le bon moment pour peser sur elles.

---

## De quoi s'agit-il ?

Atom4Love est une application Android qui met en relation des personnes géographiquement
proches, en s'appuyant sur l'infrastructure décentralisée
[Astroport.ONE](https://github.com/papiche/Astroport.ONE) (NOSTR + IPFS + Ğ1).

Trois mécanismes la distinguent d'une application de rencontre classique :

**Identité déterministe.** L'identité de l'utilisateur est dérivée de paramètres personnels
stables, jamais de données biométriques ni d'un compte hébergé chez un tiers. Aucun serveur
ne détient de profil.

**Adressage géographique opaque.** L'application ne transmet jamais de coordonnées GPS.
Elle publie l'identifiant d'une cellule hexagonale issue d'un pavage planétaire soumis à une
rotation temporelle. Le même identifiant ne désigne pas le même lieu physique d'un instant à
l'autre : sans le timestamp exact de production, l'adresse est inexploitable. C'est ce que
la spécification appelle une adresse 4D.

**Synthèse sonore collective.** Plusieurs appareils proches se comportent comme les voix d'un
synthétiseur additif distribué. La consonance du résultat dépend de l'alignement des phases
des participants — le retour social passe par le son plutôt que par un score.

### Ce que ce n'est pas

- Pas de serveur central, pas de base de données de profils, pas de compte à créer.
- Pas de biométrie, pas de reconnaissance faciale, pas d'empreinte.
- Pas d'algorithme de classement propriétaire : la logique de mise en relation est dans ce
  dépôt, lisible et modifiable.
- **Pas un projet à prétention scientifique.** Le modèle de résonance de D1 et D4 s'appuie
  sur des correspondances symboliques (éphémérides de naissance, calendrier Tzolkin)
  transposées en calculs déterministes et reproductibles. C'est un choix de conception assumé,
  pas une théorie physique. Le code, lui, se juge sur des critères ordinaires : déterminisme,
  couverture de tests, absence de fuite de données.

---

## Pile technique

| Couche | Choix |
|---|---|
| Langage | Kotlin |
| Interface | Jetpack Compose (Material 3) |
| Cryptographie | [secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) (ACINQ) |
| Messagerie | NOSTR — NIP-01, NIP-17 (gift wrap), NIP-44 (ChaCha20-Poly1305) |
| Géographie | [H3](https://h3geo.org/) (Uber) pour le pavage hexagonal statique (AAR patché vendorisé) |
| Proximité | BLE advertising + scan (beacon d'adresse 4D, service continu) |
| Build | Gradle KTS, module unique `:app` — minSdk 26, targetSdk 36, JDK 17 |

> **Note d'implémentation.** H3 fournit le pavage hexagonal de référence ; la couche de
> rotation temporelle décrite en D2 s'applique par-dessus, à l'encodage et au décodage
> (interface `CellRotation`, actuellement en v0 identité). C'est le point d'architecture le
> plus délicat du projet et il est encore ouvert à la discussion — voir les tickets.

---

## Démarrage

```bash
git clone https://github.com/FlorentLefevre-lab/Atom4Love.git
cd Atom4Love
./gradlew assembleDebug
```

Prérequis : JDK 17+, Android SDK. Aucune clé d'API, aucun compte, aucune configuration à
fournir : les clés sont dérivées localement des paramètres de naissance saisis au premier
lancement.

Pour voir la proximité fonctionner, il faut **deux appareils physiques** avec le Bluetooth
activé : chacun diffuse son adresse de cellule et voit l'autre apparaître sur le radar.
L'émulateur suffit pour tout le reste (forge, clés, radar sans voisins).

---

## Fondations techniques

Les algorithmes fondateurs ont fait l'objet de cinq publications défensives sur
[TDCommons](https://www.tdcommons.org/) (juin 2026, G1FabLab).

Ces dépôts établissent une **antériorité opposable** : ils versent les mécanismes décrits
dans l'état de l'art afin qu'ils ne puissent pas être ultérieurement appropriés par un dépôt
de brevet. TDCommons est une plateforme de publication défensive en libre soumission — ce ne
sont **pas** des publications à comité de lecture, et elles ne confèrent aucun droit exclusif.
C'est exactement l'intention recherchée.

| | Publication |
|---|---|
| **D1** | [Deterministic Personal Phase Computation from Birth Ephemeris Data for Social Resonance Matching](https://www.tdcommons.org/dpubs_series/10326) |
| **D2** | [4D Opaque Hexagonal Geo-Addressing Scheme Using Dynamic Goldberg Polyhedra](https://www.tdcommons.org/dpubs_series/10327) |
| **D3** | [Biometric Birth Ephemeris as Deterministic Parameters for Shamir Secret Sharing Key Recovery](https://www.tdcommons.org/dpubs_series/10328) |
| **D4** | [Tzolkin Kin-Based Oracle Matrices Combined with Phase Interference Metrics](https://www.tdcommons.org/dpubs_series/10329) |
| **D5** | [Decentralized Additive Synthesis Orchestra Governed by Biometric Phase Fields](https://www.tdcommons.org/dpubs_series/10330) |

Atom4Love implémente principalement D1, D2 et D5.

---

## Projets connexes

### Écosystème UPlanet / G1FabLab (Fred R.)

- **[Astroport.ONE](https://github.com/papiche/Astroport.ONE)** — la station décentralisée
  (NOSTR, IPFS, Duniter/Ğ1, MULTIPASS). AGPL-3.0. Atom4Love en est un client : il dialogue
  avec elle par API et par NOSTR, sans en être une œuvre dérivée.
- **[UPassport](https://github.com/papiche/UPassport)** — API terminal (FastAPI) pour
  l'identité et le stockage uDRIVE, authentification NOSTR NIP-42.
- **[UPlanet](https://github.com/papiche/UPlanet)** — la grille géographique et sa
  visualisation.
- **[cabine-33](https://github.com/papiche/cabine-33)** — implémentation Godot des mêmes
  algorithmes (phase personnelle, adressage hexagonal opaque, orchestre). Référence utile
  pour comparer les comportements attendus.
- **[G1FabLab sur Open Collective](https://opencollective.com/monnaie-libre)** — financement
  et actualités de l'écosystème.

### Briques amont

- [secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) — ACINQ, courbes elliptiques
  multiplateformes.
- [H3](https://github.com/uber/h3) — Uber, indexation hexagonale.
- [NIPs NOSTR](https://github.com/nostr-protocol/nips) — spécifications du protocole.

---

## Contribuer

Les contributions sont bienvenues, y compris de la part de personnes qui découvrent NOSTR ou
Compose. Voir [CONTRIBUTING.md](CONTRIBUTING.md) pour le détail.

**Signature des commits.** Le projet utilise le [Developer Certificate of
Origin](https://developercertificate.org/). Signez vos commits avec `git commit -s`, ce qui
ajoute une ligne `Signed-off-by`. Il n'y a pas de CLA : personne ne collecte vos droits, le
code restera sous AGPL-3.0.

**Par où commencer.** Les tickets étiquetés `good first issue` sont conçus pour être
réalisables sans connaître l'ensemble du système.

<!-- À COMPLÉTER : ouvrir 5 ou 6 tickets réellement autonomes avant de diffuser ce README.
     Un dépôt sans tickets ouverts ne convertit personne. Exemples de bons candidats :
     écran de réglages, thème sombre, tests unitaires sur l'encodeur H3, écran d'onboarding,
     localisation EN. -->

**Discussion.** : Salons dédiés sur WhatsApp et Telegram ...

---

## Licence

Ce projet est distribué sous licence **GNU Affero General Public License v3.0**
(voir [`LICENSE`](LICENSE)).

Concrètement : vous pouvez utiliser, étudier, modifier et redistribuer ce code. Si vous le
modifiez et le proposez comme service accessible par le réseau, vous devez publier vos
modifications sous la même licence. Il n'y aura jamais de version propriétaire d'Atom4Love,
ni de double licence.

Ce choix aligne le projet sur Astroport.ONE, également en AGPL-3.0.

> **Distribution.** L'AGPL s'accorde mal avec les conditions du Google Play Store. La
> distribution de référence se fera par [F-Droid](https://f-droid.org/) et par APK signé.

---

## Crédits

Développement Android : Florent Lefèvre.
Algorithmes fondateurs et écosystème : Fred R. / [G1FabLab](https://opencollective.com/monnaie-libre).
