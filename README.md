# GridLauncher for Android 17

A native Kotlin / Jetpack Compose launcher combining Windows Phone's information-first Start screen with flat icon-colored tiles and glass launcher controls. Based on [Tgo1014/GridLauncher](https://github.com/Tgo1014/GridLauncher)'s `develop` branch; the Apache license and attribution are preserved.

## Install

Download **GridLauncher-Android17.apk** from [Releases](https://github.com/notquiteog/GridLauncher/releases), or the APK artifact of a successful [Actions build](https://github.com/notquiteog/GridLauncher/actions/workflows/android.yml). In **Customize Start**, select **Set as default home app**. Android requires the home role to expose app shortcuts.

- **Android 17 only.** compile/target/min SDK **37**. Every legacy code path was removed rather than guarded.
- Package: `io.github.notquiteog.gridlauncher`.
- Official releases use a persistent private signing key and support in-place updates. Development and PR builds have a different key, so an official install will refuse them.

## GitHub updates

GitHub/sideload builds check the latest stable release when the launcher resumes, at most once per day. Failed checks retry no more than hourly. Customize Start includes **Check for updates** and an automatic-check toggle. Downloads are requested explicitly, bounded in size, checked against the release SHA-256, and verified for the exact package, a newer version, and the same signing certificate. Android's normal unknown-source permission and installation confirmation stay in the user's hands. Tiles and settings survive the update.

The updater checks Android's installing and initiating package and the update owner, so a Play install never uses the GitHub updater. Android does not reliably retain the original download website for sideloads, so the GitHub distribution includes other sideloads too. A Play build uses `-PdistributionChannel=play`, which disables the updater and drops its package-install permission. Debug builds check only on manual request. CI writes `update.json` from the actual signed APK and publishes it beside the APK and checksum, completing a draft release before making it visible.

## Layouts

Personal, Work and Travel ship with the app, and you can create up to 12 more with **New** in the All apps header — a new layout either copies your current tiles or starts empty, and becomes active immediately.

**Every layout is fully yours to manage, including the three built-ins.** Long press any chip to rename or delete it. Deleting removes its tiles for good, so copy it somewhere first. The last layout is never removable, and a layout you are currently standing in is moved away from before the delete happens, with the dialog saying so.

The weekday Work schedule used to refer to layouts by hardcoded name, which is why the built-ins used to be protected. Its two targets now live in Customize Start as **Weekday layout** and **Other times**, defaulting to Work and Personal so existing installs behave identically. The poller resolves the name against the layouts that actually exist and falls back, so deleting or renaming a layout the schedule points at cannot break it — you are told when the schedule moves. The schedule itself supports overnight shifts anchored to the evening they start, and choosing a layout by hand turns it off. These are launcher layouts, not Android managed work profiles.

**Press and hold a chip to drag it into a new position.** The order persists, survives rename and delete, and a deleted layout is not resurrected by a stale order. Drag and long press share one gesture handler, so holding a chip and moving becomes a reorder while holding and lifting becomes the menu; the row still scrolls under a deciding finger.

Every layout keeps its own tiles. **Copy to …** in Customize Start copies the current arrangement over another, with confirmation. Layout names are trimmed to 40 characters, stripped of control characters, and rejected only when they genuinely collide with a layout you already have — so you can call a layout "Work" once Work is gone.

Maintenance work — column changes, whole-cell migration, pruning uninstalled apps, finding orphaned widgets — walks every stored grid key, so a damaged layout list can never strand a layout.

## Start

- **Flat, edge-to-end tiles.** Each app tile takes the dominant opaque outer-edge color of its own default icon, with transparent margins ignored, and the label switches to whichever of black or white actually measures better against it. Built-in tiles use the accent color; photos, album art and widgets keep their own appearance. Choose **2–6 cells across**, default **3**. All positions and sizes are whole cells, and 2.0 layouts migrate automatically.
- **Every hub carries its own designed mark**, centred on a small tile and tucked into the bottom corner of a wider one, the way Windows Phone identified a live tile.
- **Live tiles turn.** When a tile's content changes it flips on its vertical axis and comes back, rather than cross-fading in place. The back is hidden past ninety degrees so it is never drawn mirrored.
- **Double tap to peek.** A second tap magnifies that same tile into an overlay — literally the same composable again, so the larger view cannot reveal anything the small one would not. Gated off while editing, locked, quiet, or on an excluded app, and presented as a TalkBack action rather than a gesture you have to discover.
- **Badges** can be a bare dot or a filled circle carrying a count, drawn in the tile's own ink with a knock-out number chosen for contrast. The label's end inset is the badge's real footprint, so a long name is never clipped by it.
- **Group headers** span the full width to divide sections and keep their position through every reflow. **Folders** can hold other folders. A folder's contents are editable from its own tile sheet, and **long pressing a folder** shows them as a jump list, drilling into nested folders — while a plain tap still expands it inline. Dragging a tile onto a folder puts that app inside.
- **Drag a tile** in edit mode and it lifts, casts a shadow, and lands on the cell you release it over, with the tiles around it packing to make room. Arrow keys, Tab and Enter work too. **Pin position** anchors a tile: it refuses to move and holds its cell as others come and go, and narrowing the grid will not displace it.
- **Resize** through Edit tile, from 1×1 up to larger whole-cell rectangles. An Android widget's own content may need a larger size to be useful. Android 17's resize *flow* is not public API, so resizing is expressed in whole cells rather than borrowing a screen the platform does not expose to launchers.
- **Hotseat:** up to four apps at the foot of Start, outside the tile grid, so packing, pinning and the tile width never move it. Pin from the **+** on a frequent app, or from Customize Start.
- A **semi-panoramic header** carries the date, large at the top and shrinking as you scroll, and returning from the drawer settles the tile pane back with a little parallax. The pane is clipped to its own page, so it can never paint over the one beside it.
- The **app-list arrow** sits bottom-left, as Windows Phone had it. Start is the bottom layer, so nothing about it tries to minimise and an upward swipe is not a gesture.
- **Fullscreen** hides the system bars; Android brings them back with a swipe from the edge.
- **Tinted icons** render every app icon in one flat color, drawn by the launcher from its own cache rather than by a system service. **Icon shape** offers Full bleed, Rounded square, Circle and Squircle, applied through the one path every app icon already takes. **Icon normalization** scales back the icons apps draw small inside a large canvas, so the grid reads at one consistent size without any artwork being redrawn.
- **Frosted, Clear, Acrylic and Solid** materials for the surrounding controls, wallpaper-derived tint, accent colors, light and dark themes, optional labels, and one-handed spacing.

## All apps

- **Search leads the page**, full width, with the magnifier at the right-hand end. It covers apps first, then the people, notifications and calendar events the same words actually find, each row opening the thing it names. Under the hood, starred contacts and the next week of calendar are indexed with Android 17's `android.app.search`; when that system service is unavailable the drawer scans the same rows by hand and behaves identically. **That index is off by default** and must be turned on deliberately — see *Privacy* below for why.
- **Order it A–Z, Most used or Recent**, counting real opens locally with `UsageTracker`. Only package names are stored, never timestamps, and a hub tile is not counted as an app launch. The same counts drive the **Frequent** row.
- The **jump rail** is the Windows Phone alphabet, and it lives here rather than on Start, because that is where Windows Phone kept it — Start was full-width tiles with no rail at all. Tap a letter to jump to that group, and the letter you are reading stays highlighted as you scroll. A letter no app starts with is dimmed and inert, so the alphabet keeps its shape as apps come and go. Press and hold for the whole alphabet at once. It appears only in A–Z order, where the list really has letter groups, and steps aside when a search leaves nothing to jump between.
- The **Now board** sits above the list: a vertical stack of live cards that pan horizontally and expand in place, with media cards carrying artwork and transport. It is bounded to a quarter of whatever height the drawer has left, so the app list keeps at least two and a half times the board's height however many cards arrive, and an empty board reserves no space at all.
- Categories resolve to **whichever app you actually have** — the platform intent where one exists, a curated list otherwise, and nothing at all where neither answers, because another company's content provider is not a contract. A resolved app with no live notification shows its name and an Open action, never a forecast or a headline, since there is no public API for either. Live notifications, the media session, the calendar agenda and a money app you pin are the rest of the board.

## Live information and actions

Enable **Manage notification access** for counts and live text. Notification text ships **on** by default; per-app exclusions, the lock state and quiet hours still apply.

- Tiles show real notification titles, message text, artwork, counts, progress and available actions. Tap a badge to open the preview. MessagingStyle conversations include recent messages and sender identity. Direct reply appears only when the app publishes a RemoteInput action.
- **Wide tiles stack** what else is waiting underneath the newest item, in the style of the Android 17 notification stack, rather than hiding it behind a count.
- **Call tiles.** An ongoing call notification — a real one, never a missed-call log — shows who is calling, the state, and controls. "Open in Phone" always works. Answer and decline appear **only when the calling app published those buttons on the notification it is still showing**, re-resolved at tap time so a dismissed call cannot fire a stale intent. A control that cannot work is never rendered.
- Hubs: **Clock, Music, Photos, People, Battery, Storage, Steps, Wallet, Data and Greeting**. Steps and heart rate come from the device's own sensors, and say so plainly when the hardware is absent. Data comes from Android's own network accounting. Wallet hands off to the wallet Android is already running — Android 17 gives third-party apps the wallet *provider* API but no way to read the active card, so the tile does not invent one. The Greeting tile is a plain time-of-day hello and what is next, with no assistant behind it. Calendar and favourites use optional read permissions; denial never blocks the launcher.
- **Pin a person's photo** puts a face and their latest conversation on Start, the way a contact photo sat on the Windows Phone grid. A long press on a person in the hub drops their number or address straight into any other app.
- **The People hub** folds every visible conversation onto the people you chose, newest first, with call, message and email. Matching uses app-supplied person URIs, so an app that does not identify its sender has no row.
- **The Photos hub** rotates your own recent library, or the photos you picked, honouring both a full media grant and Android 14's selected-photos grant.
- **Choose people** uses Android 17's contact picker. Only the names, numbers and addresses you explicitly shared are stored.
- Pin app destinations from a long press, or accept an app's Android pin request. Websites, geo links and documents can also be pinned.

## Privacy

This is a launcher reading everything on your screen, so the rules are narrow on purpose.

- **Notification content is never written to disk.** Not to the search index, not to a backup, not into a widget intent. The index takes a `PersistedRow` type a notification row cannot inhabit, its indexable filter is a whitelist, and results are filtered again on the way out so a stale database cannot surface one either. A JVM test walks the write path and fails if notification content reaches anything persistent; it has been checked against deliberately injected leaks. Builds that shipped before this was fixed purge the namespace once on first launch.
- **Persisting anything sensitive is opt-in and off by default.** The only thing the index holds is starred-contact fields and calendar titles, for data you already granted access to and the app already shows on tiles. Off unless asked for is the only default where that claim needs no asterisk.
- Quiet hours, meeting mode, per-app exclusions and the lock state gate notification content **everywhere** it is shown — tiles, board, previews, search and the widget — so excluded content is absent from an index rather than merely hidden in a result.
- `TileNotification` holds `contentIntent` and notification `Action` objects and is never serialized. It is memory-only and dies with the process.
- The audit table of what each persistence path holds is in the release history for 2.7.0. Automatic cloud and device transfer stay **off**: the stored grid contains the names, numbers and addresses of the people you pinned, and copying that to a cloud backup is not a decision a launcher should make for you. The on-demand export is the supported path and filters those tiles out.

## The live-tile widget

**Add live tile to your home screen** places a GridLauncher widget on the system's own home screen, so the live-tile idea survives switching launchers. It is a resizable `RemoteViews` surface: the newest notification's app, title and text, a progress bar, the stack of what else is waiting on a larger widget, and up to two real notification actions. It follows exactly the same rules as the in-app tiles, and its actions go through the same guarded path that rejects a stale or dismissed notification. It persists nothing itself.

## Quiet hours and meeting mode

**Meeting mode** silences Start immediately: no counts, no live text, no inline actions, no Now board, and a **Quiet** marker. **Quiet hours** does the same on a schedule, including overnight windows. Granting Android's Do Not Disturb access additionally silences notifications system-wide; without it Start still keeps its own schedule, and Android's own settings remain the user's to control.

## Wallpaper, motion and contrast

Start uses **Android's own wallpaper** when you have not chosen one, live wallpaper included, so it looks like part of the system rather than a separate app. Choosing your own is optional, and the grid scrolls slightly faster than the wallpaper behind it for a little depth.

Motion follows your own **Reduce motion** switch *and* Android's animation scale, re-read on resume rather than polled. Label contrast follows Android's **high text contrast** setting, pushing to whichever extreme actually reads. Opening an app uses Android's own open transition, already a clean fade-through, and Start fades back in when it closes — consistent with every other app on the device rather than a private motion language.

The wallpaper scrim follows the **ink's polarity**: black for the dark theme, the drawer's own background for the light one. A single black scrim is right for white ink and catastrophic for dark ink, and the light drawer over a wallpaper was measuring 1.19:1 — the worst element on the page now measures 4.73:1, and the dark theme is unchanged to the pixel.

## Foldables and large screens

Posture comes from the launcher's own window, not the screen behind it, so a split-screen half on a large display is still the phone experience.

- **Folded, or any window under 840dp:** the normal phone. Start and All apps stay two separate pages in the pager, byte for byte.
- **Unfolded, 840dp and wider:** Start left, All apps right, split down the middle.
- **A real hinge is respected** via `androidx.window`. A vertical hinge puts the divider on the seam; a horizontal hinge **stacks** the panes, because a left-right split would run the divider along it; a flat non-separating crease is not a hole, so the divider goes to zero width. Panes inset to the hinge bounds with a 240dp minimum, so a fold near an edge is refused rather than producing a sliver. A window with no fold reported still splits evenly.
- A hardware keyboard or trackpad moves a focus ring across the grid with the arrow keys and Tab, and Enter opens the focused tile.

**Live hinge behaviour is unverified on hardware.** The posture arithmetic is covered by JVM tests driving real `FoldingFeature` values, and both postures were verified on a 6.7" foldable AVD, but no foldable reported a hinge. A real Z Fold, Pixel Fold or Razr is the only way to prove it.

## Cross-device continuation

On Android 17, **Continue Start on another device** opts the current activity into the public Handoff API. It transfers the selected layout name and the tile you were last on; the receiving device opens its own matching layout, and only if it still has it.

The public SDK exposes activity Handoff publishing and restoration, but no general third-party-launcher feed of other apps' nearby activities, and Handoff gives no device identity to build a trustworthy device list from. This project does not fake a nearby-app feed or a roster of devices it cannot actually see. Two-device transport still needs physical-device verification; tests cover the data contract. Sources: [Android Handoff](https://developer.android.com/develop/better-together/continue-on/enable-support), [contact picker](https://developer.android.com/about/versions/17/features/contact-picker).

## Backup and limits

Explicit JSON export backs up every layout's portable tiles — not just the active one — along with appearance, layout names, and a theme-pack code. Restore confirms replacement, validates, recreates missing layouts, rehydrates installed apps and repacks; a theme code in the file is applied when present. Device-bound widgets, selected contacts and photos, documents and pinned shortcuts must be selected again, as must the wallpaper. Backups from versions 1 through 3 still restore.

A launcher cannot replace Android's lock screen, quick settings, recents, the notification shade or third-party app UI, and GridLauncher does not try. Notification history and the notification centre are Android's job. Cortana's voice assistant, the Store, Windows services and Continuum-as-a-hardware-category are not reproduced.

Media, people, photos, storage, calls, steps and the weather-adjacent parts of the board come from notifications, the system media session, your own library, installed widgets, or whichever app you actually have installed. There is no mock weather, no fabricated forecast, no invented headline and no guessed intent action. **Weather and Money tiles are deliberately absent** for exactly that reason: neither has a public Android API, and a provider that could break without notice is not something to build a Start tile on. Search is **lexical**, not semantic: `EmbeddingPropertyConfig` exists and compiles, but it needs real vectors from an on-device embedding model this app does not ship, and a fabricated vector would be fake semantic search.

Icon packs and themed icons are Android-owned through `IconManager` and are not attempted. Launcher predictions are not possible at all: `PredictionHandlerService` and `ShortcutRankerService` are absent from the Android 17 public SDK.

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

For official signing set `SIGNING_KEYSTORE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Otherwise a release uses the development key. Run compilation, lint in an isolated daemon, and release assembly separately, to avoid an AGP/Kotlin lint interaction with generated kapt sources.

Toolchain: AGP 9.4.1, Gradle 9.6.0 (checksum pinned), Kotlin 2.4.20, Java 17, Android SDK 37.0 and build-tools 37.0.0, plus `androidx.window` 1.5.1 for hinge awareness. Legacy Kotlin/AGP DSL opt-outs remain for kapt and Hilt compatibility.

## CI and tests

Each push, PR or manual run executes unit tests, lint, APK builds, signature and alignment verification, Android 17 emulator tests, and screenshot comparison. A separate verifier requires every declared device test to produce a passing result, including cases where Gradle reports success despite an installation failure. CI then installs and launches the signed release and uploads the APK, checksum, reports, logs and every screenshot — including the actual capture and a magenta diff when one changed. Successful pushes to `android-17-live-tiles` publish a GitHub Release.

Actions secrets: `APK_SIGNING_KEYSTORE` (base64) and `APK_SIGNING_PASSWORD`, alias `gridlauncher`. Keep the signing identity for update compatibility. PRs use development signing and receive no secrets.

```sh
scripts/setup-android.sh emulator
scripts/emulator-test.sh
```

**Unit tests** cover randomized packing, pinned anchors, compaction, whole-cell and group-header migration, layout-name sanitising, schedule resolution through renames and deletions, layout ordering, quiet-hour windows, theme-pack round trips and rejection, grid keyboard navigation, backup validation and versioning, the search index's persistence boundary, call-tile detection and control gating, category resolution and fallbacks, the Now board's privacy contract, posture and hinge arithmetic, icon masking, and the screenshot comparator's own sensitivity.

**Device tests** cover real notification delivery and RemoteInput, privacy exclusions, semantic progress, white drawer text, edit/native-menu separation, pinning, drag-to-reorder including a pinned tile refusing to move, layout create/rename/delete, group headers and per-tile colours surviving a restart, folder editing in both directions, quiet hours, the hotseat surviving a column change, the frequent row, the contact picker and Handoff payload, and the screenshot scenes.

### Screenshot comparison

Every visual regression this launcher ever shipped — light-theme text at 1.19:1 over the wallpaper, Start's tile pane painting across the drawer, live tiles rendering blank, the alphabet rail on the wrong page — passed a fully green build, because nothing was looking at the pixels. CI now photographs five scenes on every run and compares them: Start in both themes, All apps in both themes, and edit mode. A sixth check measures legibility rather than pixels.

A baseline is only comparable if everything else is held still, so each scene pins the clock to a fixed instant in UTC, installs a committed wallpaper fixture rather than the system one, forces 24-hour time, resets every store the launcher reads, and waits for live-tile content to finish loading before capturing. Two numbers decide whether two images match: an 8-per-channel tolerance, which absorbs antialiasing, and a 0.05% changed-pixel budget — about one glyph on a 1080×2400 screen. Those are measured, not guessed: re-recording every scene on freshly created emulators produces byte-identical images, so the noise floor on a matching device is **zero** and the budget exists only for a runner whose rasteriser differs slightly from yours.

Two emulator details turned out to be part of the picture rather than the background, and both are now pinned and verified: the **display cutout**, which is 24dp of inset across the top of every frame and which one machine's device definition emulates and another does not, and the **system bars**, whose visibility depends on what ran before in the same session.

A frame is only worth comparing if it is the same frame twice, so a capture waits for two identical captures in a row. Without that, the live-tile flip photographs mid-rotation and produces a diff of thousands of pixels that is not a regression.

The comparator itself is plain Kotlin over packed pixels in a shared test source directory, with JVM tests that check it can *fail* — a one-pixel change is found, a single changed glyph is rejected, a stray pixel is not. A comparator that has quietly stopped comparing anything fails silently, which is the one failure mode a screenshot suite cannot survive.

```sh
scripts/screenshot-baselines.sh verify   # compare, on an emulator identical to CI's
scripts/screenshot-baselines.sh record   # re-record into src/androidTest/assets
```

Baselines live in `app/src/androidTest/assets/screenshots/`. A **missing baseline is a failure, never a silent record** — a baseline nobody reviewed is not evidence of anything. Re-recording is deliberately a pull request: run the workflow with the `update_screenshots` input and CI records on the runner that will compare, then opens a PR for a human to read the image diffs. Two machines can disagree about an antialiasing pixel, which is why the authoritative baseline is the one the runner recorded.

**What this still cannot see.** Three things are deliberately not photographed, for the same reason each time: a scene that fails for a reason unrelated to the launcher is worse than a missing scene, because the only way to green it is to re-record, and a baseline people re-record without reading protects nothing.

- **A scrolled Start.** The pane is driven by a parallax drag rather than a scroll container, so it exposes no scroll semantics and the same gesture lands a few hundred pixels apart between boots.
- **The two-pane layout.** Reached through `wm size`, it renders text a shade differently from one boot to the next. `WindowPostureTest` covers the layout's arithmetic instead, and a real foldable is still the only way to see a hinge in it.
- **Legibility**, which a pixel comparison cannot judge at all: an unreadable drawer is perfectly stable from run to run. So the light theme is also asserted as a number, measured off the rendered pixels against the WCAG AA floor of 4.5:1.

`notificationListenerReceivesAndroidPostedNotification` fails on emulator images where the notification listener service never binds; it is environmental and fails identically on unmodified `HEAD`. OEM-specific rendering, media-session behaviour, the home-screen widget on an OEM shell, live hinge behaviour, and two-device Handoff still need hardware testing. Treat the emulator as necessary but not sufficient, and check the rendering on the device you care about.

`notificationListenerReceivesAndroidPostedNotification` fails on emulator images where the notification listener service never binds; it is environmental and fails identically on unmodified `HEAD`. OEM-specific rendering, media-session behaviour, the home-screen widget on an OEM shell, live hinge behaviour, and two-device Handoff still need hardware testing.
