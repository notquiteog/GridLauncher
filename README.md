# GridLauncher for Android 17

A native Kotlin / Jetpack Compose home app inspired by Windows 10 Mobile, developed from [Tgo1014/GridLauncher](https://github.com/Tgo1014/GridLauncher)'s `develop` branch. The upstream license and attribution are preserved.

## Install

Download **GridLauncher-Android17.apk** from this fork's [Releases](https://github.com/notquiteog/GridLauncher/releases), or the APK artifact of a successful [Actions build](https://github.com/notquiteog/GridLauncher/actions/workflows/android.yml). Enable installation from your browser/file manager when Android asks. In the launcher, open **Customize Start → Set as default home app**.

- Android 8.0 or later; compile and target SDK **37 / Android 17**.
- Package: `io.github.notquiteog.gridlauncher`, separate from the original launcher.
- Official fork builds use a persistent private release key, allowing in-place updates. Development/PR builds use a development signing key and cannot update an official build.

## What works

- Start with small, medium and wide tiles, collision-free resizing, hold-and-drag rearrangement and directional movement. Hold and release a tile to edit or unpin it.
- Real notification counts and rotating previews on pinned app tiles, plus aggregated folder counts. Enable Android notification access in Customize Start. Text previews are opt-in, remain in memory, and respect Android's private/secret notification visibility; no notification data is uploaded or written to backups.
- Clock, upcoming calendar event, favorite contact names, and battery/charging tiles. Calendar and People request permission when opened and degrade gracefully when denied.
- Photo tiles rotate through up to 20 pictures explicitly selected with Android's photo picker. No full photo-library permission.
- Android widget hosting, including the system bind/configuration flow. Weather, music and other providers can supply real interactive content through their widgets.
- Named app folders, searchable alphabetical app list, tap a letter to jump, app info, uninstall and Android dynamic/manifest shortcuts when selected as the default launcher.
- Accent colors, light/dark backgrounds, square or rounded tiles, wallpaper transparency, optional labels and optional content rotation.
- JSON layout backup/restore through Android's document picker. Restore confirms replacement, validates and repacks tiles, refreshes installed app metadata, and skips missing apps. Widgets/photos must be added again, and wallpaper chosen again after restore.
- Edge-to-edge layout, system insets, Android home role selection, lifecycle-aware updates, and accessible text/actions.

This is a launcher, not a replacement Android OS. It does not replace the lock screen, Android quick settings, navigation/recents, or the phone's default messaging apps. Windows services such as Cortana and Continuum are not implemented. Android notification counts are active notifications, not server unread counts; apps and Android can redact sensitive content. Weather/music use installed Android widgets rather than fabricated data. Work profiles and pinned deep shortcuts are not yet supported.

## Build locally

Install JDK 17, then:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
scripts/setup-android.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

For release signing, set `SIGNING_KEYSTORE` to an absolute keystore path, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD`, then run `./gradlew assembleRelease`. Without those variables a development key is used; do not distribute that as an official release.

Toolchain: AGP 9.4.1, Gradle 9.6.0 (checksum verified), Kotlin 2.4.20, Java 17, Android SDK 37.0 and build tools 37.0.0. The legacy Kotlin/AGP DSL opt-outs remain for kapt/Hilt compatibility.

## CI and device testing

Every push, pull request and manual dispatch runs unit tests, Android lint, APK builds, signature verification, and Android 17 emulator UI tests. The workflow also installs and launches the release APK, and uploads SHA-256 checksums, test reports, emulator logs and a screenshot. Failed device tests prevent publication of the installable artifact.

The repository's Actions secrets hold `APK_SIGNING_KEYSTORE` (base64) and `APK_SIGNING_PASSWORD`. The release alias is `gridlauncher`. Do not rotate these unless you intend to break in-place updates. Forks need their own signing secrets for push builds; pull requests build with a development key and do not receive secrets.

Device tests cover home intent registration, app search/navigation, editing/persistence, live tile updates, and folder/settings UI. Unit tests cover collision-free placement across randomized moves/resizes, backup validation and migration, and notification replacement/removal.

```sh
scripts/setup-android.sh emulator
scripts/emulator-test.sh
```

The emulator script expects a previously built release APK and requires KVM on Linux. Hardware/OEM-specific behavior still needs testing on your phone.
