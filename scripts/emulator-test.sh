#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/grid/bin/avdmanager"
[[ -x "$AVDMANAGER" ]] || AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
"$AVDMANAGER" create avd -n grid-ci -k 'system-images;android-37.0;google_apis;x86_64' -d pixel_7 --force < /dev/null
mkdir -p build/device-evidence build/device-evidence/screenshots
emulator -avd grid-ci -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -memory 2048 -cores 2 > build/device-evidence/emulator.log 2>&1 &
emulator_pid=$!
trap 'adb logcat -d > build/device-evidence/logcat.txt || true; adb pull /data/local/tmp/grid-screenshots/. build/device-evidence/screenshots > /dev/null 2>&1 || true; kill "$emulator_pid" || true' EXIT
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
# The clock tile formats itself from this device setting, so it is part of every screenshot baseline.
adb shell settings put system time_12_24 24
# The system bars are 24dp and whether they are inset or drawn under shifts the whole frame, so a
# scene captured with them hidden is a different picture from one captured with them shown. Whatever
# left them hidden on a previous run must not decide this one.
adb shell settings put global policy_control null
# Shell-owned, because the app under test is uninstalled - and its storage deleted - when a run ends.
# A display cutout is 24dp of extra inset at the top of the frame, and whether one is emulated is a
# property of the emulator image rather than of anything being tested. Two machines on the same image
# build disagreed by exactly that band. Pin it off.
for cutout in corner double hole tall emu01 waterfall; do
  adb shell cmd overlay disable "com.android.internal.display.cutout.emulation.$cutout" || true
done
adb shell mkdir -p /data/local/tmp/grid-screenshots
adb shell rm -f /data/local/tmp/grid-screenshots/*.png
# The expanded scenes cannot run at phone width, and a JUnit assumption is reported as a failure by
# the test runner rather than as a skip, so they are filtered out here and run deliberately below.
./gradlew connectedDebugAndroidTest --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.notClass=tgo1014.gridlauncher.ExpandedScreenshotTest
# AGP can report a successful task even when APK installation prevented any tests.
# The expanded scenes skip themselves at phone width; they are run and checked further down.
python3 scripts/verify-device-tests.py --exclude-class tgo1014.gridlauncher.ExpandedScreenshotTest
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

# The expanded two-pane scenes, on the same emulator and the same boot. A wider logical display
# reaches the 840dp layout for the cost of a resize; a second foldable AVD would cost another boot
# and another few minutes to photograph the same thing.
adb shell wm size 2208x1840
adb shell wm density 420
sleep 5
# The release build is signed with the release key and the test APK with the debug key, so the two
# cannot be installed one over the other.
adb uninstall io.github.notquiteog.gridlauncher || true
./gradlew connectedDebugAndroidTest --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.class=tgo1014.gridlauncher.ExpandedScreenshotTest
python3 scripts/verify-device-tests.py --class tgo1014.gridlauncher.ExpandedScreenshotTest
adb shell wm size reset
adb shell wm density reset

# Pulled before the emulator dies, so a visual regression arrives with its own picture attached.
adb pull /data/local/tmp/grid-screenshots/. build/device-evidence/screenshots