# L'Oracle du Tzolkin porté à la lettre — les quatre pouvoirs, Guide compris

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 16 août 2026 (révisée le soir)
**Statut :** ✅ **RÉPONDU** — les quatre formules sont dans l'application et testées sur les 260 KIN. Une question nouvelle, une seule, en fin de note.

> **Révision du 16 août au soir.** Fred a répondu aux trois questions de la
> version du matin, et sa réponse a **corrigé une erreur de notre côté**. Cette
> note a été reprise en conséquence : ce qui suit est l'état vrai, pas le
> brouillon. Les numéros de section n'ont pas bougé pour que l'échange reste
> lisible.

## 0. Ce que sa réponse a changé — la planche s'était trompée, pas la production

Notre premier portage venait du texte « formules clés » de `tzolkin_oracle.svg`
(inline dans `miz.html`). **Ce texte intervertissait le ton du défi et celui de
l'alternance.** Les formules qui font foi sont celles de `kin_oracle.sh`, en
production, celles qui envoient déjà les courriels Oracle aux membres. Fred a
corrigé le dessin.

Deux conséquences, notées ici pour qu'on ne les redécouvre pas :

1. **Les deux cartes affichées ne bougent pas.** Le défi et l'alternance
   partagent le sceau ; échanger leurs tons échange leurs **noms**, pas les KIN
   montrés. Concrètement, l'écran du Pixel affichait pour `KIN 83 · Akbal` un
   `⚡ Ben 213` et un `🌀 Ben 113` : c'était **l'inverse**. On montrait juste, on
   nommait faux.
2. **L'identité élégante repérée au § 1 existe, mais pas où on la voyait.** Ce
   sont l'**occulte et le défi** qui retournent le ton — pas l'occulte et
   l'alternance. Notre transcription portait la même inversion que sa planche :
   deux erreurs qui se recoupaient exactement, et donc se cachaient l'une
   l'autre. C'est ce qui explique qu'elle « fonctionnait » mathématiquement.

Le ton range en fait les cinq pouvoirs en deux camps nets :

| ton **gardé** | ton **retourné** (14 − ton) |
|---|---|
| Soi-même, le **Guide**, l'**Alternance** | le **Défi**, l'**Occulte** |

## 1. Les quatre formules, telles qu'elles tournent chez lui

```
Antipode (Défi)      : sceau+10, ton complémentaire (14−ton)
Analogue (Alternance): sceau+10, MÊME ton
Occulte              : K + K′ = 261   (sceau miroir 19−sceau, ton 14−ton)
Guide                : famille = sceau % 4 ; position = (ton−1) % 5
                       sceau = famille + position × 4 ; ton inchangé
```

C'est `domain/Oracle.kt`, quatre fonctions et rien d'autre. Le seul ajout est un
inverse : `KinMaya.ofSealAndTone(sceau, ton)`, qui retrouve le KIN unique portant
un sceau et un ton donnés — 20 et 13 étant premiers entre eux, il en existe
toujours exactement un. C'est ce qui rend ses formules calculables.

**Une propriété de l'occulte, qu'on n'a pas eu à poser** : défini par le nombre
seul (`261 − K`), il **rend** le sceau miroir (`19 − sceau`, soit une somme de 21
sur des sceaux comptés à partir de un) et le ton retourné. Sa définition la plus
courte est donc la plus complète.

## 2. Le défi et l'alternance partagent le sceau — confirmé

**±10 et +10 sont la même opération modulo 20.** Les deux cercles tombent sur la
même colonne, et seul le ton les sépare.

Conséquence pratique pour nous, et elle est heureuse : notre annonce BLE porte le
sceau mais **pas** le ton (17 octets, budget 31/31, tailles gelées). Depuis un
sceau entendu à portée, on peut donc dire *« c'est le sceau de ton défi »* — sans
pouvoir trancher entre défi et alternance. On l'écrit ainsi à l'écran, sans faire
semblant.

## 3. Au ton 7, le défi **est** l'alternance — confirmé, et rien de spécial à faire

`14 − 7 = 7`. Le ton Résonnant est son propre complément — et comme les deux
relations partagent déjà le sceau, plus rien ne les sépare : elles se referment
sur **un seul KIN**. Vingt cas sur 260, un par sceau.

Fred confirme : reproductible chez lui, et son `KIN.daily.sh` ne le traite pas
autrement non plus. Pas de cinquième position. **L'écran affiche donc les deux
cases identiques et le dit d'une ligne**, plutôt que d'en masquer une.

## 4. ✅ Le Guide — la formule, et ce qu'elle fait

C'était la seule chose qui bloquait. Elle était en production, jamais documentée.
Réponse à notre question (b) : **la famille des sceaux par 4** (Rouge, Blanc,
Bleu, Jaune — celles de `tzolkin_cycle.svg`), **pas** les cinq châteaux du champ
`color` de `phi2x.py`, qui incluent le Vert. C'est porté ainsi.

```
famille  = sceau % 4          →  Rouge, Blanc, Bleu, Jaune
position = (ton − 1) % 5      →  le rang dans la famille
sceau    = famille + position × 4
ton      = inchangé
Guide    = le KIN unique portant ce sceau et ce ton
```

Fred le dit honnêtement : cette formule n'avait **jamais été testée** avant
aujourd'hui. Nous l'avons donc passée sur les 260 KIN, et voici ce qu'elle fait —
trois propriétés à connaître avant de s'en servir, aucune n'étant un défaut, mais
aucune n'allant de soi :

| propriété | mesure sur les 260 KIN |
|---|---|
| domaine et famille | ✅ toujours valide, **toujours dans votre famille** |
| ton | ✅ inchangé, comme annoncé |
| **injectivité** | ❌ **cinq KIN partagent le même guide** — l'image ne compte que **52 KIN** (4 familles × 13 tons) |
| **idempotence** | le guide d'un guide est **ce même guide** ; ces 52 KIN sont leurs propres guides, les 208 autres ne sont le guide de personne |
| point fixe | **52 KIN sur 260 sont leur propre guide** (un sur cinq) |

La raison est structurelle : la formule **efface** votre rang dans votre famille
et le remplace par celui que dicte le ton. Deux personnes de la même famille et
du même ton ont le même guide, quel que soit leur sceau.

> ℹ️ Pour information, sans rien en conclure : la règle Dreamspell qu'on trouve
> ailleurs *décale* le sceau au lieu de l'écraser (`sceau + 4 × (3(ton−1) mod 5)`),
> ce qui donne une bijection et fait que chacun est son propre guide aux tons 1,
> 6 et 11. Elle coïncide avec la sienne sur exactement **52 KIN sur 260** — les
> points fixes. **On a porté la sienne**, c'est la sienne qui envoie les
> courriels. On le signale seulement au cas où l'écart serait une surprise.

L'écran l'affiche en tête des quatre cases, avec un pictogramme d'orientation
(🧭) plutôt que de rencontre, et il dit « vous êtes votre propre guide » quand
c'est le cas — sinon la case a l'air d'une erreur de calcul.

## 5. Match et super match : le 0,90 reste, et il est maintenant adossé

- **0,95** est sa valeur — `SUPER_COHERENCE_K`, son « match quantique ». On n'y
  touche pas.
- **0,90** reste la nôtre : il n'a pas de second seuil officiel, **mais sa page
  `atomic_match.html` utilise déjà informellement `k ≥ 0.90`** pour qualifier une
  « résonance cosmique rare » dans son message de partage. Ce n'était donc pas
  arbitraire de notre côté sans qu'on le sache. Ça ne bouge pas.

| | pas de lien de sceau | lien de sceau (10 %) |
|---|---|---|
| k < 0,90 | — | **MATCH** |
| 0,90 ≤ k < 0,95 | **MATCH** | **MATCH** |
| k ≥ 0,95 | **MATCH** | **SUPER MATCH** |

L'axe des sceaux ne se règle pas : deux sceaux sur vingt répondent (celui du
défi, celui de l'occulte), soit 10 % des gens. C'est sa grille, pas un réglage.

## 6. Une mesure qui peut lui servir : φ est uniforme, Ψ ne l'est pas

Avant de fixer ces seuils, il fallait écarter un risque. Il a retiré de son
projecteur la teinte qui dépendait de Ψ, parce que `compute_resonance_field`
reste tassée dans `[0,50 ; 0,545]` sur des coordonnées réelles. **Si φ se
comportait pareil, tout le monde matcherait avec tout le monde.**

400 naissances plausibles (60 ans de dates, 12 villes sur 5 continents), φ
calculée par notre portage de `compute_personal_phase`, **79 800 paires** :

| | prédit si φ uniforme | **mesuré** |
|---|---|---|
| match | 16,4 % | **16,38 %** |
| super match | 0,335 % | **0,335 %** |
| lien de sceau | 10,0 % | **10,17 %** |

À la décimale. **φ couvre le tour entier** — aucun secteur de 30° vide, aucun
au-delà de 20 % — et le minimum de k observé touche 0,5, le quart de tour. Sa
phase personnelle est donc utilisable comme discriminant, là où Ψ ne l'est pas.

## 7. Un détail, s'il repasse sur la planche

`tzolkin_cycle.svg` porte en légende `★ Kin 119 = Tempête Bleue T3 (exemple)`.
Le sceau est bon (Tempête) et la **famille bleue** est bonne (`18 % 4 = 2`), mais
le ton est **2** et non T3 : `(119 − 1) mod 13 = 1`, soit le deuxième ton. Et la
case surlignée dans le dessin n'est ni sur la ligne du sceau 19 ni sur la colonne
T3. Rien de grave — c'est une illustration, pas une table.

Pour mémoire, l'Oracle complet de ce KIN 119, avec les formules corrigées :
défi **KIN 129** (Lune, ton 12), alternance **KIN 249** (Lune, ton 2), occulte
**KIN 142**, guide **KIN 67** (Main, ton 2).

## La seule question qui reste

**Le guide doit-il compter comme un lien de sceau, au même titre que le défi et
l'occulte ?**

Le contexte : notre annonce BLE ne porte que le sceau. Le sceau du guide se
calcule sans le ton, donc **on pourrait** le reconnaître à portée, exactement
comme les deux autres. Nous ne l'avons pas fait, pour deux raisons — et la
décision lui appartient :

1. Le guide reste **dans notre propre famille**, quand le défi et l'occulte en
   sortent toujours (vérifié sur les 260). Il n'a donc pas la nature d'une
   rencontre : il oriente, il ne désigne pas quelqu'un à aller voir.
2. L'ajouter porterait la part des gens qui « répondent » de **10 % à 15 %**, ce
   qui déplace les échelons du match sans qu'il l'ait demandé.

**Tranché de notre côté : il reste dehors.** Mais si Fred voit le guide comme un
lien à chercher dans une salle, c'est trois lignes.

Et deux choses qui restent de la note du 15 août, indépendantes de tout ça : que
cabine-33 mette `setIncludeTxPowerLevel(true)` dans son annonce, et son modèle de
portée.
