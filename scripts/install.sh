#!/bin/sh
# Install CleanFrame on an Android TV over the local network.
# Usage: scripts/install.sh <tv-ip>[:port]      (port defaults to 5555)
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APK="$ROOT/android/app/build/outputs/apk/release/app-release.apk"
TARGET=${1:?usage: scripts/install.sh <tv-ip>[:port]}
case "$TARGET" in *:*) ;; *) TARGET="$TARGET:5555" ;; esac

[ -f "$APK" ] || { echo "Build first: (cd android && ./gradlew assembleRelease)"; exit 1; }
adb connect "$TARGET"
echo "If the TV shows 'Allow USB debugging?', choose Always allow, then press Enter here."
read _
adb -s "$TARGET" wait-for-device
adb -s "$TARGET" install -r "$APK"
adb -s "$TARGET" shell am start -n com.cleanframe.androidtv/.MainActivity
