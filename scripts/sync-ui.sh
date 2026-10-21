#!/bin/sh
# Download the upstream CleanFrame UI and adapt it into the Android app's assets.
# Usage: scripts/sync-ui.sh [git-ref]   (default: the commit pinned in UPSTREAM_REF)
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
REF=${1:-$(cat "$ROOT/UPSTREAM_REF")}
DEST="$ROOT/android/app/src/main/assets/home"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

curl -fsSL "https://codeload.github.com/nthnclrk/CleanFrame/tar.gz/$REF" -o "$TMP/cf.tgz"
tar xzf "$TMP/cf.tgz" -C "$TMP"
SRC=$(find "$TMP" -maxdepth 2 -type d -name app | head -n 1)

rm -rf "$DEST"
mkdir -p "$(dirname "$DEST")"
cp -R "$SRC" "$DEST"
cp "$(dirname "$SRC")/LICENSE" "$DEST/CLEANFRAME-LICENSE.txt"
cp "$(dirname "$SRC")/THIRD_PARTY_NOTICES.md" "$DEST/THIRD_PARTY_NOTICES.md"
python3 -I "$ROOT/scripts/patch_ui.py" "$DEST" "$ROOT/web/device.js"
