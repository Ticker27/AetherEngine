#!/usr/bin/env python3
"""Assert that assembled host APKs contain the intended native/Flutter layers."""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

ABI = "arm64-v8a"
COMMON_ENTRIES = (
    "classes.dex",
    "AndroidManifest.xml",
    f"lib/{ABI}/libaether.so",
    f"lib/{ABI}/libflutter.so",
)


def verify(apk_path: Path, build_type: str) -> None:
    if not apk_path.is_file():
        raise ValueError(f"APK does not exist: {apk_path}")

    try:
        with zipfile.ZipFile(apk_path) as apk:
            entries = set(apk.namelist())
    except zipfile.BadZipFile as error:
        raise ValueError(f"Not a valid APK/ZIP archive: {apk_path}") from error

    missing = [entry for entry in COMMON_ENTRIES if entry not in entries]
    flutter_assets = sorted(
        entry for entry in entries if entry.startswith("assets/flutter_assets/")
    )
    if not flutter_assets:
        missing.append("assets/flutter_assets/*")

    if build_type == "release":
        app_binary = f"lib/{ABI}/libapp.so"
        if app_binary not in entries:
            missing.append(app_binary)

    if missing:
        raise ValueError(
            f"{build_type} APK is missing architecture payload: {', '.join(missing)}"
        )

    print(
        f"{build_type} APK contains classes.dex, libaether.so, libflutter.so, "
        f"{len(flutter_assets)} Flutter asset entries"
        + (", and libapp.so (Dart AOT)." if build_type == "release" else ".")
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("build_type", choices=("debug", "release"))
    args = parser.parse_args()

    try:
        verify(args.apk, args.build_type)
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1) from error


if __name__ == "__main__":
    main()
