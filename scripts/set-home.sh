#!/bin/sh
# Make CleanFrame the default Home app on an Android TV.
#
#   scripts/set-home.sh <tv-ip>[:port]                    set CleanFrame as default Home
#   scripts/set-home.sh <tv-ip>[:port] --disable-others   ...and disable every other launcher
#   scripts/set-home.sh <tv-ip>[:port] --restore          re-enable launchers this script disabled
#
# Launchers are disabled (pm disable-user), never uninstalled. The list is saved in
# .local/disabled-launchers-<ip>.txt so --restore can undo it.
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TARGET=${1:?usage: scripts/set-home.sh <tv-ip>[:port] [--disable-others|--restore]}
MODE=${2:-}
case "$TARGET" in *:*) ;; *) TARGET="$TARGET:5555" ;; esac
SELF=com.cleanframe.androidtv
STATE="$ROOT/.local/disabled-launchers-${TARGET%%:*}.txt"
sh_() { adb -s "$TARGET" shell "$@" 2>/dev/null | tr -d '\r' | grep -v -E 'mutex|mma dev' || true; }

adb connect "$TARGET" >/dev/null

if [ "$MODE" = "--restore" ]; then
  [ -f "$STATE" ] || { echo "Nothing to restore ($STATE not found)."; exit 0; }
  while read -r pkg; do [ -n "$pkg" ] && sh_ pm enable "$pkg"; done < "$STATE"
  rm "$STATE"
  echo "Re-enabled launchers. Press Home on the TV to choose a default again."
  exit 0
fi

sh_ pm path "$SELF" | grep -q package: || { echo "CleanFrame is not installed. Run scripts/install.sh first."; exit 1; }
sh_ cmd package set-home-activity "$SELF/.MainActivity"
echo "Default Home: $(sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)"

# Real launchers only: skip CleanFrame and the system's boot-time fallback screens.
OTHERS=$(sh_ cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
  | grep '/' | sed 's|/.*||; s/^ *//' | sort -u \
  | grep -v -x -e "$SELF" -e com.android.tv.settings -e com.android.settings -e com.android.boot.fallbackhome || true)

if [ -z "$OTHERS" ]; then echo "No other launchers found."; exit 0; fi
echo "Other launchers:"; echo "$OTHERS" | sed 's/^/  /'
if [ "$MODE" = "--disable-others" ]; then
  mkdir -p "$(dirname "$STATE")"
  for pkg in $OTHERS; do sh_ pm disable-user --user 0 "$pkg" && echo "$pkg" >> "$STATE"; done
  echo "Disabled. Undo with: scripts/set-home.sh $TARGET --restore"
else
  echo "Google TV devices may still open their own launcher on Home; rerun with --disable-others to switch it off."
fi
