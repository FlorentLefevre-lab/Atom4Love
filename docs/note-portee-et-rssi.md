# Ce que le RSSI vaut vraiment : mesures, étalonnage, et une puissance d'émission

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 15 août 2026, mise à jour le soir même · **Statut :** mesuré et en place ; **la trame VERSION 4 est retirée**, remplacée par le champ standard du Bluetooth (§ 5)

Le 10 août je t'écrivais, dans la note SSID×NOSTR : *« RSSI ≈ −60 dBm dans la même pièce.
Test de portée complet à venir ; ordre de grandeur attendu en intérieur : 10–50 m. »*

Le test est fait. **L'ordre de grandeur était faux d'un facteur cinq**, et la façon dont on
lisait le signal l'était encore davantage. Voici les chiffres, ce qu'on en a fait, et la
seule chose qui ne peut pas se décider de notre côté.

Tout est mesuré sur le couple Pixel 10 Pro ↔ Lenovo TB350XU, annonce et scan de
`ProximityEngine`, relevés lus dans `adb logcat | grep rssi=`.

## 1. Le signal bouge de 18 dB quand personne ne bouge

Deux appareils **posés, immobiles**, 43 relevés sur 75 secondes :

```
min −79 dBm    max −61 dBm    amplitude 18 dB
médiane −68    écart-type 4,2 dB
```

Dix-huit décibels sans qu'un corps se déplace. Traduit en distance par le modèle
log-distance (n = 2,7), le même couple immobile « se déplaçait » **d'un facteur 4,6** —
de 1,2 à 5,5 m.

Ce n'est pas un défaut d'appareil, c'est la nature de la bande : trajets multiples,
orientation d'antenne, absorption. Mais ça condamne toute lecture directe du RSSI brut.

Notre écran de reconnaissance affichait quatre états (`vous brûlez` / `vous chauffez` /
`encore loin` / `aux limites`) lus sur le brut : **l'état changeait 23 fois sur 42
relevés**. Le jeu disait « tu brûles, tu refroidis, tu brûles » à quelqu'un d'assis.

Et détailler les états n'aide pas : à six paliers sur du brut, toujours 23 changements.

## 2. Ce qui règle le problème : lisser d'abord

Trois choses, dans cet ordre — l'ordre compte, les deux dernières ne servent à rien sans
la première :

1. **Médiane sur cinq relevés, puis exponentielle** (α = 0,3, ≈ 3 s de mémoire). La
   médiane tue les évanouissements profonds — un trajet multiple qui s'annule, ce −79
   isolé — que la moyenne se contenterait d'étaler.
2. **Hystérésis de 2 dB** : un état ne cède que si le signal a franchi son seuil d'autant
   de plus. Une frontière traversée en aller-retour ne fait plus clignoter le mot.
3. **Six paliers au lieu de quatre**, espacés de 5 à 10 dB.

Rejoué sur la même série de 43 relevés :

| | écart-type | changements d'état | facteur d'erreur en distance |
|---|---|---|---|
| brut, 4 états | 4,2 dB | 23 | 4,6× |
| brut, 6 états | 4,2 dB | 23 | 4,6× |
| **lissé + hystérésis, 6 états** | **0,9 dB** | **0** | **1,4×** |

## 3. L'étalonnage : −72 dBm à un mètre, pas −59

Les deux appareils posés **à un mètre exactement**, face à face, relevés des deux côtés :

```
Pixel    ← tablette    médiane −71 dBm   (n = 9)
Tablette ← Pixel       médiane −74 dBm   (n = 36)
```

Trois décibels d'asymétrie entre les deux sens, ce qui est peu et rassurant.

**P₁ = −72 dBm**, là où la convention BLE dit −59 pour une émission à 0 dBm. Ces appareils
sont **13 dB en dessous** : l'annonce se fait à puissance réduite, ce que font les
téléphones récents pour épargner la batterie.

Conséquence immédiate : avec la convention, **un pair à un mètre s'affichait à trois
mètres**.

## 4. La portée utile est de 7 mètres, pas de 10 à 50

C'est le point qui compte le plus, et celui qui corrige ma note du 10 août.

Si le plancher du récepteur est vers −95 dBm — ce qui est la sensibilité usuelle d'un scan
BLE — alors avec P₁ = −72 la distance maximale détectable vaut :

```
d_max = 10 ^ ((−72 + 95) / (10 × 2,7))  ≈  7,1 m
```

**Le jeu de piste se joue dans une salle, pas dans un bâtiment.** Notre échelle de chaleur
a donc été redéployée sur 0 à 7 m :

| | seuil | distance |
|---|---|---|
| à bout de bras | −64 dBm | < 0,5 m |
| vous brûlez | −74 | 0,5 – 1,2 m |
| très chaud | −83 | 1,2 – 2,5 m |
| vous chauffez | −88 | 2,5 – 4 m |
| encore loin | −95 | 4 – 7 m |
| aux limites | — | au plancher |

Ça a une conséquence de conception, pas seulement de réglage. Le premier coup du
« Qui est-ce ? » — le tirage, « cherche le Dragon » qui réduit une salle de trente à deux
personnes — suppose qu'on voie tout le monde dans la pièce. À sept mètres, une salle de
trente personnes n'est **pas** entièrement à portée : on en voit une fraction, et elle
change quand on marche. Le jeu reste jouable, mais il se joue par zones et non par salle.

Si on veut la salle entière, il faut monter la puissance d'annonce, et le payer en
batterie. C'est un arbitrage qui nous concerne tous les deux.

## 5. La puissance d'émission — réglée par la norme, pas par notre trame

Tout ce qui précède étalonne **un couple d'appareils**. L'annonce ne porte pas sa puissance
d'émission : rien dans la radio ne permet de la déduire, donc un téléphone qui émet plus
fort se croira systématiquement plus proche qu'il n'est. Deux stations différentes
calculeront deux distances au même endroit.

> ## ⚠ Mise à jour du soir même — la proposition qui suit est RETIRÉE
>
> J'ai proposé plus bas un **VERSION 4 à 18 octets** pour y loger la puissance
> d'émission. **Ne le fais pas : le Bluetooth a déjà un champ pour ça**, et je
> ne l'avais pas vu.
>
> `AdvertiseData.setIncludeTxPowerLevel(true)` ajoute l'**AD type 0x0A**, un
> champ normalisé qui porte la puissance réelle de la puce, en dBm. Le pair le
> lit par `ScanRecord.getTxPowerLevel()`. Trois octets, aucune négociation entre
> nous, et ça marche avec n'importe quel appareil BLE — pas seulement les nôtres.
>
> C'est en place chez nous depuis ce soir. Deux mesures qui valent d'être dites :
>
> - **nos deux appareils annoncent −7 dBm**, ce qui est le défaut d'Android
>   (`ADVERTISE_TX_POWER_MEDIUM`) ;
> - avec les −72 dBm relevés à un mètre, l'**atténuation à un mètre vaut donc
>   65 dB**. Loin des ~40 dB de l'espace libre, et ce n'est pas une erreur : une
>   antenne de téléphone est mauvaise et l'appareil était posé sur une table.
>
> Toute notre échelle de chaleur est désormais graduée en **atténuation**
> (émission − réception) et non plus en RSSI : c'est la seule grandeur
> comparable d'un appareil à l'autre.
>
> **Ce que je te demande à la place** — et c'est bien plus léger que la trame :
> que cabine-33 mette `setIncludeTxPowerLevel(true)` sur son annonce. Un
> appareil qui ne l'annonce pas est supposé à −7 dBm chez nous, ce qui vaut ce
> que vaut une supposition.
>
> ⚠ Le budget est **exact** : 3 octets de drapeaux, 4 pour l'UUID de service,
> 21 pour le bloc de données, plus 3 pour ce champ — 31 sur 31. Si tu ajoutes
> quoi que ce soit à ta charge utile, c'est lui qui saute le premier.
>
> ⚠ Et ça ne règle que **la moitié** du problème : la puissance d'émission est
> corrigée, la sensibilité du récepteur non — rien dans le BLE ne l'annonce.
> Deux appareils différents entendront toujours la même personne un peu
> différemment.

<details>
<summary>La proposition d'origine, conservée pour l'histoire</summary>

**Proposition : que la charge utile porte la puissance d'émission.**

Notre payload est versionné et se décode en dégradé (`ProximityPayload`) :

```
VERSION 1 →  9 octets   (adresse 4D)
VERSION 2 → 13 octets   (+ jeton de présence)
VERSION 3 → 17 octets   (+ signature : polarité, sceau, φ)
VERSION 4 → 18 octets   (+ puissance d'émission)   ← la proposition
```

Un octet suffit largement : la puissance d'annonce BLE tient dans la plage
−40…+8 dBm, donc un entier signé en décibels-milliwatt. Les pairs restés en v3
continuent d'être décodés sans rien changer.

</details>

Deux questions, donc, au lieu de trois :

1. **Cabine-33 a-t-elle son propre modèle de portée ?** Si l'implémentation Godot affiche
   déjà une distance, il faut que les deux disent la même chose du même signal — sinon
   deux personnes côte à côte, l'une sous Android l'autre sous cabine-33, ne liront pas le
   même mètre.
2. **Le rituel des 33 secondes parle de « moins de 50 m du centre de l'hexagone ».** Ce
   50 m-là vient de chez toi et concerne la cellule H3, pas le BLE — je le note pour qu'on
   ne les confonde pas : **la portée radio est de 7 m, le rayon du rendez-vous est de
   50 m**. Confirmes-tu que ce sont bien deux échelles distinctes, la seconde géographique
   et non radio ?

## Ce qui est déjà fait de notre côté

Le lissage, l'hystérésis, les six paliers et l'étalonnage sont dans l'app Android. Les
constantes sont nommées et commentées (`Warmth.RSSI_AT_ONE_METRE`,
`Warmth.PATH_LOSS_EXPONENT`), avec la procédure de réétalonnage écrite au-dessus : deux
appareils à un mètre, `adb logcat | grep rssi=`, médiane. Rien là-dedans ne t'engage — ce
sont nos réglages. Seule la question 1 touche au protocole.

## Méthode, pour que tu puisses refaire les mesures

```bash
adb -s <appareil> logcat -c
timeout 90 adb -s <appareil> logcat | grep -oE "rssi=-[0-9]+" | sed 's/rssi=//'
```

Statique : les deux appareils posés, sans personne autour, 40 relevés au moins.
Étalonnage : les deux à un mètre exactement, face à face, relevés **des deux côtés** —
l'asymétrie entre les sens est une donnée, pas du bruit.
