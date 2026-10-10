#!/usr/bin/env python3
"""Validate the generated first-party S1 fixture APK without decoding framework data."""

from __future__ import annotations

import sys
import zipfile
from pathlib import Path


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: verify_fixture_apk.py <fixture-apk>")
    apk = Path(sys.argv[1])
    if not apk.is_file():
        fail(f"fixture APK does not exist: {apk}")
    try:
        with zipfile.ZipFile(apk) as archive:
            names = set(archive.namelist())
            required = {"AndroidManifest.xml", "classes.dex", "assets/fixture.txt"}
            missing = sorted(required - names)
            if missing:
                fail(f"fixture APK is missing: {', '.join(missing)}")
            native = sorted(name for name in names if name.startswith("lib/"))
            if native:
                fail(f"fixture APK must not contain native libraries: {native}")
            if archive.read("assets/fixture.txt").decode("utf-8").strip() != "aether-s1-fixture":
                fail("fixture asset content is not deterministic")
    except (OSError, zipfile.BadZipFile, UnicodeDecodeError) as error:
        fail(f"invalid fixture APK: {error}")
    print(f"S1 fixture APK is valid: {apk}")


if __name__ == "__main__":
    main()
