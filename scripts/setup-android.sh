#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
TOOLS=16111833
if [[ ! -x "$ANDROID_HOME/cmdline-tools/grid/bin/sdkmanager" ]]; then
  archive=$(mktemp)
  curl --fail --location --retry 3 "https://dl.google.com/android/repository/commandlinetools-linux-${TOOLS}_latest.zip" -o "$archive"
  echo "0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8  $archive" | sha256sum --check
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  staging=$(mktemp -d)
  unzip -q "$archive" -d "$staging"
  mv "$staging/cmdline-tools" "$ANDROID_HOME/cmdline-tools/grid"
  rm -rf "$staging" "$archive"
fi
SDKMANAGER="$ANDROID_HOME/cmdline-tools/grid/bin/sdkmanager"
# yes receives SIGPIPE when the SDK manager closes stdin; check sdkmanager itself.
set +o pipefail
yes | "$SDKMANAGER" --licenses >/dev/null
status=${PIPESTATUS[1]}
set -o pipefail
[[ "$status" == 0 ]]
"$SDKMANAGER" 'platforms;android-37.0' 'build-tools;37.0.0' 'platform-tools'
if [[ "${1:-}" == emulator ]]; then
  # The emulator is the renderer behind every screenshot baseline, so it is pinned - and it can only
  # be pinned this way: no sdkmanager accepts a versioned package path for it, so an unpinned
  # install silently tracks whatever is current and the runner drifted from 37.1.11 to 37.2.12
  # under us without any commit changing. The image is left to sdkmanager because revision 6 is the
  # only revision there is; the emulator moves far more often than that.
  EMULATOR_BUILD=16428233
  if ! "$ANDROID_HOME/emulator/emulator" -version 2>/dev/null | head -1 | grep -q "37.2.12"; then
    archive=$(mktemp)
    curl --fail --location --retry 3 "https://dl.google.com/android/repository/emulator-linux_x64-${EMULATOR_BUILD}.zip" -o "$archive"
    echo "b08fc43d8608d2955f607f1b287beb525041e730086bdca3af152accac3af9c1  $archive" | sha256sum --check
    rm -rf "$ANDROID_HOME/emulator"
    unzip -q "$archive" -d "$ANDROID_HOME"
    rm -f "$archive"
  fi
  "$SDKMANAGER" 'system-images;android-37.0;google_apis;x86_64'
fi
