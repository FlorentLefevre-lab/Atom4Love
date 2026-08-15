# Ta station et notre client dérivent exactement la même chose

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 15 août 2026
**Statut :** confirmation — plus une question

> **Cette note en remplace une première version, envoyée par erreur**, qui
> annonçait une divergence de 0,064 rad sur φ. Elle était fausse : je comparais
> deux certificats calculés sur **deux fiches différentes** — les données de
> naissance de mon appareil avaient changé entre les deux publications, à mon
> insu. Toutes mes excuses pour le bruit.

## 0. Ton activation remarche

`POST /atom4love/activate` a réussi sur un compte neuf
(`florent.lefevre.test3@yopmail.com`, `u.copylaradio.com`). La station a rendu
`love_nsec`, `love_npub`, `love_hex`, **et publié son kind 30078** — il est sur
`relay.copylaradio.com` avec son `email_enc`.

Le blocage de la note du 12 août — `ACTIVATION_FAILED` parce que la publication
échouait — **n'existe plus**. Merci.

## 1. La question A du 14 août est réglée : oui, ça concorde

Atom4Love dérive la clé LOVE en local depuis le 14 août, en portant ta chaîne
(PBKDF2-HMAC-SHA256 600 k → base64url → scrypt N=4096 r=16 p=1 → SHA-256 →
secp256k1), ton SALT à sept champs et ta conversion solaire → UTC. La question
était : *est-ce que ça donne la même chose que la station ?*

**Oui, sur toute la ligne.** Fiche de référence
`1982-09-18T11:59` · `49.5626957` / `3.6366783` · poids 3,2 · polarité 0 :

| | ta station | Atom4Love |
|---|---|---|
| SALT | — | `198209181138_49.56_3.64_0_3.2_50_170` |
| instant UTC retenu | 11:38 | 11:38 |
| clé LOVE | `9f83b9ff377caea6…` | `9f83b9ff377caea6…` |
| `personal_phase` | 4,651994 | 4,651994 |
| tag `g` | `a4l:P06H8C1680AF` | `a4l:P06H8C1680AF` |
| `a5l_amplitude` | 0,506136 | 0,506136 |
| `kin_num` | 83 | 83 |

Pas d'écart, pas d'arrondi qui traîne : la clé publique est identique octet pour
octet. **Ta spécification est reproductible hors de ta station**, et ton
`tools/phi2x.py` de `master` est bien ce que la station déployée exécute.

C'est une bonne nouvelle pour toi autant que pour nous : n'importe quel client
peut désormais calculer la même chose que toi, et le vérifier seul.

## 2. Ce qui reste, et qui n'a rien à voir

**Q4.** Sur l'ancien compte, l'activation renvoie `LOVE_KEY_EXISTS_MISMATCH`, et
c'est **correct** : j'avais reforgé mon noyau entre-temps, ta station protège la
clé déjà posée. Y a-t-il un moyen de **libérer** la clé LOVE d'un compte quand la
fiche a légitimement changé, ou faut-il ouvrir un compte neuf à chaque reforge ?

Et la demande qui vaudrait pour tout le monde : **publie un vecteur de
référence**. Une fiche d'exemple avec, à côté, l'instant UTC retenu, φ, le KIN, la
maille et la clé LOVE que ta station en tire. Le tableau ci-dessus en est un —
s'il vivait dans ton dépôt, je ne t'aurais pas écrit deux fois.

## 3. Ce qu'on a corrigé de notre côté

Un certificat publié sous notre clé provisoire traînait sur ton relais et faisait
apparaître la même personne deux fois. Il est retiré (demande NIP-09 signée de
cette clé, acceptée). L'application le fait désormais toute seule quand la
station lui rend sa clé LOVE.

Le code est sur `github.com/FlorentLefevre-lab/Atom4Love`, branche `main` :
`domain/Phi2X.kt`, `domain/SolarTime.kt`, `domain/A4lAddress.kt`,
`nostr/Certificate.kt` et leurs tests.
