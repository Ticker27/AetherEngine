# Phase 1 DoD status (AC map)

All "done" rows are **static** evidence. No runtime claim is made in this round.

| AC | Evidence | Status |
|---|---|---|
| AC-1 … AC-3 | CI build logs: runs `37217095131`, `37217095084`, commit `d28c3ff` | done |
| AC-4 … AC-5 | logcat (`AetherApp`, `AetherEntry` lifecycle) | deferred (A2) |
| AC-6 | `phase-a1/evidence/binding_proof.txt` (11/11 names, `JNI_OnLoad` present) | done |
| AC-6 (static fnPtr table) | `phase-a1/evidence/fnptr_table.txt` → **11 of 11 rows** (D-A1.2 closed by the D-C1.2 debug-only `keepDebugSymbols` fix) | done |
| AC-6 (runtime fnPtr confirmation) | on-device `AetherJNI: native[NN] name=… fnPtr=0x…` lines vs the static table | deferred (A2) |
| AC-7 … AC-8 | contract test — `aether-engine.yml` Test job (5 s, pass) | done |
| AC-9 … AC-10 | logcat (provider echo, daemon `onTaskRemoved`) | deferred (A2) |
| AC-11 | `phase-a1/evidence/none_ok.txt` (permissions, network grep, NEEDED, socket symbols) | done |
| AC-12 static | `phase-a1/evidence/none_ok.txt` | done |
| AC-12 runtime | netstat before/after on device | deferred (A2) |
| AC-13 | `git status --short` clean after commit | done |

Phase A2 opens when Commander provides a device (`docs/phase-a2-plan.md`).