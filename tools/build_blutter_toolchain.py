#!/usr/bin/env python3
"""Build the analysis tool only; no target APK is downloaded or executed."""
import json
import os
from pathlib import Path
import subprocess
import sys

SOURCE_REV = "4a60ac648bf448c5a7596437243bcd0b9376fdf0"
SNAPSHOT = "80a49c7111088100a233b2ae788e1f48"

root = Path("/tmp/blutter-toolchain")
subprocess.run(["git", "clone", "https://github.com/worawit/blutter.git", str(root)], check=True)
subprocess.run(["git", "checkout", SOURCE_REV], cwd=root, check=True)
os.chdir(root)
sys.path.insert(0, str(root))
import dartvm_fetch_build
import blutter

wrapper = root / "ninja-limited.sh"
wrapper.write_text('#!/bin/sh\nexec ninja -j2 "$@"\n')
wrapper.chmod(0o755)
dartvm_fetch_build.NINJA_CMD = str(wrapper)
blutter.NINJA_CMD = str(wrapper)
info = dartvm_fetch_build.DartLibInfo("3.5.4", "android", "arm64", True, SNAPSHOT)
dartvm_fetch_build.fetch_and_build(info)
request = blutter.BlutterInput("not-used.apk", info, "/tmp/not-used", False, False, False)
blutter.cmake_blutter(request)

out = Path("/work/out")
out.mkdir(parents=True, exist_ok=True)
import shutil
shutil.copy2(request.blutter_file, out / Path(request.blutter_file).name)
shutil.copy2(root / "scripts/frida.template.js", out / "frida.template.js")
(out / "BUILD_PROVENANCE.json").write_text(json.dumps({
    "blutter_revision": SOURCE_REV, "dart_version": "3.5.4",
    "snapshot": SNAPSHOT, "host": "Alpine 3.21 aarch64 musl",
    "target": "android arm64 compressed pointers", "jobs": 2,
    "target_apk_uploaded": False,
}, indent=2) + "\n")
