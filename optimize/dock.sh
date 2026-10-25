#!/usr/bin/env bash
# The home screen's favourites row, for agents and scripts: no remote, no long-press.
#   optimize/dock.sh show                  the current order, left to right
#   optimize/dock.sh apps                  every app that can go in it: package | name
#   optimize/dock.sh set PKG1 PKG2 ...     a new order, left to right (up to 10; fewer than 5 is topped up)
#   optimize/dock.sh reset                 back to the defaults in android/launcher/.../config.xml
#   optimize/dock.sh hidden                apps left off the home screen, and why: package | reason
#   optimize/dock.sh hide PKG ...          take these off the home screen (all its lists)
#   optimize/dock.sh show PKG ...          put these on it, even against the rules (the TV's own home doesn't list them)
#   optimize/dock.sh webapp add URL [NAME] a website as an app (optional): its manifest's name, icon and colours,
#                                          full screen with a splash; first in the favourites (set keeps it there)
#   optimize/dock.sh webapp remove web:ID  take it off again;  optimize/dock.sh webapp list
#   optimize/dock.sh webapp desktop web:ID on|off   open it as on a computer (the site's desktop version), or not
#   optimize/dock.sh profile NAME [PHOTO]  the owner (optional): «صباح الخير، يا NAME», and the photo (jpg/png on
#                                          this computer) round at the top right;  optimize/dock.sh profile clear
# The home screen redraws at once. Only ADB can do this (sa.urtv.home/.DockReceiver needs the shell's DUMP).
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
send() {   # $1 action, $2 extra arguments; prints the receiver's answer (the "data" of am broadcast)
  "$PY" -I optimize/tv.py "am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.$1 ${2:-}" \
    | sed -n '/data="/,$p' | sed '1s/.*data="//; $s/"$//'
}
case "${1:-}:$#" in
  show:1) send GET_DOCK ;;
  apps:*)  send LIST_APPS ;;
  reset:*) send RESET_DOCK ;;
  hidden:*) send HIDDEN ;;
  hide:*|show:*)
    what=$(tr '[:lower:]' '[:upper:]' <<< "$1")
    shift
    [ $# -gt 0 ] || { echo "usage: optimize/dock.sh hide|show PKG ..."; exit 1; }
    for p in "$@"; do [[ "$p" =~ ^[A-Za-z0-9_.]+$ ]] || { echo "not a package name: $p"; exit 1; }; done
    send "$what" "--es apps $(IFS=,; echo "$*")" ;;
  set:*)
    shift
    [ $# -gt 0 ] || { echo "usage: optimize/dock.sh set PKG1 PKG2 ..."; exit 1; }
    for p in "$@"; do [[ "$p" =~ ^[A-Za-z0-9_.]+$ ]] || { echo "not a package name: $p"; exit 1; }; done
    send SET_DOCK "--es apps $(IFS=,; echo "$*")" ;;
  webapp:*)
    case "${2:-}" in
      add)
        [[ "${3:-}" =~ ^https?://[^\ \']+$ ]] || { echo "usage: optimize/dock.sh webapp add https://… [NAME]"; exit 1; }
        extra="--es url '$3'"
        if [ $# -ge 4 ]; then
          case "$4" in *"'"*) echo "no apostrophes in the name please"; exit 1 ;; esac
          extra="$extra --es name '$4'"
        fi
        send WEBAPP_ADD "$extra" ;;
      remove)
        [[ "${3:-}" =~ ^web:[A-Za-z0-9-]+$ ]] || { echo "usage: optimize/dock.sh webapp remove web:ID (webapp list shows it)"; exit 1; }
        send WEBAPP_REMOVE "--es id $3" ;;
      list) send WEBAPPS ;;
      desktop)
        [[ "${3:-}" =~ ^web:[A-Za-z0-9-]+$ && "${4:-}" =~ ^(on|off)$ ]] \
          || { echo "usage: optimize/dock.sh webapp desktop web:ID on|off"; exit 1; }
        send WEBAPP_DESKTOP "--es id $3 --es on $([ "$4" = on ] && echo true || echo false)" ;;
      *) echo "usage: optimize/dock.sh webapp add URL [NAME] | remove web:ID | desktop web:ID on|off | list"; exit 1 ;;
    esac ;;
  profile:*)
    if [ "${2:-}" = clear ]; then send PROFILE_CLEAR; exit 0; fi
    [ -n "${2:-}" ] || { echo "usage: optimize/dock.sh profile NAME [PHOTO] | clear"; exit 1; }
    case "$2" in *"'"*) echo "no apostrophes in the name please"; exit 1 ;; esac
    if [ -n "${3:-}" ]; then
      [ -f "$3" ] || { echo "!! no such photo: $3"; exit 1; }
      # the shell hands the file to the home screen app (only the shell may write its photo)
      "$PY" -I optimize/tv.py --push-b64 "$3" /data/local/tmp/urtv-photo
      "$PY" -I optimize/tv.py "content write --uri content://sa.urtv.home.profile/photo < /data/local/tmp/urtv-photo; rm -f /data/local/tmp/urtv-photo"
    fi
    send PROFILE "--es name '$2'" ;;
  *) sed -n '2,16p' "$0"; exit 1 ;;
esac
