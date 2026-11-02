#!/bin/sh
# Regenerates docs/screenshots/*.jpg from a headless Pixel 9 Pro Fold emulator, never a real phone.
#   1. Create an AVD named "fold" (device pixel_9_pro_fold, android-36 Google APIs image) once.
#   2. Build: npm run apk   (or ./gradlew :app:assembleRelease)
#   3. Run:   sh Tools/screenshots.sh
# -read-only means nothing installed here sticks to the AVD. Taps are in the inner screen's 2076x2152 pixels.
# For the widgets shot, install Lumen (github.com/aaryann123/lumen) on the emulator first, or add any widget.
set -e
SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
SERIAL=emulator-5580
adb() { "$SDK/platform-tools/adb" -s $SERIAL "$@"; }
OUT=$(cd "$(dirname "$0")/.." && pwd)/docs/screenshots
TMP=$(mktemp -d)
mkdir -p "$OUT"

"$SDK/emulator/emulator" -avd fold -port 5580 -no-window -no-audio -read-only -no-snapshot -no-boot-anim >/dev/null 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done

DISPLAY_ID=$(adb shell dumpsys SurfaceFlinger --display-id | head -1 | awk '{print $2}')
snap() {
  sleep 2
  adb exec-out screencap -d "$DISPLAY_ID" -p > "$TMP/$1.png"
  sips -Z 1600 -s format jpeg -s formatOptions 85 "$TMP/$1.png" --out "$OUT/$1.jpg" >/dev/null
  echo "saved $1"
}

adb install -r "$(dirname "$0")/../app/build/outputs/apk/release/app-release.apk"
adb shell cmd role add-role-holder android.app.role.HOME app.fluid.launcher 0
adb shell appwidget grantbind --package app.fluid.launcher --user 0
adb shell input keyevent KEYCODE_HOME
snap home
adb shell input swipe 1000 1700 1000 500 250          # swipe up: apps
snap drawer
adb shell input keyevent KEYCODE_HOME
adb shell input swipe 500 1300 1800 1320 600          # swipe right: widgets
snap widgets
adb shell am start -n app.fluid.launcher/.SettingsActivity
snap settings
adb emu kill
