#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/grid/bin/avdmanager"
[[ -x "$AVDMANAGER" ]] || AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
"$AVDMANAGER" create avd -n grid-ci -k 'system-images;android-37.0;google_apis;x86_64' -d pixel_7 --force < /dev/null
mkdir -p build/device-evidence
emulator -avd grid-ci -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -memory 2048 -cores 2 > build/device-evidence/emulator.log 2>&1 &
emulator_pid=$!
trap 'adb logcat -d > build/device-evidence/logcat.txt || true; kill "$emulator_pid" || true' EXIT
for i in $(seq 1 180); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
  kill -0 "$emulator_pid" || { cat build/device-evidence/emulator.log; exit 1; }
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
./gradlew connectedDebugAndroidTest --stacktrace
# Also install and launch the actual downloadable signed APK.
# AGP can already have uninstalled the test target during test cleanup.
if [[ "$(adb shell pm path io.github.notquiteog.gridlauncher)" == package:* ]]; then
  adb uninstall io.github.notquiteog.gridlauncher
fi
adb install app/build/outputs/apk/release/app-release.apk
adb shell cmd package set-home-activity io.github.notquiteog.gridlauncher/tgo1014.gridlauncher.ui.MainActivity
adb shell am start -W -n io.github.notquiteog.gridlauncher/tgo1014.gridlauncher.ui.MainActivity
sleep 8
adb shell pidof io.github.notquiteog.gridlauncher
adb exec-out screencap -p > build/device-evidence/android-17-start.png
