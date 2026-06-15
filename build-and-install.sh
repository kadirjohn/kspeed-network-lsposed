#!/usr/bin/env bash
# Build the LSPosed module APK and install it on a connected device.
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"

cd "$(dirname "$0")"

echo ">> Building debug APK (JDK: $JAVA_HOME)"
./gradlew :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
echo ">> Built: $APK"

if ! command -v adb >/dev/null 2>&1; then
  echo "!! adb not found on PATH; install the APK manually."
  exit 0
fi

if [ -z "$(adb devices | sed '1d' | grep -w device || true)" ]; then
  echo "!! No device connected. Connect the Pixel (USB debugging on) and re-run, or:"
  echo "   adb install -r $APK"
  exit 0
fi

echo ">> Installing on device"
adb install -r "$APK"

echo
echo ">> Next steps on the phone:"
echo "   1. LSPosed Manager -> Modules -> enable 'Net Speed Indicator'"
echo "   2. Scope -> tick 'System UI'"
echo "   3. Restart SystemUI:  adb shell su -c 'killall com.android.systemui'"
echo "      (or reboot)"
