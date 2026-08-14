# Ce que ton code a répondu, et les onze questions qu'il ne peut pas trancher

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 14 août 2026
**Statut :** trois questions bloquantes, huit à confirmer

Suite de la note du 10 août. Entre-temps on a lu tes sources plutôt que d'attendre :
`Astroport.ONE/tools/atom4love_publish.py`, `tools/phi2x.py`, `UPlanet/earth/atomic.html`,
`UPlanet/earth/phi2x.js`, `cabine-33/autoloads/Phi2X_Math.gd`, `zelkova/lib/g1/phi2x.dart`.
**Trois des dix-neuf questions sont réglées** — ton code les disait, il fallait le lire :

| # (10 août) | réponse trouvée dans ton code | ce qu'on a fait |
|---|---|---|
| 1 · formule de dérivation | PBKDF2-HMAC-SHA256, 600 000 tours, domaine `uplanet-a4l-v1`, base64url sans padding, puis `keygen` | porté sauf `keygen` — voir Q2 |
| 2 · encodage du SALT | `{AAAAMMJJHHmm}_{lat:.2f}_{lon:.2f}_{polarité}_{poids:.1f}_50_170` — **sept champs**, on n'en produisait que cinq | aligné |
| 3 · heure locale ou UTC | **locale**, convertie par `local_solar_to_utc` (longitude × 4 min + équation du temps) | aligné — on lisait l'heure d'horloge telle quelle, dix minutes d'écart à Paris |

Ce qui suit est ce que le code ne peut pas dire : des intentions, et deux contradictions
internes à ton propre dépôt.

---

## 0. La question qui vaut pour toutes : un vecteur de test

Publie une fiche d'exemple avec tout ce que la station en dit. Une seule ligne de
référence nous évite dix allers-retours — et permet à n'importe quel client de se
vérifier seul.

Voici le nôtre, calculé avec **ton** python. Confirme, ou corrige :

```
fiche    : 1985-04-17T15:30, lat 48.86, lon 2.35, polarité 0, poids 3.2 kg
instant  : 198504171520                     (local_solar_to_utc → unix 482599200)
SALT     : 198504171520_48.86_2.35_0_3.2_50_170
PEPPER   : 198407111156_48.86_2.35_3.2_50
salt étiré   : ojW5CjempRk65sowchLFH_qY9nU0hol3WAD1OsF8xBw
pepper étiré : i1TE464sBFqFBE0U6mRe4nH-vSlqdPfnt9KUpPGWD9k
phase    : 1.435898643 rad
KIN      : 244 · Graine · ton 10 · Jaune
npub     : ?                                ← la seule case qu'on ne sait pas remplir
```

**Il ne manque que le npub**, et c'est la question 2.

---

## A. La clé — BLOQUANT

**Q1. Les deux tailles du SALT, `50` et `170`, sont-elles gelées pour toujours ?**
Tu les mets en dur avec le commentaire « non collectée » (`BIRTH_HEIGHT_CM_DEFAULT`,
`CURRENT_HEIGHT_CM_DEFAULT`). On a fait pareil, et on a écrit dans notre code qu'on n'y
touche pas. Mais la question doit être tranchée une fois : **si tu les collectes un
jour, toutes les clés déjà dérivées meurent** — y compris celles de gens qui auront
grandi ou grossi entre-temps, ce qui rend la clé irrécupérable au lieu de simplement
différente. Notre position : gelées à jamais, et une taille réelle ne doit jamais
entrer dans le SALT.

**Q2. `keygen` : quelle est la dérivation finale ?**
On sait tout jusqu'aux deux chaînes étirées ci-dessus. Après, tu écris les deux lignes
dans un fichier et tu appelles `keygen -t nostr -s -i <cred>`. Sans cette dernière
étape, aucun client ne peut redériver sa clé LOVE — donc « vos proches sont votre
phrase de récupération » n'est vrai que si ta station est debout. Deux questions en
une : **quel algorithme**, et **est-ce volontairement réservé à la station ?** Si c'est
un choix d'architecture, dis-le et on arrête de chercher — mais alors il faut cesser
d'écrire partout que la clé se redérive n'importe où.

**Q3. Le fuseau civil et l'heure d'été sont ignorés — c'est bien définitif ?**
`local_solar_to_utc` ne connaît que la longitude et l'équation du temps. Un Parisien né
le 17 avril 1985 à 15 h 30 d'horloge était en UTC+2 : l'instant réel est 13 h 30 UTC,
ton calcul scelle 15 h 20. L'écart est constant et reproductible, donc **sans effet sur
la clé** — mais il y en a un sur la phase personnelle, qui prétend décrire une position
réelle de la Terre. Assumé ?

**Q4. `birth_datetime` est bien l'heure d'horloge du lieu, jamais UTC ?**
Le champ que le client poste à `/atom4love/activate`. Si un client envoyait déjà de
l'UTC, ta conversion s'appliquerait une seconde fois. On envoie l'heure de l'acte, telle
qu'elle est écrite dessus.

---

## B. ω_bio — deux formules dans ton dépôt, facteur 16

C'est le point qui nous a fait ouvrir cette note. **Ton dépôt calcule ω_bio de deux
façons qui n'ont pas le même ordre de grandeur.**

```python
# phi2x.py::compute_omega_bio — « synchronisée avec phi2x.js et Phi2X_Math.gd »
water_kg = max(0.1074*h + 0.3362*w - 5.0, 1.0)   # ♂, taille et poids ACTUELS
omega    = F_WATER * water_kg / 70.0             # → 225.81 Hz  (170 cm, 70 kg)

# atom4love_publish.py, ligne 236 — ce qui est RÉELLEMENT publié dans le 30078
water_ratio = 0.65 if polarity == 0 else 0.60
omega_bio   = F_WATER * (birth_weight * water_ratio / 70.0)   # → 12.77 Hz  (3.2 kg)
```

**Q5. Laquelle fait foi ?** C'est la valeur qu'un client affiche, et surtout celle sur
laquelle deux personnes se comparent. Aujourd'hui `compute_omega_bio` n'est appelée par
**aucun** de tes scripts de publication — seulement par le CLI de démonstration de
`phi2x.py`. Trois de tes quatre implémentations (py, js, gd) sont d'accord entre elles
et aucune ne sert. On a porté celle-là, parce que tu la désignes toi-même comme
canonique ; dis-nous si on s'est trompé de camp.

**Q6. Si c'est Watson : comment la station reçoit-elle taille et poids ?**
Elle ne les a pas. `atomic.html` a bien quatre champs (`birth-weight`, `birth-height`,
`current-height`, `current-weight`), mais le POST vers `/atom4love/activate` n'envoie
que `email, birth_datetime, birth_lat, birth_lon, birth_weight, polarity, auth_event,
conception_datetime, birth_place, home_lat/lon`. Les mesures restent dans le
`localStorage` du navigateur. Faut-il ajouter `current_height` et `current_weight` au
formulaire d'activation ? Sous quels noms exacts ? Nous les collectons désormais côté
Android et nous sommes prêts à les poster.

**Q7. Les constantes −5,0 et −2,0, d'où viennent-elles ?**
Watson (1980) fait décroître l'eau des hommes avec l'âge : `2,447 − 0,09156 × âge`. Tu
remplaces tout ce terme par −5,0, ce qui équivaut à figer un homme d'environ 81 ans —
un homme de 40 ans perd ~3,8 L au passage. Chez la femme, ton −2,0 colle à son −2,097.
Simplification assumée pour se passer de l'âge, ou dérive à corriger ? On a porté tes
valeurs telles quelles et on l'a écrit dans le code.

---

## C. À confirmer — sans doute des bugs, on ne touche à rien sans toi

**Q8.** `zelkova/lib/g1/phi2x.dart::phi2xComputeOmegaBio` se déclare « port exact de
`compute_omega_bio()` de phi2x.py » et **ne prend pas la taille** : il applique le ratio
fixe. C'est le commentaire qui ment, ou le code ?

**Q9.** `phi2x.py --omega 170 70 0` affiche `water_kg=45.50kg` alors que la formule qu'il
vient d'exécuter a calculé `36.79`. La ligne de debug applique le vieux ratio.

**Q10.** `birth-height` (30–65 cm) est saisi dans `atomic.html` et n'entre nulle part :
ni dans le SALT (50 en dur), ni dans ω_bio (taille adulte). À quoi sert-il ? Faut-il le
demander côté Android ? On ne l'a pas mis.

**Q11.** Le `conception_datetime` que le client envoie ne sert qu'aux tags `kin_c` :
le PEPPER, lui, recalcule `naissance − 280 jours` sans le regarder. Et
`phi2x.py::compute_conception_unix` module cette gestation par le poids
(`280 + (poids − 3,5) × 4`) alors que le PEPPER prend 280 jours secs. Les deux
coexistent volontairement — la première pour l'affichage, la seconde pour la clé ?

---

## D. Position

`atom4love_publish.py` appelle `atomic.html` « l'ancien client ». Quel client fait
référence maintenant ? Notre application Android reproduit désormais ta phase
(`1.435898643`), ton KIN (`244`) et ton SALT au caractère près, vérifiés par des tests
qui rejouent les sorties de ton python. Si tu veux un client de référence qui se teste
contre toi à chaque commit, elle est candidate.

**Rappels non répondus du 10 août**, toujours ouverts et toujours bloquants pour le
Radar : la grille pentagonale tournante (py, js) contre figée (`Phi2X_Math.gd`) —
2,03 radian d'écart, deux joueurs incomparables ; et le modulo des naissances d'avant
1970, positif en Python, signé en JS et en GDScript.
