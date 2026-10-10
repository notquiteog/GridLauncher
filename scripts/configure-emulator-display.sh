#!/usr/bin/env bash
# Shared display contract for screenshot recording and the full device suite.
set -euo pipefail

adb shell wm size 1080x2400
adb shell wm density 420
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put system time_12_24 24
adb shell settings put global policy_control null

# Keep a single cutout configuration enabled. Without --category, enable-exclusive also disables
# unrelated Pixel/framework overlays.
adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.hole

mkdir -p build/device-evidence

# The overlay applies asynchronously, so wait for the display to actually carry the cutout before
# forcing the relayout below. Relaying out against a cutout that has not landed yet is precisely
# the bug this script exists to prevent.
for attempt in $(seq 1 30); do
  adb shell dumpsys window displays > build/device-evidence/display.txt
  # A fixed-string grep over a file: no adb pipeline, so no SIGPIPE under pipefail.
  grep -Fq "mDisplayCutout=DisplayCutout{insets=Rect(0, 136 - 0, 0)" build/device-evidence/display.txt && break
  sleep 1
done
grep -Fq "mDisplayCutout=DisplayCutout{insets=Rect(0, 136 - 0, 0)" build/device-evidence/display.txt \
  || { echo 'The cutout overlay never applied; see build/device-evidence/display.txt.' >&2; exit 1; }

# SystemUI sizes the status bar once, against the display it booted with. Enable the cutout after
# boot and the cutout appears - the inset sources say so - while the status bar window never grows
# to cover it. Measured on this image: a 63px bar at boot, 74px with a cutout that arrived late,
# 136px only once laid out over it. A wm size change is a configuration change, so this forces
# SystemUI to relay out against the cutout that is now present, and the bar covers it the way the
# same image does on a device that boots with one. The size is restored immediately after; the
# gate below verifies the result.
adb shell wm size 1081x2400
sleep 2
adb shell wm size 1080x2400

for attempt in $(seq 1 30); do
  adb shell dumpsys window displays > build/device-evidence/display.txt
  # Read the complete dump before checking it: grep -m/-q in an adb pipeline can make a
  # successful match fail under pipefail when the producer receives SIGPIPE.
  if python3 - <<'PY'
from pathlib import Path
import re
display = Path('build/device-evidence/display.txt').read_text()
cutout = re.search(r'mDisplayCutout=DisplayCutout\{insets=Rect\(([^)]+)\)', display)
bars = re.search(r'type=statusBars frame=\[0,0\]\[1080,(\d+)\]', display)
raise SystemExit(0 if cutout and cutout[1] == '0, 136 - 0, 0' and bars and bars[1] == '136' else 1)
PY
  then
    echo 'Display ready: 1080x2400, 420dpi, cutout/status bar 136px.'
    exit 0
  fi
  sleep 1
done
echo 'Display did not reach the screenshot configuration; see build/device-evidence/display.txt.' >&2
exit 1