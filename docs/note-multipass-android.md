# Premier MULTIPASS créé depuis Android : ce qui marche, ce qui bloque

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 12 août 2026 · **Statut :** un blocage à diagnostiquer de ton côté, deux questions de conception

Atom4Love sait désormais ouvrir un compte Astroport.ONE. Le parcours est porté **à
l'identique de ton client** — `zelkova/lib/g1/multipass_service.dart` — et non
réinventé : le client ne dérive aucune clé, la station fait tout. J'ai retiré du
projet la dérivation locale que j'avais commencée le jour où j'ai vu que tu avais
supprimé `atomic_keys.dart` de Ẑelkova.

Premier essai réel ce matin sur `u.copylaradio.com`. Le MULTIPASS est né ;
l'activation ATOM4LOVE, elle, échoue — et la clé LOVE existe pourtant sur ta
station. C'est le sujet de cette note.

## 1. Ce qui a fonctionné

`POST /g1nostr` (email, lang, lat, lon, format=json — aucune donnée de naissance,
conformément à ton commentaire dans `multipass_service.dart`) :

- réponse complète en une trentaine de secondes : `nsec`, `npub`, `hex`, `g1pub`,
  `pass`, `ssss`, `nostrns` ;
- le compteur public de la station est passé de **7 à 8 MULTIPASS**, et le revenu
  hebdomadaire de 35 à 36 Ẑen : le compte est bien enregistré, pas simulé ;
- `GET /atom4love/challenge?email=…` répond 200 avec le `pubkey_hex` du compte —
  donc le compte est complet côté serveur.

Compte de test créé : `florent.lefevre.test@yopmail.com`
(npub `npub17hg86uz8ne2yqhvc0rnmmzr0l8hpkup7qgadyrf3nm8hgkvat8uqzfmf7u`).

## 2. Ce qui bloque : `POST /atom4love/activate` → 500 `ACTIVATION_FAILED`

Trois tentatives, même résultat. L'authentification n'est pas en cause : un
`INVALID_CHALLENGE` ou un `PUBKEY_MISMATCH` donnerait un autre code. L'event
kind 22242 est bien signé avec le `nsec` principal, sur un challenge fraîchement
obtenu.

**Et pourtant la clé LOVE existe.** Juste après l'échec :

```
GET /atom4love/dream?email=florent.lefevre.test@yopmail.com
→ {"dream_vector":null,
   "love_hex":"93a2cded53d17463962422011b2435ef4447e21a5c1c331d61438645663d279a"}
```

`atom4love_activate.sh` a donc bien écrit `.secret.love` et `HEX_LOVE`. Ce qui
n'a pas eu lieu, c'est la publication :

```
REQ {"kinds":[30078],"authors":["93a2cded…279a"]}   → 0 événement
REQ {"kinds":[30078],"#d":["atom4love"]}           → 2 événements (d'autres comptes)
```

interrogé directement sur `wss://relay.copylaradio.com`. Le relais n'est donc pas
fermé aux profils ATOM4LOVE : il en héberge déjà deux. C'est **notre** publication
qui n'aboutit pas.

Et l'un des deux vient de ton propre script. Leurs contenus :

| pubkey | publié le | champs du content | indice |
|---|---|---|---|
| `0fd65c06…` | 28 juin 2026 | `personal_phase, omega_bio, a5l_amplitude, biological_sex, kin_num, email, version` | tags `email`, `kin`, `glyph`, `tone`, `color`, `kin_c` — c'est **exactement ce qu'écrit `atom4love_publish.py`** |
| `71b4b56f…` | 20 juin 2026 | `personal_phase, omega_bio, a5l_amplitude, kin_birth, kin_conception, archetype, element_birth, pepper_type` | tag `app` — un autre producteur, probablement `atomic.html` |

Donc la publication **depuis la station a déjà fonctionné** sur ce relais, le
28 juin. Ce n'est pas une porte fermée depuis toujours : soit quelque chose a
changé depuis, soit l'échec tient à ce compte-ci.

### Pourquoi le client n'obtient jamais sa clé

`tools/atom4love_publish.py` se termine par :

```python
print(json.dumps({
    "activated": bool(publish_result.get("success")),
    …
    "love_nsec": love_nsec, "love_npub": love_npub, "love_hex": love_hex,
```

et `UPassport/routers/identity.py` :

```python
if return_code != 0 or not data.get("activated"):
    return JSONResponse(status_code=500, content={"error": …})
```

Le verdict `activated` est donc **celui de la publication du kind 30078**, pas
celui de la dérivation. Publication ratée ⇒ HTTP 500 ⇒ le `love_nsec` reste dans
le corps de réponse jeté. Résultat concret : **un compte peut avoir une clé LOVE
que son porteur ne peut pas récupérer**, alors qu'elle est écrite sur la station
et que son `love_hex` est lisible publiquement par `/atom4love/dream`.

## 3. Le courriel de PASS n'est pas arrivé

Rien sur `florent.lefevre.test@yopmail.com` (boîte consultée sans compte, comme
les `@yopmail.com` déjà présents sur ta station). L'app affiche le PASS reçu dans
`.multipass.json`, donc le compte reste récupérable — mais quelqu'un qui passerait
par le web n'aurait rien.

Deux services externes en échec sur la même station — publication NOSTR et envoi
de courriel — ça ressemble à une configuration incomplète sur `libra` plutôt qu'à
un bug de protocole. À toi de voir dans les journaux.

## 4. Ce que je te demande

1. **Pourquoi la publication échoue** sur cette station, alors qu'un profil au
   format exact de `atom4love_publish.py` y est passé le 28 juin ? Les pistes que
   je ne peux pas vérifier de l'extérieur : `a4l_proof` refusé par le filtre
   `NIP-101/30078.sh` si `ATOM4LOVE_v1` a quitté les `AUTHORIZED_APPS` du kind
   30800, ou `send_nostr_event` qui n'aboutit pas depuis la machine. Les journaux
   d'`atom4love_publish.py` tranchent en une ligne — moi, je ne vois que le 500.
2. **Faut-il découpler dérivation et publication ?** De mon point de vue de client :
   la clé LOVE est le vrai livrable de l'activation, la publication du profil en est
   la conséquence. Rendre 200 avec `love_nsec` et un drapeau `published: false`
   quand le relais refuse me paraîtrait plus juste — sinon la clé existe sans son
   porteur. Ton avis prime : c'est ton protocole.
3. **Le courriel de PASS** part-il depuis `libra` (mailjet configuré) ?
4. **Quelle station viser** pour Atom4Love en vrai ? Je pointe pour l'instant
   `u.copylaradio.com`, c'est-à-dire UPlanet ORIGIN — et je l'annonce à
   l'utilisateur comme le bac à sable que ton README décrit, purge à sept jours
   comprise. Est-ce le bon choix, ou faut-il viser une station ẐEN ?

## 5. Pour reproduire

Tout est vérifiable depuis n'importe quel poste :

```bash
curl "https://u.copylaradio.com/atom4love/dream?email=florent.lefevre.test@yopmail.com"
curl "https://u.copylaradio.com/atom4love/challenge?email=florent.lefevre.test@yopmail.com"
```

Compte de test, jetable — supprime-le sans hésiter, il sera purgé de toute façon.

---

*Atom4Love · note préparée avec l'assistance de Claude · essai réel du 12 août 2026,
tablette Lenovo TB350XU, application au commit `afe8777`.*
