#!/usr/bin/env bash
# Build the Omnitrix watch face and install it on a watch connected over ADB.
#   tools/install_watch.sh            # build + install
#   tools/install_watch.sh --rebuild  # regenerate artwork from design/ first
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PY="${PY:-$HOME/.cache/omnitrix-venv/bin/python}"

if [[ "${1:-}" == "--rebuild" ]]; then
  "$PY" tools/build_watchface.py
fi

(cd android && ./gradlew -q :watchface:assembleDebug)
APK=android/watchface/build/outputs/apk/debug/watchface-debug.apk

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No watch connected. Pair it first (see README → Install on your watch)." >&2
  exit 1
fi
"$ADB" install -r "$APK"
echo "Installed. Long-press the current face on the watch and pick “Omnitrix”."
