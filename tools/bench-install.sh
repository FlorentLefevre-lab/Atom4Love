#!/usr/bin/env bash
# Poser LE MÊME build sur tous les appareils du banc, en parallèle.
#
# ⚠ Pourquoi ce script existe, et pourquoi il vérifie par empreinte.
#
# Trois versions différentes sur trois appareils donnent une cabine muette
# SANS aucune erreur au journal (mesuré le 16/08) : on cherche alors un défaut
# de radio là où il n'y a qu'un décalage d'installation.
#
# Et `versionName` NE SUFFIT PAS à s'en assurer : il porte le dernier commit,
# pas le contenu de l'arbre. Deux builds faits du même commit avec des
# modifications différentes portent le même nom et n'ont pas les mêmes octets —
# c'est exactement ce qui est arrivé le 20/08, trois appareils affichant
# `5d33e5a+` avec deux binaires distincts. On compare donc l'empreinte de l'APK
# tel qu'il est INSTALLÉ.
#
# Usage : tools/bench-install.sh [chemin/vers.apk]
set -u

APK="${1:-composeApp/build/outputs/apk/debug/composeApp-debug.apk}"
PKG="one.astroport.atom4love.debug"

[ -f "$APK" ] || { echo "APK introuvable : $APK" >&2; exit 1; }

# ⚠ **Le banc est nommé, pour qu'un absent se voie.** Installer sur ce qui
# répond et se taire sur le reste, c'est reproduire exactement le décalage que
# ce script existe pour empêcher : un appareil resté en arrière donne une cabine
# muette sans la moindre erreur. Un appareil connu qui manque est donc un ÉCHEC,
# pas un silence.
BENCH="330022ed2fa5a3c1:A5-LineageOS 59031FDCH0028V:Pixel-10-Pro HVA5XP86:Tablette-TB350XU"

# ⚠ Sur une seule ligne : la comparaison ci-dessous cherche « espace serial
# espace », et une liste séparée par des retours à la ligne ne matche jamais —
# le premier essai déclarait les trois appareils manquants alors que deux
# étaient là.
DEVICES=$(adb devices | awk '/\tdevice$/ {print $1}' | tr '\n' ' ')
[ -n "$DEVICES" ] || { echo "aucun appareil branché" >&2; exit 1; }

missing=""
for entry in $BENCH; do
  serial=${entry%%:*}
  name=${entry##*:}
  case " $DEVICES " in
    *" $serial "*) : ;;
    *)
      state=$(adb devices | awk -v s="$serial" '$1==s {print $2}')
      state=${state:-absent}
      echo "⚠ MANQUANT : $name ($serial) — état adb : $state" >&2
      missing="$missing $name"
      ;;
  esac
done

WANT=$(sha256sum "$APK" | cut -d' ' -f1)
echo "APK   : $APK"
echo "sha256: $WANT"
echo

# ── En parallèle : l'installation dure 10 à 30 s par appareil, et les faire à
#    la file laisse le premier tourner sur le nouveau build pendant que le
#    dernier est encore sur l'ancien — la fenêtre exacte où les liens se
#    refont de travers.
for d in $DEVICES; do
  ( adb -s "$d" install -r "$APK" >/dev/null 2>&1 && echo "  installé   $d" \
      || echo "  ⚠ ÉCHEC    $d" ) &
done
wait
echo

# ── La vérification, appareil par appareil : ce qui est installé, pas ce qu'on
#    croit avoir envoyé.
fail=0
for d in $DEVICES; do
  path=$(adb -s "$d" shell pm path "$PKG" 2>/dev/null | tr -d '\r' | head -1 | cut -d: -f2)
  got=$(adb -s "$d" shell sha256sum "$path" 2>/dev/null | tr -d '\r' | cut -d' ' -f1)
  name=$(adb -s "$d" shell dumpsys package "$PKG" 2>/dev/null | grep versionName | head -1 | tr -d '\r ')
  if [ "$got" = "$WANT" ]; then
    echo "✅ $d  $name"
  else
    echo "❌ $d  $name  (installé $got)"
    fail=1
  fi
done


# ── Relancer, en parallèle aussi.
#
# ⚠ Une réinstallation coupe les liens des DEUX côtés : sans relance, les
# appareils restent l'application fermée et la maille ne se refait pas. Compter
# 30 à 60 s ensuite pour que les liens attestés reviennent — c'est le temps
# qu'il faut, pas un défaut.
if [ "${NO_LAUNCH:-}" != "1" ]; then
  for d in $DEVICES; do
    adb -s "$d" shell am start -n "$PKG/one.astroport.atom4love.MainActivity" >/dev/null 2>&1 &
  done
  wait
  echo
  echo "relancées — laisser 30 à 60 s aux liens pour se refaire"
fi

if [ -n "$missing" ]; then
  echo >&2
  echo "⚠⚠ Le banc n'est PAS synchronisé :$missing n'a pas reçu ce build." >&2
  echo "   Vérifier le câble, le mode USB, et le débogage USB sur l'appareil." >&2
  fail=1
fi

exit $fail
