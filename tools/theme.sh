#!/usr/bin/env bash
# Push a theme (a folder with config.json and optionally wallpaper.jpg) to the launcher on the TV and
# restart Home so it picks it up. The launcher reads the files from its external files directory and
# overlays config.json on its built-in config.xml; see themes/README.md for the keys.
#
# Usage: TV_HOST=192.168.1.50 tools/theme.sh themes/aurora
#        TV_HOST=192.168.1.50 tools/theme.sh --reset        # remove the overlay, back to config.xml
set -euo pipefail
# Resolve the theme folder against the caller's directory before moving into tools/.
THEME_ARG="${1:-}"
case "$THEME_ARG" in --reset|"") ;; /*) ;; *) THEME_ARG="$PWD/$THEME_ARG" ;; esac
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 themes/aurora}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.mkdziner.tvhome
DIR=/sdcard/Android/data/$PKG/files
# A unique marker lets the launcher clear on-TV layout/motion choices after every theme push,
# including a push of identical config.json bytes and --reset.
REVISION="$("$PY" -I -c 'import uuid; print(uuid.uuid4().hex)')"
tv() { "$PY" -I tv.py "$@"; }
# Restart Home so it rereads the theme. Android drops a force-stopped app's accessibility services from the
# enabled list, which would switch off InputKeyService/HomeKeyService, so put the list back if it had ours.
# Android removes them asynchronously while it handles the restart, so wait a second before writing the
# list back. Writing the same list back is not enough: the force-stop also turns accessibility_enabled off,
# and the service only binds again once the list changes. So restart first, then clear the list, write it
# back and switch accessibility on again as build-and-install does.
RESTART="a=\$(settings get secure enabled_accessibility_services); am force-stop $PKG; am start -n $PKG/.DashboardActivity >/dev/null; case \"\$a\" in *$PKG*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac"

if [ "${1:-}" = --reset ]; then
  tv "mkdir -p $DIR; rm -f $DIR/config.json $DIR/wallpaper.jpg; printf '%s' '$REVISION' > $DIR/theme_revision; $RESTART; echo reset" | tail -n 1
  exit 0
fi
THEME="${THEME_ARG:?usage: theme.sh <theme folder> | --reset}"
[ -f "$THEME/config.json" ] || { echo "no config.json in $THEME" >&2; exit 1; }
"$PY" -I -c 'import json,sys; json.load(open(sys.argv[1]))' "$THEME/config.json" 2>/dev/null \
  || { echo "$THEME/config.json is not valid JSON" >&2; exit 1; }

tv "mkdir -p $DIR" >/dev/null
tv --push "$THEME/config.json" "$DIR/config.json"
if [ -f "$THEME/wallpaper.jpg" ]; then
  "$PY" -I b64push.py "$THEME/wallpaper.jpg" "$DIR/wallpaper.jpg"
else
  tv "rm -f $DIR/wallpaper.jpg" >/dev/null
fi
# TMDB key for the browse screen; kept out of config.json because themes can be public.
if [ -f "$THEME/tmdb_key.txt" ]; then
  tv --push "$THEME/tmdb_key.txt" "$DIR/tmdb_key.txt"
fi
# Profile photo for the status pill; without one the pill shows initials.
if [ -f "$THEME/avatar.jpg" ]; then
  "$PY" -I b64push.py "$THEME/avatar.jpg" "$DIR/avatar.jpg"
else
  tv "rm -f $DIR/avatar.jpg" >/dev/null
fi
tv "printf '%s' '$REVISION' > $DIR/theme_revision" >/dev/null
tv "$RESTART; echo applied" | tail -n 1
echo "Theme $(basename "$THEME") is on the TV. Undo with: tools/theme.sh --reset"
