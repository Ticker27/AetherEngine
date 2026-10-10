# Phase 2 plan (outline only — not executed)

**Phase 2 opens after A2 closes.** Phase 2 remains closed until Phase A1 and A2 close. This file records scope only; **no Phase 2 work is executed** by this or any step of the A2-READY / Phase2-PREP round.

## Preconditions (all required before Phase 2 opens)

1. **Phase A1 closed** — PR #9 → `0831426`.
2. **Phase A1.5 closed** — PR #11 → `f04400f` (unstripped debug + docs + guards).
3. **A2 closed** — every runtime DoD item has raw evidence in `phase-a2/evidence/`: AC-4, AC-5, AC-9, AC-10, AC-12 (runtime netstat) and the on-device fnPtr confirmation (11 rows). Checklist and artifact list: `docs/phase-a2-plan.md` § A2.1–A2.9. A2 evidence exists only when a device/emulator per `docs/decisions/a2-device-spec.md` has been used.
4. **Acceptance frozen** — `docs/phase2-acceptance.md` is still marked *draft*; the order that opens Phase 2 must freeze it (including the open questions listed there).
5. **A new, explicit order from the Commander.** This document plus the prep docs open nothing by themselves.

## Scope outline

1. **Runtime patch slot** — the `// PHASE-2 hook slot` comment inside `JNI_OnLoad` in `aether-engine/app/src/main/cpp/jni_bridge.cpp`, immediately after `RegisterNatives(clazz, kMethods, 11)`. Phase 1 leaves it an empty slot; `runtime_init.cpp` is an intentionally empty translation unit reserved for the same phase. No patching code exists today.
2. **IPC fan-out test** — exercise `ProxyContentProvider.call` across the four nested instances `P0..P3` (authorities `com.aether.pc.P0` … `com.aether.pc.P3`) and record per-instance dispatch behaviour.
3. **Cross-process command routing trace** — log the path a command takes from its caller to the target provider/service instance, including component resolution and the result bundle.

## Cross-references (prepared this round)

| document | content |
|---|---|
| `docs/phase2-architecture.md` | Mermaid diagram + narrative for the three scope items; explicit "not executed" banner |
| `docs/phase2-hooks.md` | every `PHASE-2` marker with real file/line (`jni_bridge.cpp:52-53`, `runtime_init.cpp:1`), current state, and what Phase 2 would add |
| `docs/phase2-acceptance.md` | draft criteria PH2-AC1..PH2-AC3 + open questions, marked draft |

## Deliverables outline (what the Phase 2 order must produce)

- The reserved entry point implemented in `runtime_init.cpp`, with the `jni_bridge.cpp:52-53` slot wired to it — **debug-only**, release path untouched (same variant-scoping principle as D-C1.2 in `docs/decisions/unstripped-debug.md`).
- Per-instance IPC fan-out evidence for P0..P3 (and an explicit decision on `SystemCallProvider`, per the PH2-AC2 open question) — `docs/phase2-acceptance.md`.
- A cross-process routing trace artifact, with the process and UID recorded for each hop (PH2-AC3).
- `phase2/evidence/` populated with all of the above, raw output only.
- The frozen acceptance ledger with a per-AC evidence mapping (no criteria added, dropped, or weakened relative to the frozen set).
- Delivery in the standing form: **1 branch / 1 commit / 1 PR merged when CI is 8/8 green**, locks unchanged.

## Forbidden (mirrors the Phase A rules)

- **No C2 endpoint, no external endpoint, no networking call of any kind** — the Phase 1 static proof stays valid (`phase-a1/evidence/none_ok.txt`).
- **No seller-layer component and no hidden payload / imported binary.**
- **No §15 forbidden technique or token**: the CI guard's pattern (`.github/workflows/aether-engine.yml`, step "Phase 1 contract tests … forbidden tokens", line 227) is the authority; this document refers to the pattern instead of reproducing the tokens, and there is **no exemption**.
- **No secret material**: no real tokens/keys anywhere (CI has none; `.env` is operator-managed and never committed).
- **No lock change**: AGP 8.5.0 / Kotlin 1.9.24, SDK 35/28/35, `arm64-v8a` only, no signing config.
- **No `v*` tag**, no release publication, no dependency on an external APK.
- **No change to the AetherEngine upstream** outside the ordered files.
- **No device work** without a device available and an order that authorizes it (A2 rules apply: record refusals, never edit the manifest to force access).
- **No guessing**: if any of the above cannot be met, report **UNABLE** with the raw reason and stop.

## Not in scope

- No implementation, no payload, no networking, no evasion, no device work in this document.
- Any execution requires a separate order from the Commander, after A1 and A2 close.

## Gate

**Phase 2 stays closed until A2 closes.** The status of this file changes only when the Commander issues the Phase 2 opening order after reviewing the A2 evidence bundle.
