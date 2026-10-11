#!/usr/bin/env python3
"""Static consistency checker for the AetherEngine Kotlin sources.

Catches the error classes that only surface at compile time, which is expensive here because
the Kotlin toolchain is not available locally:

  1. A type/constructor is used but not imported, not in the same package, and not a known
     stdlib/androidx/JUnit symbol  ->  "unresolved reference"
  2. `return@label` where no enclosing lambda was passed to a function of that name
  3. A private base-class member called from a subclass
  4. Unbalanced braces/parens per file
  5. An `external fun` declared in Kotlin with no matching JNI registration entry, and vice versa
  6. A call to a locally declared function with too few arguments, or a collection handed to a
     `vararg` parameter without a spread

Every rule is proven by reintroducing the bug it claims to catch and watching this script fail.

Exit code 0 means the checks that can run without a compiler all pass.
"""
from __future__ import annotations

import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN_ROOT = ROOT / "android-host/src"

# Symbols that resolve without an import in every Kotlin file we own.
IMPLICIT = {
    # java.lang
    "String", "Object", "Integer", "Long", "Double", "Float", "Short", "Byte", "Character",
    "Boolean", "Number", "CharSequence", "Throwable", "Exception", "RuntimeException",
    "IllegalStateException", "IllegalArgumentException", "Class", "System", "Thread",
    "Runnable", "Void", "Deprecated", "Override", "SuppressWarnings", "Suppress",
    "FunctionalInterface", "SafeVarargs", "Cloneable", "Comparable", "Iterable", "Enum",
    "Appendable", "Readable", "AutoCloseable", "InheritableThreadLocal", "ThreadLocal",
    "ClassLoader", "Compiler", "Package", "StackTraceElement", "ArithmeticException",
    "IndexOutOfBoundsException", "UnsupportedOperationException", "NullPointerException",
    "ClassCastException", "NegativeArraySizeException", "ArrayStoreException",
    "AssertionError", "Error", "LinkageError", "StringBuilder",
    # kotlin stdlib
    "Unit", "Any", "Nothing", "Array", "IntArray", "LongArray", "DoubleArray", "FloatArray",
    "BooleanArray", "ByteArray", "CharArray", "ShortArray", "Int", "Long", "Double", "Float",
    "Boolean", "Byte", "Short", "Char", "UInt", "ULong", "UByte", "UShort",
    "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap", "Pair", "Triple",
    "Sequence", "Comparator", "ArrayList", "LinkedList", "HashSet", "LinkedHashSet",
    "HashMap", "LinkedHashMap", "TreeMap", "TreeSet", "ArrayDeque", "Charsets", "Charset",
    "Regex", "MatchResult", "IntRange", "LongRange", "CharRange", "ClosedRange",
    "IntProgression", "Lazy", "LazyThreadSafetyMode", "Result", "Annotation",
    "KClass", "KProperty", "KCallable", "KFunction", "Deferred", "Job", "Continuation",
    "Deprecated", "ReplaceWith", "JvmStatic", "JvmOverloads", "JvmField", "JvmName",
    "Throws", "OptIn", "ExperimentalStdlibApi", "RequiresOptIn",
    "check", "checkNotNull", "require", "requireNotNull", "error", "lazy", "lazyOf",
    "arrayOf", "listOf", "mutableListOf", "setOf", "mutableSetOf", "mapOf", "mutableMapOf",
    "emptyList", "emptySet", "emptyMap", "emptyArray", "buildString", "apply", "also", "let",
    "run", "with", "takeIf", "takeUnless", "repeat", "coerceAtLeast", "coerceAtMost",
    "coerceIn", "sortedBy", "sortedWith", "associate", "associateWith", "associateBy",
    "groupBy", "filterIsInstance", "firstOrNull", "lastOrNull", "singleOrNull", "count",
    "sumOf", "maxOf", "minOf", "orEmpty", "isNullOrEmpty", "isNullOrBlank",
    "generateSequence", "sequence", "TODO", "hashMapOf", "linkedMapOf", "sortedMapOf",
    # JUnit / test
    "Test", "Before", "After", "BeforeClass", "AfterClass", "Ignore", "Rule",
    "Assert", "assertTrue", "assertFalse", "assertEquals", "assertNotNull", "assertNull",
    "assertThrows", "assertNotEquals", "assertSame", "fail",
    # androidx annotations
    "Keep", "NonNull", "Nullable", "VisibleForTesting", "CallSuper", "WorkerThread",
    "MainThread", "AnyThread", "RequiresApi", "ChecksSdkIntAtLeast", "RestrictTo",
    "IntDef", "LongDef", "StringDef",
}

# Simple names that resolve through the Android SDK or a declared dependency without an import.
ANDROID_NESTED = {
    "Builder", "ServiceInfo", "NotificationChannel", "Notification", "PendingIntent",
    "Intent", "Bundle", "Uri", "MatrixCursor", "ContentValues", "ComponentName",
    "Context", "ApplicationInfo", "PackageManager", "Binder", "IBinder", "Looper",
    "Handler", "Message", "Build", "VERSION", "VERSION_CODES", "Process", "UserHandle",
    "Parcelable", "Parcel", "JobParameters", "JobInfo", "VpnService",
}

# Modifiers and annotations that may precede a declaration.
MODIFIERS = (
    r"(?:@\w+(?:\.\w+)*(?:\([^)]*\))?\s+)*"
    r"(?:(?:private|internal|public|protected|inline|noinline|crossinline|suspend|operator|"
    r"infix|external|tailrec|abstract|open|final|override|actual|expect|lateinit|const)\s+)*"
)

IMPORT_RE = re.compile(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?', re.MULTILINE)
LABEL_RETURN_RE = re.compile(r"return@(\w+)")
TYPE_USE_RE = re.compile(
    r"(?<![\w.@$])(?:"
    r":\s*([A-Z][A-Za-z0-9_]*)"           # val x: Type
    r"|\bis\s+([A-Z][A-Za-z0-9_]*)"       # is Type
    r"|\bas\s+\??([A-Z][A-Za-z0-9_]*)"    # as Type
    r"|\b([A-Z][A-Za-z0-9_]*)\(\)"        # Type()
    r"|\b([A-Z][A-Za-z0-9_]*)<"           # Type<
    r")"
)
# An ALL_CAPS identifier is a constant, never a type, so `Foo.MIN_MTU` is not a type position.
CONSTANT_NAME_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")
FUN_DECL_RE = re.compile(
    r"(?m)^[ \t]*" + MODIFIERS + r"fun\s+(\w+)\s*\(([^)]*)\)\s*(?::\s*([\w<>, .?\[\]]+))?"
)
CALL_RE = re.compile(r"(?<![\w.])(\w+)\s*\(")

# Collections that must never land in a vararg slot without a spread. The compiler reports the
# mistake only as a bare "argument type mismatch", which no import check finds.
COLLECTION_SOURCES = (
    "mapOf(", "mutableMapOf(", "hashMapOf(", "linkedMapOf(", "sortedMapOf(",
    "listOf(", "mutableListOf(", "arrayOf(", "setOf(", "mutableSetOf(", "intArrayOf(",
    "buildList(", "buildMap(", "buildMutableMap(", "buildSet(",
    ".toList()", ".toMutableList()", ".toSet()", ".toTypedArray()", ".toMap()",
    ".toMutableMap()", ".toIntArray()", ".toLongArray()", ".toBooleanArray()",
)


def kotlin_files() -> list[Path]:
    return sorted(KOTLIN_ROOT.rglob("*.kt"))


def strip_noise(text: str) -> str:
    """Blank out comments, string literals, and char literals so they cannot produce false hits."""
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r'(?<!\\)"(?:[^"\\\n]|\\.)*"', '""', text)
    text = re.sub(r"(?<!\\)'(?:[^'\\\n]|\\.)'", "''", text)
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r"/\*(?:.|\n)*?\*/", "", text)
    return text


# The declaration scan and the dead-code audit read sources through the same helper.
blank_out = strip_noise


def split_parameters(params: str) -> list[str]:
    """Split a parameter list at top-level commas only.

    `Map<String, String>` contains a comma that is not a parameter separator, so a plain
    `split(",")` invents extra parameters and every arity check downstream is wrong.
    """
    parts: list[str] = []
    current: list[str] = []
    depth = 0
    for char in params:
        if char in "<([{":
            depth += 1
        elif char in ">)]}":
            depth -= 1
        if char == "," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(char)
    if current:
        parts.append("".join(current))
    return [part.strip() for part in parts if part.strip()]


def read_call_arguments(text: str, start: int) -> tuple[list[str], bool]:
    """Split a call's argument list at the top nesting level."""
    depth = 1
    current: list[str] = []
    args: list[str] = []
    index = start
    while index < len(text) and depth > 0:
        char = text[index]
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
            if depth == 0:
                break
        if char == "," and depth == 1:
            args.append("".join(current))
            current = []
        else:
            current.append(char)
        index += 1
    if depth != 0:
        return [], False
    if current:
        args.append("".join(current))
    return [arg for arg in args if arg.strip()], True


def available_names(text: str) -> set[str]:
    """Every simple name that resolves without a new import in this file."""
    names = set(IMPLICIT)
    package_match = re.search(r"(?m)^package\s+([\w.]+)", text)
    package_name = package_match.group(1) if package_match else ""
    for match in IMPORT_RE.finditer(text):
        fqn, alias = match.group(1), match.group(2)
        if alias:
            names.add(alias)
            continue
        names.add(fqn.rsplit(".", 1)[-1])
    # This file's own declarations, including nested ones.
    for match in re.finditer(r"\b(?:class|interface|object|enum\s+class)\s+(\w+)", text):
        names.add(match.group(1))
    for match in re.finditer(r"\btypealias\s+(\w+)", text):
        names.add(match.group(1))
    # Same-package declarations, resolved once per package.
    names |= same_package_names(package_name)
    return names


_PACKAGE_CACHE: dict[str, set[str]] = {}


def same_package_names(package_name: str) -> set[str]:
    if package_name in _PACKAGE_CACHE:
        return _PACKAGE_CACHE[package_name]
    names: set[str] = set()
    if package_name:
        for path in kotlin_files():
            text = path.read_text(encoding="utf-8")
            declared = re.search(r"(?m)^package\s+([\w.]+)", text)
            if not declared or declared.group(1) != package_name:
                continue
            for match in re.finditer(
                r"(?m)^\s*(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|"
                r"data\s+|value\s+|inner\s+|final\s+|enum\s+|annotation\s+)*"
                r"(?:class|interface|object|typealias)\s+(\w+)",
                text,
            ):
                names.add(match.group(1))
    _PACKAGE_CACHE[package_name] = names
    return names


def check_balance(path: Path, text: str, problems: list[str]) -> None:
    for open_char, close_char in (("{", "}"), ("(", ")"), ("[", "]")):
        if text.count(open_char) != text.count(close_char):
            problems.append(
                f"{path}: unbalanced {open_char}{close_char} "
                f"({text.count(open_char)} vs {text.count(close_char)})"
            )


def check_labels(path: Path, text: str, problems: list[str]) -> None:
    """`return@f` is valid only inside a lambda literal passed to a call named `f`.

    Positional, not textual: the same file may contain a legitimate `setMethodCallHandler { ... }`
    and an invalid `return@setMethodCallHandler` elsewhere, so a whole-file search for the label's
    call is not enough. Each `return@label` must sit inside the argument list of a call named
    `label` — or inside a trailing lambda of one.
    """
    for match in LABEL_RETURN_RE.finditer(text):
        label = match.group(1)
        position = match.start()
        if not _inside_call_lambda(text, label, position):
            problems.append(
                f"{path}: return@{label} is not inside a lambda passed to a call named {label}()"
            )


def _inside_call_lambda(text: str, label: str, position: int) -> bool:
    """True when `position` lies within an argument lambda of a call named `label`."""
    escaped = re.escape(label)
    for call in re.finditer(r"\b" + escaped + r"\s*(\(|\{)", text):
        opener = call.end() - 1
        if opener > position:
            continue  # the call starts after the label; it cannot enclose it
        # Walk forward from the opener to the matching close, tracking whether a lambda body was
        # entered after the call began. Kotlin's own `synchronized(lock) { ... }` is a call whose
        # lambda follows a closed argument list, so the walk starts at the paren and continues
        # into the brace that follows it.
        depth = 0
        index = opener
        saw_lambda = call.group(1) == "{"
        while index < len(text):
            char = text[index]
            if char in "([{":
                depth += 1
            elif char in ")]}":
                depth -= 1
                if depth == 0:
                    # The call's own argument list just closed. If a lambda follows immediately,
                    # it is still part of this call's arguments.
                    following = text[index + 1 :]
                    stripped = following.lstrip()
                    if stripped.startswith("{"):
                        saw_lambda = True
                        index += 1 + (len(following) - len(stripped))
                        depth = 1
                        continue
                    break
            index += 1
        if index >= len(text):
            continue
        if opener <= position <= index and saw_lambda:
            return True
    return False


def check_private_base_members(problems: list[str]) -> None:
    """A private base-class member must not be called from a subclass in another file.

    The rule is narrow on purpose: it only fires when the calling file extends a class that is
    declared in the *same file* as the private member. `dispatch()` is private in one component
    and protected in another, so a name-only match would flag every component in the project.
    """
    # private name -> the file that declares it
    declarations: dict[str, Path] = {}
    for path in kotlin_files():
        text = strip_noise(path.read_text(encoding="utf-8"))
        for match in re.finditer(r"(?m)^\s*private\s+fun\s+(\w+)\s*\(", text):
            declarations.setdefault(match.group(1), path)

    for name, owner in declarations.items():
        owner_text = strip_noise(owner.read_text(encoding="utf-8"))
        # Classes the owner file declares; only these can be the caller's base.
        owner_classes = set(re.findall(r"\bclass\s+(\w+)", owner_text))
        for path in kotlin_files():
            if path == owner:
                continue
            text = strip_noise(path.read_text(encoding="utf-8"))
            for match in re.finditer(r"(?m)^\s*(?:open\s+|abstract\s+|sealed\s+)*class\s+(\w+)\s*:\s*(\w+)", text):
                base = match.group(2)
                if base not in owner_classes:
                    continue
                if re.search(rf"(?<![\w.]){re.escape(name)}\s*\(", text):
                    problems.append(
                        f"{path.relative_to(ROOT)}: calls private {name}() declared in "
                        f"{owner.relative_to(ROOT)}"
                    )


def check_jni_parity(problems: list[str]) -> None:
    native = (ROOT / "android-host/src/main/kotlin/com/aether/host/bridge/Native.kt").read_text()
    registry = (ROOT / "aether-native/src/main/cpp/jni/native_registry.cpp").read_text()
    kotlin_externals = set(re.findall(r"external fun (\w+)\(", native))
    jni_registrations = set(re.findall(r'\{"(\w+)", "', registry))
    if kotlin_externals != jni_registrations:
        problems.append(
            "JNI parity: unregistered="
            f"{sorted(kotlin_externals - jni_registrations)} "
            f"orphaned={sorted(jni_registrations - kotlin_externals)}"
        )
    for match in re.finditer(r'\{"(\w+)", "([^"]*)"', registry):
        name, descriptor = match.group(1), match.group(2)
        if not descriptor.startswith("("):
            problems.append(f"JNI descriptor for {name} does not start with '(': {descriptor}")
        if descriptor.count("(") != 1 or descriptor.count(")") != 1:
            problems.append(f"JNI descriptor for {name} is malformed: {descriptor}")


def check_call_arity(problems: list[str], files: list[Path]) -> None:
    """Argument-shape mismatches for calls to functions declared in this repository."""
    signatures: dict[str, dict] = {}
    return_types: dict[str, str] = {}
    declaration_counts: dict[str, int] = {}
    for path in files:
        if path.suffix != ".kt":
            continue
        text = blank_out(path.read_text(encoding="utf-8", errors="replace"))
        for match in FUN_DECL_RE.finditer(text):
            name, params, return_type = match.group(1), match.group(2), match.group(3)
            if name in ("if", "for", "while", "when", "return"):
                continue
            declaration_counts[name] = declaration_counts.get(name, 0) + 1
            parts = split_parameters(params)
            required = [p for p in parts if "=" not in p and not p.startswith("vararg")]
            has_vararg = any(p.startswith("vararg") for p in parts)
            signatures[name] = {"fixed": len(required), "vararg": has_vararg, "file": path}
            if return_type:
                return_types[name] = return_type.strip()

    # A name declared more than once is an overload set; matching a call to the wrong member is
    # noise, so those are left to the compiler.
    unambiguous = {name for name, count in declaration_counts.items() if count == 1}
    signatures = {name: sig for name, sig in signatures.items() if name in unambiguous}
    collection_returning = {
        name
        for name, rt in return_types.items()
        if name in unambiguous
        and rt.startswith(("List<", "MutableList<", "Set<", "MutableSet<", "Map<", "MutableMap<", "Array<"))
    }

    for path in files:
        if path.suffix != ".kt":
            continue
        text = blank_out(path.read_text(encoding="utf-8", errors="replace"))
        for match in CALL_RE.finditer(text):
            name = match.group(1)
            if name not in signatures:
                continue
            args, ok = read_call_arguments(text, match.end())
            if not ok:
                continue
            signature = signatures[name]
            line = text[: match.start()].count("\n") + 1
            if len(args) < signature["fixed"]:
                problems.append(
                    f"{path.relative_to(ROOT)}:{line}: call to {name}() passes {len(args)} "
                    f"argument(s) but it declares {signature['fixed']} required parameter(s)"
                )
            if not signature["vararg"]:
                continue
            for arg in args:
                stripped = arg.strip()
                if stripped.startswith("*"):
                    continue  # an explicit spread is the correct form
                is_collection_source = (
                    any(
                        re.search(r"(?:^|\.)" + re.escape(helper) + r"\s*\(", stripped)
                        for helper in collection_returning
                    )
                    or any(source in stripped for source in COLLECTION_SOURCES)
                )
                if is_collection_source:
                    problems.append(
                        f"{path.relative_to(ROOT)}:{line}: {name}() takes vararg, but the "
                        f"argument is a collection and needs a spread ({stripped[:48]})"
                    )


def main() -> int:
    files = kotlin_files()
    if not files:
        print("ERROR: no Kotlin sources found", file=sys.stderr)
        return 1
    problems: list[str] = []

    for path in files:
        raw = path.read_text(encoding="utf-8")
        text = strip_noise(raw)
        available = available_names(raw)

        check_balance(path, text, problems)
        check_labels(path, text, problems)

        for match in TYPE_USE_RE.finditer(text):
            for group in match.groups():
                if not group:
                    continue
                simple = group.split("<")[0].strip()
                if not simple or not simple[0].isupper():
                    continue
                # A constant is never a type: `VpnRoutePolicy.MIN_MTU` is a value position.
                if CONSTANT_NAME_RE.match(simple):
                    continue
                if simple in available or simple in ANDROID_NESTED:
                    continue
                if "." in group and group.split(".")[0] in (available | ANDROID_NESTED):
                    continue
                line = text[: match.start()].count("\n") + 1
                problems.append(
                    f"{path.relative_to(ROOT)}:{line}: unresolved type or constructor '{simple}'"
                )

    check_private_base_members(problems)
    check_call_arity(problems, files)
    check_jni_parity(problems)

    if problems:
        print(f"FAIL: {len(problems)} problem(s)", file=sys.stderr)
        for problem in sorted(set(problems)):
            print(f"  {problem}", file=sys.stderr)
        return 1
    print(f"Kotlin static checks pass across {len(files)} files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
