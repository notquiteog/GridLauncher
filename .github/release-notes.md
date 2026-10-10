GridLauncher 2.11.0 brings a more polished Windows 10 Mobile-style Start screen and new Android 17 launcher integrations.

### New and improved

- Refined tile spacing, light typography, alphabet headings, All apps navigation, scrolling, and light-theme rendering.
- Drag one app onto another in edit mode to create a folder, then name it. Slow drags work reliably, pinned tiles stay anchored, and widgets and shortcuts retain their contents.
- Hide individual app suggestions, restore them, or switch suggestions off. Recent and Most used now follow separate, correct ordering.
- Voice search through an installed speech app, plus an explicit web-search action. Typing alone does not send queries to a web service.
- Separate work/private-profile browser with profile-aware launches, pause/resume, private-space lock/unlock, and hide/show controls. Locked apps stay out of personal search and tiles.
- Predictive Back with cancellation support, including while editing a layout.
- Provider-requested widget pinning with Android binding consent, cancellation cleanup, and state restoration.
- Current heart-rate permissions and sensor rationale, plus safer notification actions and profile isolation.

### Installation and compatibility

Requires Android 17 (API 37). Install **GridLauncher-Android17.apk**; it uses the existing release signing identity for in-place updates. `update.json` and `SHA256SUMS` support the built-in updater and download verification.

The release pipeline runs unit tests, lint, Android 17 device tests and screenshot comparisons, verifies signing/alignment, and installs and launches the signed release APK before publication.

Work/private apps open in the separate profile browser; full profile-specific Start layouts remain future work. Android/OEM components retain Recents, the lock screen, notification shade, taskbar and system bubble management. Voice recognition, managed-profile policy, authentication and physical foldable behavior depend on the installed provider/device and require hardware validation.

See the [README](https://github.com/notquiteog/GridLauncher/blob/main/README.md) for setup, privacy controls and compatibility details.
