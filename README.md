<p align="center">
  <img src="docs/logo.webp" alt="Atom4Love" width="240" />
</p>

<h1 align="center">Atom4Love</h1>

<p align="center">
  <sub>Les deux langues sont dans cette page : ouvrir l'une referme l'autre, sans rechargement.<br />
  Both languages live in this page: opening one closes the other, with no reload.</sub>
</p>

<details name="atom4love-langue" open>
<summary><b>&nbsp;🇫🇷&nbsp; Français</b></summary>

<p align="center">
  <strong>Client Android natif pour la rencontre de proximité<br />
  sans serveur central, sans biométrie et sans traçage de position.</strong>
</p>


<p align="center">
  <a href="https://www.gnu.org/licenses/agpl-3.0"><img src="https://img.shields.io/badge/Licence-AGPL%20v3-blue.svg" alt="Licence AGPL v3" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg" alt="Kotlin" /></a>
  <a href="https://github.com/FlorentLefevre-lab/Atom4Love/releases/latest"><img src="https://img.shields.io/badge/APK-t%C3%A9l%C3%A9charger-00ffcc.svg" alt="Télécharger l'APK" /></a>
  <img src="https://img.shields.io/badge/statut-alpha%20publi%C3%A9e-orange.svg" alt="Statut : alpha publiée" />
</p>

---

## Comment l'affinité entre deux personnes est calculée

Il n'y a pas de profil à remplir, pas de photo, pas de préférences à cocher et aucun
algorithme de recommandation. Ce qu'Atom4Love compare, c'est **l'instant et le lieu de la
naissance** — l'éphéméride : où la Terre se trouvait sur son orbite, où elle en était de sa
rotation, et sous quel point du globe. De là sortent deux grandeurs, et rien d'autre ne
circule.

**L'onde — la phase personnelle φ.** L'instant de naissance donne un angle, à la minute et
au degré près : l'angle annuel de l'orbite, l'angle du jour, et le décalage du lieu sur une
grille pentagonale qui tourne en 14,83 h. Deux personnes qui se croisent croisent deux ondes,
et leur accord se lit dans un seul nombre, **`k`**, entre 0,5 et 1. Il monte quand les deux
ondes sont en phase — **ou exactement en opposition**, ce qui revient à se répondre : un
aimant trouve son pôle contraire. Ce qui ne dit rien, c'est le quart de tour, entre les deux.
Aucun de ces calculs ne demande le réseau : les deux appareils trouvent le même `k`, chacun
de son côté, à partir de ce que porte déjà l'annonce **BLE** — *Bluetooth Low Energy*, la
diffusion sans connexion et sans appairage par laquelle deux appareils se signalent l'un à
l'autre à quelques mètres.

**Le sceau — le compte galactique du Tzolkin.** Le Tzolkin est le compte maya de 260 jours :
vingt sceaux, qui sont des archétypes, croisés de treize tons, qui sont des niveaux
d'énergie. Une date de naissance tombe sur l'une des 260 cases — c'est le **KIN**, ce que le
vocabulaire du calendrier appelle une signature galactique. Autour de lui, l'Oracle en
désigne quatre autres : le **guide**, le **défi**, l'**alternance** et l'**occulte**. Ce sont
quatre façons de se compléter, jamais un contraire — un défi ne vaut pas moins qu'une union.

**Ce qui en sort, et ce qui n'en sort pas.** Un plan qui s'aligne fait un
$\color{orange}{\textsf{\textbf{match}}}$ : `k` au-delà de 0,90, ou un sceau de votre Oracle en
face — une personne croisée sur six. Les deux ensemble font un
$\color{red}{\textsf{\textbf{super match}}}$, `k` passant alors 0,95 : trois rencontres sur
mille. Ces deux seuils viennent de Fred et ont été mesurés sur 79 800 paires. Il n'y a rien d'autre :
pas de note, pas de classement, pas de file de suggestions, et **personne n'apprend qu'on
l'a vu**. La réciprocité existe, mais elle est physique — deux écrans qui battent le même
rythme dans une salle, chacun l'ayant calculé seul.

> Les formules sont celles de Fred R. / G1FabLab, publiées en **D1** (phase personnelle) et
> **D4** (matrices d'oracle Tzolkin) — voir « Fondations techniques ». Le modèle s'appuie sur
> des correspondances symboliques transposées en calculs déterministes : c'est un choix de
> conception assumé, pas une théorie physique. Voir « Ce que ce n'est pas ».

---

## UI/UX : l'expérience utilisateur de votre propre incarnation en mode jeu de cartes (Tzolkin)

La barre du bas ne compte que trois entrées : **Carte**, **Plateau**, **Noyau** — ce qui est
dehors, le jeu, et soi. L'**Aide** et les **Réglages** sont devenus deux boutons
dans la ligne du haut. Le **Radar** et la **Constellation**, eux, n'étaient pas deux
endroits mais deux façons de regarder les autres — de près par la radio, ou à l'échelle du monde
par le relais ; ils sont réunis dans l'onglet Carte, où un sélecteur bascule entre *Ici* et *Le
monde*. Le **Plateau** garde sa place au milieu, mais reste éteint tant qu'aucun sceau n'est à
portée.

<table>
  <tr>
    <td width="33%" valign="top">
      <img src="docs/captures/02-carte-ici.png" alt="Carte · Ici" width="100%" /><br />
      <b>🌍 Carte · Ici</b><br />
      Ce qui est à portée d'antenne : la cellule qu'on diffuse, le nombre de noyaux dans le
      portail, et la résonance de chacun — un sceau et un <code>k</code>, jamais un nom.
    </td>
    <td width="33%" valign="top">
      <img src="docs/captures/03-plateau.png" alt="Plateau" width="100%" /><br />
      <b>🎴 Plateau</b> — <i>en lumière de jour</i><br />
      Le tirage : votre carte et ses quatre compléments, puis les cartes à portée avec leur
      chaleur. « Cherche le Dragon » réduit une salle de trente à deux personnes.
    </td>
    <td width="33%" valign="top">
      <img src="docs/captures/04-lanterne.png" alt="Rendez-vous" width="100%" /><br />
      <b>🔦 Rendez-vous</b><br />
      La lanterne bat un rythme que l'autre téléphone calcule tout seul, à partir des deux φ
      déjà dans l'air. Rien de plus n'est émis ; le dernier mètre est franchi par les yeux.
    </td>
  </tr>
  <tr>
    <td valign="top">
      <img src="docs/captures/05-cabine.png" alt="Cabine" width="100%" /><br />
      <b>💬 Cabine</b><br />
      Le canal chiffré et attesté (Noise), ici porté par le Bluetooth classique. Les questions
      s'y échangent selon une seule règle : proposer, c'est donner.
    </td>
    <td valign="top">
      <img src="docs/captures/06-constellation.png" alt="Carte · Le monde" width="100%" /><br />
      <b>🌍 Carte · Le monde</b><br />
      La constellation des noyaux dont la station a scellé la clé LOVE, chacun à son
      <b>lieu de naissance</b> au kilomètre — jamais là où il se trouve.
    </td>
    <td valign="top">
      <img src="docs/captures/07-noyau.png" alt="Noyau" width="100%" /><br />
      <b>⚛ Noyau</b> — <i>en lumière de jour</i><br />
      La fiche scellée et ce qu'elle calcule : conception, portail, KIN, phase. Rien n'en part.
      La dissolution efface tout, derrière une double confirmation.
    </td>
  </tr>
</table>

<p><i>Les vignettes sont réduites pour tenir en une grille. Pour lire un écran en pleine
taille <b>sans quitter cette page</b>, ouvrez-le ici — cliquer la vignette, elle, emmène sur
la page du fichier.</i></p>

<details>
  <summary>🔍 &nbsp;<b>Carte · Ici</b></summary>
  <p><img src="docs/captures/02-carte-ici.png" alt="Carte · Ici, en pleine taille" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Plateau</b></summary>
  <p><img src="docs/captures/03-plateau.png" alt="Plateau, en pleine taille" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Rendez-vous</b></summary>
  <p><img src="docs/captures/04-lanterne.png" alt="Rendez-vous, en pleine taille" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Cabine</b></summary>
  <p><img src="docs/captures/05-cabine.png" alt="Cabine, en pleine taille" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Carte · Le monde</b></summary>
  <p><img src="docs/captures/06-constellation.png" alt="Constellation, en pleine taille" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Noyau</b></summary>
  <p><img src="docs/captures/07-noyau.png" alt="Noyau, en pleine taille" width="540" /></p>
</details>

<details>
  <summary>Le splash, les Réglages, l'Aide — et les maquettes d'origine</summary>

  <table>
    <tr>
      <td width="33%" valign="top">
        <img src="docs/captures/01-splash.png" alt="Splash" width="100%" /><br />
        L'atome au lancement.
      </td>
      <td width="33%" valign="top">
        <img src="docs/captures/08-reglages.png" alt="Réglages" width="100%" /><br />
        Trois langues, deux lumières, le corps du jour — et la bulle de version, d'où partent
        la mise à jour et la désinstallation.
      </td>
      <td width="33%" valign="top">
        <img src="docs/captures/09-aide.png" alt="Aide" width="100%" /><br />
        L'Aide, la F.A.Q. et Zion : ce que fait la station, et pourquoi elle le fait ainsi.
      </td>
    </tr>
  </table>

  <br />

  <p>
    <b>Les maquettes d'origine (juin 2026).</b> Elles décrivent le parcours <b>visé au
    départ</b>, à six onglets, avant que la cabine, le jeu en trois coups et le MULTIPASS
    n'existent. Gardées pour l'histoire ; l'application ne leur ressemble plus.
  </p>

  <p align="center">
    <img src="docs/maquettes-origine.png"
         alt="Parcours Android — maquettes d'origine" width="720" />
  </p>
</details>

> Captures prises le 18 août 2026 sur deux appareils réels (Pixel 10 Pro en lumière de nuit,
> tablette en lumière de jour), version `0.2.2`. Les deux noyaux se voyaient l'un l'autre en
> BLE : le voisin, la carte à portée et la conversation chiffrée sont ceux d'un vrai croisement,
> pas une mise en scène. Le **MULTIPASS** n'est pas montré : son écran porte un code PASS.

---

## État du projet

**Stade : alpha publiée — v0.2.2 (versionCode 4, 17 août 2026).** L'application s'installe,
se met à jour toute seule, et le parcours complet a été éprouvé sur appareils réels : forger
son noyau, dériver ses clés, se voir apparaître sur le radar de l'autre, ouvrir une cabine
chiffrée et s'y parler. La détection de proximité et la cabine ont été **validées en croisé
sur deux téléphones**, dans les deux sens.

Ce n'est pas encore une bêta : rien n'est promis sur la stabilité, la rotation D2 n'est pas
arrêtée, et la confiance au premier contact reste à durcir.

### Ce qui fonctionne aujourd'hui

- **Forge du noyau** — date, heure (facultative), lieu et onde ; autocomplétion des communes,
  récapitulatif avant validation, persistance locale, dissolution complète.
  ⚠ La précision des coordonnées fait partie de la clé : elles s'affichent et se saisissent
  entières, sinon la même fiche rouvre une autre clé.
- **Clés déterministes** — la chaîne complète PBKDF2 → scrypt → SHA-256 → secp256k1, portée
  en Kotlin depuis les outils de la station et **vérifiée octet pour octet** contre elle sur
  un MULTIPASS réel.
- **NOSTR, en lecture et en écriture** — relais déduit de l'hôte, comme chez Fred. L'app
  publie son certificat ATOM4LOVE (kind 30078, `d=atom4love`) sur un geste explicite et
  irréversible qui montre d'abord exactement ce qui partira ; elle tient un carnet NIP-02
  (kind 3) alimenté par les rencontres attestées, écoute les accueils, et porte un salon de
  cabine (kind 24242).
- **MULTIPASS** — demande d'inscription depuis l'app, redérivation de la clé LOVE après une
  reforge. Tout le reste (portefeuilles, uDRIVE) reste l'ouvrage de la station.
- **Adressage géographique** — cellule H3 calculée depuis la position, portail Goldberg dérivé
  du lieu de naissance. Aucune coordonnée ne quitte l'appareil, et la localisation ne passe
  jamais par les services Google.
- **Proximité BLE continue** — annonce de 17 octets (adresse 4D, jeton de présence, polarité,
  sceau maya, phase), scan en tâche de fond, registre des voisins. Portée mesurée : **7 m**.
  La balise ne s'annonce que si la localisation est accordée, et se tait pendant les
  transferts.
- **Le jeu en trois coups** — tirage (Plateau), reconnaissance (lanterne), questions (cabine).
  $\color{orange}{\textsf{\textbf{Match}}}$ et
  $\color{red}{\textsf{\textbf{super match}}}$ sur les seuils de Fred, calibrés sur 79 800
  paires. Rien n'est classé, rien n'est noté, et personne n'apprend qu'on l'a choisi.
- **Cabine chiffrée** — Noise, **entièrement porté en Kotlin** (29 fichiers, vérifiés octet
  pour octet contre l'implémentation Java de référence et en cabine réelle Kotlin ↔ Java).
  Quatre médiums, avec escalade sur échec : BLE (la seule porte, la seule qui découvre et
  atteste), Bluetooth classique RFCOMM (78–102 ko/s), Wi-Fi du lieu, Wi-Fi Direct (15,5 Mo/s).
  Pièces jointes, vidéo à sa qualité d'origine, tout effacé en sortant.
- **Mise à jour intégrée** — l'app lit un manifeste, télécharge l'APK, **vérifie son
  empreinte SHA-256** et passe la main à l'installeur du système. La désinstallation part du
  même endroit.
- **Habillage** — thème jour et nuit, trois langues (français, anglais, espagnol), Material 3,
  typographies embarquées, splash animé.
- **45 fichiers de tests** JVM et instrumentés, dont un test différentiel qui compare le
  portage Noise à sa référence.

### Ce qui reste ouvert

- **Rotation temporelle D2** — la v0 diffuse l'index H3 statique, sans rotation
  (`CellRotation.None`). Assumé tant que la formule n'est pas arrêtée : la portée BLE mesurée
  (7 m) borne l'information révélée à des appareils déjà dans la même pièce.
- **Confiance au premier contact** — la cabine chiffre et atteste, mais rien ne protège encore
  contre un inconnu qui se présenterait pour un autre à la toute première rencontre.

**Les décisions d'architecture les plus structurantes (rotation D2, premier contact) sont
encore ouvertes : c'est le bon moment pour peser sur elles.**

---

## Installer

L'APK est signé (schémas v2 et v3) et publié sur GitHub :

```
https://github.com/FlorentLefevre-lab/Atom4Love/releases/latest/download/atom4love-latest.apk
```

Cette adresse ne nomme aucune version : elle sert toujours la dernière. Le manifeste
[`latest.json`](latest.json) porte le numéro, la taille, la date et l'empreinte SHA-256 —
c'est lui que l'application lit pour savoir qu'elle est en retard, et c'est cette empreinte
qu'elle vérifie avant de proposer l'installation.

Une fois la première version installée, les suivantes se prennent depuis l'application, par
la bulle de version au bas des Réglages.

> **Play Protect.** Android prévient qu'il ne connaît pas l'éditeur : c'est le comportement
> normal pour une application hors magasin. La distribution de référence se fera par
> [F-Droid](https://f-droid.org/) et par APK signé — l'AGPL s'accorde mal avec les conditions
> du Play Store.

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
Elle publie l'identifiant d'une cellule hexagonale issue d'un pavage planétaire, destiné à
être soumis à une rotation temporelle : le même identifiant ne désignerait plus le même lieu
physique d'un instant à l'autre. C'est ce que la spécification appelle une adresse 4D — la
formule de rotation est le point encore ouvert (voir « État du projet »).

**La rencontre est l'authentification.** Il n'y a pas de messagerie ouverte : on se tire
d'abord dessus une carte, on se reconnaît ensuite des yeux dans la salle, et la cabine
chiffrée ne s'ouvre qu'après — jamais pour découvrir quelqu'un. Le jeu ne révèle aucune
identité ; il permet de devenir trouvable.

### Ce que ce n'est pas

- Pas de serveur central, pas de base de données de profils, pas de compte à créer.
- Pas de biométrie, pas de reconnaissance faciale, pas d'empreinte.
- Pas d'algorithme de classement propriétaire : la logique de mise en relation est dans ce
  dépôt, lisible et modifiable.
- Pas de score et pas de réciprocité serveur : deux téléphones qui battent le même rythme
  dans une salle se constatent avec les yeux, rien ne circule pour le dire.
- **Pas un projet à prétention scientifique.** Le modèle de résonance de D1 et D4 s'appuie
  sur des correspondances symboliques (éphémérides de naissance, calendrier Tzolkin)
  transposées en calculs déterministes et reproductibles. C'est un choix de conception assumé,
  pas une théorie physique. Le code, lui, se juge sur des critères ordinaires : déterminisme,
  couverture de tests, absence de fuite de données.

---

## Pile technique

| Couche | Choix |
|---|---|
| Langage | Kotlin 2.2 |
| Interface | Jetpack Compose (Material 3), mises en page adaptatives, thème jour/nuit, fr/en/es |
| Architecture | Hilt, Room, DataStore, WorkManager, OkHttp |
| Cryptographie | [secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) (ACINQ) ; scrypt et PBKDF2 pour la dérivation de la clé LOVE |
| Cabine chiffrée | [Noise Protocol](https://noiseprotocol.org/) — portage Kotlin intégral de `noise-java`, testé en différentiel contre sa référence |
| Messagerie | NOSTR — NIP-01, NIP-02 (carnet), NIP-09, NIP-19, NIP-42, NIP-78 (kind 30078 `d=atom4love`) |
| Géographie | [H3](https://h3geo.org/) 4.4.0 (Uber) pour le pavage hexagonal statique — AAR local patché, l'artefact amont omet `libm` |
| Proximité | BLE advertising + scan (annonce de 17 octets, service continu), RFCOMM, Wi-Fi Direct, sockets TCP encadrés |
| Média | Media3 (vidéo de la cabine, jamais ré-encodée), Coil |
| Typographie | [Atkinson Hyperlegible Next](https://fonts.google.com/specimen/Atkinson+Hyperlegible+Next) (texte — dessinée pour la basse vision), [JetBrains Mono](https://fonts.google.com/specimen/JetBrains+Mono) (données, adresses, compteurs) et [Cinzel Decorative](https://fonts.google.com/specimen/Cinzel+Decorative) (le nom, sur le splash) — embarquées, SIL OFL 1.1, textes dans `licenses/` |
| Build | Gradle KTS 9.5, AGP 9.3.1, module unique `:composeApp` — minSdk 26, targetSdk 36, JDK 17. Arborescence déjà nommée comme un projet multiplateforme (`commonMain`, `androidMain`, `iosMain`), Android seul compilé — voir `docs/note-portage-ios.md` |

> **Note d'implémentation.** H3 fournit le pavage hexagonal de référence ; la couche de
> rotation temporelle décrite en D2 s'applique par-dessus, à l'encodage et au décodage
> (interface `CellRotation`, actuellement en v0 identité). C'est le point d'architecture le
> plus délicat du projet et il est encore ouvert à la discussion.

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
activé : chacun diffuse son adresse de cellule et voit l'autre apparaître sur le radar. Une
cabine en demande deux aussi, et **la même version des deux côtés** — trois numéros différents
donnent une cabine muette, sans la moindre erreur au journal. L'émulateur suffit pour tout le
reste (forge, clés, radar sans voisins).

> ⚠ Les tests instrumentés (`connectedAndroidTest`) désinstallent l'application et effacent
> le noyau scellé de l'appareil. Notez les cinq données de la fiche avant de les lancer.

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

Atom4Love implémente **D1, D2 et D4**. D5 a été retiré du dépôt le 15 août 2026 ; D3 n'a pas
été abordé.

---

## Projets connexes

### Écosystème UPlanet / G1FabLab (Fred R.)

- **[Astroport.ONE](https://github.com/papiche/Astroport.ONE)** — la station décentralisée
  (NOSTR, IPFS, Duniter/Ğ1, MULTIPASS). AGPL-3.0. Atom4Love en est un client : il dialogue
  avec elle par API et par NOSTR, sans en être une œuvre dérivée.
- **[UPassport](https://github.com/papiche/UPassport)** — API terminal (FastAPI) pour
  l'identité et le stockage uDRIVE, authentification NOSTR NIP-42.
- **[UPlanet](https://github.com/papiche/UPlanet)** — la grille géographique et sa
  visualisation ; c'est aussi là que vit le miroir de l'APK, dans `earth/apk/`.
- **[cabine-33](https://github.com/papiche/cabine-33)** — implémentation Godot des mêmes
  algorithmes (phase personnelle, adressage hexagonal opaque, orchestre). Référence utile
  pour comparer les comportements attendus.
- **[G1FabLab sur Open Collective](https://opencollective.com/monnaie-libre)** — financement
  et actualités de l'écosystème.

### Briques amont

- [secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) — ACINQ, courbes elliptiques
  multiplateformes.
- [noise-java](https://github.com/rweather/noise-java) — Southern Storm Software, implémentation de
  référence du Noise Protocol, portée en Kotlin dans ce dépôt.
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
     caractérisation de la portée RSSI, tests unitaires sur l'encodeur H3, relecture des
     traductions es/en, accessibilité du Plateau. -->

**Discussion.** Salons dédiés sur WhatsApp et Telegram…

---

## Licence

Ce projet est distribué sous licence **GNU Affero General Public License v3.0**
(voir [`LICENSE`](LICENSE)).

Concrètement : vous pouvez utiliser, étudier, modifier et redistribuer ce code. Si vous le
modifiez et le proposez comme service accessible par le réseau, vous devez publier vos
modifications sous la même licence. Il n'y aura jamais de version propriétaire d'Atom4Love,
ni de double licence.

Ce choix aligne le projet sur Astroport.ONE, également en AGPL-3.0.

---

## Crédits

Développement Android : Florent Lefèvre.
Algorithmes fondateurs et écosystème : Fred R. / [G1FabLab](https://opencollective.com/monnaie-libre).
Portage Noise : d'après `noise-java` (Southern Storm Software, MIT) — en-têtes de copyright conservés dans chaque fichier.
Typographies : *Atkinson Hyperlegible Next* (Braille Institute of America), *JetBrains
Mono* (JetBrains) et *Cinzel Decorative* (Natanael Gama) — toutes trois sous SIL Open
Font License 1.1.

</details>

<details name="atom4love-langue">
<summary><b>&nbsp;🇬🇧&nbsp; English</b></summary>

<p align="center">
  <strong>Native Android client for proximity encounters —<br />
  no central server, no biometrics, no location tracking.</strong>
</p>


<p align="center">
  <a href="https://www.gnu.org/licenses/agpl-3.0"><img src="https://img.shields.io/badge/License-AGPL%20v3-blue.svg" alt="License AGPL v3" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg" alt="Kotlin" /></a>
  <a href="https://github.com/FlorentLefevre-lab/Atom4Love/releases/latest"><img src="https://img.shields.io/badge/APK-download-00ffcc.svg" alt="Download the APK" /></a>
  <img src="https://img.shields.io/badge/status-published%20alpha-orange.svg" alt="Status: published alpha" />
</p>

---

## How the affinity between two people is computed

There is no profile to fill in, no photo, no preferences to tick and no recommendation
algorithm. What Atom4Love compares is **the moment and the place of a birth** — the
ephemeris: where the Earth stood on its orbit, how far into its rotation it was, and under
which point of the globe. Two quantities come out of that, and nothing else travels.

**The wave — the personal phase φ.** The moment of birth gives an angle, to the minute and
to the degree: the annual angle of the orbit, the angle of the day, and the offset of the
place on a pentagonal grid that turns once every 14.83 h. Two people crossing paths cross
two waves, and their agreement reads as a single number, **`k`**, between 0.5 and 1. It
rises when the two waves are in phase — **or exactly opposed**, which amounts to answering
each other: a magnet finds its opposite pole. What says nothing is the quarter turn, in
between. None of this needs the network: both devices arrive at the same `k`, each on its
own, from what the **BLE** advertisement already carries — *Bluetooth Low Energy*, the
connectionless, pairing-free broadcast by which two devices signal each other at a few
metres.

**The seal — the Tzolkin's galactic count.** The Tzolkin is the Mayan count of 260 days:
twenty seals, which are archetypes, crossed with thirteen tones, which are energy levels. A
date of birth falls on one of the 260 cells — that is the **KIN**, what the calendar's
vocabulary calls a galactic signature. Around it, the Oracle names four others: the
**guide**, the **challenge**, the **alternation** and the **occult**. They are four ways of
completing you, never an opposite — a challenge is worth no less than a union.

**What comes out of it, and what does not.** One plane falling into line makes a
$\color{orange}{\textsf{\textbf{match}}}$: `k` above 0.90, or a seal from your Oracle facing
you — one person crossed in six. Both together make a
$\color{red}{\textsf{\textbf{super match}}}$, `k` then passing 0.95: three encounters in a
thousand. Both thresholds come from Fred and were measured over 79,800 pairs. There is
nothing else: no score, no ranking, no queue of suggestions, and **nobody is told they were
seen**. Reciprocity does exist, but it is physical — two screens beating the same rhythm in
a room, each having computed it alone.

> The formulas are Fred R. / G1FabLab's, published as **D1** (personal phase) and **D4**
> (Tzolkin oracle matrices) — see "Technical foundations". The model rests on symbolic
> correspondences turned into deterministic computations: that is a design choice, owned as
> such, not a physical theory. See "What this is not".

---

## UI/UX: the user experience of your own incarnation as a card game (Tzolkin)

The bottom bar holds three entries only: **Map**, **Board**, **Nucleus** — what is outside,
the game, and yourself. **Help** and **Settings** became two buttons in the top row. The
**Radar** and the **Constellation** were never two places but two ways of looking at other
people — close by over the radio, or worldwide through the relay; they share the Map tab,
where a selector switches between *Here* and *The world*. The **Board** keeps its place in
the middle, but stays dark until a seal is within range.

<table>
  <tr>
    <td width="33%" valign="top">
      <img src="docs/captures/02-carte-ici.png" alt="Map · Here" width="100%" /><br />
      <b>🌍 Map · Here</b><br />
      What is within antenna range: the cell you broadcast, how many nuclei are in the
      portal, and each one's resonance — a seal and a <code>k</code>, never a name.
    </td>
    <td width="33%" valign="top">
      <img src="docs/captures/03-plateau.png" alt="Board" width="100%" /><br />
      <b>🎴 Board</b> — <i>in daylight</i><br />
      The deal: your card and its four complements, then the cards in range with their heat.
      "Look for the Dragon" narrows a room of thirty down to two people.
    </td>
    <td width="33%" valign="top">
      <img src="docs/captures/04-lanterne.png" alt="Rendezvous" width="100%" /><br />
      <b>🔦 Rendezvous</b><br />
      The lantern beats a rhythm the other phone works out on its own, from the two φ already
      in the air. Nothing more is emitted; the last metre is crossed by eye.
    </td>
  </tr>
  <tr>
    <td valign="top">
      <img src="docs/captures/05-cabine.png" alt="Cabin" width="100%" /><br />
      <b>💬 Cabin</b><br />
      The encrypted, attested channel (Noise), here carried by classic Bluetooth. Questions
      are traded there under a single rule: to offer is to give.
    </td>
    <td valign="top">
      <img src="docs/captures/06-constellation.png" alt="Map · The world" width="100%" /><br />
      <b>🌍 Map · The world</b><br />
      The constellation of nuclei whose LOVE key the station has sealed, each at its
      <b>place of birth</b> to the kilometre — never where it currently is.
    </td>
    <td valign="top">
      <img src="docs/captures/07-noyau.png" alt="Nucleus" width="100%" /><br />
      <b>⚛ Nucleus</b> — <i>in daylight</i><br />
      The sealed record and what it computes: conception, portal, KIN, phase. None of it
      leaves. Dissolving erases everything, behind a double confirmation.
    </td>
  </tr>
</table>

<p><i>The thumbnails are shrunk to fit a grid. To read a screen at full size <b>without
leaving this page</b>, open it here — clicking the thumbnail, on the other hand, takes you to
the file's page.</i></p>

<details>
  <summary>🔍 &nbsp;<b>Map · Here</b></summary>
  <p><img src="docs/captures/02-carte-ici.png" alt="Map · Here, full size" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Board</b></summary>
  <p><img src="docs/captures/03-plateau.png" alt="Board, full size" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Rendezvous</b></summary>
  <p><img src="docs/captures/04-lanterne.png" alt="Rendezvous, full size" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Cabin</b></summary>
  <p><img src="docs/captures/05-cabine.png" alt="Cabin, full size" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Map · The world</b></summary>
  <p><img src="docs/captures/06-constellation.png" alt="Constellation, full size" width="540" /></p>
</details>
<details>
  <summary>🔍 &nbsp;<b>Nucleus</b></summary>
  <p><img src="docs/captures/07-noyau.png" alt="Nucleus, full size" width="540" /></p>
</details>

<details>
  <summary>The splash, the Settings, the Help — and the original mockups</summary>

  <table>
    <tr>
      <td width="33%" valign="top">
        <img src="docs/captures/01-splash.png" alt="Splash" width="100%" /><br />
        The atom at launch.
      </td>
      <td width="33%" valign="top">
        <img src="docs/captures/08-reglages.png" alt="Settings" width="100%" /><br />
        Three languages, two lights, today's body — and the version bubble, where updating and
        uninstalling start from.
      </td>
      <td width="33%" valign="top">
        <img src="docs/captures/09-aide.png" alt="Help" width="100%" /><br />
        Help, F.A.Q. and Zion: what the station does, and why it does it that way.
      </td>
    </tr>
  </table>

  <br />

  <p>
    <b>The original mockups (June 2026).</b> They describe the journey <b>as first
    intended</b>, with six tabs, before the cabin, the three-move game and the MULTIPASS
    existed. Kept for the record; the application no longer looks like them.
  </p>

  <p align="center">
    <img src="docs/maquettes-origine.png"
         alt="Android journey — original mockups" width="720" />
  </p>
</details>

> Screenshots taken on 18 August 2026 on two real devices (Pixel 10 Pro in night light,
> tablet in daylight), version `0.2.2`. **The interface is shown in French**; the application
> also ships in English and Spanish, and the language is chosen in the Settings. The two
> nuclei could see each other over BLE: the neighbour, the card in range and the encrypted
> conversation are those of a real crossing, not a staged one. The **MULTIPASS** is not shown:
> its screen carries a PASS code.

---

## Project status

**Stage: published alpha — v0.2.2 (versionCode 4, 17 August 2026).** The application
installs, updates itself, and the whole journey has been exercised on real devices: forging
your nucleus, deriving your keys, appearing on someone else's radar, opening an encrypted
cabin and talking in it. Proximity detection and the cabin have been **validated across two
phones**, in both directions.

This is not a beta yet: nothing is promised about stability, the D2 rotation is not settled,
and trust on first contact still needs hardening.

### What works today

- **Forging the nucleus** — date, time (optional), place and wave; town autocompletion, a
  summary before sealing, local persistence, complete dissolution.
  ⚠ The precision of the coordinates is part of the key: they are displayed and entered in
  full, otherwise the same record reopens a different key.
- **Deterministic keys** — the full PBKDF2 → scrypt → SHA-256 → secp256k1 chain, ported to
  Kotlin from the station's own tools and **verified byte for byte** against it on a real
  MULTIPASS.
- **NOSTR, reading and writing** — the relay is derived from the host, as on Fred's side. The
  app publishes its ATOM4LOVE certificate (kind 30078, `d=atom4love`) on an explicit and
  irreversible gesture that first shows exactly what will leave; it keeps a NIP-02 contact
  list (kind 3) fed by attested encounters, listens for welcomes, and carries a cabin room
  (kind 24242).
- **MULTIPASS** — enrolment requested from the app, LOVE key re-derivation after a re-forge.
  Everything else (wallets, uDRIVE) remains the station's work.
- **Geographic addressing** — H3 cell computed from the position, Goldberg portal derived
  from the place of birth. No coordinate leaves the device, and location never goes through
  Google services.
- **Continuous BLE proximity** — a 17-byte advertisement (4D address, presence token,
  polarity, Mayan seal, phase), background scanning, a register of neighbours. Measured
  range: **7 m**. The beacon only announces a cell if location has been granted, and falls
  silent during transfers.
- **The three-move game** — the deal (Board), recognition (the lantern), questions (the
  cabin). $\color{orange}{\textsf{\textbf{Match}}}$ and
  $\color{red}{\textsf{\textbf{super match}}}$ on Fred's thresholds, calibrated over 79,800
  pairs. Nothing is ranked, nothing is scored, and nobody learns they were chosen.
- **Encrypted cabin** — Noise, **fully ported to Kotlin** (29 files, checked byte for byte
  against the reference Java implementation and in a real Kotlin ↔ Java cabin). Four media,
  escalating on failure: BLE (the only door, the only one that discovers and attests),
  classic Bluetooth RFCOMM (78–102 kB/s), the venue's Wi-Fi, Wi-Fi Direct (15.5 MB/s).
  Attachments, video at its original quality, everything erased on the way out.
- **Built-in updating** — the app reads a manifest, downloads the APK, **verifies its SHA-256
  digest** and hands over to the system installer. Uninstalling starts from the same place.
- **Finish** — day and night themes, three languages (French, English, Spanish), Material 3,
  embedded typefaces, animated splash.
- **45 test files**, JVM and instrumented, including a differential test comparing the Noise
  port against its reference.

### What is still open

- **D2 temporal rotation** — v0 broadcasts the static H3 index, with no rotation
  (`CellRotation.None`). Owned as such while the formula is not settled: the measured BLE
  range (7 m) bounds what is revealed to devices already in the same room.
- **Trust on first contact** — the cabin encrypts and attests, but nothing yet protects
  against a stranger presenting themselves as someone else at the very first encounter.

**The most structural architecture decisions (D2 rotation, first contact) are still open:
this is the right moment to weigh in on them.**

---

## Install

The APK is signed (schemes v2 and v3) and published on GitHub:

```
https://github.com/FlorentLefevre-lab/Atom4Love/releases/latest/download/atom4love-latest.apk
```

That address names no version: it always serves the latest. The [`latest.json`](latest.json)
manifest carries the number, the size, the date and the SHA-256 digest — it is what the
application reads to learn it is behind, and that digest is what it verifies before offering
to install.

Once the first version is installed, the following ones are taken from inside the
application, through the version bubble at the bottom of the Settings.

> **Play Protect.** Android warns that it does not know the publisher: that is normal
> behaviour for an application from outside a store. Reference distribution will go through
> [F-Droid](https://f-droid.org/) and signed APKs — the AGPL sits badly with the Play Store's
> terms.

---

## What is this?

Atom4Love is an Android application that connects people who are geographically close,
building on the decentralised [Astroport.ONE](https://github.com/papiche/Astroport.ONE)
infrastructure (NOSTR + IPFS + Ğ1).

Three mechanisms set it apart from a conventional dating application:

**Deterministic identity.** The user's identity is derived from stable personal parameters,
never from biometric data nor from an account hosted by a third party. No server holds a
profile.

**Opaque geographic addressing.** The application never transmits GPS coordinates. It
publishes the identifier of a hexagonal cell from a planetary tiling, meant to be subjected
to a temporal rotation: the same identifier would no longer designate the same physical place
from one moment to the next. That is what the specification calls a 4D address — the rotation
formula is the point still open (see "Project status").

**The encounter is the authentication.** There is no open messaging: you first draw a card on
each other, then recognise each other by eye in the room, and the encrypted cabin only opens
afterwards — never to discover someone. The game reveals no identity; it lets you become
findable.

### What this is not

- No central server, no profile database, no account to create.
- No biometrics, no face recognition, no fingerprint.
- No proprietary ranking algorithm: the matching logic is in this repository, readable and
  modifiable.
- No score and no server-side reciprocity: two phones beating the same rhythm in a room are
  noticed by eye, nothing travels to say it.
- **Not a project with scientific pretensions.** The resonance model of D1 and D4 rests on
  symbolic correspondences (birth ephemerides, the Tzolkin calendar) turned into
  deterministic, reproducible computations. That is a design choice, owned as such, not a
  physical theory. The code, for its part, is judged on ordinary criteria: determinism, test
  coverage, absence of data leaks.

---

## Technical stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.2 |
| Interface | Jetpack Compose (Material 3), adaptive layouts, day/night theme, fr/en/es |
| Architecture | Hilt, Room, DataStore, WorkManager, OkHttp |
| Cryptography | [secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) (ACINQ); scrypt and PBKDF2 for the LOVE key derivation |
| Encrypted cabin | [Noise Protocol](https://noiseprotocol.org/) — full Kotlin port of `noise-java`, differentially tested against its reference |
| Messaging | NOSTR — NIP-01, NIP-02 (contact list), NIP-09, NIP-19, NIP-42, NIP-78 (kind 30078 `d=atom4love`) |
| Geography | [H3](https://h3geo.org/) 4.4.0 (Uber) for the static hexagonal tiling — local patched AAR, the upstream artefact omits `libm` |
| Proximity | BLE advertising + scanning (17-byte advertisement, continuous service), RFCOMM, Wi-Fi Direct, framed TCP sockets |
| Media | Media3 (cabin video, never re-encoded), Coil |
| Typography | [Atkinson Hyperlegible Next](https://fonts.google.com/specimen/Atkinson+Hyperlegible+Next) (text — designed for low vision), [JetBrains Mono](https://fonts.google.com/specimen/JetBrains+Mono) (data, addresses, counters) and [Cinzel Decorative](https://fonts.google.com/specimen/Cinzel+Decorative) (the name, on the splash) — embedded, SIL OFL 1.1, texts in `licenses/` |
| Build | Gradle KTS 9.5, AGP 9.3.1, single `:composeApp` module — minSdk 26, targetSdk 36, JDK 17. The tree is already named like a multiplatform project (`commonMain`, `androidMain`, `iosMain`), Android alone is compiled — see `docs/note-portage-ios.md` |

> **Implementation note.** H3 provides the reference hexagonal tiling; the temporal rotation
> layer described in D2 applies on top of it, at encoding and decoding time (the
> `CellRotation` interface, currently the v0 identity). It is the most delicate architectural
> point of the project and it is still open to discussion.

---

## Getting started

```bash
git clone https://github.com/FlorentLefevre-lab/Atom4Love.git
cd Atom4Love
./gradlew assembleDebug
```

Requirements: JDK 17+, Android SDK. No API key, no account, nothing to configure: the keys
are derived locally from the birth parameters entered on first launch.

To see proximity work you need **two physical devices** with Bluetooth on: each broadcasts its
cell address and sees the other appear on the radar. A cabin needs two as well, and **the same
version on both sides** — three different numbers give a silent cabin, without a single error
in the log. The emulator is enough for everything else (forging, keys, radar with no
neighbours).

> ⚠ Instrumented tests (`connectedAndroidTest`) uninstall the application and erase the
> device's sealed nucleus. Write down the five fields of the record before running them.

---

## Technical foundations

The founding algorithms were the subject of five defensive publications on
[TDCommons](https://www.tdcommons.org/) (June 2026, G1FabLab).

These filings establish **enforceable prior art**: they place the described mechanisms into
the state of the art so that they cannot later be appropriated by a patent filing. TDCommons
is an open-submission defensive publication platform — these are **not** peer-reviewed
publications, and they confer no exclusive right. That is exactly the intent.

| | Publication |
|---|---|
| **D1** | [Deterministic Personal Phase Computation from Birth Ephemeris Data for Social Resonance Matching](https://www.tdcommons.org/dpubs_series/10326) |
| **D2** | [4D Opaque Hexagonal Geo-Addressing Scheme Using Dynamic Goldberg Polyhedra](https://www.tdcommons.org/dpubs_series/10327) |
| **D3** | [Biometric Birth Ephemeris as Deterministic Parameters for Shamir Secret Sharing Key Recovery](https://www.tdcommons.org/dpubs_series/10328) |
| **D4** | [Tzolkin Kin-Based Oracle Matrices Combined with Phase Interference Metrics](https://www.tdcommons.org/dpubs_series/10329) |
| **D5** | [Decentralized Additive Synthesis Orchestra Governed by Biometric Phase Fields](https://www.tdcommons.org/dpubs_series/10330) |

Atom4Love implements **D1, D2 and D4**. D5 was removed from the repository on 15 August 2026;
D3 has not been tackled.

---

## Related projects

### The UPlanet / G1FabLab ecosystem (Fred R.)

- **[Astroport.ONE](https://github.com/papiche/Astroport.ONE)** — the decentralised station
  (NOSTR, IPFS, Duniter/Ğ1, MULTIPASS). AGPL-3.0. Atom4Love is a client of it: it talks to it
  over its API and over NOSTR, without being a derivative work.
- **[UPassport](https://github.com/papiche/UPassport)** — terminal API (FastAPI) for identity
  and uDRIVE storage, NOSTR NIP-42 authentication.
- **[UPlanet](https://github.com/papiche/UPlanet)** — the geographic grid and its
  visualisation; it is also where the APK mirror lives, under `earth/apk/`.
- **[cabine-33](https://github.com/papiche/cabine-33)** — a Godot implementation of the same
  algorithms (personal phase, opaque hexagonal addressing, orchestra). A useful reference for
  comparing expected behaviours.
- **[G1FabLab on Open Collective](https://opencollective.com/monnaie-libre)** — funding and
  news from the ecosystem.

### Upstream building blocks

- [secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) — ACINQ, multiplatform elliptic
  curves.
- [noise-java](https://github.com/rweather/noise-java) — Southern Storm Software, the
  reference implementation of the Noise Protocol, ported to Kotlin in this repository.
- [H3](https://github.com/uber/h3) — Uber, hexagonal indexing.
- [NOSTR NIPs](https://github.com/nostr-protocol/nips) — the protocol specifications.

---

## Contributing

Contributions are welcome, including from people new to NOSTR or to Compose. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the details.

**Signing commits.** The project uses the [Developer Certificate of
Origin](https://developercertificate.org/). Sign your commits with `git commit -s`, which
adds a `Signed-off-by` line. There is no CLA: nobody collects your rights, the code stays
under AGPL-3.0.

**Where to start.** Issues labelled `good first issue` are meant to be doable without knowing
the whole system.

**Discussion.** Dedicated rooms on WhatsApp and Telegram …

---

## Licence

This project is distributed under the **GNU Affero General Public License v3.0** (see
[`LICENSE`](LICENSE)).

Concretely: you may use, study, modify and redistribute this code. If you modify it and offer
it as a service reachable over a network, you must publish your modifications under the same
licence. There will never be a proprietary version of Atom4Love, nor a dual licence.

That choice aligns the project with Astroport.ONE, also under AGPL-3.0.

---

## Credits

Android development: Florent Lefèvre.
Founding algorithms and ecosystem: Fred R. / [G1FabLab](https://opencollective.com/monnaie-libre).
Noise port: after `noise-java` (Southern Storm Software, MIT) — copyright headers kept in
every file.
Typefaces: *Atkinson Hyperlegible Next* (Braille Institute of America), *JetBrains Mono*
(JetBrains) and *Cinzel Decorative* (Natanael Gama) — all three under the SIL Open Font
License 1.1.

</details>
