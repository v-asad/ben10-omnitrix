#!/usr/bin/env bash
# Build the Omnitrix app + watch face and install them on a watch connected over ADB.
#   tools/install_watch.sh            # build + install
#   tools/install_watch.sh --rebuild  # regenerate artwork from design/ first
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PY="${PY:-$HOME/.cache/omnitrix-venv/bin/python}"

if [[ "${1:-}" == "--rebuild" ]]; then
  "$PY" tools/build_watchface.py
fi

# Alien art (fast, Pillow only): approved art from art/aliens or hourglass fallbacks.
"$PY" tools/build_alien_art.py

(cd android && ./gradlew -q :watchface:assembleDebug :app:assembleRelease)
APKS=(android/app/build/outputs/apk/release/app-release.apk android/watchface/build/outputs/apk/debug/watchface-debug.apk)

# After pairing, the watch often shows up twice (manual IP + mDNS auto-connect),
# which makes plain `adb` refuse with "more than one device". Pick one.
SERIAL="${ANDROID_SERIAL:-$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
if [[ -z "$SERIAL" ]]; then
  echo "No watch connected. Pair it first (see README → Install on your watch)." >&2
  exit 1
fi
echo "Installing on $SERIAL ($("$ADB" -s "$SERIAL" shell getprop ro.product.model | tr -d '\r'))"
# App first: the face's alien slot binds to the app's complication provider.
for apk in "${APKS[@]}"; do "$ADB" -s "$SERIAL" install -r "$apk"; done
echo "Installed. Long-press the current face on the watch and pick “Omnitrix”."
