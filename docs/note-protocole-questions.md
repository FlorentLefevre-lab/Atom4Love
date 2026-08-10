# Dix-neuf questions pour figer le protocole

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 10 août 2026 · **Statut :** décisions attendues

L'application tourne maintenant de bout en bout sur du réel : clé LOVE forgée et
persistée, balise BLE validée en croisé (Pixel ↔ Lenovo, pastilles RSSI sur le radar),
antenne NOSTR connectée à `relay.copylaradio.com` en écoute seule, portails Goldberg
calculés. Tout ce qui reste ouvert est de ton ressort : ce sont les points que deux
stations doivent calculer à l'identique sans se parler. Numérotés pour qu'on puisse
s'y référer facilement.

> **Si tu n'as que dix minutes** : les questions **1 à 3** (formule de dérivation,
> encodage du SALT, heure locale ou UTC) et la **8** (convention des portails).
> Ces quatre-là verrouillent l'identité et l'adressage — tout le reste peut suivre.

## A. La dérivation de la clé LOVE — BLOQUANT

Tant que la formule n'est pas figée, toutes les clés forgées sont provisoires.
Ce que fait la v0 embarquée (`LoveKeyForge`) :

```
clé privée = SHA-256( SALT "|" PEPPER )   → re-haché jusqu'à un scalaire secp256k1 valide

SALT   = AAAAMMJJHHmn_lat_lon_sexe_poids     ex : 198504171530_48.86_2.35_0_3.2
         lat/lon à 2 décimales, poids à 1 décimale, point décimal US
PEPPER = timestamp ms de la conception
         = naissance − (280 + (poids − 3,5) × 4) jours
```

1. **Quelle est la formule MULTIPASS exacte d'Astroport.ONE** — algorithme (SHA-256 ?
   scrypt ? argon2 ?), nombre de passes, format d'entrée précis ? Un seul caractère de
   différence entre nos deux implémentations = deux clés sans aucun rapport.
2. **L'encodage du SALT te convient-il** : séparateurs `_`, arrondis (2 décimales
   ≈ 1,1 km), point décimal US ? Chaque arrondi est un choix de protocole : il définit
   quelles saisies « voisines » donnent la même clé.
3. **L'heure de naissance du SALT est-elle locale ou UTC ?** Aujourd'hui l'app prend
   l'heure saisie telle quelle (traitée comme UTC dans le calcul de conception). Un
   natif de Tokyo et un natif de Paris nés « à 15 h 30 » : même valeur dans le SALT,
   ou pas ? À trancher explicitement.
4. **Jumeaux** : nés même lieu, même minute, même poids, même onde ⇒ même clé.
   Assumé, ou faut-il un discriminant ?

## B. Le premier échange publié sur le relais — BLOQUANT

L'antenne est branchée mais volontairement en écoute seule : ce qui part sur un relais
public est public, et pour l'instant rien ne part.

5. **Que publie-t-on en premier** — un kind 0 (profil : quel contenu ?), un événement
   de présence de cabine, les « pensées ici » du radar ? Kinds standards ou custom a4l ?
6. **Publie-t-on avant que la dérivation (question 1) soit figée ?** Avis de Florent :
   non — sinon des npub v0 orphelins traîneront sur le relais pour toujours.
7. **Un seul relais** (copylaradio) ou une liste officielle de démarrage ?

## C. La convention des portails Goldberg — À VALIDER

Implémentée le 10 août, à reproduire à l'identique côté Astroport sinon les adresses
seront incompatibles : les 12 sommets sont les 12 pentagones H3 de la résolution 0,
ordonnés par cellule de base croissante ; le portail d'un lieu est le sommet le plus
proche à vol d'oiseau. Adresse affichée : `a4l:P01H881FB5BB15` (portail + « H » +
cellule H3 en hexa).

| Portail | Étoile | Base H3 | Lat | Lon |
|---|---|---|---|---|
| P01 | Sirius | 4 | 64.7000 | 10.5362 |
| P02 | Canopus | 14 | 50.1032 | −143.4785 |
| P03 | Vega | 24 | 39.1000 | 122.3000 |
| P04 | Arcturus | 38 | 23.7179 | −67.1323 |
| P05 | Rigel | 49 | 10.4473 | 58.1577 |
| P06 | Procyon | 58 | 2.3009 | −5.2454 |
| P07 | Altair | 63 | −2.3009 | 174.7546 |
| P08 | Betelgeuse | 72 | −10.4473 | −121.8423 |
| P09 | Aldebaran | 83 | −23.7179 | 112.8677 |
| P10 | Antares | 97 | −39.1000 | −57.7000 |
| P11 | Pollux | 107 | −50.1032 | 36.5215 |
| P12 | Spica | 117 | −64.7000 | −169.4638 |

8. **Valides-tu cette convention** — ordre, noms d'étoiles, plus-proche-sommet, format
   d'adresse ? (Un test JVM la revalide contre la grille H3 à chaque build ; la table
   ci-dessus fait foi.)
9. **Deux usages du portail** dans l'app : lieu de naissance (fiche d'incarnation) et
   position courante (radar). Les deux notions existent-elles dans ton protocole,
   sous quels noms ?
10. **La dérive φ** (`PHI_DRIFT_HOURS = 14.83`, dans la config depuis le début) : à quoi
    sert-elle exactement, et où doit-elle intervenir ?

## D. La rotation D2 de l'adresse 4D — À VALIDER

La couture est prête (`CellRotation`), mais la v0 est l'identité : l'adresse BLE
diffusée est l'index H3 brut.

11. **La formule D2** : quelle fonction (permutation à clé d'époque ?), quelle période —
    est-ce le 14,83 h de la dérive φ ? — et qui peut l'inverser : tout le monde, ou
    seulement tester l'égalité ?
12. **L'horloge des époques D2** doit-elle être LA fenêtre temporelle unique de tout le
    protocole (rejoint la question 15) ?

## E. Les cinq questions de la note SSID × NOSTR — DÉJÀ POSÉES (10 août)

Rappel de la note précédente (jetons `H(BSSID ∥ sel_de_paire ∥ époque)` chiffrés
NIP-44/NIP-17 entre noyaux liés — jamais de SSID en clair).

13. **Place du signal Wi-Fi** dans la hiérarchie D2 : critère d'entrée dans un portail,
    métadonnée de présence, ou étage à part ?
14. **BSSID ou SSID** comme entrée du jeton ? (Penchant de Florent : BSSID, puisqu'il
    n'est jamais révélé.)
15. **L'époque du jeton** peut-elle partager l'horloge de la rotation D2 ? Une seule
    mécanique de fenêtres glissantes pour tout le protocole serait élégante.
16. **Diffusion publique de jetons** : porte fermée définitivement, ou réservée aux
    lieux publics assumés (cabines) ?
17. **Noyau en 4G sans Wi-Fi** : émettre un jeton « nulle part » pour ne pas fuiter
    l'information d'absence ?

## F. En réserve, si le temps le permet

18. **Le npub dans le payload BLE** : un npub fixe diffusé en continu est un traceur
    radio parfait, en contradiction avec l'esprit D2. Suggestion à débattre : un jeton
    tournant dérivé du npub (même mécanique d'époques), le npub complet ne s'échangeant
    qu'au premier contact — handshake à définir ; c'est lui qui fournit le
    `sel_de_paire` des jetons Wi-Fi.
19. **Les écrans encore muets** — « pensées ici », les 41 cartes du Plateau, la
    Résonance : quelles données, quels événements NOSTR ?

---

*Atom4Love · note préparée avec l'assistance de Claude · état du code au commit
`fc4fa58` (10 août 2026) · mesures issues des tests croisés Pixel 10 Pro ↔ Lenovo
TB350XU.*
