#!/usr/bin/env bash
# Piloter un écran du banc en UNE commande, au lieu d'un aller-retour par geste.
#
# ⚠ Pourquoi ce script existe : chaque `adb shell` coûte ~200 ms, chaque dump
# d'interface ~1,5 s, et chaque capture ~1 s. Enchaîner huit gestes à la main
# revenait à vingt secondes d'attente ET huit tours de dialogue. Ici tout est
# groupé : un dump, une recherche, une série de taps, un verdict.
#
#   tools/bench-drive.sh <serial> find  "<texte>"          → coordonnées
#   tools/bench-drive.sh <serial> tap   "<texte>"          → touche ce texte
#   tools/bench-drive.sh <serial> texts                    → tout ce qui est lisible
#   tools/bench-drive.sh <serial> shot  <fichier.png>      → capture
#   tools/bench-drive.sh <serial> selfie "<pseudo>"        → scénario complet
#   tools/bench-drive.sh <serial> found  "<pseudo>"        → « j'ai trouvé »
set -u
S="$1"; shift
PKG=one.astroport.atom4love.debug

# ⚠ `/dev/tty` et non un fichier : le dump sort directement sur stdout, ce qui
# économise le `cat` et le round-trip d'écriture sur l'appareil.
dump() { adb -s "$S" exec-out uiautomator dump /dev/tty 2>/dev/null; }

texts() { dump | python3 "$(dirname "$0")/ui-texts.py"; }

find_one() { texts | grep -F -m1 -- "$1" | cut -f1; }

tap_text() {
  local p; p=$(find_one "$1")
  [ -n "$p" ] || { echo "introuvable : $1" >&2; return 1; }
  adb -s "$S" shell input tap ${p%,*} ${p#*,}
}

case "${1:-}" in
  texts) texts ;;
  find)  find_one "$2" ;;
  tap)   tap_text "$2" ;;
  shot)  adb -s "$S" exec-out screencap -p > "$2" ;;

  # ── Le scénario du visage, d'un bout à l'autre ──────────────────────────
  #
  # Les temps d'attente sont mesurés, pas devinés : 4 s pour que la lanterne se
  # compose, 3 s pour le dialogue, 7 s pour que CameraX ouvre le capteur (l'A5
  # de 2016 met 5 s), 2 s pour que le déclencheur réponde.
  selfie)
    tap_text "$2" || exit 1; sleep 4
    tap_text "Prendre un selfie" || exit 1; sleep 3
    tap_text "Prendre la photo" || exit 1; sleep 7
    # Le déclencheur n'a pas de texte : il est au centre, sous le cercle.
    read -r W H < <(adb -s "$S" shell wm size | sed 's/.*: //' | tr 'x' ' ')
    adb -s "$S" shell input tap $((W/2)) $((H*72/100))
    sleep 3
    adb -s "$S" logcat -d | grep -iE "visage préparé|message .* par |échec d'émission" | tail -2
    ;;

  found)
    tap_text "$2" || exit 1; sleep 4
    tap_text "trouvé la personne" || exit 1; sleep 2
    adb -s "$S" logcat -d | grep -iE "rencontre" | tail -2
    ;;

  *) echo "action inconnue : ${1:-}" >&2; exit 2 ;;
esac
