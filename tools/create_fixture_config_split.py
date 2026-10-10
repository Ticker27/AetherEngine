#!/usr/bin/env python3
"""CI-only creation of a real code-free config split, signed with the fixture debug key."""
import argparse
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--sdk", required=True)
parser.add_argument("--keystore", required=True)
parser.add_argument("--output-dir", required=True)
args = parser.parse_args()
out = Path(args.output_dir)
out.mkdir(parents=True, exist_ok=True)
manifest = out / "AndroidManifest.xml"
ns = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ns)
root = ET.Element("manifest", {"package": "com.aether.fixture", "split": "config.arm64_v8a",
    f"{{{ns}}}versionCode": "1", f"{{{ns}}}versionName": "1.0.0"})
ET.SubElement(root, "uses-sdk", {f"{{{ns}}}minSdkVersion": "24", f"{{{ns}}}targetSdkVersion": "36"})
ET.SubElement(root, "application", {f"{{{ns}}}hasCode": "false"})
ET.ElementTree(root).write(manifest, encoding="utf-8", xml_declaration=True)
tools = Path(args.sdk) / "build-tools/36.0.0"
unsigned = out / "fixture-config-unsigned.apk"
signed = out / "fixture-config-arm64.apk"
subprocess.run([str(tools / "aapt2"), "link", "--manifest", str(manifest), "-I",
    str(Path(args.sdk) / "platforms/android-36/android.jar"), "-o", str(unsigned)], check=True)
# This is the standard disposable Android debug certificate, not a release credential.
os.environ["FIXTURE_DEBUG_PASSWORD"] = "android"
subprocess.run([str(tools / "apksigner"), "sign", "--ks", args.keystore,
    "--ks-key-alias", "androiddebugkey", "--ks-pass", "env:FIXTURE_DEBUG_PASSWORD",
    "--key-pass", "env:FIXTURE_DEBUG_PASSWORD", "--out", str(signed), str(unsigned)], check=True)
subprocess.run([str(tools / "apksigner"), "verify", str(signed)], check=True)
import hashlib
certificate = subprocess.run(["keytool", "-exportcert", "-alias", "androiddebugkey",
    "-keystore", args.keystore, "-storepass:env", "FIXTURE_DEBUG_PASSWORD"],
    check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
(out / "fixture-signer.sha256").write_text(hashlib.sha256(certificate).hexdigest() + "\n")
print("config fixture created:", signed)
