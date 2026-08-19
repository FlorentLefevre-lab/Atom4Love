#!/usr/bin/env bash
#
# Publier une version d'Atom4Love.
#
# Ni Play Store ni F-Droid : l'APK se prend au dépôt du projet, et le site de
# la station en tient le miroir. Ce script fabrique les trois choses qui font
# une version publiée, et rien de plus :
#
#   1. l'APK release **signé** ;
#   2. son empreinte SHA-256 ;
#   3. `latest.json`, le manifeste que l'application lit pour savoir qu'elle
#      est vieille.
#
# Il ne pousse rien tout seul. La dernière commande est toujours la vôtre.
#
# ── Ce qu'il refuse de faire, et pourquoi ─────────────────────────────────
#
#  - publier sans keystore : un APK signé d'une autre clé ne remplace JAMAIS
#    celui qui est installé. Android le refuse, et la personne devrait
#    désinstaller — donc perdre son noyau scellé — pour passer à la suite ;
#  - publier un versionCode qui ne monte pas : le seul nombre qu'Android
#    regarde. S'il ne bouge pas, la mise à jour n'existe pas ;
#  - publier depuis un arbre sale : une version est un point du dépôt, pas un
#    état de votre bureau.
#
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
OUT="$ROOT/build/release"

die() { printf '\n✗ %s\n' "$*" >&2; exit 1; }
say() { printf '· %s\n' "$*"; }

NOTES="${1:-}"

# ── 1. Ce que le build déclare ────────────────────────────────────────────
VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' composeApp/build.gradle.kts | head -1)
VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' composeApp/build.gradle.kts | head -1)
MIN_SDK=$(grep -oP 'minSdk\s*=\s*\K[0-9]+' composeApp/build.gradle.kts | head -1)
[ -n "$VERSION_NAME" ] && [ -n "$VERSION_CODE" ] || die "versionName/versionCode illisibles dans composeApp/build.gradle.kts"
TAG="v$VERSION_NAME"
say "version $VERSION_NAME · code $VERSION_CODE · minSdk $MIN_SDK · tag $TAG"

# ── 2. Les refus ──────────────────────────────────────────────────────────
[ -f keystore.properties ] || die "keystore.properties absent : voir docs/note-distribution-apk.md.
  Sans clé stable, l'APK publié ne pourra jamais mettre à jour celui d'avant."

# `latest.json` est exclu du contrôle : c'est CE script qui l'écrit, et il doit
# rester rejouable — corriger une coquille dans les notes, refaire l'APK avant
# publication. Tout le reste doit être commité : une version est un point du
# dépôt, pas un état du bureau.
[ -z "$(git status --porcelain -- . ':!latest.json')" ] ||
  die "l'arbre n'est pas propre : commitez d'abord."

if git rev-parse "$TAG" >/dev/null 2>&1; then
  die "le tag $TAG existe déjà : incrémentez versionName ET versionCode."
fi

LAST_CODE=$(git show HEAD:latest.json 2>/dev/null | grep -oP '"versionCode"\s*:\s*\K[0-9]+' || echo 0)
if [ "$VERSION_CODE" -le "$LAST_CODE" ]; then
  die "versionCode $VERSION_CODE n'est pas supérieur au dernier publié ($LAST_CODE)."
fi

# ── 3. Compiler et signer ─────────────────────────────────────────────────
say "compilation release…"
./gradlew --quiet :composeApp:assembleRelease

APK_BUILT="composeApp/build/outputs/apk/release/composeApp-release.apk"
[ -f "$APK_BUILT" ] || die "APK introuvable : $APK_BUILT"

# apksigner vit dans le SDK, jamais dans le PATH — on le cherche là où il est.
SDK_DIR=$(grep -oP '^sdk.dir=\K.*' local.properties 2>/dev/null || echo "${ANDROID_HOME:-$HOME/Android/Sdk}")
APKSIGNER=$(ls -1 "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
if [ -n "$APKSIGNER" ]; then
  say "vérification de la signature…"
  "$APKSIGNER" verify --print-certs "$APK_BUILT" | grep -E "Signer #1 certificate (DN|SHA-256)" || \
    die "APK non signé — vérifiez keystore.properties."
else
  say "⚠ apksigner introuvable : signature NON vérifiée."
fi

# ── 4. L'APK, son empreinte, son manifeste ────────────────────────────────
mkdir -p "$OUT"
# Un seul nom, définitif : le lien se donne une fois (page, message, QR code)
# et ne se réécrit jamais. GitHub sert la dernière release sous
# `releases/latest/download/…`, la station sert le fichier qu'on y dépose.
#
# ⚠ Le contenu change donc sous une adresse constante : un cache ou un
# navigateur peut servir l'APK d'hier. C'est précisément ce que l'empreinte du
# manifeste attrape — l'app refuse et le dit, elle n'installe pas l'ancien.
APK_NAME="atom4love-latest.apk"
cp "$APK_BUILT" "$OUT/$APK_NAME"

SHA=$(sha256sum "$OUT/$APK_NAME" | cut -d' ' -f1)
SIZE=$(stat -c%s "$OUT/$APK_NAME")
DATE=$(date -u +%Y-%m-%d)
(cd "$OUT" && sha256sum "$APK_NAME" > SHA256SUMS)

GH_BASE="https://github.com/FlorentLefevre-lab/Atom4Love/releases/latest/download"
MIRROR="https://u.copylaradio.com/www/$APK_NAME"

cat > latest.json <<JSON
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "minSdk": $MIN_SDK,
  "publishedAt": "$DATE",
  "sha256": "$SHA",
  "sizeBytes": $SIZE,
  "url": "$GH_BASE/$APK_NAME",
  "mirrors": ["$MIRROR"],
  "notes": "$NOTES"
}
JSON

say "APK      : $OUT/$APK_NAME ($((SIZE / 1048576)) Mo)"
say "empreinte: $SHA"
say "manifeste: latest.json"

# ── 5. Ce qu'il reste à faire — de votre main ─────────────────────────────
#
# Le conseil est composé AVANT l'affichage : un heredoc dans une substitution
# de commande, avec un `||` entre deux, ne s'analyse pas — et un script qui
# finit sur une erreur de syntaxe fait douter de tout ce qu'il vient de faire.
if command -v gh >/dev/null; then
  PUBLISH_HINT="       gh release create $TAG \"$OUT/$APK_NAME\" \"$OUT/SHA256SUMS\" \\
         --title \"$TAG\" --notes \"${NOTES:-$TAG}\""
else
  PUBLISH_HINT="       (gh n'est pas installé — par la page des releases)
       git push && git tag -a $TAG -m \"$TAG\" && git push origin $TAG
       puis https://github.com/FlorentLefevre-lab/Atom4Love/releases/new?tag=$TAG
       en y joignant $OUT/$APK_NAME et $OUT/SHA256SUMS"
fi

cat <<FIN

Reste à publier. Dans l'ordre, parce que l'app lit le manifeste AVANT de
télécharger : si latest.json arrive le premier, il annonce un APK qui n'existe
pas encore.

  1. La release GitHub, avec l'APK et ses sommes :
$PUBLISH_HINT
  2. Le manifeste, une fois l'APK en ligne :
       git add latest.json && git commit -m "$TAG" && git push

  3. Le miroir chez Fred (son dépôt, son geste — voir
     docs/note-distribution-apk.md) : $APK_NAME et atom4love.json
     dans www/ (les TROIS écrasés, index.html compris), puis ./microledger.me.sh

  4. Vérifier que la mécanique tourne, depuis un appareil :
       curl -sS https://raw.githubusercontent.com/FlorentLefevre-lab/Atom4Love/main/latest.json
FIN
