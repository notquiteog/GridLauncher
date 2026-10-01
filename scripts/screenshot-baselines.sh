#!/usr/bin/env bash
# Records or verifies the launcher's screenshot baselines on an emulator that matches CI exactly.
#
# Compact postures only. The two-pane layout reached with `wm size` renders text a shade differently
# from one boot to the next, so a baseline of it would fail for reasons that have nothing to do with
# the launcher; `WindowPostureTest` covers that layout's arithmetic instead.
#
# The device profile, the renderer and the display settings are all part of a baseline. Change any of
# them and every image is a different image, so this script pins the same AVD, the same swiftshader
# flags, the same clock format and the same display sizes the workflow uses. CI already compares
# baselines as part of the ordinary test run; this exists to *produce* them, and to reproduce CI's
# comparison locally when you want to see a scene for yourself.
#
#   scripts/screenshot-baselines.sh record    # write baselines into src/androidTest/assets
#   scripts/screenshot-baselines.sh verify    # compare against them, in both postures
set -euo pipefail
: "${ANDROID_HOME:?}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
MODE="${1:-record}"
AVD=grid-shots
DEVICE_ROOT="$PWD/app/src/androidTest/assets/screenshots"
EVIDENCE="$PWD/build/device-evidence/screenshots"
# Shell-owned, because the app under test is uninstalled - and its storage deleted - when a run ends.
REMOTE=/data/local/tmp/grid-screenshots
AVDMANAGER="$ANDROID_HOME/cmdline-tools/grid/bin/avdmanager"
[[ -x "$AVDMANAGER" ]] || AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"

adb kill-server >/dev/null 2>&1 || true
"$AVDMANAGER" create avd -n "$AVD" -k 'system-images;android-37.0;google_apis;x86_64' -d pixel_7 --force < /dev/null

rm -rf "$EVIDENCE"
mkdir -p build/device-evidence "$EVIDENCE"
emulator -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader \
  -memory 2048 -cores 2 > build/device-evidence/screenshot-emulator.log 2>&1 &
emulator_pid=$!
trap 'adb logcat -d > build/device-evidence/screenshot-logcat.txt || true; kill "$emulator_pid" || true' EXIT

for _ in $(seq 1 180); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
  kill -0 "$emulator_pid" || { cat build/device-evidence/screenshot-emulator.log; exit 1; }
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] || { cat build/device-evidence/screenshot-emulator.log; exit 1; }

adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# The clock tile formats itself from this device setting. Left alone it differs between images and
# between machines, which is exactly the sort of invisible input that quietly invalidates a baseline.
adb shell settings put system time_12_24 24
# The system bars are 24dp and whether they are inset or drawn under shifts the whole frame, so a
# scene captured with them hidden is a different picture from one captured with them shown. Whatever
# left them hidden on a previous run must not decide this one.
adb shell settings put global policy_control null
# Created here rather than from the test: the app's shell can copy *into* this directory but the
# instrumentation runner will not chain a mkdir in front of the copy.
# A display cutout is 24dp of extra inset at the top of every frame, and whether one is emulated
# comes from the emulator's device definition rather than from anything being tested. Two machines on
# the same image build disagreed by exactly that band, which is twenty per cent of the pixels changed.
#
# It has to be a toggle, not a disable. Disabling an overlay that is already off leaves the display
# holding its previous configuration, so the cutout survives; enabling and then disabling forces the
# display to re-apply its config and land on "no cutout". The wait afterwards is the same point in
# time: the reconfiguration is not instant.
CUTOUT=com.android.internal.display.cutout.emulation.hole
adb shell cmd overlay enable-exclusive "$CUTOUT" >/dev/null 2>&1 || true
adb shell cmd overlay disable "$CUTOUT" >/dev/null 2>&1 || true
cutout_clear() {
  adb shell dumpsys window displays 2>/dev/null | grep -m1 "mDisplayCutout=" | grep -q "insets=Rect(0, 0 - 0, 0)"
}
for _ in $(seq 1 20); do
  cutout_clear && break
  sleep 1
done
cutout_clear || { echo "a display cutout is still emulated; every screenshot would be 24dp taller" >&2; exit 1; }
adb shell mkdir -p "$REMOTE"
adb shell rm -f "$REMOTE"/*.png

record_flag=()
[[ "$MODE" == record ]] && record_flag=(-PscreenshotBaselines=update)

./gradlew connectedDebugAndroidTest --offline "${record_flag[@]}" \
  -Pandroid.testInstrumentationRunnerArguments.class=tgo1014.gridlauncher.ScreenshotTest
python3 scripts/verify-device-tests.py --class tgo1014.gridlauncher.ScreenshotTest

# "$REMOTE/." copies the contents in rather than nesting a directory of the same name.
adb pull "$REMOTE/." "$EVIDENCE" > /dev/null

if [[ "$MODE" == record ]]; then
  copied=0
  for scene in "$EVIDENCE"/*.png; do
    [[ -e "$scene" ]] || { echo "no captures came back" >&2; exit 1; }
    case "$(basename "$scene")" in
      *.actual.png|*.diff.png|wallpaper-*) continue ;;
    esac
    cp "$scene" "$DEVICE_ROOT/"
    copied=$((copied + 1))
  done
  echo "Recorded $copied baselines into app/src/androidTest/assets/screenshots."
  echo "Look at them before committing. A baseline nobody reviewed is not evidence."
else
  failed=0
  for scene in "$EVIDENCE"/*.actual.png; do
    [[ -e "$scene" ]] || continue
    failed=$((failed + 1))
    echo "CHANGED: $(basename "$scene" .actual.png) - see $(basename "$scene" .actual.png).diff.png"
  done
  if [[ "$failed" == 0 ]]; then echo "All scenes matched their baselines."; else exit 1; fi
fi