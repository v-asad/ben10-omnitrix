#!/usr/bin/env bash
# Wireless debugging keeps the watch's Wi-Fi awake (the biggest overnight drain in our
# diagnostics). Turn it off when you're done developing; turn it back on from the watch:
# Settings → Developer options → Wireless debugging.
#   tools/wireless_debugging.sh off | status
set -euo pipefail
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
SERIAL="${ANDROID_SERIAL:-$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
[[ -z "$SERIAL" ]] && { echo "No watch connected." >&2; exit 1; }
case "${1:-status}" in
  off)
    "$ADB" -s "$SERIAL" shell settings put global adb_wifi_enabled 0 || true
    echo "Wireless debugging turned off (this disconnects adb). Re-enable it on the watch when you need it."
    ;;
  status)
    echo "Wireless debugging: $([[ "$("$ADB" -s "$SERIAL" shell settings get global adb_wifi_enabled | tr -d '\r')" == 1 ]] && echo on || echo off)"
    ;;
  *) echo "usage: $0 off|status" >&2; exit 2 ;;
esac
