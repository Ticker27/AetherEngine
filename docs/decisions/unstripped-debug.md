# Unstripped debug investigation (D-A15.2 → closed by D-C1.1 / D-C1.2)

**Problem**: the packaged debug library carries no symbol table, so the fnPtr table (D-A1.2) is not obtainable from the shipped `.so`. This round finds the cause, applies a debug-only fix, and measures the result in CI.

## D-C1.1 probe — ordered commands, raw output

```
$ grep -n -iE 'strip|-Wl,-s|doNotStrip|keepDebugSymbols|debugSymbolLevel|packaging' \
      aether-engine/app/src/main/cpp/CMakeLists.txt \
      aether-engine/app/build.gradle.kts \
      aether-engine/gradle.properties
[grep exit=1]  (1 = zero matches)

$ ls -l aether-engine/app/proguard-rules.pro
ls: cannot access 'aether-engine/app/proguard-rules.pro': No such file or directory

$ cat -n aether-engine/app/src/main/cpp/CMakeLists.txt
     1  cmake_minimum_required(VERSION 3.22.1)
     2  project(aether LANGUAGES CXX)
     3
     4  add_library(aether SHARED
     5      jni_bridge.cpp
     6      runtime_init.cpp
     7  )
     8
     9  target_compile_options(aether PRIVATE -std=c++17 -O2 -fvisibility=hidden)
    10  target_link_libraries(aether PRIVATE log)

$ cat -n aether-engine/app/build.gradle.kts | sed -n '30,50p'
    30      }
    31
    32      buildTypes {
    33          debug { isMinifyEnabled = false }
    34      }
    ...

$ cat -n aether-engine/gradle.properties
     1  android.useAndroidX=true
     2  org.gradle.jvmargs=-Xmx2g
```

## Verdict per file

| file (probed for `strip` / `-Wl,-s` / `doNotStrip` / `keepDebugSymbols` / `debugSymbolLevel` / `packaging`) | lines that matter | strip directive | verdict |
|---|---|---|---|
| `app/src/main/cpp/CMakeLists.txt` | L9 `target_compile_options(aether PRIVATE -std=c++17 -O2 -fvisibility=hidden)`, L10 `target_link_libraries(aether PRIVATE log)` | **none** | No `-s`, no `-Wl,--strip-all`, no `llvm-strip` call. `-fvisibility=hidden` only keeps a symbol out of `.dynsym`; it does not remove `.symtab`. `-O2` does not strip either. The CMake link does not strip. |
| `app/build.gradle.kts` | L16 `ndk { abiFilters += "arm64-v8a" }`, L32–34 `buildTypes { debug { isMinifyEnabled = false } }` | **none** | No `packaging { }` block, no `doNotStrip`, no `ndk.debugSymbolLevel`, no `buildTypes.debug.*strip`. The module never opted out of packaging-time stripping, so the AGP default applied. |
| `gradle.properties` | L1–2 only (`android.useAndroidX=true`, `org.gradle.jvmargs=-Xmx2g`) | **none** | No `android.buildTypes.debug.strip`. That key belongs to NDK build (Android.mk/Application.mk) and has no effect on the CMake path, so it could not have been the cause even if present. |
| `app/proguard-rules.pro` | file **does not exist** | n/a | No ProGuard/R8 rules exist at all. R8 is disabled for debug (`isMinifyEnabled = false`) and, in any case, R8 never edits native ELF symbol tables. |

## Root cause — which line strips

**No line in this repository requests stripping.** Measured facts (reproducible with the commands above):

- the packaged debug `lib/arm64-v8a/libaether.so` has no `.symtab` and no `.debug_*` section (`phase-a1/evidence/fnptr_table.txt`), while the CMake build itself is not configured to strip (probe above), and `nm -a` reports "no symbols";
- therefore the removal happens **in the build/packaging stage, not in repository configuration**: AGP strips merged JNI libraries while packaging a variant unless the library matches `packaging.jniLibs.keepDebugSymbols`. With no opt-out in this module, the shipped debug library is stripped and the unstripped copy is only left behind in the build intermediates, which the `Build` job does not upload.

Consequence for the fix: the opt-out must be expressed where AGP reads it per variant. The AGP DSL has **no `packaging` block on build types** — a `packaging { jniLibs { … } }` call written inside `buildTypes { debug { … } }` resolves against the outer `android {}` extension and silently applies to **every** variant, release included (same trapping recorded as a silent-scope bug in other Android templates). The variant API is the only debug-only form.

## Fix — exact line to change (debug-only)

Added to `aether-engine/app/build.gradle.kts`, after the `android { }` block and before `dependencies { }`:

```kotlin
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.packaging.jniLibs.keepDebugSymbols.add("**/libaether.so")
    }
}
```

- **debug-only**: the selector matches the `debug` build type only; `release` keeps AGP's default (stripped) packaging.
- **narrow**: matches `libaether.so` only, so any future prebuilt JNI library keeps the default behaviour.
- **rejected alternative**: `buildTypes { debug { packaging { jniLibs { keepDebugSymbols += "**/libaether.so" } } } }` — no `BuildType.packaging` in the DSL, so the glob would leak into the release variant (silent, no error).
- **rejected alternative**: adding `-Wl,--strip-all` / an explicit `llvm-strip` step to `CMakeLists.txt` — does the opposite of the goal.

## Verification plan

1. CI `Build` job builds `:app:assembleDebug`, then runs the step **"Native symbol diagnostic (D-C1.2 keepDebugSymbols verification)"**, which unzips `lib/arm64-v8a/libaether.so` out of `app-debug.apk` and prints `readelf -SW`.
2. **Acceptance**: `readelf -SW lib/arm64-v8a/libaether.so | grep '\.symtab'` returns a line (`.symtab` present). The pre-fix library had none.
3. Then the D-A1.2 command set is rerun and recorded in `phase-a1/evidence/fnptr_table.txt`:
   `readelf -sW … | grep -Ei 'n_(ac|aior|awl|chl|djp|eio|i$|ic|ilil|jpo|update)'` → 11 rows, plus `objdump -d --section=.text` (aarch64-capable form on the runner).
4. **Failure branch**: if `.symtab` is still absent, the same diagnostic step prints the pre-packaging CMake output and the JNI-lib intermediates, so the stage that removed the table can be named from raw output; the fnPtr table then stays `UNABLE` and no extra package may be installed to work around it.
5. **Release unaffected**: nothing in the change reads or selects the release variant; the release path keeps AGP defaults.

## Command adaptation (recorded, not hidden)

The stubs are file-static C++ functions, so `.symtab` carries **mangled** names (`_ZL4n_acP7_JNIEnv…`). The D-A1.2 pattern `grep -Ei 'n_(ac|aior|awl|chl|djp|eio|i$|ic|ilil|jpo|update)'` anchors the last alternative with `$` and therefore returns **10** rows (missing `n_i`); dropping that anchor returns all **11**. The stubs are also the reason `.text` holds exactly one `GLOBAL` FUNC symbol (`JNI_OnLoad`).

## Status — measured

D-C1.1 = closed by this document. D-C1.2 = applied and measured in CI (run `37250641393`, Build job `111577362329`, PR #11):

| probe | before (A1 build, `d28c3ff`) | after (PR #11 head `4c12640`) |
|---|---|---|
| `lib/arm64-v8a/libaether.so` size | 6,760 bytes | 55,784 bytes |
| `readelf -SW \| grep .symtab` | no match | `[26] .symtab SYMTAB 0000000000000000 00c750 000678 18 28 63 8` |
| `readelf -sW \| grep kMethods` | no match | `25: 0000000000002088 264 OBJECT LOCAL DEFAULT 15 _ZL8kMethods` |
| D-A1.2 fnPtr rows | 0 of 11 | **11 of 11** |

Raw output for both states: the before-state is in the A1 evidence (`phase-a1/evidence/fnptr_table.txt` history, `symbols.txt`), the after-state is `phase-a1/evidence/fnptr_table.txt`. The release path is untouched — the opt-out is variant-scoped and the contract test asserts the scoped form.
