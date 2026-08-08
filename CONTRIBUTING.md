# Contribuer à Atom4Love

Merci de l'intérêt que vous portez au projet. Les contributions sont bienvenues, y compris
de la part de personnes qui découvrent NOSTR, Compose ou l'écosystème UPlanet.

Le projet est au stade d'amorçage : les décisions d'architecture sont encore ouvertes et
une contribution bien argumentée peut réellement infléchir la conception.

---

## Mettre en place l'environnement

```bash
git clone https://github.com/FlorentLefevre-lab/Atom4Love.git
cd Atom4Love
./gradlew assembleDebug
```

Prérequis : JDK 17 ou supérieur, Android SDK (via Android Studio ou les *command-line tools*).

Aucune clé d'API, aucun compte et aucun relais NOSTR ne sont nécessaires pour compiler et
lancer le mode démo. Si ce n'est pas le cas chez vous, c'est un bug : ouvrez une issue.

---

## Signature des commits (DCO)

Le projet applique le [Developer Certificate of Origin](https://developercertificate.org/).
En signant vos commits, vous attestez simplement que vous avez le droit de soumettre le code
que vous proposez sous la licence du projet.

```bash
git commit -s -m "Votre message"
```

L'option `-s` ajoute une ligne `Signed-off-by:` à partir de votre configuration Git. Pensez
à renseigner `user.name` et `user.email` au préalable.

**Il n'y a pas de CLA.** Personne ne collecte vos droits d'auteur, aucune cession n'est
demandée, et le code restera sous AGPL-3.0. Vous restez titulaire de vos contributions.

---

## Proposer une modification

1. Ouvrez une issue avant de commencer un travail conséquent, pour éviter les doublons et
   valider l'approche.
2. Créez une branche depuis `main` : `git switch -c feat/nom-explicite`.
3. Faites des commits atomiques et signés, avec un message à l'impératif présent.
4. Ouvrez la *pull request* en décrivant le problème résolu, pas seulement le code écrit.
5. Une PR qui échoue à la compilation ou aux tests ne sera pas relue tant qu'elle n'est pas
   verte.

Les petites PR sont relues vite. Les PR de 2 000 lignes touchant six domaines à la fois
stagnent — découpez.

---

## Conventions de code

- **Kotlin** : conventions officielles JetBrains. Indentation 4 espaces, pas de tabulations.
- **Compose** : composables sans état autant que possible, l'état remonte à l'appelant.
  Un composable qui accède directement à un dépôt de données ou au réseau sera refusé.
- **Nommage** : anglais pour le code et les identifiants, français ou anglais pour les
  commentaires et la documentation, au choix.
- **Tests** : toute logique de calcul (dérivation d'identité, encodage géographique, phase)
  doit être couverte par des tests unitaires déterministes. C'est non négociable : ces
  fonctions doivent produire exactement le même résultat sur tout appareil, indéfiniment.

---

## Règles d'architecture non négociables

Ces contraintes découlent directement de la raison d'être du projet. Une PR qui les enfreint
sera refusée quelle que soit sa qualité technique.

- **Aucun service centralisé.** Pas de Firebase, pas de Google Play Services, pas de backend
  propriétaire, pas de service d'authentification tiers.
- **Aucune télémétrie.** Pas d'analytique, pas de rapport de plantage automatique, pas de
  collecte d'usage — même anonymisée, même optionnelle.
- **Aucune coordonnée GPS brute ne quitte l'appareil.** La position ne sort qu'encodée sous
  forme d'adresse hexagonale opaque associée à son timestamp.
- **Aucune donnée biométrique.** Ni capture, ni stockage, ni traitement.
- **Aucune dépendance sous licence non libre**, ni sous licence incompatible avec l'AGPL-3.0.
  En cas de doute sur une bibliothèque, posez la question dans l'issue avant de l'intégrer.
- **Aucun secret dans le dépôt.** Clés, jetons et identifiants passent par des variables
  d'environnement ou `local.properties`, jamais par un commit.

---

## Par où commencer

Les issues étiquetées [`good first issue`](https://github.com/FlorentLefevre-lab/Atom4Love/labels/good%20first%20issue)
sont pensées pour être réalisables sans connaître l'ensemble du système.

Les issues `help wanted` demandent davantage de contexte mais restent délimitées.

Vous pouvez aussi contribuer sans écrire de Kotlin : traduction, documentation, tests sur
des appareils variés, revue de la conception cryptographique, retours d'ergonomie.

---

## Discussion

<!-- À COMPLÉTER : salon Matrix, forum, ou adresse de contact -->

Pour toute question sur l'écosystème sous-jacent, voir
[Astroport.ONE](https://github.com/papiche/Astroport.ONE) et le
[collectif G1FabLab](https://opencollective.com/monnaie-libre).

---

## Licence des contributions

En contribuant, vous acceptez que votre travail soit distribué sous
**GNU Affero General Public License v3.0**, comme le reste du projet.
