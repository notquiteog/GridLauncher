#!/usr/bin/env python3
from pathlib import Path
manifest = Path('app/build/intermediates/merged_manifests/release/processReleaseMainManifest/AndroidManifest.xml')
if not manifest.exists():
    manifest = Path('app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml')
assert manifest.exists(), 'Play manifest was not generated'
assert 'android.permission.REQUEST_INSTALL_PACKAGES' not in manifest.read_text(), 'Play build must not request APK installation'
config = Path('app/build/generated/source/buildConfig/release/tgo1014/gridlauncher/BuildConfig.java').read_text()
assert 'DISTRIBUTION_CHANNEL = "play"' in config, 'Play build must disable the GitHub updater'
print('Verified Play build omits APK installation permission and disables GitHub updates')
