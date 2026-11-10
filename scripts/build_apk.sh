#!/usr/bin/env sh
# Build the Android APK.
#   ./scripts/build_apk.sh debug
#   ./scripts/build_apk.sh release https://dealmate.example.com/
# Release signing reads android/keystore.properties or DEALMATE_KEYSTORE_* env vars (see android/README.md).
set -eu
cd "$(dirname "$0")/../android"
variant="${1:-debug}"
base_url="${2:-}"
args=""
[ -n "$base_url" ] && args="-Pdealmate.baseUrl=$base_url"
if [ "$variant" = "release" ]; then
  ./gradlew :app:assembleRelease $args
  find app/build/outputs/apk/release -name '*.apk'
else
  ./gradlew :app:assembleDebug $args
  find app/build/outputs/apk/debug -name '*.apk'
fi
