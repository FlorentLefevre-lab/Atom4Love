# φ ne vit pas dans la station — et c'est là que ça coince

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 15 août 2026
**Statut :** une bonne nouvelle, un écart de 0,064 rad, deux questions

## 0. D'abord : ton activation remarche

`POST /atom4love/activate` a réussi ce matin sur un compte neuf
(`florent.lefevre.test3@yopmail.com`, `u.copylaradio.com`). La station a rendu
`love_nsec`, `love_npub`, `love_hex`, **et publié son kind 30078** — il est sur
`relay.copylaradio.com` avec son `email_enc`.

Le blocage de la note du 12 août — `ACTIVATION_FAILED` parce que la publication
échouait — **n'existe plus**. Merci.

Au passage, sur l'ancien compte l'activation renvoie maintenant
`LOVE_KEY_EXISTS_MISMATCH`, et c'est **correct** : j'avais reforgé mon noyau
entre-temps, ta station protège la clé déjà posée. Le message dit exactement ce
qu'il faut.

**Q4.** Y a-t-il un moyen de **libérer** la clé LOVE d'un compte quand la fiche a
légitimement changé, ou faut-il ouvrir un compte neuf à chaque reforge ?

---

## 1. La clé, on s'en accommode. C'est φ le sujet.

Nous avons maintenant deux certificats pour la même personne, la même fiche, tous
les deux sur ton relais en ce moment : celui de ta station et celui d'Atom4Love.
Les clés LOVE n'ont rien à voir l'une avec l'autre.

**Et ça, ce n'est pas grave** — c'est même le contrat : la station fait la clé,
elle fait autorité, et notre dérivation locale n'est qu'une clé provisoire que
la clé LOVE remplace dès qu'un MULTIPASS existe. On ne te demande pas de changer
ça.

Ce qui est grave, c'est ce qui diverge **à côté** :

| | ta station | Atom4Love | écart |
|---|---|---|---|
| **`personal_phase`** | **4,651994** | **4,588028** | **0,064 rad** |
| tag `g` (maille 1 km) | `a4l:P06H8C1680AF` | `a4l:P06H8C1780AE` | 1,7 km |
| `kin_num` | 83 | 83 | — |
| `omega_bio` | 12,7659 | 12,7659 | — |

Le KIN et l'ω_bio tombent juste : la date et le poids voyagent bien. Il ne reste
que φ — et la maille, qui en dépend par le même instant.

---

## 2. Pourquoi φ ne peut pas attendre la station

Parce que **φ vit hors d'elle**. Trois endroits, aucun ne passe par un compte :

- l'**annonce BLE de proximité** d'Atom4Love porte φ. Un téléphone sans compte
  l'émet, un autre la lit, et les deux calculent leur k tout seuls ;
- le **Radar** compare ces φ-là, à portée d'antenne, hors ligne ;
- la **carte** compare notre φ à celle des certificats de ton relais.

Là, ta station ne fait rien. C'est nous. Si notre φ n'est pas la tienne, alors une
personne inscrite et une personne sans compte **ne s'accorderont jamais** sur leur
résonance, alors qu'elles sont nées au même instant.

### Ce que 0,064 rad coûte, en clair

`k = 1 / (1 + |sin Δφ|)`. Deux personnes réellement **en phase**, l'une calculée
par toi, l'autre par nous :

```
Δφ = 0,064   →   k = 1 / (1 + 0,0639) = 0,940   →   88 %
```

- Ton vortex ne trace son lien d'or et n'annonce sa « singularité créatrice »
  qu'au-dessus de **k > 0,95**. Une rencontre parfaite **ne franchirait jamais le
  seuil**.
- Et ta tolérance de singularité optique — `is_optical_singularity(tol=0.05)` —
  vaut **0,05 rad**. **Notre désaccord, 0,064, est plus grand que la fenêtre dans
  laquelle tu déclares un accord.**

L'erreur est maximale aux extrémités de l'échelle, là où k vaut 1 : exactement là
où le produit veut dire quelque chose. Au milieu, elle ne se voit pas.

**Ça se voit déjà sur ta carte** : la même personne y apparaît deux fois, à un
kilomètre, **à 88 % de résonance avec elle-même**. C'est le chiffre ci-dessus,
grandeur nature.

---

## 3. Ce qu'on t'a envoyé, au caractère près

```
POST /atom4love/activate
  email          florent.lefevre.test3@yopmail.com
  birth_datetime 1982-09-18T11:54
  birth_lat      49.570096
  birth_lon      3.614939
  birth_weight   3.2
  polarity       0
  birth_place    Laon, France
  auth_event     <kind 22242 signé par le nsec principal>
```

Pas de `conception_datetime` : ton PEPPER recalcule naissance − 280 jours de
toute façon.

## 4. Ce que ça donne quand je refais le calcul avec **ton** code

`tools/phi2x.py` et `tools/atom4love_publish.py` de `master`, sur ces valeurs
exactes :

```
local_solar_to_utc(1982, 9, 18, 11, 54, lon=3.614939)  →  1982-09-18 11:33:00 UTC
compute_personal_phase(401196780, 49.570096, 3.614939) →  4.588028          ← notre valeur
geo_tag_a4l(49.570096, 3.614939, 401196780)            →  a4l:P06H8C1780AE  ← notre maille
```

**Avec ton code, sur nos entrées, on retrouve nos valeurs — pas les tiennes.**

En cherchant quel instant donnerait ta φ, je tombe sur **11 h 38 UTC**, cinq
minutes après ce que rend ton propre `local_solar_to_utc`. Et aucune coordonnée
plausible ne rend ta maille : les coordonnées envoyées, Laon réel
(`49,5646 / 3,6244`) et l'arrondi à deux décimales donnent tous les trois
`8C1780AE`.

---

## 5. Les deux questions

**Q5. Pour `1982-09-18T11:54` à Laon, quelle heure UTC ta station retient-elle au
juste ?** Cinq minutes séparent nos deux φ, et c'est tout ce qui les sépare. Si
`local_solar_to_utc` a bougé — équation du temps, arrondi à la minute, autre
chose —, c'est ce que nous devons porter.

**Q6. Quelle version de `phi2x.py` la station déployée fait-elle tourner ?** Je
travaille sur `master` de `papiche/Astroport.ONE`. Si l'écart vient de là, ces
deux questions n'en font qu'une.

Et la demande qui les résoudrait toutes : **un vecteur de référence publié**. Une
fiche d'exemple, et à côté l'instant UTC retenu, φ, le KIN, la maille et la clé
LOVE que ta station en tire. Une ligne, et n'importe quel client se vérifie tout
seul — au lieu de t'écrire. Le nôtre, pour commencer, est ci-dessus.

---

## 6. Ce qu'on fait en attendant

Atom4Love **suit la station**, comme convenu : dès qu'un MULTIPASS rend une clé
LOVE, c'est elle qui fait autorité. Mais la résonance, elle, continue de se
calculer chez nous pour tous ceux qui n'ont pas de compte — et c'est pour eux que
cette note existe.

Le certificat publié sous notre clé provisoire reste sur ton relais ; dis-nous si
tu préfères qu'on l'efface, nous n'avons pas le moyen de le faire nous-mêmes.

Le code est sur `github.com/FlorentLefevre-lab/Atom4Love`, branche `main` :
`domain/Phi2X.kt`, `domain/A4lAddress.kt`, `nostr/Certificate.kt` et leurs tests.
