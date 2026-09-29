# GridLauncher for Android 17

A native Kotlin / Jetpack Compose launcher combining Windows Phone's information-first Start screen with flat icon-colored tiles and glass launcher controls. Based on [Tgo1014/GridLauncher](https://github.com/Tgo1014/GridLauncher)'s `develop` branch; the Apache license and attribution are preserved.

## Install

Download **GridLauncher-Android17.apk** from [Releases](https://github.com/notquiteog/GridLauncher/releases), or the APK artifact of a successful [Actions build](https://github.com/notquiteog/GridLauncher/actions/workflows/android.yml). In **Customize Start**, select **Set as default home app**. Android requires the home role to expose app shortcuts.

- **Android 17 only.** compile/target/min SDK **37**. Every legacy code path has been removed rather than guarded.
- Package: `io.github.notquiteog.gridlauncher`.
- Official releases use a persistent private signing key and support in-place updates. Development/PR builds have a different key.

## GitHub updates

GitHub/sideload builds check the latest stable release when the launcher resumes, at most once per day. Failed checks retry no more than hourly. Customize Start includes **Check for updates** and an automatic-check toggle. Downloads are requested explicitly, bounded in size, checked against the release SHA-256, and verified for the exact package, newer version and same signing certificate. Android's normal unknown-source permission and installation confirmation remain in control. Tiles/settings survive the update.

The updater checks Android's installing/initiating package and update owner; Play installs never use the GitHub updater. Android does not reliably retain the original download website for sideloads, so the GitHub distribution includes other sideloads too. A Play build uses `-PdistributionChannel=play`, which disables the updater and removes its package-install permission from the manifest. Debug builds check only on manual request. CI publishes `update.json` from the actual signed APK alongside the APK and checksum, completing the draft release before making it visible.

## Layouts

Personal, Work and Travel always exist. **Create your own** with **New** in the All apps header: a new layout either copies your current tiles or starts empty, becomes active immediately, and is capped at 12 custom layouts. **Long press your own layout** to rename or delete it; deleting removes its tiles for good, so copy it somewhere first. Personal, Work and Travel are never renamed or deleted, because the Work schedule refers to them by name. Layout names are trimmed to 40 characters, stripped of control characters, and may not shadow a built-in name.

Every layout keeps its own tiles. **Copy to …** in Customize Start copies the current arrangement over another layout, with confirmation. Choosing a layout by hand turns the weekday Work schedule off; the schedule itself supports overnight shifts anchored to the evening they start. These are launcher layouts, not Android managed work profiles.

Maintenance work (column changes, whole-cell migration, pruning uninstalled apps, finding orphaned widgets) walks every stored grid key, so a damaged layout list can never strand a layout.

## Start and editing

- A **hotseat** holds up to four apps at the foot of Start, with a search pill and a way into Customize Start. It sits outside the tile grid, so packing, pinning and the tile width never move it.
- The drawer can be ordered **A–Z**, by **Most used** or by **Recent**, counting real opens locally with `UsageTracker`. Only package names are stored, never timestamps, and a hub tile is not counted as an app launch. The same counts drive the **Frequent** row above the list.
- **Tinted icons** renders every app icon in one flat color, drawn by the launcher from its own cache rather than by a system service. **Icon normalization** scales back the icons that apps draw small inside a large canvas, so the grid reads at one consistent size without any artwork being redrawn.
- **Fullscreen** hides the status and navigation bars; Android brings them back with a swipe from the edge.
- Paging between Start and All apps needs a real swipe: a small horizontal wobble is swallowed, so the screen only changes when you mean it.
- Start is the bottom layer, so nothing about it tries to minimise. An upward swipe on Start does not look like closing an app, and Start never treats it as a gesture.
- Home holds tiles and widgets only. A semi-panoramic header carries the date, large at the top of the grid and shrinking and fading as you scroll. A **Quiet** marker appears while quiet hours or meeting mode are on. The date, **Ask**, Edit layout, settings, the layout chips and the Now area live in All apps. Choosing Edit layout or another layout returns to Home; Android Back exits editing.
- Square, edge-to-edge flat tiles with no gaps, gradients, blur or borders. Each app tile uses the dominant opaque outer-edge color of its default icon; transparent margins are ignored. Labels switch between black and white for contrast. Built-in tiles use the accent color; photo/widget content retains its own appearance. **Tile color** in Edit tile overrides the icon's own color, with **Auto** to go back. Choose **2–6 cells across**, default **3**. All positions and sizes use whole cells; existing 2.0 layouts migrate automatically.
- Large, unmodified Android app icons, including their original adaptive backgrounds. No replacement icon packs or foreground-only recoloring.
- **Long press** an app tile for its Android-published dynamic/manifest shortcuts and app info. These are actual app actions, not a layout-edit gesture. Apps decide which actions they provide.
- Open **All apps → Edit layout**. **Drag a tile** to move it: it lifts, casts a shadow and lands on the cell you release it over, snapping to whole cells. Arrow keys, Tab, Enter and the directional buttons in Edit tile work too. Tap a tile to resize, remove, recolour or **Pin position**. A pinned tile refuses to move and stays fixed as anchors when other tiles are added or removed; narrowing the grid refuses to displace a pinned tile that would no longer fit.
- Tile/widget dimensions include **1×1, 1×2, 2×1, 2×2**, and larger whole-cell rectangles that fit the selected width. An Android widget's own content may need a larger size to be useful.
- **Group headers** span the full width of the grid to divide sections, and keep their position through every reflow. **Folders** can hold other folders; open one, then open the inner one to drill down, and **Up** steps back out. Dragging a tile onto a folder puts that app inside it.
- **Resize widget** hands a bound widget to Android's own resize flow.
- **Now Playing** reads the system's real media session: album art, title, artist and transport, updating as playback changes.
- **Counts as dots** shows a bare dot instead of a number. A live tile flips on its vertical axis when its content changes, the way Windows Phone tiles turned, rather than cross-fading in place. **Tile color** offers your own accent as an explicit choice alongside the icon's own colours.
- **Frosted, Clear, Acrylic and Solid** materials for surrounding controls, wallpaper-derived tint, accent colors, light/dark themes, optional labels, and one-handed spacing. Acrylic is the sharpest, Frosted the softest. Dark app-list text is explicitly white.
- On a large screen or desktop window (840dp and wider) Start and All apps sit side by side, the Continuum gesture expressed through real windowing instead of a hardware category. Touch layouts keep the single-column Start/All apps pager.

## Live information and actions

Enable **Manage notification access** for counts and live text. Notification text now ships **on** by default; per-app exclusions, the device lock state and quiet hours still apply.

- Tiles show real notification titles, message text, artwork, counts, progress and available actions. Tap a count to expand the notification preview. MessagingStyle conversations include recent messages and sender identity. Direct reply is available only when the app publishes a RemoteInput action.
- **Wide tiles stack** what else is waiting underneath the newest item, in the style of the Android 17 notification stack, instead of hiding it behind a count.
- **Now** in All apps surfaces ongoing media, progress, navigation and timer notifications without rearranging pinned tiles. Finished/dismissed notifications disappear. Android 17 semantic annotations supply caution/urgent/safe labels and colors when the source app provides them.
- Folder tiles aggregate counts and expand inline into live app tiles.
- Clock, **Music**, **Photos**, People, **Battery**, **Storage**, **Steps**, **Wallet**, **Data** and a **Greeting** tile. Every hub carries its own designed mark, centred on a small tile and tucked into the bottom corner of a wider one, the way Windows Phone identified a live tile. Data comes from Android's own network accounting; the Greeting tile is a plain time-of-day hello and what is next, with no assistant behind it.
- **Pin a person's photo** puts a face and their latest conversation on Start, the way a contact photo sat on the Windows Phone grid. Steps and heart rate come from the device's own sensors through `SensorManager`, the way the Windows Phone Steps app did; the tile says so plainly when the hardware is absent. Wallet hands off to the wallet Android is already running — Android 17 gives third-party apps the wallet *provider* API but no way to read the active card, so the tile does not invent one. Calendar and favourites use optional read permissions; denial does not prevent using the launcher. Battery reports charge, charge cycles, time to full and temperature. Storage reports used and free space from `StatFs`.
- **The Photos hub** rotates your own recent library, or the photos you picked, and honours both a full media grant and Android 14's "selected photos" grant. **The People hub** folds every visible conversation onto the people you chose, newest first, with call, message and email, and a long-press drag that drops a number or address straight into any other app. Conversation matching uses app-supplied person URIs, so an app that does not identify its sender has no row.
- **Choose people** uses Android 17's contact picker (a phone picker fallback on older Android). Only selected names, numbers and emails are stored locally. Pin one person or a group. Contact pictures appear for favourite contacts when contact access is granted; otherwise tiles show initials.
- Pin app destinations from **All apps → long press → Pin: shortcut**, or accept an app's Android pin request. Websites, geo links and documents can also be pinned in Customize Start.

## The live-tile widget

**Add live tile to your home screen** places a GridLauncher widget on the system's own home screen, so the live-tile idea survives switching launchers. It is a resizable `RemoteViews` surface: the newest notification's app, title and text, a progress bar, the stack of what else is waiting on a larger widget, and up to two real notification actions. It follows exactly the same rules as the in-app tiles — opt-in, per-app exclusions, quiet hours, locked-device privacy — and its actions go through the same guarded path that rejects a stale or dismissed notification.

## Quiet hours and meeting mode

**Meeting mode** silences Start immediately: no counts, no live text, no inline actions, no Now board, and a **Quiet** marker. **Quiet hours** does the same on a schedule, including overnight windows. Granting Android's Do Not Disturb access additionally silences notifications system-wide; without it Start still keeps its own schedule, and Android's own settings remain the user's to control.

## Wallpaper, motion and contrast

Start uses **Android's own wallpaper** when you have not chosen one, live wallpaper included, so it looks like part of the system rather than a separate app. Choosing your own wallpaper is optional, and the grid scrolls slightly faster than the wallpaper behind it for a little depth. Motion follows your own **Reduce motion** switch *and* Android's animation scale; label contrast follows Android's **high text contrast** setting, pushing to whichever extreme actually reads.

Opening an app uses Android's own open transition, which is already a clean fade-through, and Start fades back in when the app closes. That keeps the launcher consistent with every other app on the device instead of inventing its own motion language.

## Materials, theme packs and keyboard

A **theme pack** is a short, hand-typable code carrying accent, material, tile width and live-tile behaviour — the shareable part of a theme, never your apps. Copy one in Customize Start and paste it into someone else's launcher; a malformed code is rejected rather than guessed. A hardware keyboard or trackpad moves a focus ring across the grid with the arrow keys and Tab, and Enter opens the focused tile.

## Cross-device continuation

On Android 17, **Continue Start on another device** opts the current activity into the public Handoff API. It transfers the selected layout name and the tile you were last on; the receiving device opens its own matching layout, and only if it still has it. It requires compatible system support and GridLauncher on both devices.

The public Android 17 SDK exposes activity Handoff publishing and restoration, but no general third-party-launcher feed of other apps' nearby activities, and Handoff gives no device identity to build a trustworthy device list from. This project does not fake a nearby-app suggestion feed or a roster of devices it cannot actually see. Two-device transport still needs physical-device verification; tests cover the data contract. Sources: [Android Handoff](https://developer.android.com/develop/better-together/continue-on/enable-support), [contact picker](https://developer.android.com/about/versions/17/features/contact-picker).

## Backup and limits

Explicit JSON export backs up the current layout's portable tiles and appearance, the names of all your layouts, and a theme-pack code. Restore confirms replacement, validates, recreates missing layouts, rehydrates installed apps and repacks; a theme code in the file is applied when present. Device-bound widgets, selected contacts/photos, documents and pinned shortcuts must be selected again; wallpaper must be selected again. Backups from versions 1 and 2 still restore.

Automatic cloud and device transfer stay **off** deliberately: the stored grid contains the names, numbers and addresses of the people you pinned, and copying that to a cloud backup is not a decision a launcher should make for you. The on-demand export is the supported path, and it filters those tiles out. If you enable Android backup yourself, treat the app's data as personal.

A launcher cannot replace Android's lock screen, system quick settings, recents, the notification shade or third-party app UI, and GridLauncher does not try: notification history and the notification center are Android's job, so Start shows live information on its tiles and nothing more. Cortana's voice assistant, the Windows Store, Windows services and Continuum-as-hardware are not reproduced. Media, people, photos, storage and weather are sourced from notifications, the system media session, your own library or installed widgets; no mock weather, no fabricated activity, no guessed intent actions.

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

The script expects an already-built release APK and Linux KVM. Unit tests cover randomized packing, pinned anchors, compaction, migration, header packing, layout-name sanitising, quiet-hour windows, theme-pack round trips and rejection, grid keyboard navigation, backup validation, and the Ask Start and search engines including their fallbacks. Device tests cover actual notification delivery and RemoteInput, privacy, semantic progress, white app-list text, dedicated edit/native-menu separation, pinning, drag-to-reorder including a pinned tile refusing to move, creating/renaming/deleting a custom layout, group headers and per-tile colours surviving a restart, quiet hours, theme packs, the hotseat surviving a column change, the frequent row, and the Android 17 contact picker and Handoff payload. `realAndroidWidgetCanResizeToWholeCellRectangles` is known to fail on some emulator images before it interacts with the launcher, and is the one outstanding item. OEM-specific rendering, media-session behaviour, the home-screen widget on an OEM shell, and two-device Handoff still need hardware testing.
