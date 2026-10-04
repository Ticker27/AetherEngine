# Unstripped debug investigation (D-A15.2)

**Observed**: the packaged debug library carries no symbol table, so the fnPtr table (D-A1.2) is not obtainable from the shipped `.so`.

## Observed facts (from commit `d28c3ff` artifacts, `aether-engine-debug-d28c3ff…`)

| probe | command | result |
|---|---|---|
| section list | `readelf -SW lib/arm64-v8a/libaether.so` | `.rodata .eh_frame_hdr .eh_frame .text .plt .data.rel.ro .fini_array .dynamic .got.plt .comment .shstrtab` — **no `.symtab`**, no `.debug_*` |
| debug info count | `readelf -S … \| grep -c 'debug_info\|debug_line'` | `0` |
| APK native payload | `unzip -l app-debug.apk \| grep '\.so\|lib/'` | one entry only: `lib/arm64-v8a/libaether.so` (6,760 bytes) — no separate symbols archive is uploaded by the Build job |
| our configs | `grep -n -iE 'strip\|doNotStrip\|keepDebugSymbols\|debugSymbolLevel' …` | no match (only `ndk { abiFilters += "arm64-v8a" }`) |
| CMake flags | `cat app/src/main/cpp/CMakeLists.txt` | `-std=c++17 -O2 -fvisibility=hidden` — no `-s`, `--strip-all`, no `llvm-strip` |

## Verdict — which line causes the strip

**No line in this repository requests stripping.** The strip comes from the **Android Gradle Plugin default packaging behaviour**: AGP strips JNI libraries while packaging unless the module opts out with `packaging { jniLibs { keepDebugSymbols … } }`. For the debug variant the AGP default `ndk.debugSymbolLevel = FULL` writes the unstripped library to `native-debug.zip` instead — and the Build job uploads only the APK, so those symbols are never collected.

`gradle.properties` cannot influence this: `android.buildTypes.debug.strip` is an NDK-build (makefiles) key and has no effect on the CMake path.

## Options to keep `.symtab` in debug builds (none applied in this round)

1. **Keep symbols inside the debug APK** — add to `aether-engine/app/build.gradle.kts`:
   ```kotlin
   packaging {
       jniLibs { keepDebugSymbols += "**/libaether.so" }
   }
   ```
   Makes the D-A1.2 fnPtr table statically obtainable (`readelf -sW … | grep n_`). Cost: larger debug APK and local symbol names present in the debug build only.
2. **Keep AGP defaults, collect symbols out-of-band** — upload `aether-engine/app/build/outputs/native-debug/native-debug.zip` from the Build job. APK size unchanged; analysis needs the archive rather than the APK.
3. **Not recommended** — adding `-Wl,--strip-all` or an `llvm-strip` step in `CMakeLists.txt` does the opposite of the goal.

## Recommendation

Option 1 if the goal is to close D-A1.2 statically; option 2 if APK size matters. Both are build/CI edits and need a separate order.

## Not changed in this round

`aether-engine/app/build.gradle.kts`, `CMakeLists.txt`, and `gradle.properties` are untouched here — this round changed only the CI artifact label and the docs.