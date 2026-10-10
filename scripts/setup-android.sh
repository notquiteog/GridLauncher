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
  "$SDKMANAGER" 'emulator' 'system-images;android-37.0;google_apis;x86_64'
fi
