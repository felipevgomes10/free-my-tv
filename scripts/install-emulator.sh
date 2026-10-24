#!/usr/bin/env bash
# Builds the Home and installs it on an Android emulator.
#
# It refuses any device that is not an emulator: the real TV is only ever changed
# by Setup, once all of Phase 1 is ready (ticket 14).
#
# Usage: scripts/install-emulator.sh [emulator-serial]
set -euo pipefail

cd "$(dirname "$0")/.."

serial="${1:-${ANDROID_SERIAL:-}}"
if [[ -z "$serial" ]]; then
  serial="$(adb devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')"
fi

if [[ -z "$serial" ]]; then
  echo "No running emulator found. Start one, or pass its serial (emulator-NNNN)." >&2
  exit 1
fi

if [[ "$serial" != emulator-* ]] || [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "Refusing to install on '$serial': it is not an emulator." >&2
  exit 1
fi

# The build needs JDK 17 (Gradle 8.11 can't run on newer ones), whatever JAVA_HOME the shell has.
if command -v brew >/dev/null && [[ -d "$(brew --prefix openjdk@17 2>/dev/null)" ]]; then
  export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi

./gradlew --quiet :app:assembleDebug
# -d: an emulator that tried a self-update may hold a higher version than this checkout.
adb -s "$serial" install -r -d app/build/outputs/apk/debug/app-debug.apk
echo "Installed on $serial."
