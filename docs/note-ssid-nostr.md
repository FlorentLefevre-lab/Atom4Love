# Co-localisation par SSID Wi-Fi via NOSTR : analyse et garde-fous

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 10 août 2026 · **Statut :** proposition à discuter

Tu as suggéré qu'une fois deux noyaux connectés via NOSTR, ils pourraient partager leur
SSID Wi-Fi pour se savoir « au même endroit ». L'idée ouvre un étage de co-localisation
qui nous manque — mais publier un SSID en clair revient à publier une adresse postale.
Voici l'analyse, et un schéma qui garde le bénéfice sans le risque.

## 1. Où on en est côté radio

Le module de proximité BLE est validé en conditions réelles (Pixel 10 Pro ↔ tablette
Lenovo) : annonce et scan continus portés par un service foreground, payload structuré
de 9 octets portant l'adresse 4D (cellule H3 résolution 8, rotation temporelle D2 encore
à brancher — la couture est prête). Les deux appareils se détectent mutuellement en
quelques secondes, sans lancement simultané, avec un RSSI ≈ −60 dBm dans la même pièce.
Test de portée complet à venir ; ordre de grandeur attendu en intérieur : 10–50 m.

## 2. Ce que le SSID ajouterait : un barreau manquant

Chaque signal de co-localisation couvre une échelle différente. Le Wi-Fi partagé comble
précisément le trou entre la portée radio directe et la cellule H3 :

| Signal | Échelle | Infrastructure | État |
|---|---|---|---|
| **BLE** (annonce + scan) | mètres → même pièce | aucune — fonctionne hors ligne | validé |
| **Même Wi-Fi** (via NOSTR) | logement → bâtiment | Internet + relais NOSTR | cette note |
| **Cellule H3** (adresse 4D) | ~460 m (res 8) | position locale, jamais transmise | validé |

Cas d'usage typique : deux noyaux liés, aux étages opposés d'un immeuble ou dans le même
café — invisibles en BLE, mais sur le même réseau. Le signal transite par le relais, donc
*aucune proximité radio n'est requise* : c'est du « matching de présence », pas de la
détection.

## 3. Le problème du SSID en clair

> ⚠ **Un SSID est géolocalisable.** Les bases de wardriving publiques (WiGLE et consorts)
> associent SSID et BSSID à des coordonnées GPS. Publier « Freebox-A2F3 » sur un relais,
> même chiffré pour un destinataire, c'est révéler son domicile à ce destinataire ; le
> publier en clair, c'est le révéler à tout l'Internet. Frontalement contraire au principe
> fondateur « pas de traçage de position ».

Deux limites secondaires à garder en tête :

- **Ambiguïté des SSID partagés** : « eduroam » ou « McDonald's Free WiFi » couvrent des
  milliers de sites — même SSID ≠ même lieu. Le BSSID (adresse de la borne) lève
  l'ambiguïté mais est encore plus sensible.
- **Dépendance à l'infrastructure** : il faut du Wi-Fi (pas de la 4G) et un relais
  joignable. Le BLE reste le seul étage totalement autonome — cohérent avec le
  « sans serveur central » du projet.

## 4. Schéma proposé : comparer sans révéler

Le besoin réel n'est jamais de *connaître* le SSID de l'autre — seulement de savoir si
c'est *le même*. Une égalité se teste sur des empreintes.

**Mode recommandé — échange apparié, après premier contact.** Deux noyaux qui se sont
déjà rencontrés (échange de npub, par exemple lors d'un contact BLE) s'envoient
périodiquement, en message chiffré NIP-44 sous gift wrap NIP-17, un jeton :

```
jeton = H( BSSID ∥ sel_de_paire ∥ époque )

sel_de_paire : dérivé du couple de npub (unique par relation)
époque       : fenêtre temporelle glissante (à caler sur la
               mécanique de rotation D2 — même horloge ?)
```

Jetons égaux ⇒ même borne, même moment. Le contact n'apprend rien d'autre : pas le nom du
réseau, pas le lieu, et les jetons de deux relations différentes sont incomparables entre
eux (le sel de paire l'empêche). Le relais, lui, ne voit que du chiffré.

**Mode déconseillé — diffusion publique.** Publier un jeton en clair (sel global ou
sel = cellule ⊕ époque) reste attaquable par dictionnaire : l'espace des SSID est
énumérable, et un attaquant qui connaît la borne d'un lieu précis peut tester « qui est
ici ». Le scoping par cellule + époque réduit l'attaque de masse mais pas l'attaque
ciblée. À réserver, si un jour on y tient, à des lieux publics assumés (cabines ?),
jamais aux domiciles.

## 5. Faisabilité Android

- Lire le SSID/BSSID courant exige `ACCESS_FINE_LOCATION` (déjà accordée pour la cellule
  H3) et, depuis Android 12, l'API `NetworkCallback` avec `FLAG_INCLUDE_LOCATION_INFO`.
- Le calcul du jeton est trivial (un hash) ; l'envoi s'appuie sur la pile NOSTR déjà
  prévue (NIP-17/NIP-44 sont au programme du client).
- Coût batterie négligeable — on lit l'état de la connexion, on ne scanne pas.

## 6. Questions ouvertes

1. Quelle place pour ce signal dans la hiérarchie D2 — critère d'entrée dans un
   *portail*, simple métadonnée de présence, ou étage à part ?
2. BSSID (précis, plus sensible) ou SSID (ambigu, un peu moins parlant) comme entrée du
   jeton ? Mon penchant : BSSID, puisqu'il n'est jamais révélé.
3. L'époque du jeton peut-elle partager l'horloge de la rotation temporelle D2 ? Une
   seule mécanique de fenêtres glissantes pour tout le protocole serait élégante.
4. Diffusion publique : on ferme la porte définitivement, ou on la garde pour des lieux
   publics assumés ?
5. Quel comportement quand un noyau est en 4G (pas de Wi-Fi) : l'absence de jeton est une
   information en soi — faut-il émettre quand même un jeton « nulle part » pour ne pas
   fuiter ce bit ?

---

*Atom4Love · note préparée avec l'assistance de Claude · les mesures citées proviennent
du test croisé du 10 août 2026 (Pixel 10 Pro ↔ Lenovo TB350XU).*
