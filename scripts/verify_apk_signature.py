#!/usr/bin/env python3
"""Verify the release APK was signed with Android signature schemes v1 and v2."""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def find_apksigner() -> Path | str:
    configured_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if configured_home:
        candidates = sorted(Path(configured_home).glob("build-tools/*/apksigner"), reverse=True)
        if candidates:
            return candidates[0]
    on_path = shutil.which("apksigner")
    if on_path:
        return on_path
    fail("apksigner was not found under ANDROID_HOME/ANDROID_SDK_ROOT or PATH")


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: verify_apk_signature.py <signed-apk>")
    apk = Path(sys.argv[1])
    if not apk.is_file():
        fail(f"APK does not exist: {apk}")
    apksigner = find_apksigner()
    try:
        result = subprocess.run(
            [str(apksigner), "verify", "--verbose", str(apk)],
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        output = getattr(error, "stdout", "") + getattr(error, "stderr", "")
        fail(f"apksigner verification failed: {output[-4000:]}")
    output = result.stdout + result.stderr
    if not re.search(r"Verified using v1 scheme \(JAR signing\): true", output):
        fail("APK is not verified with Android v1/JAR signing")
    if not re.search(r"Verified using v2 scheme \(APK Signature Scheme v2\): true", output):
        fail("APK is not verified with Android v2 signing")
    print(f"Release APK signature schemes v1 and v2 verified: {apk}")


if __name__ == "__main__":
    main()
