#!/usr/bin/env python3
"""Fail-closed S1 ZIP/JUnit evidence checks; standard library only, no APK execution."""
import argparse
from collections import Counter
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile

FIXTURE = "assets/fixture-guest.apk"
CONFIG_SPLIT = "assets/fixture-config-arm64.apk"
SIGNER_PIN = "assets/fixture-signer.sha256"
TEST_NAMES = ("FixtureGuestLoaderTest", "FixtureGuestLaunchTest")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def zip_evidence(source, code_free=False):
    with zipfile.ZipFile(source) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        duplicates = sorted(name for name, n in Counter(names).items() if n > 1)
        if duplicates:
            raise ValueError(f"duplicate ZIP members: {duplicates}")
        if any(entry.flag_bits & 1 for entry in entries):
            raise ValueError("encrypted ZIP member")
        if code_free and any(re.fullmatch(r"classes(?:\d+)?\.dex", name) for name in names):
            raise ValueError("code-free config split must not contain DEX")
        required = ["AndroidManifest.xml"] + ([] if code_free else ["classes.dex"])
        required += [name for name in (FIXTURE, CONFIG_SPLIT, SIGNER_PIN, "assets/fixture.txt") if name in names]
        hashes = {}
        for name in required:
            data = archive.read(name)
            if not data:
                raise ValueError(f"empty member: {name}")
            hashes[name] = sha256(data)
        return {
            "members": [{"name": e.filename, "bytes": e.file_size,
                         "compressed_bytes": e.compress_size, "crc32": f"{e.CRC:08x}"}
                        for e in entries],
            "member_sha256": hashes,
            "native_abis": sorted({n.split("/")[1] for n in names
                                   if n.startswith("lib/") and len(n.split("/")) > 2}),
            "embedded_apks": sorted(n for n in names if n.lower().endswith(".apk")),
        }


def inspect_artifacts(repo, expected_abi=None):
    repo = Path(repo).resolve()
    report = {"schema_version": 1, "kind": "s1-apk-evidence", "valid": False,
              "commit": os.environ.get("GITHUB_SHA"),
              "run_id": os.environ.get("GITHUB_RUN_ID"),
              "runtime_scope": "static APK inspection only; no device acceptance",
              "expected_native_abi": expected_abi,
              "arm64_result": "not_run", "s1_complete": False,
              "candidates": [], "selected": {}, "issues": []}
    groups = {role: [] for role in ("host", "test", "fixture")}
    roots = [("host", repo / "android-host/build/outputs/apk"),
             ("fixture", repo / "fixture-guest/build/outputs/apk")]
    for module, root in roots:
        # AGP puts test APKs in androidTest/debug, NOT debugAndroidTest.
        for path in sorted(root.rglob("*.apk")):
            parts = path.relative_to(root).parts[:-1]
            role = ("test" if module == "host" and "androidTest" in parts
                    else module) if "debug" in parts else "unexpected"
            item = {"path": path.relative_to(repo).as_posix(), "role": role,
                    "bytes": path.stat().st_size, "sha256": file_sha256(path)}
            report["candidates"].append(item)
            if role in groups:
                groups[role].append(item)
            else:
                report["issues"].append(f"unexpected APK candidate: {item['path']}")
            try:
                item.update(zip_evidence(path))
            except (OSError, ValueError, KeyError, RuntimeError, zipfile.BadZipFile) as exc:
                report["issues"].append(f"{item['path']}: {exc}")
    for role, candidates in groups.items():
        if len(candidates) != 1:
            report["issues"].append(f"expected exactly one {role} debug APK; got {len(candidates)}")
        elif "members" in candidates[0]:
            report["selected"][role] = candidates[0]["path"]
    for item in report["candidates"]:
        if "members" not in item:
            continue
        expected = sorted([FIXTURE, CONFIG_SPLIT]) if item["role"] == "test" else []
        if item["embedded_apks"] != expected:
            report["issues"].append(f"{item['path']}: embedded APKs must be {expected}")
        if item["role"] == "fixture" and "assets/fixture.txt" not in item["member_sha256"]:
            report["issues"].append(f"{item['path']}: missing assets/fixture.txt")
        if item["role"] == "host" and SIGNER_PIN in item["member_sha256"]:
            report["issues"].append(f"{item['path']}: test fixture signer pin must not be in host assets")
        if item["role"] == "host" and expected_abi:
            names = {entry["name"] for entry in item["members"]}
            if f"lib/{expected_abi}/libaether.so" not in names:
                report["issues"].append(f"{item['path']}: missing libaether.so for {expected_abi}")
            if item["native_abis"] != [expected_abi]:
                report["issues"].append(f"{item['path']}: unexpected native ABIs {item['native_abis']}")
    selected = report["selected"]
    split_path = repo / "android-host/build/generated/fixtureSplit/fixture-config-arm64.apk"
    try:
        report["config_split"] = {"path": split_path.relative_to(repo).as_posix(),
                                   "sha256": file_sha256(split_path),
                                   **zip_evidence(split_path, code_free=True)}
        if report["config_split"]["embedded_apks"] or report["config_split"]["native_abis"]:
            raise ValueError("config fixture must contain no nested APKs or native libraries")
    except (OSError, ValueError, KeyError, RuntimeError, zipfile.BadZipFile) as exc:
        report["issues"].append(f"config split: {exc}")
    if "test" in selected:
        try:
            with zipfile.ZipFile(repo / selected["test"]) as test_zip:
                pin = test_zip.read(SIGNER_PIN).decode("ascii").rstrip("\r\n")
            if not re.fullmatch(r"[0-9a-f]{64}", pin):
                raise ValueError("signer pin must be nonempty 64 lowercase hex characters")
            report["fixture_signer_sha256"] = pin  # Public certificate hash, NOT a private key.
        except (OSError, ValueError, KeyError, RuntimeError, zipfile.BadZipFile) as exc:
            report["issues"].append(f"fixture signer pin: {exc}")
        try:
            with zipfile.ZipFile(repo / selected["test"]) as test_zip:
                if test_zip.getinfo(CONFIG_SPLIT).file_size > 64 * 1024 * 1024:
                    raise ValueError("config fixture exceeds 64 MiB limit")
                nested_split = test_zip.read(CONFIG_SPLIT)
            if sha256(nested_split) != report.get("config_split", {}).get("sha256"):
                raise ValueError("staged config split differs from the config built in this run")
            report["embedded_config_split"] = {"sha256": sha256(nested_split),
                **zip_evidence(io.BytesIO(nested_split), code_free=True)}
        except (OSError, ValueError, KeyError, RuntimeError, zipfile.BadZipFile) as exc:
            report["issues"].append(f"embedded config split: {exc}")
    if "test" in selected and "fixture" in selected:
        try:
            with zipfile.ZipFile(repo / selected["test"]) as test_zip:
                if test_zip.getinfo(FIXTURE).file_size > 64 * 1024 * 1024:
                    raise ValueError("fixture exceeds 64 MiB limit")
                nested = test_zip.read(FIXTURE)
            fixture_item = next(i for i in groups["fixture"] if i["path"] == selected["fixture"])
            if sha256(nested) != fixture_item["sha256"]:
                raise ValueError("staged fixture differs from the fixture built in this run")
            report["embedded_fixture"] = {"sha256": sha256(nested),
                                          **zip_evidence(io.BytesIO(nested))}
            if "assets/fixture.txt" not in report["embedded_fixture"]["member_sha256"]:
                raise ValueError("embedded fixture missing assets/fixture.txt")
        except (OSError, ValueError, KeyError, RuntimeError, zipfile.BadZipFile) as exc:
            report["issues"].append(f"embedded fixture: {exc}")
    report["valid"] = not report["issues"]
    return report


def discover_scope(repo):
    repo = Path(repo).resolve()
    found = {name: [] for name in TEST_NAMES}
    root = repo / "android-host/src/androidTest"
    for path in sorted(root.rglob("*")):
        if path.suffix not in (".kt", ".java"):
            continue
        text = path.read_text(encoding="utf-8")
        package = re.search(r"^\s*package\s+([\w.]+)\s*;?", text, re.MULTILINE)
        for name in TEST_NAMES:
            if re.search(r"\bclass\s+" + name + r"\b", text):
                if not package:
                    raise ValueError(f"missing package for {name}")
                found[name].append({"class": package[1] + "." + name,
                                    "source": path.relative_to(repo).as_posix(),
                                    "source_sha256": file_sha256(path)})
    if len(found[TEST_NAMES[0]]) != 1 or len(found[TEST_NAMES[1]]) > 1:
        raise ValueError("require one Loader test class and at most one Launch test class")
    sources = [item for name in TEST_NAMES for item in found[name]]
    return {"schema_version": 1, "classes": [s["class"] for s in sources], "sources": sources}


def inspect_xml(roots, expected_classes=(), min_tests=1):
    report = {"schema_version": 1, "kind": "s1-junit-evidence", "valid": False,
              "files": [], "tests": 0, "failures": 0, "errors": 0, "skipped": 0,
              "class_pass_counts": {}, "issues": []}
    paths = set()
    for root in roots:
        root = Path(root)
        if root.is_file():
            paths.add(root)
        else:
            paths.update(root.rglob("*.xml"))
    if not paths:
        report["issues"].append("no test XML files found")
    for path in sorted(paths):
        record = {"path": path.as_posix(), "sha256": file_sha256(path)}
        report["files"].append(record)
        try:
            data = path.read_bytes()
            if b"<!DOCTYPE" in data or b"<!ENTITY" in data:
                raise ValueError("DTD/entity not allowed in test evidence")
            root = ET.fromstring(data)
            if root.tag not in ("testsuite", "testsuites"):
                raise ValueError("not a JUnit testsuite/testsuites document")
            suites = list(root.iter("testsuite"))
            if not suites:
                raise ValueError("no testsuites")
            if root.tag == "testsuites":
                aggregate = list(root.iter("testcase"))
                for key, tag in (("tests", None), ("failures", "failure"),
                                 ("errors", "error"), ("skipped", "skipped")):
                    if key in root.attrib:
                        actual = len(aggregate) if tag is None else sum(
                            case.find(tag) is not None for case in aggregate)
                        if int(root.attrib[key]) != actual:
                            raise ValueError(f"aggregate {key} count mismatch")
            local = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
            classes = Counter()
            for suite in suites:
                cases = suite.findall("testcase")
                observed = {"tests": len(cases),
                            "failures": sum(c.find("failure") is not None for c in cases),
                            "errors": sum(c.find("error") is not None for c in cases),
                            "skipped": sum(c.find("skipped") is not None for c in cases)}
                # Validate aggregating suites too, but count only leaf cases once.
                aggregate = list(suite.iter("testcase"))
                for key, tag in (("tests", None), ("failures", "failure"),
                                 ("errors", "error"), ("skipped", "skipped")):
                    actual = len(aggregate) if tag is None else sum(
                        case.find(tag) is not None for case in aggregate)
                    declared = int(suite.attrib["tests"] if key == "tests"
                                   else suite.get(key, "0"))
                    if declared < 0 or declared != actual:
                        raise ValueError(f"{key} count mismatch: declared={declared}, actual={actual}")
                    local[key] += observed[key]
                for case in cases:
                    class_name = case.get("classname", suite.get("name", ""))
                    if expected_classes and class_name not in expected_classes:
                        raise ValueError(f"unscoped test case: {class_name}")
                    if all(case.find(tag) is None for tag in ("failure", "error", "skipped")):
                        classes[class_name] += 1
            record["counts"] = local
            for key, value in local.items():
                report[key] += value
            for name, count in classes.items():
                report["class_pass_counts"][name] = report["class_pass_counts"].get(name, 0) + count
        except (OSError, ValueError, KeyError, ET.ParseError) as exc:
            report["issues"].append(f"{path}: {exc}")
    if report["tests"] <= 0:
        report["issues"].append("test count must be greater than zero")
    if report["tests"] < min_tests:
        report["issues"].append(f"test count below required minimum: {report['tests']} < {min_tests}")
    if report["failures"] or report["errors"]:
        report["issues"].append("test failures/errors present")
    if report["tests"] == report["skipped"] and report["tests"]:
        report["issues"].append("no executed passing tests")
    for name in expected_classes:
        if report["class_pass_counts"].get(name, 0) <= 0:
            report["issues"].append(f"required class has no passing test: {name}")
    if expected_classes and report["skipped"]:
        report["issues"].append("scoped instrumentation tests must not be skipped")
    report["valid"] = not report["issues"]
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    apks = commands.add_parser("apks")
    apks.add_argument("--repo", type=Path, default=Path("."))
    apks.add_argument("--output", type=Path, required=True)
    apks.add_argument("--abi", choices=("x86_64", "arm64-v8a"), required=True)
    apks.add_argument("--copy-to", type=Path)
    scope = commands.add_parser("scope")
    scope.add_argument("--repo", type=Path, default=Path("."))
    scope.add_argument("--output", type=Path, required=True)
    xml = commands.add_parser("xml")
    xml.add_argument("--root", type=Path, action="append", required=True)
    xml.add_argument("--output", type=Path, required=True)
    xml.add_argument("--scope", type=Path)
    xml.add_argument("--min-tests", type=int, default=1)
    args = parser.parse_args(argv)
    if args.command == "scope":
        try:
            result = discover_scope(args.repo)
        except (OSError, ValueError) as exc:
            write_json(args.output, {"valid": False, "issues": [str(exc)]})
            print(str(exc), file=sys.stderr)
            return 1
        write_json(args.output, result)
        print(",".join(result["classes"]))
        return 0
    if args.command == "apks":
        result = inspect_artifacts(args.repo, args.abi)
        write_json(args.output, result)
        if result["valid"] and args.copy_to:
            args.copy_to.mkdir(parents=True, exist_ok=True)
            sums = []
            for role, relative in sorted(result["selected"].items()):
                source = args.repo / relative
                shutil.copyfile(source, args.copy_to / f"{role}-debug.apk")
                sums.append(f"{file_sha256(source)}  {relative}\n")
            source = args.repo / result["config_split"]["path"]
            shutil.copyfile(source, args.copy_to / "fixture-config-arm64.apk")
            sums.append(f"{file_sha256(source)}  {result['config_split']['path']}\n")
            (args.copy_to / "SHA256SUMS.source").write_text("".join(sums), encoding="utf-8")
    else:
        expected = json.loads(args.scope.read_text(encoding="utf-8"))["classes"] if args.scope else ()
        result = inspect_xml(args.root, expected, max(1, args.min_tests))
        write_json(args.output, result)
    print(json.dumps({"valid": result["valid"], "issues": result["issues"]}, sort_keys=True))
    return 0 if result["valid"] else 1


if __name__ == "__main__":
    sys.exit(main())
