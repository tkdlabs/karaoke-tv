#!/usr/bin/env bash
set -euo pipefail

# Build and install the debug APK on an Android TV device.
# Usage: ./scripts/deploy.sh <TV_IP>

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <TV_IP>" >&2
  exit 1
fi

TV_IP="$1"

./gradlew assembleDebug
adb connect "${TV_IP}:5555"
adb -s "${TV_IP}:5555" install -r app/build/outputs/apk/debug/app-debug.apk
