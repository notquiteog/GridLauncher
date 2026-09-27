#!/usr/bin/env python3
"""Publish updater metadata derived from the actual signed APK, not a guessed version."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

apk = Path(sys.argv[1])
tag = sys.argv[2]
assert re.fullmatch(r"android17-\d+", tag), "Invalid release tag"
aapt = Path(os.environ["ANDROID_HOME"]) / "build-tools/37.0.0/aapt2"
badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
package, code, name = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging).groups()
assert package == "io.github.notquiteog.gridlauncher"
metadata = {
    "schema": 1, "packageName": package, "versionCode": int(code), "versionName": name,
    "minSdk": int(re.search(r"sdkVersion:'(\d+)'", badging)[1]),
    "apkUrl": f"https://github.com/notquiteog/GridLauncher/releases/download/{tag}/GridLauncher-Android17.apk",
    "sha256": hashlib.file_digest(apk.open("rb"), "sha256").hexdigest(), "size": apk.stat().st_size,
}
(apk.parent / "update.json").write_text(json.dumps(metadata, indent=2) + "\n")
print(f"Updater manifest: {package} {name} ({code})")
