#!/bin/bash
# Offline map for nDashMo: Sumedang + greater Bandung (user, 2026-10-30), about 55 MB. Elsewhere the map uses the network.
#
# Makes data/sumedang-bandung.mbtiles from OpenFreeMap's own tiles for the area, zoom 0-14 (fetch.py, about 2,500 tiles,
# a few minutes): the same OpenMapTiles data the app loads online, so the map style works unchanged. (Building it from
# OpenStreetMap with planetiler was tried first: 2+ GB of downloads, hours on a slow connection.)
#
# Usage:  offline-map/build.sh            make data/sumedang-bandung.mbtiles
#         offline-map/build.sh install    then copy it to the tablet (uses $ADB_SERIAL if set)
# Needs Python 3. Run again every few months for new roads.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DATA="$HERE/data"
OUT="$DATA/sumedang-bandung.mbtiles"
mkdir -p "$DATA"

python3 "$HERE/fetch.py" "$OUT.part" && mv "$OUT.part" "$OUT"
ls -lh "$OUT"

if [ "${1:-}" = "install" ]; then
  # The app reads it from its own files folder (OfflineMap.java serves /tiles/ from it).
  # Copied under another name, then renamed in one step: the app never sees a half-copied file.
  DEST=/sdcard/Android/data/me.faizal.carhome/files
  adb ${ADB_SERIAL:+-s "$ADB_SERIAL"} push "$OUT" "$DEST/sumedang-bandung.mbtiles.part"
  adb ${ADB_SERIAL:+-s "$ADB_SERIAL"} shell mv "$DEST/sumedang-bandung.mbtiles.part" "$DEST/sumedang-bandung.mbtiles"
fi
