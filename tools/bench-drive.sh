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

# ⚠ **On réessaie avant de renoncer.** Compose compose à sa main : un dump
# lancé une seconde trop tôt rend un écran à moitié posé, et le bouton qu'on
# cherche n'y est pas encore. Trois tentatives à 1,5 s valent mieux qu'un échec
# qui ferait croire à un défaut d'affichage — ça m'est arrivé quatre fois
# aujourd'hui.
find_one() {
  local i p
  for i in 1 2 3 4; do
    p=$(texts | grep -F -m1 -- "$1" | cut -f1)
    [ -n "$p" ] && { echo "$p"; return 0; }
    # ⚠⚠ **Le dump ne montre que ce qui est RENDU.** Une carte sous la ligne de
    # flottaison n'existe pas pour uiautomator, et le pilote concluait
    # « introuvable » sur un écran qui la portait trois centimètres plus bas —
    # deux fois aujourd'hui, dont une où j'ai cru la radio morte. On fait donc
    # défiler entre deux tentatives, puis on remonte pour ne pas laisser
    # l'écran ailleurs qu'on l'a trouvé.
    read -r W H < <(adb -s "$S" shell wm size | sed 's/.*: //' | tr 'x' ' ')
    adb -s "$S" shell input swipe $((W/2)) $((H*70/100)) $((W/2)) $((H*35/100)) 250
    sleep 1.2
  done
  return 1
}

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
    # ⚠ Le déclencheur se touche par son NOM depuis qu'il en a un
    # (`contentDescription`). Il était visé à 72 % de la hauteur physique — un
    # nombre qui ne veut rien dire dès qu'on change d'appareil, et qui tombait
    # dans la barre de navigation sur l'A5.
    tap_text "Déclencher" || exit 1
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
