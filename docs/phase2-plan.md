# Phase 2 plan (stub — outline only)

**Phase 2 opens after A2 closes.** Phase 2 remains closed until Phase A1 and A2 close. This file records scope only; **no Phase 2 work is executed** by this or any step of the A1.5 round.

## Scope outline

1. **Runtime patch slot** — the `// PHASE-2 hook slot` comment inside `JNI_OnLoad` in `aether-engine/app/src/main/cpp/jni_bridge.cpp`, immediately after `RegisterNatives(clazz, kMethods, 11)`. Phase 1 leaves it an empty slot; `runtime_init.cpp` is an intentionally empty translation unit reserved for the same phase. No patching code exists today.
2. **IPC fan-out test** — exercise `ProxyContentProvider.call` across the four nested instances `P0..P3` (authorities `com.aether.pc.P0` … `com.aether.pc.P3`) and record per-instance dispatch behaviour.
3. **Cross-process command routing trace** — log the path a command takes from its caller to the target provider/service instance, including component resolution and the result bundle.

## Not in scope

- No implementation, no payload, no networking, no evasion, no device work in this document.
- Any execution requires a separate order from the Commander, after A1 and A2 close.