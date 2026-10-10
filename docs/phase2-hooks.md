# Phase 2 hook markers (inventory — all reserved)

Inventory of every `PHASE-2` marker that exists in the tree today, with real file/line numbers.
**None of them is implemented**; each is a comment or an empty translation unit. Nothing in this file
is executed by Phase 1 or Phase A2.

## Search performed (verbatim, this round)

```
$ grep -rn 'PHASE-2' aether-engine/
aether-engine/app/src/main/cpp/jni_bridge.cpp:52:    // PHASE-2 hook slot (ยังไม่ implement)
aether-engine/app/src/main/cpp/runtime_init.cpp:1:// PHASE-2 hook: runtime memory prep จะอยู่ที่นี่
[exit=0]
```

Coverage check for the paths named in the order (Kotlin classes, manifest, CMake):

```
$ grep -rn 'PHASE-2' aether-engine/app/src/main/kotlin \
                    aether-engine/app/src/main/AndroidManifest.xml \
                    aether-engine/app/src/main/cpp/CMakeLists.txt
[exit=1]  (1 = zero matches)
```

| searched path | matches | exit |
|---|---|---|
| `aether-engine/app/src/main/cpp/jni_bridge.cpp` | 1 marker (+1 commented-out call, see row 2) | 0 |
| `aether-engine/app/src/main/cpp/runtime_init.cpp` | 1 marker | 0 |
| `aether-engine/app/src/main/kotlin/**` (all 12 Kotlin classes) | **0** | 1 |
| `aether-engine/app/src/main/AndroidManifest.xml` | **0** | 1 |
| `aether-engine/app/src/main/cpp/CMakeLists.txt` | **0** (it only names `runtime_init.cpp` as a source, line 6) | 1 |
| `aether-engine/app/build.gradle.kts`, `aether-engine/README.md` | 0 in Gradle; README prose only (see below) | 1 |

There is **no PHASE-2 comment in any Kotlin class**. The markers are confined to the native sources.

## Marker sites

| # | file | line | current state (verbatim) | what Phase 2 would add | status |
|---|---|---|---|---|---|
| 1 | `aether-engine/app/src/main/cpp/jni_bridge.cpp` | **52** | `// PHASE-2 hook slot (ยังไม่ implement)` — comment only, inside `JNI_OnLoad`, immediately after the `RegisterNatives(clazz, kMethods, 11)` call (line 44) and the 11-line fnPtr log loop | the call into the runtime entry point at that point of `JNI_OnLoad` (following register, before `return JNI_VERSION_1_6`) | **not implemented — reserved** |
| 2 | `aether-engine/app/src/main/cpp/jni_bridge.cpp` | **53** | `// runtime_patch_apply(env, clazz);` — the intended call, commented out | the function named by that call, with `env`/`clazz` already in scope | **not implemented — reserved** |
| 3 | `aether-engine/app/src/main/cpp/runtime_init.cpp` | **1** | `// PHASE-2 hook: runtime memory prep จะอยู่ที่นี่` — the file's only content besides line 2 `// Phase 1: intentionally empty`; it is an empty translation unit | the implementation of the reserved runtime entry point, kept in this TU so `jni_bridge.cpp`'s proven registration path is untouched | **not implemented — reserved** |

Build wiring (not a marker, recorded for completeness): `runtime_init.cpp` is compiled into
`libaether.so` by `aether-engine/app/src/main/cpp/CMakeLists.txt` lines 4–7
(`add_library(aether SHARED jni_bridge.cpp runtime_init.cpp)`).

## Non-code references (prose, not markers)

| file | line | text | note |
|---|---|---|---|
| `aether-engine/README.md` | 8 | "Hidden payload, C2 and seller layer intentionally omitted; Phase 2 hook slot is a comment only." | documentation of the same state |
| `aether-engine/README.md` | 35 | "`runtime_init.cpp` — Phase 2 placeholder (intentionally empty)" | tree diagram annotation |
| `docs/phase2-plan.md` | 7 | description of the `PHASE-2 hook slot` inside `JNI_OnLoad` | scope outline |

## Status summary

- Markers found: **3** (two lines in one slot in `jni_bridge.cpp`, one in `runtime_init.cpp`), all comment-only.
- Markers in Kotlin: **0**.
- Implemented PHASE-2 code: **0 lines** — no function, class, or dependency is added for Phase 2 in this round.
- Phase 2 remains closed until Phase A2 closes (`docs/phase2-plan.md`).
