# Tes deux services sont fermés à tout Android antérieur à 10, et il suffit d'un certificat pour rouvrir

**De :** Florent · **Pour :** Fred (G1FabLab) · **Date :** 16 août 2026
**Statut :** mesure — une correction à faire chez toi, rien à faire chez moi

> Rien d'urgent : mon banc contourne le problème. Mais tel quel, `relay` et
> `u.copylaradio.com` sont **injoignables depuis n'importe quel téléphone
> Android de moins de 10**, et ça se corrige en réémettant un certificat.

## 1. Le symptôme

Un Galaxy A5 (2016) sous Android 7, sur mon banc, n'arrive à ouvrir **aucune**
connexion vers tes deux services. Côté client :

```
javax.net.ssl.SSLHandshakeException: Handshake failed
  Caused by: SSLProtocolException: SSL handshake terminated
    SSLV3_ALERT_HANDSHAKE_FAILURE
    HANDSHAKE_FAILURE_ON_CLIENT_HELLO
```

**C'est ton serveur qui refuse**, dès le premier message. Il ne s'agit ni d'un
certificat expiré, ni d'une autorité inconnue, ni d'un problème de nom : le
ClientHello est rejeté avant tout le reste.

## 2. Ce que la mesure dit

À l'openssl, en faisant varier ce que le client annonce :

| `relay.copylaradio.com` | résultat |
|---|---|
| TLS 1.2, groupes par défaut (avec P-384) | ✅ `ECDHE-ECDSA-AES256-GCM-SHA384` |
| TLS 1.2, `P-256:P-384` | ✅ passe |
| TLS 1.2, **`X25519:P-256`** | ❌ `alert handshake failure (40)` |
| TLS 1.3, `P-256` seul | ✅ passe |

Même résultat exactement sur `u.copylaradio.com`.

Et à titre de comparaison, un service public qui, lui, fonctionne sur le même
téléphone :

| hôte | clé du certificat | Android 7 |
|---|---|---|
| `relay.copylaradio.com` | **ECDSA P-384** | ❌ |
| `u.copylaradio.com` | **ECDSA P-384** | ❌ |
| `api-adresse.data.gouv.fr` | ECDSA **P-256** | ✅ |

## 3. Pourquoi, précisément

Ce n'est **pas** ton `ssl_ecdh_curve` : la ligne « TLS 1.3, P-256 seul → passe »
le prouve, ton serveur sait très bien négocier un échange de clés en P-256.

C'est **la courbe de la clé de ton certificat**. En TLS 1.2, la RFC 4492 §5.1
fait porter l'extension `supported_groups` sur deux choses à la fois : les
courbes d'échange de clés **et la courbe de la clé publique du certificat**. Un
serveur dont le certificat est en P-384 ne peut donc pas le présenter à un
client qui n'annonce pas `secp384r1` — il n'a rien à répondre, et envoie
`handshake_failure`.

En TLS 1.3 cette contrainte disparaît (la courbe du certificat relève des
`signature_algorithms`). Mais **Android ne parle TLS 1.3 qu'à partir de la
version 10** : tout ce qui est en dessous est en TLS 1.2, donc soumis à la
règle ci-dessus.

Or la pile TLS d'Android (BoringSSL) **n'annonce que X25519 et P-256 par
défaut** sur ces versions-là. L'intersection avec ton P-384 est vide.

## 4. Ce que ça exclut, concrètement

Tout Android de 4.4 à 9 inclus — soit, aujourd'hui encore, une part non
négligeable des téléphones qui traînent dans les poches, et précisément le genre
d'appareil qu'une application de rencontre par la radio croise en vrai. Pour eux
tes deux services n'existent pas : ni relais NOSTR, ni guichet MULTIPASS.

Vérifié dans les deux sens : le même téléphone passé sous Android 10 se connecte
au relais du premier coup, sans qu'une seule ligne ait changé chez toi ni chez
moi.

## 5. La correction

**Réémettre les deux certificats en ECDSA P-256.** Let's Encrypt délivre du
P-256 par défaut ; le P-384 est un choix explicite quelque part dans ta
configuration ACME. Selon le client :

```bash
# certbot
certbot certonly --key-type ecdsa --elliptic-curve secp256r1 -d relay.copylaradio.com

# acme.sh
acme.sh --issue -d relay.copylaradio.com --keylength ec-256
```

Rien d'autre à toucher : ni nginx, ni `ssl_ecdh_curve`, ni les protocoles.

## 6. Ce que ça ne coûte pas

P-256 offre 128 bits de sécurité, P-384 en offre 192. Les deux sont très
au-dessus de ce qu'exige un service web, et **P-256 est ce qu'utilisent
aujourd'hui la quasi-totalité des sites en ECDSA** — dont `api-adresse.data.gouv.fr`
ci-dessus. Tu ne descends pas d'un cran de sécurité utile, tu enlèves une
exclusion.

Si tu tiens à P-384, l'autre voie est de servir **deux certificats** (un P-256
et un P-384) et de laisser nginx choisir selon le client — mais c'est plus de
configuration pour le même résultat.

## 7. Ce qui n'était PAS en cause, pour l'écarter

J'ai d'abord cru à un problème d'autorité. C'en était un aussi, mais séparé, et
**je l'ai réglé de mon côté** : tes chaînes remontent à **ISRG Root X2**, racine
ECDSA publiée en 2020, absente des magasins figés avant. Atom4Love embarque
désormais les deux racines ISRG, donc ce point-là est clos et ne demande rien.

Les deux problèmes se ressemblaient à l'écran et n'avaient rien à voir : le
premier disait `Trust anchor for certification path not found`, le second
`HANDSHAKE_FAILURE_ON_CLIENT_HELLO`. Seul le second reste, et il est chez toi.
