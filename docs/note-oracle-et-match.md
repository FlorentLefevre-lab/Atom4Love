# L'Oracle du Tzolkin porté à la lettre — et le Guide, qui ne peut pas l'être

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 16 août 2026
**Statut :** trois formules sur quatre en place et testées ; **une question qui bloque le Guide**, deux qui peuvent attendre
**Page partageable :** https://claude.ai/code/artifact/59eefa85-4fc1-4dc3-b1fb-25e0e10d34b8

Tes cinq pouvoirs de l'Oracle sont maintenant dans l'application Android. Trois
sont calculés, testés sur les 260 KIN, et affichés. Le quatrième — le Guide — est
resté dehors, et cette note dit exactement pourquoi : il n'y manque pas du
travail, il y manque une décision qui t'appartient.

Tout ce qui suit vient de `tzolkin_oracle.svg` et du texte de `miz.html`. Rien
n'a été redessiné, rien n'a été « complété » de mon côté.

## 1. Ce qui est porté, tel quel

```
Antipode : sceau+10, même ton.
Analogue : sceau±10, ton complém. (14−ton)
Occulte  : K+K′ = 261
```

C'est `domain/Oracle.kt`, trois fonctions et rien d'autre. Le seul ajout est un
inverse : `KinMaya.ofSealAndTone(sceau, ton)`, qui retrouve le KIN unique portant
un sceau et un ton donnés — 20 et 13 étant premiers entre eux, il en existe
toujours exactement un. C'est ce qui rend tes deux premières formules
calculables.

**Une propriété de ton occulte, que je n'ai pas eu à poser** : défini par le
nombre seul (`261 − K`), il **rend** le sceau miroir (`19 − sceau`, soit une
somme de 21 sur des sceaux comptés à partir de un) et le ton retourné, le même
que celui de l'alternance. Ta définition la plus courte est donc la plus
complète : les deux autres grandeurs s'en déduisent. C'est épinglé dans le test.

## 2. Le défi et l'alternance partagent le sceau — confirmation

J'ai d'abord cru à une contradiction entre ton cercle « Sceau±10 » et ton
« sceau+10 ». Il n'y en a pas : **±10 et +10 sont la même opération modulo 20**.
Les deux cercles tombent sur la même colonne, et seul le ton les sépare —
identique pour le défi, retourné pour l'alternance.

Ça a une conséquence pratique pour nous, et elle est heureuse. Notre annonce BLE
porte le sceau mais **pas** le ton (17 octets, budget 31/31, tailles gelées).
Depuis un sceau entendu à portée, on peut donc dire *« c'est le sceau de ton
défi »* — sans pouvoir trancher entre défi et alternance. On l'écrit ainsi à
l'écran, sans faire semblant.

## 3. Ce que les tests ont trouvé : au ton 7, le défi **est** l'alternance

`14 − 7 = 7`. Le ton Résonnant est son propre complément — et comme les deux
relations partagent déjà le sceau, plus rien ne les sépare : elles se referment
sur **un seul KIN**. Vingt cas sur 260, un par sceau.

Mon test l'interdisait ; c'est le test qui avait tort. L'écran affiche donc les
deux cases identiques et le dit d'une ligne, plutôt que d'en masquer une.

**Est-ce que ça se lit ainsi chez toi ?** Si un ton 7 doit être traité autrement
— une cinquième position, ou un Guide qui prend le relais — dis-le, c'est trois
lignes à changer.

## 4. ⚠ Le Guide : la seule chose qui bloque

Ta définition est `« Guide : même famille-couleur »`. Elle ne suffit pas, pour
deux raisons distinctes.

### a) Elle ne désigne pas un KIN

Tes trois autres relations rendent un KIN **unique**, parce qu'elles fixent le
sceau *et* le ton. « Même famille-couleur » ne fixe ni l'un ni l'autre : c'est un
critère d'appartenance, qui laisse 52 ou 65 KIN candidats selon le découpage. Il
manque la règle qui choisit lequel.

### b) « Famille-couleur » désigne deux choses différentes dans ton propre matériel

C'est le point sur lequel je ne peux rien deviner :

| Source | Découpage | Nombre | Pour le KIN 119 |
|---|---|---|---|
| `tzolkin_cycle.svg` (légende) | les **sceaux**, par cycle de 4 | Rouge, Blanc, Bleu, Jaune | **Bleue** — ta légende écrit « Tempête Bleue » |
| `phi2x.py` → tag `color` du certificat | les **châteaux**, par cycle de 5 | Rouge, Blanc, Bleu, Jaune, **Vert** | **Vert**, si je lis bien `KIN_COLORS` |

Les deux découpages ne se recouvrent pas, et **c'est le second qui part sur le
relais** dans chaque certificat 30078. Selon celui qu'on retient, le Guide d'un
même KIN n'est pas le même.

> **La question, en une phrase :** quelle est la formule exacte du Guide — quel
> sceau, quel ton — et sa « famille-couleur » est-elle celle des sceaux par 4 ou
> celle des châteaux par 5 ?

Tant que tu n'as pas répondu, le Guide reste hors du code. Les trois autres
tournent ; je préfère un pouvoir manquant à un pouvoir inventé.

## 5. Match et super match : deux seuils, dont un qui est le nôtre

On a gamifié la rencontre avec deux mots ordinaires, mais posés sur tes
grandeurs. Deux axes, jamais un score :

| | pas de lien de sceau | lien de sceau |
|---|---|---|
| k < 0,90 | — | **MATCH** |
| 0,90 ≤ k < 0,95 | **MATCH** | **MATCH** |
| k ≥ 0,95 | **MATCH** | **SUPER MATCH** |

- **0,95** est ta valeur — `SUPER_COHERENCE_K`, ton « match quantique ». On n'y
  touche pas.
- **0,90 est la nôtre**, faute d'en avoir une de toi. Elle ouvre le match à 7,1 %
  des rencontres sur la seule phase ; à 0,80 on serait à 16 %, et le mot ne
  vaudrait plus rien. **Si tu as un second seuil, on prend le tien.**

L'axe des sceaux, lui, ne se règle pas : deux sceaux sur vingt répondent (celui
du défi, celui de l'occulte), soit 10 % des gens. C'est ta grille, pas un
réglage.

## 6. Une mesure qui peut te servir : φ est uniforme, Ψ ne l'est pas

Avant de fixer ces seuils, il fallait écarter un risque. Tu as retiré de ton
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
au-delà de 20 % — et le minimum de k observé touche 0,5, le quart de tour. Ta
phase personnelle est donc utilisable comme discriminant, là où Ψ ne l'est pas.

## 7. Un détail, si tu repasses sur la planche

`tzolkin_cycle.svg` porte en légende `★ Kin 119 = Tempête Bleue T3 (exemple)`.
Le sceau est bon (Tempête), mais le calcul donne **ton 2** : `(119 − 1) mod 13 =
1`, soit le deuxième ton. Et la case surlignée dans le dessin n'est ni sur la
ligne du sceau 19 ni sur la colonne T3. Rien de grave — c'est une illustration,
pas une table — mais autant le dire puisque j'ai eu à la relire de près.

## Ce qu'on attend de toi

1. **La formule du Guide**, et quelle famille-couleur elle vise (§ 4). C'est la
   seule qui bloque quelque chose.
2. Le **ton 7** : défi et alternance confondus, est-ce bien ainsi ? (§ 3)
3. Un **second seuil de résonance**, si tu en as un, pour remplacer notre 0,90
   (§ 5).

Et deux choses qui restent de la note du 15 août : que cabine-33 mette
`setIncludeTxPowerLevel(true)` dans son annonce, et ton modèle de portée.
