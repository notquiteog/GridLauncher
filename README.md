# GridLauncher for Android 17

A native Kotlin / Jetpack Compose launcher combining Windows Phone's information-first Start screen with flat icon-colored tiles and glass launcher controls. Based on [Tgo1014/GridLauncher](https://github.com/Tgo1014/GridLauncher)'s `develop` branch; the Apache license and attribution are preserved.

## Install

Download **GridLauncher-Android17.apk** from [Releases](https://github.com/notquiteog/GridLauncher/releases), or the APK artifact of a successful [Actions build](https://github.com/notquiteog/GridLauncher/actions/workflows/android.yml). In **Customize Start**, select **Set as default home app**. Android requires the home role to expose app shortcuts.

- Android 8.0 or later; compile/target SDK **37 / Android 17**.
- Package: `io.github.notquiteog.gridlauncher`.
- Official releases use a persistent private signing key and support in-place updates. Development/PR builds have a different key.

## GitHub updates

GitHub/sideload builds check the latest stable release when the launcher resumes, at most once per day. Failed checks retry no more than hourly. Customize Start includes **Check for updates** and an automatic-check toggle. Downloads are requested explicitly, bounded in size, checked against the release SHA-256, and verified for the exact package, newer version and same signing certificate. Android's normal unknown-source permission and installation confirmation remain in control. Tiles/settings survive the update.

The updater checks Android's installing/initiating package and update owner; Play installs never use the GitHub updater. Android does not reliably retain the original download website for sideloads, so the GitHub distribution includes other sideloads too. A Play build uses `-PdistributionChannel=play`, which disables the updater and removes its package-install permission from the manifest. Debug builds check only on manual request. CI publishes `update.json` from the actual signed APK alongside the APK and checksum, completing the draft release before making it visible.

## Start and editing

- Compact top bar with the date, Edit layout and settings in one row.

- Square, edge-to-edge flat tiles with no gaps, gradients, blur or borders. Each app tile uses the dominant opaque outer-edge color of its default icon; transparent margins are ignored. Labels switch between black and white for contrast. Built-in tiles use the accent color; photo/widget content retains its own appearance. Choose **2–6 cells across**, default **3**. All positions and sizes use whole cells; existing 2.0 layouts migrate automatically.
- Large, unmodified Android app icons, including their original adaptive backgrounds. No replacement icon packs or foreground-only recoloring.
- **Long press** an app tile for its Android-published dynamic/manifest shortcuts and app info. These are actual app actions, not a layout-edit gesture. Apps decide which actions they provide.
- Tap **Edit layout**, then a tile, to resize, move, remove or **Pin position**. Tap **Done** to leave editing. Movement uses whole-cell directions. Pinned positions are fixed anchors; removing a tile packs unpinned tiles into free spaces. Narrowing the grid refuses to displace a pinned tile that would no longer fit.
- Tile/widget dimensions include **1×1, 1×2, 2×1, 2×2**, and larger whole-cell rectangles that fit the selected width. An Android widget's own content may need a larger size to be useful.
- Personal, Work and Travel have separate persistent arrangements. Copy a layout with confirmation, or enable a weekday Work schedule with chosen hours (including overnight shifts). Manual selection disables scheduling. These are launcher layouts, not Android managed work profiles.
- Spring movement/resizing and press feedback, perspective live-tile updates, animated Now expansion, native sheets and panoramic Start/app-list paging. Reduce motion, system animation settings and battery saver suppress decorative motion; battery saver also uses solid surfaces.
- Frosted, Clear and Solid materials for surrounding controls, wallpaper-derived tint, accent colors, light/dark themes, optional labels, and one-handed spacing. Dark app-list text is explicitly white.

## Live information and actions

Enable **Manage notification access** for counts. Turn on **Show notification previews** to display content, and use per-app preview controls to exclude individual apps.

- Tiles show real notification titles, message text, artwork, counts, progress and available actions. Tap a count to expand the notification preview. MessagingStyle conversations include recent messages and sender identity. Direct reply is available only when the app publishes a RemoteInput action.
- **Now** surfaces ongoing media, progress, navigation and timer notifications without rearranging pinned tiles. Finished/dismissed notifications disappear. Android 17 semantic annotations supply caution/urgent/safe labels and colors when the source app provides them.
- Folder tiles aggregate counts and expand inline into live app tiles.
- Clock, calendar, favorite People mosaics and battery/charging tiles. Calendar/favorites use optional read permissions; denial does not prevent using the launcher.
- **Choose people** uses Android 17's contact picker (a phone picker fallback on older Android). Only selected names, numbers and emails are stored locally. Pin one person or a group; call/message/email through the appropriate Android app. Contact pictures appear for favorite contacts when contact access is granted; otherwise tiles show initials. Notification matching uses app-supplied person URIs, so it cannot reliably link every app's conversations.
- Photo tiles rotate up to 20 photo-picker selections. Native Android widgets use the normal bind/configure flow.
- Pin app destinations from **All apps → long press → Pin: shortcut**, or accept an app's Android pin request. Websites, geo links and documents can also be pinned in Customize Start.

Notifications and PendingIntent action tokens are memory-only: never logged, uploaded or included in backups. Preview content/actions require opt-in and an unlocked device; per-app exclusions also apply. Secret notifications and group summaries are omitted. Android may redact data before delivery. Counts represent active notifications, not server unread counts.

## Cross-device continuation

On Android 17, **Continue Start on another device** opts the current activity into the public Handoff API. It transfers only the selected layout name; the receiving device opens its own matching layout. It requires compatible system support and GridLauncher on both devices.

The public Android 17 SDK exposes activity Handoff publishing and restoration, but no general third-party-launcher feed of other apps' nearby activities. This project does not fake a nearby-app suggestion feed. Two-device transport still needs physical-device verification; tests cover the data contract. Sources: [Android Handoff](https://developer.android.com/develop/better-together/continue-on/enable-support), [contact picker](https://developer.android.com/about/versions/17/features/contact-picker).

## Backup and limits

Explicit JSON export backs up the current layout's portable app/built-in/folder tiles and appearance. Restore confirms replacement, validates, rehydrates installed apps and repacks. Device-bound widgets, selected contacts/photos, documents and pinned shortcuts must be selected again; wallpaper must be selected again. Automatic cloud/device backup is disabled to avoid copying contact snapshots and device-bound grants.

A launcher cannot replace Android's lock screen, system quick settings, recents or third-party app UI. Cortana, Windows services and Continuum are not reproduced. Media/weather are sourced from notifications or installed widgets; no mock weather or fabricated activity is shipped.

## Build

Install JDK 17:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
scripts/setup-android.sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
./gradlew lintDebug --no-daemon
./gradlew assembleRelease
```

APK paths: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`.

For official signing set `SIGNING_KEYSTORE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD`. Otherwise release uses the development key. Run compilation, lint in an isolated daemon, and release assembly separately to avoid an AGP/Kotlin lint interaction with generated kapt sources.

Toolchain: AGP 9.4.1, Gradle 9.6.0 (checksum pinned), Kotlin 2.4.20, Java 17, Android SDK 37.0/build tools 37.0.0. Legacy Kotlin/AGP DSL opt-outs remain for kapt/Hilt compatibility.

## CI and tests

Each push/PR/manual run executes unit tests, lint, APK builds, signature/alignment verification, and Android 17 emulator tests. A separate verifier requires every declared device test to produce a passing result, including cases where Gradle reports success despite installation failure. CI then installs/launches the signed release and uploads APK, checksum, reports, logs and screenshot. Successful pushes to `android-17-live-tiles` publish a GitHub Release.

Actions secrets: `APK_SIGNING_KEYSTORE` (base64) and `APK_SIGNING_PASSWORD`, alias `gridlauncher`. Keep the signing identity for update compatibility. PRs use development signing and receive no secrets.

```sh
scripts/setup-android.sh emulator
scripts/emulator-test.sh
```

The script expects an already-built release APK and Linux KVM. Tests cover actual notification delivery and RemoteInput, privacy, semantic progress, white app-list text, dedicated edit/native-menu separation, pinning, column/profile persistence, Handoff payloads and core launcher flows. Unit tests exercise randomized packing, pinned anchors, compaction, migration, backup validation and schedules. OEM-specific rendering, performance and two-device Handoff still need hardware testing.
