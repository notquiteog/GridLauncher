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

# Keep a single cutout configuration enabled. Toggling it off immediately can race resource
# updates. Without --category, enable-exclusive also disables unrelated Pixel/framework overlays.
adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.hole

mkdir -p build/device-evidence
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
