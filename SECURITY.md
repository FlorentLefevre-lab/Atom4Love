# Politique de sécurité

## Avertissement sur l'état du projet

Atom4Love est en développement précoce. **Le code n'a fait l'objet d'aucun audit
indépendant.** N'utilisez pas cette application avec des clés cryptographiques réelles, des
données personnelles réelles ou votre position réelle tant qu'une version auditée n'a pas
été publiée.

## Versions prises en charge

Aucune version stable n'est publiée à ce jour. Seule la branche `main` est maintenue.

| Version | Prise en charge |
|---|---|
| `main` | ✅ |

## Ce que nous considérons comme une vulnérabilité

Ce projet ayant pour raison d'être la protection de l'identité et de la position, le
périmètre est plus large que pour une application ordinaire. Nous traitons comme des
vulnérabilités, en particulier :

**Désanonymisation de la position.** Toute méthode permettant, à partir d'adresses
hexagonales publiées, de reconstituer une position réelle ou une trajectoire sans disposer
du timestamp requis. Toute corrélation entre plusieurs adresses réduisant significativement
l'espace de recherche.

**Fuite de métadonnées.** Toute caractéristique observable d'un message — taille, cadence,
séquencement, en-têtes — permettant à un relais ou à un observateur réseau d'inférer
l'identité des correspondants ou la nature des échanges.

**Faiblesse de la dérivation d'identité.** Entropie insuffisante, collisions, ou toute
méthode permettant de retrouver une clé privée à partir de données publiques ou devinables.

**Manipulation des parts de secret.** Toute faille dans le traitement, le stockage ou la
transmission des fragments SSSS permettant à une partie non autorisée de reconstituer une
clé.

**Vulnérabilités classiques** : exécution de code, injection, contournement de chiffrement,
stockage non protégé de données sensibles sur l'appareil, permissions Android excessives.

## Ce qui n'entre pas dans le périmètre

- Les questions de conception déjà documentées comme telles dans le README.
- Les faiblesses des dépendances tierces déjà publiquement connues et corrigées en amont —
  signalez-les plutôt comme une issue de mise à jour.
- Les problèmes exigeant un accès physique à un appareil déverrouillé.

## Comment signaler

**Ne créez pas d'issue publique pour une vulnérabilité.**

Utilisez de préférence le signalement privé de GitHub :
onglet **Security** du dépôt → **Report a vulnerability**. Le rapport reste confidentiel
jusqu'à publication d'un correctif.

À défaut : <!-- À COMPLÉTER : adresse de contact, idéalement avec une clé publique -->

Merci d'inclure une description du problème, les étapes de reproduction, la version ou le
commit concerné, et l'impact estimé.

## Ce que vous pouvez attendre

- Accusé de réception sous 7 jours.
- Évaluation initiale et retour sous 30 jours.
- Mention dans les crédits du correctif, sauf si vous préférez rester anonyme.

Le projet ne dispose d'aucun budget et **n'offre pas de prime**. C'est un projet libre
bénévole ; nous ne pouvons offrir que de la reconnaissance et de la réactivité.

## Divulgation

Nous vous demandons de nous laisser un délai raisonnable pour corriger avant toute
publication. En contrepartie, nous nous engageons à ne pas faire traîner : si un correctif
tarde, nous vous en expliquerons la raison plutôt que de vous ignorer.
