# À quoi sert ω_bio : la réponse, construite

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 15 août 2026
**Statut :** une réponse à ta question retournée, un défaut corrigé chez nous, quatre points chez toi

Le 14 août tu m'as retourné la question : *ω_bio, pour en faire quoi ?* Tu avais
répondu « → binaural » et laissé le reste ouvert. Plutôt que de re-demander, on
l'a construit. Voici ce que ça donne, ce que ça a cassé chez nous, et les quatre
choses que ça pose chez toi.

---

## 1. Ce qui est construit : un « Qui est-ce ? » en quatre échelons

Le but de l'application est qu'une rencontre se **concrétise** — que deux
personnes dans un bar aillent l'une vers l'autre. Entre « quelqu'un ici te
répond à 92 % » et « bonjour », il y a un trou que personne ne traverse seul.
Quatre coups le franchissent, et **l'ordre est la règle** :

| | | ce que ça demande au réseau |
|---|---|---|
| **1. Le tirage** | une carte par personne à portée : son sceau, sa résonance, sa chaleur | rien — l'annonce BLE porte déjà sceau, polarité et φ |
| **2. La reconnaissance** | les deux écrans battent le même rythme ; on cherche celui qui bat comme le sien | rien — le motif se dérive des deux φ, des deux côtés |
| **3. Les questions** | un attribut échangé, une fois, symétriquement | la cabine (Noise, attestée) |
| **4. La cabine** | on parle | — |

Les deux premiers ne demandent **rien à personne** et ne diffusent pas un octet
de plus : ta signature de proximité suffisait déjà. Le motif de reconnaissance
est un hachage symétrique des deux φ calé sur le temps Unix — deux appareils
tombent sur la même figure sans s'être rien dit, et un seul qui cherche clignote
seul, sans que personne l'apprenne. Vérifié en croisé : `▮▮▮▯▯▯▯▯▯▮▯▯▯▮▯▯` des
deux côtés.

Le troisième est le premier qui ait besoin d'un canal, et **ce n'est pas un choix
d'architecture** : φ est public, donc toute clé qu'on en dériverait le serait
aussi. Deux inconnus ne peuvent rien se dire que la salle n'entende tant qu'ils
n'ont rien ouvert. D'où la cabine — et d'où le fait qu'elle vienne **après** la
reconnaissance, jamais avant : elle porte le npub attesté, et s'en servir pour
se trouver reviendrait à savoir qui est quelqu'un avant de l'avoir trouvé.

**La règle du troisième coup : proposer, c'est donner.** L'offre part avec sa
propre réponse, sans retour possible. Ça interdit l'interrogatoire — chaque
question coûte exactement ce qu'elle demande — et ça rend le premier pas
signifiant. On ne demande que ce qu'on accepte de dire, et on le dit d'abord.

Et **ω_bio est la sixième question** — la dernière de la liste, parce que c'est
la seule qui parle du corps et non de la date.

---

## 2. Ce que construire ça a révélé chez nous — corrigé

Notre cabine **annonçait ω_bio d'elle-même**, à chaque pair attesté, dès la fin
du handshake. Personne ne l'avait demandée, rien ne pouvait la refuser.

C'était un dévoilement sans accord au milieu d'un jeu dont toute la règle est
qu'on ne retourne rien sans les deux — et ça contredisait ta propre phrase du
14 août : *ces données ne se divulguent qu'entre gens qui se suivent*. On l'avait
écrit dans le code, en commentaire, juste au-dessus de la ligne qui faisait le
contraire.

C'est corrigé : l'onde se demande maintenant comme le reste. **Le battement
devient ce que la question rapporte**, au lieu d'un cadeau d'entrée — et il ne
peut naître qu'au seul endroit où les deux valeurs se rejoignent.

---

## 3. Ce que le binaural mesure vraiment — et pourquoi ça tranche ta Q5

C'est le point qui vaut le déplacement. En faisant sonner deux vraies ondes,
une chose apparaît qu'on ne voit pas sur le papier.

Nos deux appareils, relevés à l'écran :

```
Pixel      304,5 Hz   (49,6 kg d'eau)
tablette   202,2 Hz   (32,9 kg d'eau)
écart      102,3 Hz
rapport    1,5059  →  une quinte juste à 6,8 cents près
```

**Un battement binaural demande un écart inférieur à ~30 Hz.** Au-delà, on
n'entend pas un battement : on entend **deux hauteurs distinctes formant un
intervalle**. Sur l'amplitude adulte que donne Watson — de 25 à 55 kg d'eau,
soit 153 à 338 Hz — l'écart entre deux personnes va de 0 à ~185 Hz. Le battement
n'existe donc que pour deux corps **proches en eau** ; partout ailleurs, c'est un
accord.

Ce n'est pas un défaut, c'est peut-être mieux que ce qui était prévu : deux corps
semblables battent, deux corps différents s'accordent ou frottent. Mais ça
demande de le décider, parce que la sonorisation n'est pas la même.

**Et ça tranche ta Q5 du 14 août.** Les deux formules de ton dépôt ne sont pas
seulement d'ordres de grandeur différents — une seule est **jouable** :

- Watson (`compute_omega_bio`) : 150 à 340 Hz. Audible, et l'écart fait sens.
- poids de naissance (`atom4love_publish.py`) : ~12 Hz. Deux ondes de 12 Hz ne
  sont pas des sons ; leur écart est de l'ordre du hertz. Il n'y a rien à
  entendre.

Si ω_bio sert au binaural — c'est ta réponse du 14 — alors **c'est Watson qui
fait foi**, et la valeur publiée dans le `30078` n'est pas la même grandeur. Ce
sont deux quantités qui portent le même nom.

---

## 4. Ce qui reste à trancher

**J1. Confirmes-tu que Watson fait foi pour le binaural ?** Et si oui, que
faut-il faire du champ `omega_bio` du certificat, qui porte l'autre grandeur ?
Le renommer réglerait la moitié du problème — deux noms pour deux choses.

**J2. Le corps sur le relais public.** Le `30078` publie `omega_bio` en clair
dans son `content`. Avec la formule de naissance, il **s'inverse en poids de
naissance** (une seule inconnue, la polarité est publiée à côté) — c'était déjà
la question 3 de ma note de ce matin. Ta règle « au follow seulement » et ce
champ ne peuvent pas tenir ensemble. Lequel des deux bouge ?

**J3. Ta Q6 devient bloquante si Watson fait foi.** La station n'a ni la taille
ni le poids actuels : `/atom4love/activate` ne les poste pas, ils restent dans le
`localStorage` d'`atomic.html`. On les collecte côté Android et on est prêts à
les envoyer — **sous quels noms exacts ?** Ou bien la station ne calcule jamais
ω_bio, et c'est aux clients de le faire ?

**J4. Veux-tu le protocole des questions côté web ?** Il tient en cinq octets
dans la cabine, et cabine-33 pourrait le parler :

```
QUESTION  [0x0B][étape 1][trait 1][valeur 2]        (big-endian)
  étape   1 = OFFRE (je donne et je demande) · 2 = RÉPONSE · 3 = REFUS
  trait   1 tonalité · 2 couleur · 3 décennie · 4 heure · 5 KIN · 6 ω_bio
  valeur  le nombre ; ω_bio en dixièmes de hertz ; 0 sur un REFUS
```

Deux octets de valeur, et c'est délibéré : ce format ne peut porter aucun nom,
aucun texte libre, aucune adresse. Ce qui n'y entre pas ne se demande pas dans
ce jeu.

Une précision qui a son importance si tu l'implémentes : sur une OFFRE comme sur
une RÉPONSE, la valeur est **celle de l'émetteur**. Et un REFUS part à zéro — il
ne doit pas emporter la valeur qu'il vient justement de refuser de donner.

---

## 5. Ce qu'on n'a pas cherché à garantir, et qu'on écrit à l'écran

Celui qui reçoit une offre tient la valeur de l'autre avant d'avoir répondu ;
rien n'oblige son appareil à la lui cacher. L'échange équitable à deux sans
arbitre n'existe pas, et un schéma d'engagement n'y changerait rien — on peut
toujours ne pas révéler.

Plutôt qu'une garantie fausse, l'écran le dit : *proposer, c'est donner*. Le
dommage est borné — il faut être dans la salle, attesté, et ça ne rapporte qu'un
attribut, une fois. Et chaque question **affiche ce qu'elle livre avant qu'on y
réponde** : « votre âge à dix ans près », « ramène votre date de naissance à
quelques jours près — et la date forge la moitié de votre clé ». Un jeu qui fait
céder des données en cachant leur portée n'est pas un jeu.

C'est le même principe que ta cabine close, appliqué à un cran plus fin : la
clôture ne porte pas sur le chemin mais sur ce qui se retourne, et rien ne se
retourne sans l'accord des deux.

---

Le code est sur `github.com/FlorentLefevre-lab/Atom4Love`, branche `main` :
`proximity/Rendezvous.kt`, `domain/Questions.kt`, `chat/wire/ChatFrames.kt`
(trame `0x0B`) et leurs tests. Les quatre échelons ont été joués en croisé entre
deux appareils, pas seulement compilés.
