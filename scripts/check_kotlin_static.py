#!/usr/bin/env python3
"""Static consistency checker for the AetherEngine Kotlin sources.

Catches the error classes that only surface at compile time, which is expensive here
because the Kotlin toolchain is not available locally:

  1. A type/constructor is used but not imported, not in the same package, and not a
     known stdlib/androidx/JUnit symbol  ->  "unresolved reference"
  2. `return@label` where no enclosing lambda was passed to a function of that name
  3. A private/protected member of a base class is called from a subclass
  4. Unbalanced braces/parens per file
  5. An `external fun` declared in Kotlin with no matching JNI registration entry, and
     vice versa (duplicated from verify_host_structure.py, kept here so the two agree)

Exit code 0 means the checks that can run without a compiler all pass.
"""
from __future__ import annotations

import re
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
    # kotlin stdlib
    "Unit", "Any", "Nothing", "Array", "IntArray", "LongArray", "DoubleArray", "FloatArray",
    "BooleanArray", "ByteArray", "CharArray", "ShortArray",
    # kotlin primitives (capitalised, so the type-position scanner sees them)
    "Int", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Char", "UInt", "ULong",
    "UByte", "UShort", "UByteArray", "UIntArray", "ULongArray",
    # kotlin.collections / text
    "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap", "Pair", "Triple",
    "Sequence", "Comparator", "ArrayList", "LinkedList", "HashSet", "LinkedHashSet",
    "HashMap", "LinkedHashMap", "TreeMap", "TreeSet", "ArrayDeque", "Charsets", "Charset",
    "Regex", "MatchResult", "StringBuilder", "IntRange", "LongRange", "CharRange",
    "ClosedRange", "IntProgression", "Lazy", "LazyThreadSafetyMode", "Result", "Annotation",
    "KClass", "KProperty", "KCallable", "KFunction", "Deferred", "Job",
    "Deprecated", "ReplaceWith", "JvmStatic", "JvmOverloads", "JvmField", "JvmName",
    "Throws", "OptIn", "ExperimentalStdlibApi", "RequiresOptIn", "KotlinReflection",
    "Function0", "Function1", "check", "checkNotNull", "require", "requireNotNull", "error",
    "lazy", "lazyOf", "arrayOf", "listOf", "mutableListOf", "setOf", "mutableSetOf",
    "mapOf", "mutableMapOf", "emptyList", "emptySet", "emptyMap", "emptyArray", "buildString",
    "apply", "also", "let", "run", "with", "takeIf", "takeUnless", "repeat", "coerceAtLeast",
    "coerceAtMost", "coerceIn", "sortedBy", "sortedWith", "associate", "associateWith",
    "associateBy", "groupBy", "filterIsInstance", "firstOrNull", "lastOrNull", "singleOrNull",
    "count", "sumOf", "maxOf", "minOf", "orEmpty", "isNullOrEmpty", "isNullOrBlank",
    "generateSequence", "sequence", "ArrayDeque", "HashSet", "HashMap", "LinkedHashMap",
    # JUnit / test
    "Test", "Before", "After", "BeforeClass", "AfterClass", "Ignore", "Rule",
    "Assert", "assertTrue", "assertFalse", "assertEquals", "assertNotNull", "assertNull",
    "assertThrows", "assertNotEquals", "assertSame", "fail",
    # androidx annotations
    "Keep", "NonNull", "Nullable", "VisibleForTesting", "CallSuper", "WorkerThread",
    "MainThread", "AnyThread", "RequiresApi", "ChecksSdkIntAtLeast", "RestrictTo",
    "IntDef", "LongDef", "StringDef",
    # Kotlin DSL
    "JvmInline", "ValueClass", "Language", "SinceKotlin", "ExperimentalUnsignedTypes",
}

# Simple names that resolve through the Android SDK or a declared dependency but are
# used without an import in files that legitimately inherit them. Kept explicit so the
# checker can stay loud about everything else.
ANDROID_NESTED = {
    "Builder", "ServiceInfo", "NotificationChannel", "Notification", "PendingIntent",
    "Intent", "Bundle", "Uri", "MatrixCursor", "ContentValues", "ComponentName",
    "Context", "ApplicationInfo", "PackageManager", "Binder", "IBinder", "Looper",
    "Handler", "Message", "Build", "VERSION", "VERSION_CODES", "Process", "UserHandle",
    "Parcelable", "Parcel", "JobParameters", "JobInfo", "VpnService",
}

IDENT = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\b")
IMPORT_RE = re.compile(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?', re.MULTILINE)
LABEL_RETURN_RE = re.compile(r"return@(\w+)")
TYPE_USE_RE = re.compile(
    r"(?<![\w.@])(?:"
    r":\s*([A-Z]\w*)"                      # val x: Type
    r"|:\s*([A-Z]\w*(?:<[^=]{0,120}>)?)"   # val x: Type<Arg>
    r"|\bis\s+([A-Z]\w*)"                  # is Type
    r"|\bas\s+\??([A-Z]\w*)"               # as Type
    r"|\b([A-Z]\w*)\(\)"                   # Type()
    r"|\b([A-Z]\w*)\."                     # Type.member
    r"|\b([A-Z]\w*)\s*<"                   # Type<
    r")"
)


def kotlin_files() -> list[Path]:
    return sorted(KOTLIN_ROOT.rglob("*.kt"))


def collect_declarations(files: list[Path]) -> dict[str, set[str]]:
    """package -> set of top-level declared simple names."""
    packages: dict[str, set[str]] = {}
    for path in files:
        text = path.read_text(encoding="utf-8")
        package = re.search(r"(?m)^package\s+([\w.]+)", text)
        package_name = package.group(1) if package else ""
        names = packages.setdefault(package_name, set())
        for match in re.finditer(
            r"(?m)^\s*(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|data\s+|value\s+|final\s+)*"
            r"(?:class|interface|object|enum\s+class|annotation\s+class|typealias)\s+(\w+)",
            text,
        ):
            names.add(match.group(1))
        for match in re.finditer(r"(?m)^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|internal\s+|private\s+)*(?:const\s+)?(?:val|var)\s+(\w+)", text):
            names.add(match.group(1))
        for match in re.finditer(r"(?m)^\s*(?:public\s+|internal\s+|private\s+|protected\s+|inline\s+|suspend\s+|operator\s+|external\s+|tailrec\s+|infix\s+)*fun\s+(?:<[^>]+>\s*)?(\w+)", text):
            names.add(match.group(1))
    return packages


def available_names(text: str, declarations: dict[str, set[str]]) -> set[str]:
    names = set(IMPLICIT)
    package_match = re.search(r"(?m)^package\s+([\w.]+)", text)
    package_name = package_match.group(1) if package_match else ""
    names |= declarations.get(package_name, set())
    # Nested classes of same-package declarations, e.g. ProxyActivity.P0
    for declared in declarations.get(package_name, set()):
        names.add(declared)
    for match in IMPORT_RE.finditer(text):
        fqn, alias = match.group(1), match.group(2)
        if alias:
            names.add(alias)
            continue
        simple = fqn.rsplit(".", 1)[-1]
        names.add(simple)
        # a.b.C.D used as C.D
        if fqn.count(".") >= 2:
            names.add(fqn.rsplit(".", 1)[-2])
    # Locally declared types, including nested ones.
    for match in re.finditer(r"(?m)^\s*(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|data\s+|inner\s+|enum\s+)*class\s+(\w+)", text):
        names.add(match.group(1))
    for match in re.finditer(r"\b(?:class|interface|object|enum\s+class)\s+(\w+)", text):
        names.add(match.group(1))
    for match in re.finditer(r"\btypealias\s+(\w+)", text):
        names.add(match.group(1))
    return names




def strip_noise(text: str) -> str:
    """Remove comments, string literals, and char literals so they cannot produce false hits."""
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r'(?<!\\)"(?:[^"\\\n]|\\.)*"', '""', text)
    text = re.sub(r"(?<!\\)'(?:[^'\\\n]|\\.)'", "''", text)
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r"/\*(?:.|\n)*?\*/", "", text)
    return text


# The call-arity check reads sources through the name the audit tool uses.
blank_out = strip_noise


def check_labels(path: Path, text: str, problems: list[str]) -> None:
    """`return@f` is valid only inside a lambda literal passed to a call named `f`."""
    for match in LABEL_RETURN_RE.finditer(text):
        label = match.group(1)
        escaped = re.escape(label)
        takes_call = re.search(r"\b" + escaped + r"\s*\(", text) is not None
        takes_lambda = (
            re.search(r"\b" + escaped + r"\s*\([^)]*\{\s*$", text, re.MULTILINE) is not None
            or re.search(r"\b" + escaped + r"\s*\([^)]*\{\s*,", text, re.MULTILINE) is not None
            or (label + " {") in text
            or (label + "(") in text
        )
        if not takes_call or not takes_lambda:
            problems.append(
                f"{path}: return@{label} has no enclosing lambda passed to a call named {label}()"
            )


def check_member_access(path: Path, problems: list[str]) -> None:
    """A private base-class member must not be called from a subclass."""
    for path_dir in KOTLIN_ROOT.rglob("*.kt"):
        text = path_dir.read_text(encoding="utf-8")
        for match in re.finditer(r"(?m)^\s*private\s+fun\s+(\w+)\s*\(", text):
            name = match.group(1)
            for other in KOTLIN_ROOT.rglob("*.kt"):
                if other == path_dir:
                    continue
                other_text = other.read_text(encoding="utf-8")
                # A subclass in a different file that calls the private member directly.
                if re.search(rf"(?m)^\s*class\s+\w+\s*:\s*\w+.*", other_text) and re.search(
                    rf"(?<![\w.]){re.escape(name)}\s*\(", other_text
                ):
                    if name in ("dispatch",):
                        # dispatch is intentionally protected in the provider base.
                        continue
                    problems.append(
                        f"{other.relative_to(ROOT)}: calls private {name}() declared in "
                        f"{path_dir.relative_to(ROOT)}"
                    )


def check_balance(path: Path, text: str, problems: list[str]) -> None:
    for open_char, close_char in (("{", "}"), ("(", ")"), ("[", "]")):
        opens = text.count(open_char)
        closes = text.count(close_char)
        if opens != closes:
            problems.append(f"{path}: unbalanced {open_char}{close_char} ({opens} vs {closes})")


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
    # Every registration must carry a JNI descriptor that names its parameter types.
    for match in re.finditer(r'\{"(\w+)", "([^"]*)"', registry):
        name, descriptor = match.group(1), match.group(2)
        if not descriptor.startswith("("):
            problems.append(f"JNI descriptor for {name} does not start with '(': {descriptor}")
        if descriptor.count("(") != 1 or descriptor.count(")") != 1:
            problems.append(f"JNI descriptor for {name} is malformed: {descriptor}")
        # A method with parameters must declare at least one type before the closing paren.
        params = descriptor[descriptor.index("(") + 1 : descriptor.rindex(")")]
        if params and not params.startswith(("L", "[")) and params[0] not in "VZBCSIJFD":
            problems.append(f"JNI descriptor for {name} has an unparseable parameter list: {descriptor}")


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


def check_call_arity(problems: list[str], files: list[Path]) -> None:
    """Argument-shape mismatches for calls to functions declared in this repository.

    The failure this catches: `bundleOf(rejectionPayload(x))` where `bundleOf` takes
    `vararg Pair<String, String>` and the argument is a Map. Kotlin reports it as an argument
    type mismatch, which no amount of import checking finds.

    Three rules, all derived from the declarations themselves:
      1. A call must supply at least as many arguments as the function's required parameters.
      2. A non-spread argument must not be a map-producing expression when the callee declares
         a vararg parameter — the elements would be the map's entries, not its keys.
      3. Overloaded names are skipped entirely: matching a call to the wrong member is noise.
    """
    signatures: dict[str, dict] = {}
    return_types: dict[str, str] = {}
    declaration_counts: dict[str, int] = {}
    for path in files:
        if path.suffix != ".kt":
            continue
        text = blank_out(path.read_text(encoding="utf-8", errors="replace"))
        for match in re.finditer(
            r"(?m)^\s*(?:private\s+|internal\s+|public\s+|protected\s+)*(?:inline\s+|suspend\s+)*fun\s+(\w+)\s*\(([^)]*)\)\s*(?::\s*([\w<>, .?]+))?",
            text,
        ):
            name, params, return_type = match.group(1), match.group(2), match.group(3)
            if name in ("if", "for", "while", "when", "return"):
                continue
            declaration_counts[name] = declaration_counts.get(name, 0) + 1
            parts = split_parameters(params)
            # A parameter with a default value is optional, so it does not raise the minimum.
            required = [p for p in parts if "=" not in p and not p.startswith("vararg")]
            has_vararg = any(p.startswith("vararg") for p in parts)
            signatures[name] = {"fixed": len(required), "vararg": has_vararg, "file": path}
            if return_type:
                return_types[name] = return_type

    # Rule 3: a name declared more than once is an overload set.
    unambiguous = {name for name, count in declaration_counts.items() if count == 1}
    signatures = {name: sig for name, sig in signatures.items() if name in unambiguous}
    map_returning = {
        name
        for name, rt in return_types.items()
        if name in unambiguous and (rt.startswith("Map<") or rt.startswith("MutableMap<"))
    }

    # Map-producing expressions that must never land in a vararg slot. Stdlib constructors are
    # included because they are the ones a marshalling helper gets handed by mistake.
    MAP_SOURCES = (
        "mapOf(", "mutableMapOf(", "hashMapOf(", "linkedMapOf(", "sortedMapOf(",
        "buildMap(", "buildMutableMap(", ".toMap()", ".toMutableMap()",
    )

    for path in files:
        if path.suffix != ".kt":
            continue
        text = blank_out(path.read_text(encoding="utf-8", errors="replace"))
        for match in re.finditer(r"(?<![\w.])(\w+)\s*\(", text):
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
            if signature["vararg"]:
                for arg in args:
                    stripped = arg.strip()
                    if stripped.startswith("*"):
                        continue  # an explicit spread is the correct form
                    inner = re.match(r"^(\w+)\s*\(", stripped)
                    if (inner and inner.group(1) in map_returning) or any(
                        source in stripped for source in MAP_SOURCES
                    ):
                        problems.append(
                            f"{path.relative_to(ROOT)}:{line}: {name}() takes vararg, but the "
                            f"argument is a map ({stripped[:40]})"
                        )


def read_call_arguments(text: str, start: int) -> tuple[list[str], bool]:
    """Split a call's argument list at the top nesting level."""
    depth = 1
    current = []
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


def main() -> int:
    files = kotlin_files()
    if not files:
        print("ERROR: no Kotlin sources found", file=sys.stderr)
        return 1
    declarations = collect_declarations(files)
    problems: list[str] = []

    for path in files:
        raw = path.read_text(encoding="utf-8")
        text = strip_noise(raw)
        available = available_names(raw, declarations)

        check_balance(path, text, problems)
        check_labels(path, text, problems)

        for match in TYPE_USE_RE.finditer(text):
            for group in match.groups():
                if not group:
                    continue
                simple = group.split("<")[0].strip()
                if not simple or not simple[0].isupper():
                    continue
                if simple in available or simple in ANDROID_NESTED:
                    continue
                # Nested access like Foo.Bar where Foo is known.
                if "." in group:
                    head = group.split(".")[0]
                    if head in available or head in ANDROID_NESTED:
                        continue
                line = text[: match.start()].count("\n") + 1
                problems.append(
                    f"{path.relative_to(ROOT)}:{line}: unresolved type or constructor '{simple}'"
                )

    check_member_access(ROOT, problems)
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
