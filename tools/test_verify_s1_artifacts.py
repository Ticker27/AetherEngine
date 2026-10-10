"""Synthetic ZIP/XML tests only: no SDK, Gradle, credentials or external APKs."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

import verify_s1_artifacts as check

LOADER = "com.aether.host.virtualization.loader.FixtureGuestLoaderTest"
LAUNCH = "com.aether.host.virtualization.loader.FixtureGuestLaunchTest"


def apk_bytes(members):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in members.items():
            archive.writestr(name, data)
    return stream.getvalue()


BASE = {"AndroidManifest.xml": b"synthetic manifest", "classes.dex": b"synthetic DEX"}


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.host = self.repo / "android-host/build/outputs/apk/debug/custom-host.apk"
        # Regress the old incorrect debugAndroidTest glob assumption.
        self.test = self.repo / "android-host/build/outputs/apk/androidTest/debug/custom-test.apk"
        self.fixture = self.repo / "fixture-guest/build/outputs/apk/debug/custom-fixture.apk"
        self.split = self.repo / "android-host/build/generated/fixtureSplit/fixture-config-arm64.apk"
        self.put(self.split, {"AndroidManifest.xml": b"synthetic code-free config manifest"})
        self.make_fixture()
        self.put(self.host, {**BASE, "lib/x86_64/libaether.so": b"synthetic ELF"})
        self.make_test()

    def put(self, path, members):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(apk_bytes(members))

    def make_fixture(self, **overrides):
        self.put(self.fixture, {**BASE, "assets/fixture.txt": b"first-party fixture", **overrides})

    def make_test(self, extra=None):
        self.put(self.test, {**BASE, check.FIXTURE: self.fixture.read_bytes(),
                            check.CONFIG_SPLIT: self.split.read_bytes(),
                            check.SIGNER_PIN: b"a" * 64 + b"\n", **(extra or {})})

    def inspect(self, abi="x86_64"):
        return check.inspect_artifacts(self.repo, abi)

    def invalid(self, text):
        report = self.inspect()
        self.assertFalse(report["valid"], report)
        self.assertIn(text, "\n".join(report["issues"]))
        return report

    def test_actual_candidates_and_nested_fixture_hash(self):
        report = self.inspect()
        self.assertTrue(report["valid"], report["issues"])
        self.assertEqual(report["selected"]["test"], self.test.relative_to(self.repo).as_posix())
        self.assertEqual(report["embedded_fixture"]["sha256"], check.file_sha256(self.fixture))
        self.assertIn("assets/fixture.txt", report["embedded_fixture"]["member_sha256"])
        self.assertEqual(len(report["candidates"]), 3)
        self.assertEqual(report["arm64_result"], "not_run")
        self.assertFalse(report["s1_complete"])

    def test_public_signer_pin_evidence(self):
        report = self.inspect()
        self.assertTrue(report["valid"], report["issues"])
        self.assertEqual(report["fixture_signer_sha256"], "a" * 64)

    def test_missing_signer_pin(self):
        self.put(self.test, {**BASE, check.FIXTURE: self.fixture.read_bytes(),
                            check.CONFIG_SPLIT: self.split.read_bytes()})
        self.invalid("fixture signer pin:")

    def test_empty_signer_pin(self):
        self.make_test({check.SIGNER_PIN: b""})
        self.invalid("empty member")

    def test_signer_pin_bad_format(self):
        for pin in (b"A" * 64, b"a" * 63, b"g" * 64, b" a" + b"a" * 63):
            with self.subTest(pin=pin):
                self.make_test({check.SIGNER_PIN: pin})
                self.invalid("64 lowercase hex")

    def test_config_split_hash_and_code_free_evidence(self):
        report = self.inspect()
        self.assertTrue(report["valid"], report["issues"])
        self.assertEqual(report["embedded_config_split"]["sha256"], check.file_sha256(self.split))
        self.assertNotIn("classes.dex", report["config_split"]["member_sha256"])

    def test_missing_config_asset(self):
        self.put(self.test, {**BASE, check.FIXTURE: self.fixture.read_bytes()})
        self.invalid("embedded APKs must be")

    def test_missing_generated_config(self):
        self.split.unlink()
        self.invalid("config split:")

    def test_stale_embedded_config(self):
        self.put(self.split, {"AndroidManifest.xml": b"changed config"})
        self.invalid("staged config split differs")

    def test_code_in_config_split_rejected(self):
        self.put(self.split, BASE)
        self.make_test()
        self.invalid("code-free config split must not contain DEX")

    def test_native_code_in_config_split_rejected(self):
        self.put(self.split, {"AndroidManifest.xml": b"config", "lib/arm64-v8a/libextra.so": b"ELF"})
        self.make_test()
        self.invalid("no nested APKs or native libraries")

    def test_missing_test(self):
        self.test.unlink()
        self.invalid("exactly one test")

    def test_two_test_apks(self):
        self.put(self.test.with_name("second.apk"), {**BASE, check.FIXTURE: self.fixture.read_bytes()})
        self.invalid("exactly one test")

    def test_two_hosts(self):
        self.put(self.host.with_name("second.apk"), BASE)
        self.invalid("exactly one host")

    def test_missing_fixture(self):
        self.fixture.unlink()
        self.invalid("exactly one fixture")

    def test_missing_embedded_fixture(self):
        self.put(self.test, BASE)
        self.invalid("embedded APKs must be")

    def test_fixture_is_not_host_asset(self):
        self.put(self.host, {**BASE, check.FIXTURE: self.fixture.read_bytes()})
        self.invalid("embedded APKs must be []")

    def test_extra_embedded_apk_rejected(self):
        self.make_test({"assets/another.apk": b"not permitted"})
        self.invalid("embedded APKs must be")

    def test_stale_staged_fixture_rejected(self):
        self.make_fixture(**{"assets/fixture.txt": b"changed after staging"})
        self.invalid("staged fixture differs")

    def test_missing_fixture_text(self):
        self.put(self.fixture, BASE)
        self.make_test()
        self.invalid("missing assets/fixture.txt")

    def test_missing_fixture_dex(self):
        self.put(self.fixture, {"AndroidManifest.xml": b"manifest", "assets/fixture.txt": b"fixture"})
        self.make_test()
        self.invalid("classes.dex")

    def test_empty_required_member(self):
        self.make_fixture(**{"assets/fixture.txt": b""})
        self.make_test()
        self.invalid("empty member")

    def test_host_must_not_embed_fixture_pin(self):
        self.put(self.host, {**BASE, "lib/x86_64/libaether.so": b"ELF", check.SIGNER_PIN: b"a" * 64})
        self.invalid("signer pin must not be in host assets")

    def test_bad_zip(self):
        self.test.write_bytes(b"not a ZIP")
        self.invalid("not a zip file")

    def test_duplicate_zip_member(self):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.test, "a") as archive:
                archive.writestr(check.FIXTURE, self.fixture.read_bytes())
        self.invalid("duplicate ZIP members")

    def test_wrong_native_abi(self):
        self.invalid_for_arm = check.inspect_artifacts(self.repo, "arm64-v8a")
        self.assertFalse(self.invalid_for_arm["valid"])
        self.assertIn("missing libaether.so", "\n".join(self.invalid_for_arm["issues"]))

    def test_mixed_host_abi_rejected(self):
        self.put(self.host, {**BASE, "lib/x86_64/libaether.so": b"x86",
                            "lib/arm64-v8a/libaether.so": b"arm"})
        self.invalid("unexpected native ABIs")

    def test_arm64_artifact_only_supported_not_accepted(self):
        self.put(self.host, {**BASE, "lib/arm64-v8a/libaether.so": b"arm"})
        report = self.inspect("arm64-v8a")
        self.assertTrue(report["valid"])
        self.assertEqual(report["arm64_result"], "not_run")
        self.assertFalse(report["s1_complete"])

    def test_unexpected_release_apk_rejected(self):
        self.put(self.host.parent.parent / "release" / "host-release.apk", BASE)
        self.invalid("unexpected APK candidate")

    def test_cli_copies_exact_debug_bytes(self):
        output, copy = self.repo / "evidence.json", self.repo / "copy"
        with contextlib.redirect_stdout(io.StringIO()):
            rc = check.main(["apks", "--repo", str(self.repo), "--abi", "x86_64",
                             "--output", str(output), "--copy-to", str(copy)])
        self.assertEqual(rc, 0)
        self.assertTrue(json.loads(output.read_text())["valid"])
        self.assertEqual((copy / "test-debug.apk").read_bytes(), self.test.read_bytes())
        self.assertIn(check.file_sha256(self.host), (copy / "SHA256SUMS.source").read_text())

    def test_cli_failure_writes_evidence_without_copy(self):
        self.test.unlink()
        output, copy = self.repo / "evidence.json", self.repo / "copy"
        with contextlib.redirect_stdout(io.StringIO()):
            rc = check.main(["apks", "--repo", str(self.repo), "--abi", "x86_64",
                             "--output", str(output), "--copy-to", str(copy)])
        self.assertEqual(rc, 1)
        self.assertFalse(json.loads(output.read_text())["valid"])
        self.assertFalse(copy.exists())


class XmlTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def put(self, text, name="TEST.xml"):
        path = self.root / name
        path.write_text(text)
        return path

    def suite(self, classname=LOADER, child="", tests=1, failures=0, errors=0, skipped=0):
        return (f'<testsuite tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}">'
                f'<testcase classname="{classname}" name="scoped">{child}</testcase></testsuite>')

    def inspect(self, expected=(LOADER,)):
        return check.inspect_xml([self.root], expected)

    def invalid(self, issue):
        report = self.inspect()
        self.assertFalse(report["valid"])
        self.assertIn(issue, "\n".join(report["issues"]))

    def test_pass_nonzero_scoped(self):
        self.put(self.suite())
        report = self.inspect()
        self.assertTrue(report["valid"], report)
        self.assertEqual(report["tests"], 1)
        self.assertEqual(report["failures"], 0)
        self.assertEqual(report["class_pass_counts"][LOADER], 1)

    def test_loader_and_launch_both_required_when_selected(self):
        self.put(self.suite(), "loader.xml")
        self.put(self.suite(LAUNCH), "launch.xml")
        report = self.inspect((LOADER, LAUNCH))
        self.assertTrue(report["valid"], report)
        self.assertEqual(report["tests"], 2)

    def test_missing_xml(self):
        self.invalid("no test XML")

    def test_malformed_xml(self):
        self.put("<testsuite")
        self.invalid("unclosed token")

    def test_not_junit(self):
        self.put('<resources><item>not tests</item></resources>')
        self.invalid("not a JUnit")

    def test_empty_suite(self):
        self.put('<testsuite tests="0" failures="0"/>')
        self.invalid("test count must be greater than zero")

    def test_failures_are_required_zero(self):
        self.put(self.suite(child='<failure message="actual failure"/>', failures=1))
        self.invalid("test failures/errors present")

    def test_errors_are_required_zero(self):
        self.put(self.suite(child='<error/>', errors=1))
        self.invalid("test failures/errors present")

    def test_all_skipped_is_not_success(self):
        self.put(self.suite(child='<skipped/>', skipped=1))
        self.invalid("no executed passing tests")

    def test_skipped_in_scoped_run_is_rejected(self):
        self.put(self.suite(), "pass.xml")
        self.put(self.suite(child='<skipped/>', skipped=1), "skip.xml")
        self.invalid("must not be skipped")

    def test_nonzero_counts_without_cases_rejected(self):
        self.put('<testsuite tests="10" failures="0"/>')
        self.invalid("tests count mismatch")

    def test_hidden_failure_child_rejected(self):
        self.put(self.suite(child='<failure/>'))
        self.invalid("failures count mismatch")

    def test_declared_failure_without_child_rejected(self):
        self.put(self.suite(failures=1))
        self.invalid("failures count mismatch")

    def test_missing_required_class(self):
        self.put(self.suite())
        report = self.inspect((LOADER, LAUNCH))
        self.assertFalse(report["valid"])
        self.assertIn(f"required class has no passing test: {LAUNCH}", report["issues"])

    def test_minimum_four_cases_required(self):
        self.put(self.suite())
        report = check.inspect_xml([self.root], (LOADER,), min_tests=4)
        self.assertFalse(report["valid"])
        self.assertIn("test count below required minimum: 1 < 4", report["issues"])

    def test_four_cases_pass_with_loader_and_launch(self):
        for index, name in enumerate((LOADER, LOADER, LOADER, LAUNCH)):
            self.put(self.suite(name), f"case-{index}.xml")
        report = check.inspect_xml([self.root], (LOADER, LAUNCH), min_tests=4)
        self.assertTrue(report["valid"], report)
        self.assertEqual(report["tests"], 4)

    def test_unscoped_other_test_rejected(self):
        self.put(self.suite("com.aether.OtherTest"))
        self.invalid("unscoped test case")

    def test_testsuites_root(self):
        self.put('<testsuites tests="1" failures="0">' + self.suite() + '</testsuites>')
        self.assertTrue(self.inspect()["valid"])

    def test_testsuites_root_counts_not_trusted(self):
        self.put('<testsuites tests="1" failures="1">' + self.suite() + '</testsuites>')
        self.invalid("aggregate failures count mismatch")

    def test_nested_suites_do_not_double_count(self):
        self.put('<testsuite tests="1" failures="0">' + self.suite() + '</testsuite>')
        report = self.inspect()
        self.assertTrue(report["valid"], report)
        self.assertEqual(report["tests"], 1)

    def test_dtd_not_accepted(self):
        self.put('<!DOCTYPE testsuite>' + self.suite())
        self.invalid("DTD/entity")

    def test_unit_tests_need_no_specific_class_but_must_execute(self):
        self.put(self.suite("com.aether.host.LocalTest"))
        self.assertTrue(self.inspect(())["valid"])

    def test_cli_xml_failure_is_nonzero(self):
        output = self.root / "report.json"
        with contextlib.redirect_stdout(io.StringIO()):
            rc = check.main(["xml", "--root", str(self.root), "--output", str(output)])
        self.assertEqual(rc, 1)
        self.assertFalse(json.loads(output.read_text())["valid"])


class ScopeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.sources = self.repo / "android-host/src/androidTest/kotlin"
        self.sources.mkdir(parents=True)

    def add(self, name, package="com.aether.host.virtualization.loader", filename=None):
        path = self.sources / (filename or name + ".kt")
        path.write_text(f"package {package}\nclass {name} {{}}\n")

    def test_discover_loader_package_not_assumed_from_filename(self):
        self.add("FixtureGuestLoaderTest", "com.example.changed.loader", "Renamed.kt")
        scope = check.discover_scope(self.repo)
        self.assertEqual(scope["classes"], ["com.example.changed.loader.FixtureGuestLoaderTest"])
        self.assertEqual(len(scope["sources"][0]["source_sha256"]), 64)

    def test_discover_launch_both_package_paths(self):
        self.add("FixtureGuestLoaderTest")
        self.add("FixtureGuestLaunchTest", "com.aether.host.virtualization.launch")
        self.assertEqual(check.discover_scope(self.repo)["classes"],
                         [LOADER, "com.aether.host.virtualization.launch.FixtureGuestLaunchTest"])

    def test_missing_loader_rejected(self):
        self.add("FixtureGuestLaunchTest")
        with self.assertRaisesRegex(ValueError, "require one Loader"):
            check.discover_scope(self.repo)

    def test_ambiguous_loader_rejected(self):
        self.add("FixtureGuestLoaderTest")
        self.add("FixtureGuestLoaderTest", "com.other", "Duplicate.kt")
        with self.assertRaisesRegex(ValueError, "require one Loader"):
            check.discover_scope(self.repo)


if __name__ == "__main__":
    unittest.main()
