#!/usr/bin/env python3
"""CI-only creation of a code-free config split signed with the SAME certificate as the fixture APK.

The signer is taken from the fixture APK itself (apksigner --print-certs), not from a keystore
path. The split must be signed by that key, and the pin is that certificate's SHA-256. If the
keystore does not contain the fixture's certificate, the script fails instead of producing a
mismatched pin. Only public debug certificates are involved; no private key material is printed.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PASSWORD_ENV = "FIXTURE_DEBUG_PASSWORD"
ALIAS = "androiddebugkey"


def run(cmd, **kwargs):
    return subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)


def fixture_signer_sha256(apksigner: Path, fixture_apk: Path) -> str:
    """Return the single SHA-256 signer digest of the fixture APK, failing on zero or many signers."""
    text = run([str(apksigner), "verify", "--print-certs", str(fixture_apk)]).stdout.decode()
    digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})", text)
    if len(digests) != 1:
        raise SystemExit(f"fixture APK must have exactly one signer; found {len(digests)}")
    return digests[0]


def keystore_certificate_sha256(keystore: Path) -> str:
    cert = run(["keytool", "-exportcert", "-alias", ALIAS, "-keystore", str(keystore),
                "-storepass:env", PASSWORD_ENV]).stdout
    return hashlib.sha256(cert).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--keystore", required=True)
    parser.add_argument("--fixture-apk", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    out = Path(args.output_dir)
    out.mkdir(parents=True, exist_ok=True)
    tools = Path(args.sdk) / "build-tools/36.0.0"
    apksigner = tools / "apksigner"
    fixture_apk = Path(args.fixture_apk)
    keystore = Path(args.keystore)
    os.environ[PASSWORD_ENV] = "android"  # Standard disposable Android debug password, not a release secret.

    # Signer identity comes from the fixture APK, never from an assumed keystore location.
    fixture_pin = fixture_signer_sha256(apksigner, fixture_apk)
    keystore_pin = keystore_certificate_sha256(keystore)
    if keystore_pin != fixture_pin:
        raise SystemExit(
            "keystore certificate does not match the fixture APK signer; refusing to build a split "
            f"with a different key (fixture={fixture_pin}, keystore={keystore_pin})"
        )

    manifest = out / "AndroidManifest.xml"
    ns = "http://schemas.android.com/apk/res/android"
    ET.register_namespace("android", ns)
    root = ET.Element("manifest", {"package": "com.aether.fixture", "split": "config.arm64_v8a",
        f"{{{ns}}}versionCode": "1", f"{{{ns}}}versionName": "1.0.0"})
    ET.SubElement(root, "uses-sdk", {f"{{{ns}}}minSdkVersion": "24", f"{{{ns}}}targetSdkVersion": "36"})
    ET.SubElement(root, "application", {f"{{{ns}}}hasCode": "false"})
    ET.ElementTree(root).write(manifest, encoding="utf-8", xml_declaration=True)

    unsigned = out / "fixture-config-unsigned.apk"
    signed = out / "fixture-config-arm64.apk"
    run([str(tools / "aapt2"), "link", "--manifest", str(manifest), "-I",
         str(Path(args.sdk) / "platforms/android-36/android.jar"), "-o", str(unsigned)])
    run([str(apksigner), "sign", "--ks", str(keystore), "--ks-key-alias", ALIAS,
         "--ks-pass", f"env:{PASSWORD_ENV}", "--key-pass", f"env:{PASSWORD_ENV}",
         "--out", str(signed), str(unsigned)])
    run([str(apksigner), "verify", str(signed)])

    split_pin = fixture_signer_sha256(apksigner, signed)
    if split_pin != fixture_pin:
        raise SystemExit(f"split signer {split_pin} does not equal fixture signer {fixture_pin}")
    (out / "fixture-signer.sha256").write_text(fixture_pin + "\n")
    print("config fixture created:", signed, "signer=", fixture_pin)
    return 0


if __name__ == "__main__":
    sys.exit(main())
