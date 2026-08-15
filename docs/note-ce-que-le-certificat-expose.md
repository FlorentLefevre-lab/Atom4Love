# Ce que le certificat 30078 expose — une fuite en clair, et trois questions

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 15 août 2026
**Statut :** un point urgent, trois à trancher

Atom4Love publie désormais son propre `kind 30078 / d=atom4love` depuis Android,
sans passer par la station — l'activation MULTIPASS refuse toujours (note du
12 août), et il fallait bien exister sur la carte. **Ta porte d'écriture
fonctionne** : `sha256("<pubkey>:ATOM4LOVE_v1")` suffit, `relay.copylaradio.com`
a répondu `OK true`, sans station et sans repli sur `ATOM4LOVE_ALPHA`.

Le certificat est construit à la lettre d'`atom4love_publish.py` : mêmes tags,
même ordre, mêmes arrondis. Voici le nôtre, si tu veux un vecteur de contrôle —
il est vérifié contre ton propre `phi2x.py` par dix tests unitaires :

```
pubkey  6ab2eeb386c083a8c55cbf08ac005f2727d68397241d0c168feb7fb40250e241
tags    d=atom4love · a4l_proof · g=a4l:P06 · g=a4l:P06H8C1780AE
        a5l:8190 · kin=83 · glyph=Nuit · tone=5 · color=Blanc
content {"personal_phase":4.588028,"omega_bio":12.7659,"a5l_amplitude":0.506114,
         "biological_sex":0,"kin_num":83,"version":1}
```

En le construisant, on a relu ce que le relais porte déjà. Trois certificats, et
ce qui suit.

---

## 1. URGENT — une adresse e-mail en clair sur le relais public

Le certificat de `0fd65c06f5b7…` (publié le 25 juin 2026, `created_at`
1782668942) porte l'adresse e-mail de la personne **deux fois en clair** : dans
un tag `email`, et dans son `content` JSON.

Nous ne la recopions pas ici — cette note vit dans un dépôt git. Elle se lit en
une requête :

```
REQ {"kinds":[30078],"#d":["atom4love"],"limit":100}
```

C'est exactement ce que ton propre commentaire interdit, trois lignes au-dessus
du code qui chiffre :

> Email chiffré `$UPLANETNAME` […] **jamais par un observateur extérieur**. Même
> principe que l'obfuscation SSID BLE/WiFi de cabine-33 […] : *ne jamais
> diffuser en clair une donnée qui identifie directement la personne.*
> — `atom4love_publish.py`

L'`atom4love_publish.py` actuel écrit bien `email_enc`. Ce certificat vient donc
d'un client ou d'une version antérieure. Deux choses à faire, et aucune n'est de
notre ressort :

- **prévenir la personne**, c'est son adresse ;
- **supprimer l'événement** — tu as le panneau capitaine d'`atomic_projector.html`
  (`/api/nostr/admin/delete`) et l'`UPLANETNAME` qu'il demande.

Republier par-dessus ne suffit pas : le 30078 est remplaçable, mais tout relais
qui a déjà recopié l'ancien le garde.

**Q1.** Y a-t-il un autre client, encore en service, qui écrit `email` en clair ?

---

## 2. La résidence en clair — le `hex` calculé puis jeté

Dans le bloc `atom4love-home` :

```python
home_geo_tag = phi2x.geo_tag_a4l(float(home_lat_s), float(home_lon_s), birth_unix)
home_tags = [["d", "atom4love-home"], ["app", "atom4love"],
             ["a4l_proof", a4l_proof], ["g", home_geo_tag["penta"]],
             ["lat", home_lat_s], ["lon", home_lon_s]]
```

`geo_tag_a4l` rend `{penta, hex, pentagon_id, q, r}`. Tu publies `penta` — un
sommet d'icosaèdre, qui ne localise rien — et tu **jettes `hex`**, le seul champ
qui place à 1 km. À la place partent `lat` et `lon` bruts, pleine précision.
`atomic_map.html::_loadHomeLayer` ne lit d'ailleurs que ces deux-là.

Trois choses nous font penser à un oubli plutôt qu'à une décision :

1. on ne calcule pas une valeur pour la mettre à la poubelle ;
2. toute la maille `a4l:` existe précisément pour que des coordonnées ne partent
   jamais en clair — c'est ce que tu fais pour la naissance ;
3. tu as écrit `d=atom4love-priv`, dont le seul contenu est `home_lat`/`home_lon`
   **chiffrés NIP-44** vers soi-même et vers la clé UMAP du lieu. Tu avais donc
   déjà tranché que la résidence est une donnée privée.

Une naissance à 1 km ne dit pas où l'on dort. Une résidence exacte, si.

**Q2.** Oubli, ou choix assumé ? Si c'est un oubli, la correction tient en une
ligne chez toi (`["g", home_geo_tag["hex"]]` au lieu des tags `lat`/`lon`) et
deux dans `atomic_map.html` (décoder le `g` comme le fait déjà `_decodeA4l`).

**En attendant ta réponse, Atom4Love ne publie pas de résidence du tout.** On
sait lire le calque — il compte **zéro** `atom4love-home` et **zéro**
`atom4love-priv` sur le relais aujourd'hui, personne ne s'en sert. On n'écrira
que le `hex`, et seulement quand ta carte saura le lire.

---

## 3. `omega_bio` publié rend le poids de naissance

Indépendamment de la question Q5 de la note du 14 août — *laquelle des deux
formules fait foi* —, celle qui est **réellement publiée** est inversible :

```
poids = ω × 70 ÷ (F_WATER × ratio)
12,7659 × 70 ÷ (429,62 × 0,65) = 3,2 kg
```

Et `biological_sex` est publié à côté, qui donne le ratio. N'importe qui lisant
le relais connaît donc le poids de naissance de chacun, à 10 g près. Ce n'est
pas grave en soi ; couplé au KIN (le jour), à la maille de naissance et à φ, ça
fait beaucoup de la fiche d'état civil.

**Q3.** Intentionnel ? Si la formule Watson l'emporte (taille et poids
d'aujourd'hui), le problème disparaît de lui-même — la valeur n'est plus
inversible sans la taille.

---

## 4. Deux formats de contenu coexistent

Les trois certificats du relais ne se ressemblent pas :

| | `71b4b56f…` | `0fd65c06…` | le nôtre |
|---|---|---|---|
| KIN | `kin_birth{}` dans le contenu | tag `kin` + `kin_num` | tag `kin` + `kin_num` |
| nom du sceau | `glyphFr` dans le contenu | tag `glyph` = `Manik` (maya) | tag `glyph` = `Nuit` (français) |
| polarité | absente | `biological_sex` | `biological_sex` |
| en plus | `kin_conception`, `element_birth`, `element_conception`, `archetype`, `pepper_type` | tag `action`, tags `*_c` | — |
| tag `phase` | absent | absent | absent |

Le premier porte cinq champs que `atom4love_publish.py` n'écrit plus du tout —
l'archétype, les éléments, le type de PEPPER. Ils ont disparu du format courant :
volontairement, ou en même temps que le client qui les produisait ?

Le tag `glyph` du second porte le nom **maya** là où `atom4love_publish.py`
écrit `KIN_GLYPHS_FR[gi]`, donc le français. Un lecteur qui compare des chaînes
ne s'y retrouvera pas.

Et le tag `phase` n'est écrit par aucun des trois : seul `atomic_demo.html` le
pose, et c'est pourtant lui que lit `atomic_projector.html`. Sans conséquence
tant que le projecteur filtre sur `#t=zicmama_demo` — il ne voit jamais ces
certificats-là —, mais tout écran qui voudrait lire la vraie constellation par
les tags trouvera porte close.

**Q4.** Le format de `atom4love_publish.py` fait-il foi pour tout le monde ? Si
oui, faut-il republier les anciens certificats ou lire les deux formes ? Nous
lisons les deux, pour l'instant.

---

## Ce qu'on a fait de notre côté

- `nostr/Certificate.kt` construit et signe le 30078 ; publier demande une
  confirmation qui **montre l'événement entier**, tag par tag, avant de l'envoyer.
- `email_enc` n'est jamais écrit : on n'a pas `$UPLANETNAME`, et c'est très bien.
- Le calque `atom4love-home` est lu, jamais écrit — voir Q2.
- La carte du monde décode le `g` hex comme `atomic_map.html`, et affiche
  résonance et distance depuis la maille de naissance.

Le code est sur `github.com/FlorentLefevre-lab/Atom4Love`, branche `main`.
