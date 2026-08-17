# noise-java — code tiers vendorisé

Implémentation Java du protocole Noise, par Rhys Weatherley (Southern Storm
Software). Sert de socle au handshake Noise XX du chat BLE.

- **Amont** : https://github.com/rweather/noise-java
- **Commit repris** : `49377b6dfc6a1e75740bce2318118291a57c0d6e`
- **Reprise le** : 2026-08-11
- **Licence** : MIT (voir `LICENSE.txt` dans ce dossier, en-tête dans chaque fichier)
- **Contenu** : `src/main/java/com/southernstorm/noise/` de l'amont, **sans une seule
  modification**

## Pourquoi vendoriser plutôt que dépendre

`minSdk = 26` : Android ne fournit ChaCha20-Poly1305 qu'à partir de l'API 28 et
X25519 (XDH) qu'à partir de l'API 33. La plateforme ne peut donc pas fournir la
crypto de Noise sur nos cibles. noise-java est en Java pur, autonome, sans
dépendance — il apporte Curve25519, ChaChaPoly, SHA-256 et la machine à états
des handshakes.

L'amont n'est pas publié sur Maven Central, d'où la copie dans l'arbre.

## Pourquoi tout copier, y compris l'inutile

Nous n'utilisons que `Noise_XX_25519_ChaChaPoly_SHA256`. Curve448, AES-GCM,
Blake2, SHA-512 et NewHope (post-quantique) ne nous servent pas — mais
`Noise.java` est une fabrique qui les référence tous : les élaguer imposerait
de **modifier du code cryptographique tiers**, au prix de l'auditabilité et de
toute resynchronisation future avec l'amont.

Le code inutilisé ne coûte rien au binaire livré : R8 le supprime de la release
faute d'être atteignable (même mécanisme que pour les sondes `diag/`).

## Vérifier que rien n'a été touché

```sh
git clone https://github.com/rweather/noise-java /tmp/noise-java
cd /tmp/noise-java && git checkout 49377b6dfc6a1e75740bce2318118291a57c0d6e
diff -r /tmp/noise-java/src/main/java/com/southernstorm/noise \
        app/src/main/java/com/southernstorm/noise \
        --exclude=LICENSE.txt --exclude=PROVENANCE.md
```

Toute divergence signalée par cette commande est une modification locale à
justifier — il ne devrait jamais y en avoir.
