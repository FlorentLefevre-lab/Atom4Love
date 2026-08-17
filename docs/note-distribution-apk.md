# Distribuer Atom4Love par APK : le dépôt fait foi, la station fait miroir

**De :** Atom4Love · **Pour :** Fred (G1FabLab) · **Date :** 17 août 2026 · **Statut :** en place côté app, une demande côté station

Atom4Love ne passera ni par le Play Store ni par F-Droid pour le moment. L'application
va donc chercher elle-même sa version : elle lit un manifeste, télécharge l'APK, vérifie
son empreinte, et passe la main à l'installeur du système. Ce document dit comment ça
marche, ce qui est déjà chez toi, et ce que je te demande.

## 1. Ce que tu as déjà écrit, et qu'on suit

Ton dépôt UPlanet porte un dossier `earth/apk/`, avec `coracle.apk` dedans. Et ta station
le sert correctement — vérifié le 17/08 :

```
$ curl -sI https://u.copylaradio.com/earth/apk/coracle.apk
200 · application/vnd.android.package-archive · 49 479 480 octets
```

Le bon type MIME, donc un navigateur Android le propose directement à l'installation.
C'est ce chemin-là qu'on emprunte : on ne trace pas le nôtre à côté du tien.

Deux constats, en revanche :

- **rien ne référence cet APK** — aucune occurrence de `.apk` dans tout le dépôt. Le
  fichier est servi, mais aucune page ne le propose ;
- **il n'est pas dans la publication IPFS** : `/ipns/copylaradio.com/apk/coracle.apk`
  répond 404, alors que `/ipns/copylaradio.com/miz.html` répond bien. Le CID de `.chain`
  est antérieur au dépôt du fichier. Aujourd'hui, la seule porte est l'uSPOT.

## 2. Le manifeste : un seul fichier, deux adresses

L'application lit `latest.json`, dans cet ordre :

1. `https://raw.githubusercontent.com/FlorentLefevre-lab/Atom4Love/main/latest.json` — le
   dépôt du projet, qui **fait foi** ;
2. `https://u.copylaradio.com/earth/apk/atom4love.json` — **ton miroir**.

C'est le même fichier aux deux endroits. Peu importe lequel répond : l'empreinte SHA-256
qu'il porte dit quels octets sont les bons, et l'app refuse tout le reste.

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "minSdk": 26,
  "publishedAt": "2026-08-20",
  "sha256": "3f2a…",
  "sizeBytes": 28311552,
  "url": "https://github.com/FlorentLefevre-lab/Atom4Love/releases/latest/download/atom4love-latest.apk",
  "mirrors": ["https://u.copylaradio.com/earth/apk/atom4love-latest.apk"],
  "notes": "une phrase sur ce qui change"
}
```

Les champs inconnus sont ignorés à la lecture : tu peux en ajouter sans rendre le fichier
illisible aux APK déjà installés. C'est la seule promesse qui compte — **un vieil APK doit
toujours savoir lire le manifeste du jour**, sinon il ne saura jamais qu'il est vieux.

## 3. Ce que je te demande

Deux gestes, dans ton dépôt, à chaque version que je publie :

1. **écraser** `atom4love-latest.apk` **et** `atom4love.json` dans `earth/apk/` ;
2. lancer `./microledger.me.sh` comme d'habitude.

Le nom ne change jamais, volontairement : le lien se donne une fois — sur une page,
dans un message, sur un QR code — et ne se réécrit plus. Côté GitHub c'est
`releases/latest/download/atom4love-latest.apk`, qui redirige toujours vers la
dernière version publiée.

⚠ Un contenu qui change sous une adresse constante, c'est ce que les caches
servent mal. D'où l'empreinte dans le manifeste : si un cache rend l'APK d'hier,
l'application le voit, refuse, et le dit. Les deux fichiers doivent donc être
écrasés **ensemble** — un APK neuf avec l'ancien JSON, ou l'inverse, se
présente comme une corruption.

Et, si tu le veux bien, un troisième qui ne me regarde pas mais qui manque : **une page
qui propose les APK**. Il y en a deux à cette adresse maintenant (le tien et le mien) et
aucun lien vers eux. Je ne l'écris pas à ta place — c'est ton dossier, ta grammaire de
pages, et je ne construis pas là où tu n'as rien écrit. Dis-moi si tu la veux, et sous
quelle forme.

Une question ouverte, aussi : **veux-tu que les APK entrent dans la publication IPFS ?**
Un CID est immuable, ce qui est exactement ce qu'on veut d'un binaire signé — mais ça
alourdit chaque republication d'`earth/` de quelques dizaines de mégaoctets. Ton dépôt,
ton arbitrage.

## 4. Ce que l'application fait, et ce qu'elle ne fait pas

Elle télécharge, elle vérifie, elle **passe la main**. Le dernier écran est celui du
système, avec la liste des permissions et un bouton qu'il faut appuyer. Rien ne
s'installe sans ce geste-là.

Deux verrous d'Android, et on n'en contourne aucun :

- `REQUEST_INSTALL_PACKAGES` ne s'accorde pas à l'installation de l'app, mais dans les
  réglages, application par application. L'app y conduit ; elle ne l'arrache pas ;
- l'écran d'installation lui-même, hors de nous.

La vérification SHA-256 n'est pas décorative : sans elle, un miroir compromis ou un
transfert tronqué mettrait un APK inconnu devant l'installeur. En cas d'écart, le fichier
est effacé et l'app le dit — elle ne retente pas.

## 5. Mode d'emploi, côté maintenance

### La clé de signature — avant tout le reste

Android refuse d'installer une mise à jour signée d'une autre clé que la version en place.
Cette clé doit donc naître **avant le premier APK diffusé**, et ne jamais se perdre : la
perdre obligerait chaque porteur à désinstaller — donc à perdre son noyau scellé — pour
passer à la version suivante.

```bash
mkdir -p ~/.atom4love
keytool -genkeypair -v \
  -keystore ~/.atom4love/atom4love-release.jks \
  -storetype PKCS12 \
  -alias atom4love \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Astroport.ONE, OU=Atom4Love, O=Astroport.ONE, C=FR"
```

Le `-dname` est passé en argument : il ne reste qu'un mot de passe à saisir, et
surtout ce nom-là est **gravé dans le certificat**, visible de qui inspecte
l'APK. C'est la station qui signe, pas une personne — aucun nom privé n'y
figure. `-validity 10000` porte jusqu'en 2053 : un certificat qui expire avant
la fin de vie de l'application est un piège qu'on ne se pose pas.

Puis, à la racine du dépôt, un `keystore.properties` (déjà dans `.gitignore`, il ne part
jamais avec le code) :

```properties
storeFile=/home/<vous>/.atom4love/atom4love-release.jks
storePassword=…
keyAlias=atom4love
keyPassword=…
```

⚠ Sauvegarder le `.jks` **hors de la machine**. Il ne se régénère pas.

### L'identité publique de l'application

Clé créée le 17/08/2026. Tout APK légitime porte ce certificat, et n'importe qui
peut le recalculer sur un fichier téléchargé :

```
$ apksigner verify --print-certs atom4love-latest.apk
Signer #1 certificate DN: CN=Astroport.ONE, OU=Atom4Love, O=Astroport.ONE, C=FR
Signer #1 certificate SHA-256 digest: f64a78a8a96fee398b089d773614aca8a08660a935a71d3c41714006c933f633
Signer #1 key algorithm: RSA · 4096 bits
```

Signature **v2 + v3** (`enableV3Signing` dans `app/build.gradle.kts`). Le v2 suffit
à installer ; le v3 garde ouverte la **rotation de clé** — remplacer cette clé un
jour sans que personne ait à désinstaller. Sans lui, une clé perdue serait sans
recours. C'est pour cela qu'il est posé avant la première publication : un APK
déjà installé ne connaît que les schémas qu'il portait.

### Publier une version

```bash
# 1. incrémenter versionName ET versionCode dans app/build.gradle.kts, commiter
# 2. puis :
./tools/release.sh "ce qui change, en une phrase"
```

Le script refuse de publier sans keystore, sur un arbre sale, sur un tag qui existe déjà,
ou avec un `versionCode` qui ne monte pas. Il compile, vérifie la signature, calcule
l'empreinte, écrit `latest.json`, et **s'arrête là** : il affiche les commandes qui
restent, dans l'ordre — la release GitHub d'abord, le manifeste ensuite. Jamais l'inverse,
sinon l'app annonce un APK qui n'existe pas encore.

### Le piège du banc

Un build **debug** porte l'applicationId `one.astroport.atom4love.debug`. L'APK release
téléchargé par la mécanique ne le remplacera jamais : il s'installera **à côté**. C'est
normal, et c'est même commode — mais deux icônes identiques sur l'écran d'accueil se
confondent vite. L'onglet Réglages dit laquelle tourne.
