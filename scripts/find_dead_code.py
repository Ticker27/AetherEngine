#!/usr/bin/env python3
"""Dead-code and dangling-reference audit for AetherEngine.

Answers two questions the compiler cannot:

  1. What is declared but never referenced anywhere  ->  dead code to delete.
  2. What is referenced but does not exist           ->  a dangling reference that fails at
                                                        runtime, not at compile time.

Reference sources scanned: every text file in the repository (Kotlin, Java, Dart, C++, Python,
YAML, Markdown, XML). Framework entry points are held in an explicit allowlist with a reason,
because the platform — not our code — is their caller.

A declaration is dead when its name occurs exactly once in the whole repository: its own
declaration. One extra occurrence anywhere — a call, a type position, a `when` branch, a
manifest entry, a doc link — keeps it.

Exit code 1 when anything is dangling; dead code is reported as a warning so a human decides.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

SKIP_DIRS = {".git", "build", ".gradle", ".dart_tool", "ephemeral"}
# Flutter's per-platform scaffold folders. Matched only as direct children of the Flutter
# module, because "web" is also a real package name in this project (virtualization/web).
PLATFORM_DIRS = {"ios", "windows", "linux", "macos", "web"}
TEXT_SUFFIXES = {
    ".kt", ".java", ".dart", ".cpp", ".h", ".hpp", ".py", ".yml", ".yaml", ".md", ".xml",
    ".json", ".txt", ".sh", ".kts", ".gradle", ".properties",
}

# Declarations the platform calls, so our code never references them by name.
FRAMEWORK_ENTRY_POINTS = {
    # JUnit
    "@Test", "@Before", "@After", "@BeforeClass", "@AfterClass",
    # Android component callbacks (framework -> our override)
    "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy", "onBind",
    "onStartCommand", "onTaskRemoved", "onNewIntent", "onReceive", "onRevoke", "onCreateView",
    "attachBaseContext", "onConfigurationChanged", "onSaveInstanceState", "onRestoreInstanceState",
    "onActivityResult", "onRequestPermissionsResult", "onLowMemory", "onTrimMemory",
    "onCreateOptionsMenu", "onOptionsItemSelected", "onBackPressed", "onUserLeaveHint",
    "onProviderDeleted", "call", "query", "insert", "delete", "update", "getType", "openFile",
    "onStartJob", "onStopJob", "attachInfo", "configureFlutterEngine", "cleanUpFlutterEngine",
    # JNI
    "JNI_OnLoad", "JNI_OnUnload",
}


def is_skipped(path: Path) -> bool:
    """True for generated/VCS/platform-scaffold paths that must not be audited."""
    parts = path.parts
    if any(part in SKIP_DIRS for part in parts):
        return True
    # Only treat a platform folder as scaffold when it sits directly under flutter-app/.
    for index, part in enumerate(parts[:-1]):
        if part == "flutter-app" and parts[index + 1] in PLATFORM_DIRS:
            return True
    return False


def text_files() -> list[Path]:
    out = []
    for path in ROOT.rglob("*"):
        if is_skipped(path):
            continue
        if path.suffix in TEXT_SUFFIXES and path.is_file():
            out.append(path)
    return out


def blank_out(text: str) -> str:
    """Replace comments, string literals, and char literals with blanks of equal length.

    Line and column offsets are preserved so a match position in the blanked text maps
    exactly onto the original source. Removing the noise instead of blanking it shifts every
    later line number, which silently mis-attributes every finding.
    """
    def blank(match: re.Match) -> str:
        return re.sub(r"[^\n]", " ", match.group(0))

    text = re.sub(r'"""(?:.|\n)*?"""', blank, text)
    text = re.sub(r'(?<!\\)"(?:[^"\\\n]|\\.)*"', blank, text)
    text = re.sub(r"(?<!\\)'(?:[^'\\\n]|\\.)'", blank, text)
    text = re.sub(r"//[^\n]*", blank, text)
    text = re.sub(r"/\*(?:.|\n)*?\*/", blank, text)
    return text


def kotlin_declarations(path: Path) -> list[tuple[str, str, int]]:
    """(kind, name, line) for every declaration in a Kotlin file."""
    text = blank_out(path.read_text(encoding="utf-8"))
    found: list[tuple[str, str, int]] = []
    patterns = [
        ("class", r"(?m)^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|data\s+|value\s+|inner\s+|final\s+)*class\s+([\w$]+)"),
        ("interface", r"(?m)^\s*(?:public\s+|internal\s+|private\s+|sealed\s+|fun\s+)*interface\s+([\w$]+)"),
        ("object", r"(?m)^\s*(?:public\s+|internal\s+|private\s+)*object\s+([\w$]+)"),
        ("enum class", r"(?m)^\s*(?:public\s+|internal\s+|private\s+)*enum\s+class\s+([\w$]+)"),
        ("fun", r"(?m)^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|internal\s+|private\s+|protected\s+|inline\s+|noinline\s+|crossinline\s+|suspend\s+|operator\s+|infix\s+|external\s+|tailrec\s+|abstract\s+|open\s+|override\s+)*fun\s+(?:<[^>]+>\s*)?(?:[\w$.]+\.)?([\w$]+)\s*\("),
        ("val", r"(?m)^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|internal\s+|private\s+|protected\s+|const\s+|lateinit\s+|override\s+|open\s+|abstract\s+)*val\s+([\w$]+)"),
        ("var", r"(?m)^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|internal\s+|private\s+|protected\s+|const\s+|lateinit\s+|override\s+|open\s+|abstract\s+)*var\s+([\w$]+)"),
    ]
    for kind, pattern in patterns:
        for match in re.finditer(pattern, text):
            line = text[: match.start()].count("\n") + 1
            found.append((kind, match.group(1), line))
    return found


def has_test_annotation(path: Path, line: int) -> bool:
    lines = path.read_text(encoding="utf-8").splitlines()
    window = lines[max(0, line - 4) : line + 1]
    return any(annotation in candidate for candidate in window for annotation in ("@Test", "@Before", "@After"))


def is_override(path: Path, line: int) -> bool:
    lines = path.read_text(encoding="utf-8").splitlines()
    window = lines[max(0, line - 4) : line + 1]
    return any("override" in candidate or "@Override" in candidate for candidate in window)


def build_reference_counts(files: list[Path]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for path in files:
        text = blank_out(path.read_text(encoding="utf-8", errors="replace"))
        for match in re.finditer(r"\b([A-Za-z_][A-Za-z0-9_$]*)\b", text):
            counts[match.group(1)] = counts.get(match.group(1), 0) + 1
    return counts


def manifest_components() -> dict[str, tuple[str, str]]:
    components: dict[str, tuple[str, str]] = {}
    manifests = [
        (ROOT / "android-host/src/main/AndroidManifest.xml", "android-host/src/main/AndroidManifest.xml"),
        (ROOT / "fixture-guest/src/main/AndroidManifest.xml", "fixture-guest/src/main/AndroidManifest.xml"),
    ]
    for path, relative in manifests:
        if not path.is_file():
            continue
        app = ET.parse(path).getroot().find("application")
        if app is None:
            continue
        for tag in ("activity", "service", "receiver", "provider"):
            for node in app.findall(tag):
                name = node.get(f"{ANDROID_NS}name", "")
                if name:
                    components[name] = (tag, relative)
    return components


def kotlin_class_names() -> set[str]:
    """Every declared class name, including nested ones composed with their owner.

    `FileProvider$a` is declared as a nested `class a` inside `FileProvider`, so the simple
    name alone never matches a manifest entry. Both spellings are indexed.
    """
    names: set[str] = set()
    for path in sorted(ROOT.rglob("*.kt")):
        if is_skipped(path):
            continue
        text = blank_out(path.read_text(encoding="utf-8"))
        # Track the most recent top-level class so nested declarations compose to Owner$Nested.
        owner: str | None = None
        for line in text.splitlines():
            top = re.match(r"^(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|data\s+|value\s+|inner\s+|final\s+)*class\s+([\w$]+)", line)
            if top:
                owner = top.group(1)
                names.add(owner)
                continue
            nested = re.match(r"^\s+(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|data\s+|inner\s+|final\s+)*class\s+([\w$]+)", line)
            if nested and owner:
                names.add(nested.group(1))
                names.add(f"{owner}${nested.group(1)}")
    # Java sources: the fixture guest is Java, so its classes are declared with `class X`.
    for path in sorted(ROOT.rglob("*.java")):
        if is_skipped(path):
            continue
        for match in re.finditer(r"\bclass\s+([\w$]+)", path.read_text(encoding="utf-8")):
            names.add(match.group(1))
    return names


def string_resources() -> dict[str, Path]:
    found: dict[str, Path] = {}
    for res in sorted((ROOT / "android-host/src/main/res").rglob("*.xml")):
        for match in re.finditer(r'<string name="([\w$]+)"', res.read_text(encoding="utf-8")):
            found[match.group(1)] = res
    return found


def main() -> int:
    files = text_files()
    counts = build_reference_counts(files)
    problems: list[str] = []
    dead: list[str] = []
    manifest_class_names = {
        name.rsplit(".", 1)[-1] for name in manifest_components()
    }

    # ---- 1. dead declarations -------------------------------------------------
    for path in sorted(ROOT.rglob("*.kt")):
        if is_skipped(path):
            continue
        relative = path.relative_to(ROOT)
        in_test_source = "src/test/" in path.as_posix() or "src/androidTest/" in path.as_posix()
        for kind, name, line in kotlin_declarations(path):
            if name in ("companion", "it", "this"):
                continue
            # JUnit discovers test classes and @Test/@Before/@After methods reflectively.
            if in_test_source:
                continue
            if has_test_annotation(path, line) or is_override(path, line):
                continue
            if name in FRAMEWORK_ENTRY_POINTS or name in manifest_class_names:
                continue
            if name in FRAMEWORK_ENTRY_POINTS:
                continue
            # Local vals/vars are used inside their own function, so their name occurs again
            # in the same file. A count of 1 means nothing anywhere uses it.
            if counts.get(name, 0) <= 1:
                dead.append(f"{relative}:{line}: {kind} '{name}' occurs only at its declaration")

    # ---- 2. dangling manifest -> class references ------------------------------
    classes = kotlin_class_names()
    for name, (tag, relative) in manifest_components().items():
        simple = name.rsplit(".", 1)[-1]
        if simple in classes or name in classes:
            continue
        problems.append(f"{relative}: <{tag}> {name} has no declaring Kotlin class")

    # ---- 3. workflow references to paths that do not exist ----------------------
    for workflow in sorted((ROOT / ".github/workflows").glob("*.yml")):
        text = workflow.read_text(encoding="utf-8")
        for candidate in re.findall(r"(?<![\w-])((?:scripts|tools|integration-test)/[\w./-]+)", text):
            if not (ROOT / candidate).exists():
                problems.append(f".github/workflows/{workflow.name}: references missing path {candidate}")

    # ---- 4. string resources never referenced ----------------------------------
    all_kotlin = "\n".join(
        path.read_text(encoding="utf-8", errors="replace")
        for path in ROOT.rglob("*.kt")
        if "build" not in path.parts
    )
    all_xml = "\n".join(
        path.read_text(encoding="utf-8", errors="replace")
        for path in ROOT.rglob("*.xml")
        if "build" not in path.parts
    )
    for name, res in string_resources().items():
        if f"R.string.{name}" not in all_kotlin and f"@string/{name}" not in all_xml:
            problems.append(f"{res.relative_to(ROOT)}: string '{name}' is never referenced")

    # ---- 5. scripts not wired into any check ------------------------------------
    # A tool is reachable when a shell script, workflow, Gradle file, or the plan names it.
    wiring_suffixes = {".sh", ".yml", ".py", ".md", ".kts", ".gradle"}
    for path in sorted(list((ROOT / "scripts").glob("*.py")) + list((ROOT / "tools").glob("*.py"))):
        mentioned = any(
            path.name in other.read_text(encoding="utf-8", errors="replace")
            for other in files
            if other != path and other.suffix in wiring_suffixes
        )
        if not mentioned:
            problems.append(f"{path.relative_to(ROOT)}: not referenced by any script, workflow, or check")

    if problems:
        print(f"FAIL: {len(problems)} dangling reference(s)", file=sys.stderr)
        for problem in sorted(set(problems)):
            print(f"  {problem}", file=sys.stderr)
    if dead:
        print(f"DEAD: {len(dead)} unreferenced declaration(s)", file=sys.stderr)
        for entry in sorted(set(dead)):
            print(f"  {entry}", file=sys.stderr)
    if not problems and not dead:
        print("No dead declarations and no dangling references.")
        return 0
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
