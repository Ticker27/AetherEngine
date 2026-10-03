#!/usr/bin/env python3
"""Validate the host-container source tree and Android component registrations."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
MANIFEST_PATH = ROOT / "android-host/src/main/AndroidManifest.xml"

REQUIRED_FILES = [
    "android-host/src/main/kotlin/com/aether/host/AetherApplication.kt",
    "android-host/src/main/kotlin/com/aether/host/bootstrap/HostInitializer.kt",
    "android-host/src/main/kotlin/com/aether/host/bridge/Native.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/loader/DynamicApkLoader.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/loader/GuestApkTrustPolicy.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/activity/VirtualActivity.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyPendingActivity.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/activity/TransparentProxyActivity.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/components/receiver/ProxyBroadcastReceiver.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/FileProvider.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/ProxyContentProvider.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/SystemCallProvider.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/DaemonService.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyJobService.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyService.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyVpnService.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/flags/flagger.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/util/MethodUtils.kt",
    "android-host/src/main/kotlin/com/aether/host/virtualization/web/InternalWebBrowser.kt",
    "android-host/src/main/res/xml/aether_file_paths.xml",
    "android-host/src/main/res/values/styles.xml",
    "docs/host-container.md",
]

PACKAGE = "com.aether.host.virtualization"
ACTIVITIES = [
    "com.aether.host.MainActivity",
    *[f"{PACKAGE}.activity.ProxyActivityP{i}" for i in range(4)],
    *[f"{PACKAGE}.activity.ProxyActivityP{i}_L" for i in range(4)],
    *[f"{PACKAGE}.activity.TransparentProxyActivityP{i}" for i in range(4)],
    *[f"{PACKAGE}.activity.ProxyPendingActivityP{i}" for i in range(4)],
    f"{PACKAGE}.web.InternalWebBrowser",
]
SERVICES = [
    f"{PACKAGE}.components.service.DaemonService",
    f"{PACKAGE}.components.service.DaemonInnerService",
    *[f"{PACKAGE}.components.service.ProxyServiceP{i}" for i in range(4)],
    *[f"{PACKAGE}.components.service.ProxyJobServiceP{i}" for i in range(4)],
    f"{PACKAGE}.components.service.ProxyVpnService",
]
RECEIVERS = [f"{PACKAGE}.components.receiver.ProxyBroadcastReceiver"]
PROVIDERS = [
    f"{PACKAGE}.components.provider.FileProvider$a",
    f"{PACKAGE}.components.provider.FileProvider$b",
    *[f"{PACKAGE}.components.provider.ProxyContentProviderP{i}" for i in range(4)],
    f"{PACKAGE}.components.provider.SystemCallProvider",
]

SOURCE_CLASSES = {
    "AetherApplication": "android-host/src/main/kotlin/com/aether/host/AetherApplication.kt",
    "MainActivity": "android-host/src/main/kotlin/com/aether/host/MainActivity.kt",
    "ProxyActivityP0": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "ProxyActivityP1": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "ProxyActivityP2": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "ProxyActivityP3": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "ProxyActivityP0_L": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "ProxyActivityP1_L": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "ProxyActivityP2_L": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "ProxyActivityP3_L": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyActivity.kt",
    "TransparentProxyActivityP0": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/TransparentProxyActivity.kt",
    "TransparentProxyActivityP1": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/TransparentProxyActivity.kt",
    "TransparentProxyActivityP2": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/TransparentProxyActivity.kt",
    "TransparentProxyActivityP3": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/TransparentProxyActivity.kt",
    "ProxyPendingActivityP0": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyPendingActivity.kt",
    "ProxyPendingActivityP1": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyPendingActivity.kt",
    "ProxyPendingActivityP2": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyPendingActivity.kt",
    "ProxyPendingActivityP3": "android-host/src/main/kotlin/com/aether/host/virtualization/activity/ProxyPendingActivity.kt",
    "InternalWebBrowser": "android-host/src/main/kotlin/com/aether/host/virtualization/web/InternalWebBrowser.kt",
    "ProxyBroadcastReceiver": "android-host/src/main/kotlin/com/aether/host/virtualization/components/receiver/ProxyBroadcastReceiver.kt",
    "ProxyContentProviderP0": "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/ProxyContentProvider.kt",
    "ProxyContentProviderP1": "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/ProxyContentProvider.kt",
    "ProxyContentProviderP2": "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/ProxyContentProvider.kt",
    "ProxyContentProviderP3": "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/ProxyContentProvider.kt",
    "SystemCallProvider": "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/SystemCallProvider.kt",
    "ProxyServiceP0": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyService.kt",
    "ProxyServiceP1": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyService.kt",
    "ProxyServiceP2": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyService.kt",
    "ProxyServiceP3": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyService.kt",
    "ProxyJobServiceP0": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyJobService.kt",
    "ProxyJobServiceP1": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyJobService.kt",
    "ProxyJobServiceP2": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyJobService.kt",
    "ProxyJobServiceP3": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyJobService.kt",
    "DaemonService": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/DaemonService.kt",
    "DaemonInnerService": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/DaemonService.kt",
    "ProxyVpnService": "android-host/src/main/kotlin/com/aether/host/virtualization/components/service/ProxyVpnService.kt",
    "FileProvider": "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/FileProvider.kt",
    "a": "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/FileProvider.kt",
    "b": "android-host/src/main/kotlin/com/aether/host/virtualization/components/provider/FileProvider.kt",
}


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def resolve_manifest_name(name: str) -> str:
    if name.startswith("."):
        return f"com.aether.host{name}"
    return name


def main() -> None:
    for relative in REQUIRED_FILES:
        if not (ROOT / relative).is_file():
            fail(f"missing required host file: {relative}")

    for simple_name, relative in SOURCE_CLASSES.items():
        source = (ROOT / relative).read_text()
        if not re.search(rf"\bclass\s+{re.escape(simple_name)}\b", source):
            fail(f"manifest component class {simple_name} is missing from {relative}")

    try:
        manifest = ET.parse(MANIFEST_PATH).getroot()
    except ET.ParseError as error:
        fail(f"invalid AndroidManifest.xml: {error}")

    application = manifest.find("application")
    if application is None:
        fail("AndroidManifest.xml has no <application>")

    tags = {"activity": ACTIVITIES, "service": SERVICES, "receiver": RECEIVERS, "provider": PROVIDERS}
    declared: dict[str, dict[str, ET.Element]] = {}
    for tag, expected_names in tags.items():
        nodes = application.findall(tag)
        by_name = {
            resolve_manifest_name(node.get(f"{ANDROID_NS}name", "")): node
            for node in nodes
        }
        declared[tag] = by_name
        for expected in expected_names:
            if expected not in by_name:
                fail(f"manifest does not declare <{tag}> {expected}")

    for tag in ("activity", "service", "receiver", "provider"):
        for name, node in declared[tag].items():
            exported = node.get(f"{ANDROID_NS}exported")
            permission = node.get(f"{ANDROID_NS}permission")
            if name == f"{PACKAGE}.components.service.ProxyVpnService":
                if exported != "true" or permission != "android.permission.BIND_VPN_SERVICE":
                    fail("ProxyVpnService must be system-bindable only (exported + BIND_VPN_SERVICE)")
            elif tag == "activity" and name == "com.aether.host.MainActivity":
                if exported != "true":
                    fail("launcher MainActivity must be exported")
            elif exported != "false":
                fail(f"host component must explicitly set android:exported=false: {name}")

    for i in range(4):
        job_name = f"{PACKAGE}.components.service.ProxyJobServiceP{i}"
        if declared["service"][job_name].get(f"{ANDROID_NS}permission") != "android.permission.BIND_JOB_SERVICE":
            fail(f"{job_name} must require BIND_JOB_SERVICE")

    for suffix in ("a", "b"):
        provider = declared["provider"][f"{PACKAGE}.components.provider.FileProvider${suffix}"]
        if provider.get(f"{ANDROID_NS}grantUriPermissions") != "true":
            fail(f"FileProvider${suffix} must grant URI permissions explicitly")
        if not any(meta.get(f"{ANDROID_NS}name") == "android.support.FILE_PROVIDER_PATHS"
                   for meta in provider.findall("meta-data")):
            fail(f"FileProvider${suffix} is missing AndroidX path metadata")

    file_paths = (ROOT / "android-host/src/main/res/xml/aether_file_paths.xml").read_text()
    if re.search(r"<(external-path|external-cache-path|external-files-path|root-path)\b", file_paths):
        fail("FileProvider paths must remain limited to host-owned internal directories")

    native_source = (ROOT / "android-host/src/main/kotlin/com/aether/host/bridge/Native.kt").read_text()
    registry_source = (ROOT / "aether-native/src/main/cpp/jni/native_registry.cpp").read_text()
    if "package com.aether.host.bridge" not in native_source or "object Native" not in native_source:
        fail("Native facade Kotlin package is not the expected bridge package")
    if 'com/aether/host/bridge/Native' not in registry_source:
        fail("JNI registry class path does not match the Native Kotlin package")

    print("Host-container structure and manifest registrations are consistent.")


if __name__ == "__main__":
    main()
