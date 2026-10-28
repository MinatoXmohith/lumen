#!/usr/bin/env bash
# Push a mood-wallpaper folder to the launcher: wallpapers.json plus the static images, live videos and thumbnails it
# lists. The launcher shows them under Launcher settings → Wallpapers (Static and Live). Keep videos at 1080p H.264.
#
# Usage: TV_HOST=<tv ip> tools/wallpapers.sh ~/Desktop/TV-Demo/wallpapers/tv
#        TV_HOST=<tv ip> tools/wallpapers.sh --reset      # remove them, back to the built-in set
set -euo pipefail
SRC="${1:?usage: wallpapers.sh <folder with wallpapers.json> | --reset}"
cd "$(dirname "$0")"
PY=venv/bin/python
PKG=com.mkdziner.tvhome
DIR=/sdcard/Android/data/$PKG/files/wallpapers
tv() { "$PY" -I tv.py "$@"; }
if [ "$SRC" = --reset ]; then
  tv "rm -rf $DIR; echo removed"
  exit 0
fi
[ -f "$SRC/wallpapers.json" ] || { echo "no wallpapers.json in $SRC" >&2; exit 1; }
"$PY" -I -c 'import json,sys; json.load(open(sys.argv[1]))' "$SRC/wallpapers.json"
tv "rm -rf $DIR; mkdir -p $DIR/static $DIR/live" >/dev/null
# every file the manifest names, then the manifest last (the launcher only lists files that exist)
"$PY" -I -c '
import json, sys
m = json.load(open(sys.argv[1]))
for group in ("static", "live"):
    for e in m.get(group, []):
        for k in ("file", "thumb"):
            if e.get(k): print(e[k])
' "$SRC/wallpapers.json" | while IFS= read -r rel; do
  echo "→ $rel"
  "$PY" -I b64push.py "$SRC/$rel" "$DIR/$rel" | tail -n 1
done
tv --push "$SRC/wallpapers.json" "$DIR/wallpapers.json"
echo "Wallpapers are on the TV. Pick one in Launcher settings → Wallpapers."
