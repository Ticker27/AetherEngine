#!/usr/bin/env python3
"""Check the two Snake payload folders imported as opaque Android assets."""

from __future__ import annotations

import hashlib
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAYLOAD_ROOT = ROOT / "android-host/src/main/assets/snake"
EXPECTED_TREES = {
    "assets": (16, 1_141_120, "6c8151a2e8734b0ad995adcc76aaf06b070fba935449cd4a09afb7e6927fc9d5"),
    "res": (840, 366_375, "2d8e87edd1172f25456a2baf6a38e986f7a9b94d97299b13d753380d85b068d7"),
}
RESOURCE_FILENAME_SHAPE = re.compile(r"^[a-z0-9_]+\.[a-z0-9]+$")


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def tree_digest(base: Path) -> tuple[int, int, str, Counter[str]]:
    files = sorted(path for path in base.rglob("*") if path.is_file())
    total_bytes = 0
    digest = hashlib.sha256()
    extensions: Counter[str] = Counter()
    for path in files:
        relative = path.relative_to(base).as_posix()
        data = path.read_bytes()
        total_bytes += len(data)
        extensions[path.suffix.lower() or "<none>"] += 1
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(hashlib.sha256(data).digest())
    return len(files), total_bytes, digest.hexdigest(), extensions


def main() -> None:
    archive = ROOT / "docs/snake.zip"
    if archive.exists():
        fail("docs/snake.zip should not be retained; only its assets/ and res/ folders were requested")

    if not PAYLOAD_ROOT.is_dir():
        fail(f"missing extracted payload directory: {PAYLOAD_ROOT.relative_to(ROOT)}")
    actual_top_level = {path.name for path in PAYLOAD_ROOT.iterdir()}
    if actual_top_level != set(EXPECTED_TREES):
        fail(f"expected only assets/ and res/ under the Snake payload, found {sorted(actual_top_level)}")

    summary: dict[str, tuple[int, int, str, Counter[str]]] = {}
    for folder, (expected_count, expected_bytes, expected_hash) in EXPECTED_TREES.items():
        base = PAYLOAD_ROOT / folder
        count, total, digest, extensions = tree_digest(base)
        if count != expected_count or total != expected_bytes or digest != expected_hash:
            fail(
                f"{folder}/ payload mismatch: files={count}, bytes={total}, "
                f"tree-sha256={digest}"
            )
        summary[folder] = (count, total, digest, extensions)

    compiled_xml = 0
    total_xml = 0
    nonmatching_names = 0
    for path in (PAYLOAD_ROOT / "res").rglob("*"):
        if not path.is_file():
            continue
        if path.suffix.lower() == ".xml":
            total_xml += 1
            if path.read_bytes()[:4] == b"\x03\x00\x08\x00":
                compiled_xml += 1
        if not RESOURCE_FILENAME_SHAPE.fullmatch(path.name):
            nonmatching_names += 1

    if total_xml != 602 or compiled_xml != 601 or nonmatching_names != 463:
        fail("res/ XML or filename-shape inventory changed; review the payload before use")

    for relative in (
        "docs/reference/snake-engine/AndroidManifest.xml",
        "docs/reference/snake-engine/classes.dex",
        "docs/reference/snake-engine/resources.arsc",
        "docs/reference/snake-engine/libapp.so",
    ):
        if (ROOT / relative).exists():
            fail(f"redundant loose package binary remains: {relative}")

    assets_count, assets_bytes, _, _ = summary["assets"]
    res_count, res_bytes, _, _ = summary["res"]
    print(
        "Snake payload verified: "
        f"assets/ {assets_count} files/{assets_bytes:,} bytes; "
        f"res/ {res_count} files/{res_bytes:,} bytes; "
        f"{compiled_xml}/{total_xml} XML files use compiled AXML, "
        f"and {nonmatching_names} filenames fail the recorded lowercase filename-shape check."
    )


if __name__ == "__main__":
    main()
