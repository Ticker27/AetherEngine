# Phase 2 acceptance criteria — **DRAFT**

> **Draft — frozen only when Phase 2 opens.** These criteria are not final, not approved, and not
> applicable to any current work. Phase 2 has **not** been executed and stays closed until Phase A2
> closes (`docs/phase2-plan.md`). Nothing below may be used to claim completion today.

Numbering is provisional (`PH2-AC*`) and kept separate from the Phase 1 `AC-*` set
(`docs/phase1-dod.md`) so the two ledgers can never be confused.

| AC | provisional statement | intended proof (shape only) | status |
|---|---|---|---|
| **PH2-AC1** | The runtime patch slot is exercised in the **debug** build: the hook at `jni_bridge.cpp:52-53` calls into the `runtime_init.cpp` entry point, and the call is observable at runtime without changing Phase 1's registration result. | debug-build logcat showing the slot entered, plus the unchanged `RegisterNatives rc=0 entries=11` and 11 `native[NN]` lines; release path untouched (variant-scoped, same principle as D-C1.2). | draft — not implemented |
| **PH2-AC2** | IPC fan-out returns from **P0..P3**: one dispatched command reaches all four `ProxyContentProvider` instances (`com.aether.pc.P0` … `com.aether.pc.P3`) and each reply is captured per instance. | per-instance evidence for P0/P1/P2/P3 (reply bundle + `AetherPC` log line each), showing all four answered for one command. | draft — not implemented |
| **PH2-AC3** | A cross-process command is routed **with proof**: the trace from caller → resolved component → provider/service instance → result bundle is recorded, including the process/UID of each hop. | a routing trace artifact naming every hop, cross-checked against the process list (`ps -A` / `/proc/<pid>`) for the run. | draft — not implemented |

## Open questions (must be settled before freeze)

1. Whether PH2-AC2 requires the `SystemCallProvider` (`com.aether.syscall`) in the same dispatch or
   treats it as a separate criterion.
2. Whether PH2-AC3 can be satisfied by the single-process baseline that exists today, or whether a
   second process is a precondition — that decision may change the Phase A2/Phase 2 boundary.
3. Whether any §15 forbidden-token category remains excluded without exemption (expected: yes; the
   Phase 1 guard carries no exemption).

## Status

Draft. Freeze happens on the order that opens Phase 2, and only after A2 closes.
