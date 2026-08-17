# noise-java, porté en Kotlin

Implémentation du protocole Noise par Rhys Weatherley (Southern Storm
Software), **réécrite en Kotlin le 17 août 2026**. Sert de socle au handshake
Noise XX du chat BLE.

- **Amont** : https://github.com/rweather/noise-java
- **Commit repris** : `49377b6dfc6a1e75740bce2318118291a57c0d6e`
- **Reprise le** : 2026-08-11 (Java), **portée le 2026-08-17** (Kotlin)
- **Licence** : MIT (voir `LICENSE.txt` dans ce dossier, en-tête dans chaque
  fichier). `crypto/RijndaelAES.kt` et les parties de `crypto/Curve448.kt`
  venues d'Ed448-Goldilocks portent leurs propres mentions.
- **Contenu** : les 29 fichiers de `src/main/java/com/southernstorm/noise/` de
  l'amont, portés un par un

## Pourquoi vendoriser

`minSdk = 26` : Android ne fournit ChaCha20-Poly1305 qu'à partir de l'API 28 et
X25519 (XDH) qu'à partir de l'API 33. La plateforme ne peut donc pas fournir la
crypto de Noise sur nos cibles. noise-java est autonome, sans dépendance — il
apporte Curve25519, ChaChaPoly, SHA-256 et la machine à états des handshakes.

L'amont n'est pas publié sur Maven Central, d'où la copie dans l'arbre.

## Pourquoi porter en Kotlin

Java ne compile pas vers Kotlin/Native. Tant que ce paquet était en Java, la
cabine chiffrée ne pouvait pas exister sur iPhone, et rien ne pouvait monter
dans `commonMain`. C'est l'étape 1 de `docs/note-portage-ios.md`, et elle ne
dépendait d'aucun arbitrage iOS.

⚠ **Une transcription littérale n'aurait servi à rien.** Quatre classes de
hachage héritaient de `java.security.MessageDigest`, et
`javax.crypto.ShortBufferException` / `BadPaddingException` traversaient toute
l'API. Le port les remplace par les nôtres — voir `Platform.kt`, qui tient
**toutes** les substitutions dans un seul fichier.

## Ce que le port change, et rien d'autre

| | |
|---|---|
| `java.security.MessageDigest` | `crypto/MessageDigest.kt`, réduite aux 4 opérations réellement appelées |
| `ShortBufferException`, `BadPaddingException`, `NoSuchAlgorithmException`, `DigestException` | les nôtres, mêmes noms, dans `Platform.kt` |
| `AEADBadTagException` par réflexion | un vrai sous-type de `BadPaddingException` |
| `java.security.SecureRandom` | `SecureRandomSource`, l'unique couture de plateforme |
| `char[]` (entier 16 bits non signé) | `IntArray` + `and 0xFFFF` à chaque `(char)` — voir la note de classe de `NewHope.kt` |
| `Noise.createHash` interrogeait le JCA | rend toujours l'implémentation maison : même sortie, un peu plus lente |

⚠ **Une seule classe reste liée à la JVM** : `AESGCMOnCtrCipherState`, qui
n'existe que pour déléguer AES à `javax.crypto`. C'est sa raison d'être ; côté
iOS il n'y a rien à écrire, `Noise.createCipher` retombe seul sur
`AESGCMFallbackCipherState`. Rien chez nous n'appelle AES-GCM.

⚠ **Les défauts de l'amont sont reproduits, pas corrigés** — chacun porte un
commentaire : le `return 32` de `Blake2b.engineDigest` (qui écrit 64 octets),
les `Arrays.fill(buf, offset, length)` qui passent une longueur là où une borne
est attendue, le `FLAG_REMOTE_EPHEMERAL` écrit deux fois dans
`noise_pattern_IKnoidh_hfs`, l'`outputOffset` ignoré de `sha3256`. Corriger en
silence ferait diverger le port de son oracle sans que rien ne le signale.

## Ce qu'on a perdu, et ce qui le remplace

Avant le port, l'intégrité se vérifiait d'une commande : `diff -r` contre
l'amont, qui ne devait rien signaler. **Cette propriété est morte** — c'était
le prix, et il était su.

Ce qui la remplace est dans les tests :
`androidUnitTest/kotlin/com/southernstorm/noise/ref/` tient la copie **Java
intacte** de l'amont, au paquet près (`com.southernstorm.noise.ref`), et
`PortageDifferentielTest` compare les deux implémentations **octet par octet** —
primitives sur tailles limites, handshake XX complet à clés éphémères fixées,
échange New Hope de bout en bout.

⚠ **Ne pas supprimer la copie de référence.** Elle ne part dans aucun APK
(source de test seulement) et c'est le seul oracle extérieur du port. Sans
elle, les 28 tests comportementaux restants ne compareraient plus que le port à
lui-même — et un port faux des deux côtés les passerait tous.

Pour revérifier que la référence est bien l'amont intact :

```sh
git clone https://github.com/rweather/noise-java /tmp/noise-java
cd /tmp/noise-java && git checkout 49377b6dfc6a1e75740bce2318118291a57c0d6e
diff -r /tmp/noise-java/src/main/java/com/southernstorm/noise \
        <(cd composeApp/src/androidUnitTest/kotlin/com/southernstorm/noise/ref && \
          sed 's/com\.southernstorm\.noise\.ref/com.southernstorm.noise/g' -r .)
```

Seuls les noms de paquet doivent différer.

## Le jour où ça monte dans `commonMain`

Rien à déplacer sauf `AESGCMOnCtrCipherState.kt` (reste dans `androidMain`) et
l'implémentation de `SecureRandomSource`, qui devient un `expect`/`actual`.
Le test différentiel, lui, ne peut pas suivre : il dépend de sources Java.
