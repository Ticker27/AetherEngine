# Phase A2 plan — A2-READY checklist (prepared; not executed)

> **Status: A2-READY, prep only.** Prepared by the `A2-READY + PHASE2-PREP` order. **No device and no
> emulator was used** in this round, no command below was run, and no A2 code exists. Every command is a
> template for A2 execution day; the evidence paths under `phase-a2/evidence/` are intentionally empty
> until then (scaffold: `phase-a2/README.md`).

Runtime DoD: AC-4, AC-5, AC-9, AC-10, AC-12 (runtime netstat), plus the on-device confirmation of the fnPtr table whose static form is already closed (`phase-a1/evidence/fnptr_table.txt` → 11/11 rows since D-C1.2; background in `docs/decisions/unstripped-debug.md`).

## Preconditions

- arm64-v8a device or emulator, API 28 or higher — the APK ships `arm64-v8a` only (`abiFilters`), so an x86 emulator cannot install it.
- `adb` reachable from the operator host; `aether-engine/app/build/outputs/apk/debug/app-debug.apk` present (from `./gradlew :app:assembleDebug`).
- Locked device/emulator spec, alternative emulator path, and the device-evidence commands: sections **A2.1** and `docs/decisions/a2-device-spec.md`.

## Runtime checklist — run in order

All commands run from the `aether-engine/` directory unless stated. Column **AC** maps each command to the acceptance criterion its output closes; the logcat lines behind AC-4/5/9/10/12 are listed under "Expected logcat content". The combined form `adb logcat -c && adb shell am start -n com.aether/.Entry` is steps 4+5 below. Sections A2.1–A2.9 give the same run in checklist form with the evidence file each step writes.

| # | command | expected output | AC |
|---|---|---|---|
| 1 | `./gradlew :app:assembleDebug` | exit 0, APK produced | AC-1 |
| 2 | `adb install -r app/build/outputs/apk/debug/app-debug.apk` | `Success` | AC-1 |
| 3 | `adb shell netstat -tunp > /tmp/net_before.txt` | baseline snapshot | AC-12 |
| 4 | `adb logcat -c` | (clears buffer) | — |
| 5 | `adb shell am start -n com.aether/.Entry` | `Starting: Intent { … cmp=com.aether/.Entry }` | AC-4 |
| 6 | `adb shell content call --uri content://com.aether.pc.P0 --method test --arg x` | `Bundle[{method=test, arg=x, reply=aether-echo}]` | AC-9 |
| 7 | `adb shell am startservice -n com.aether/.helper.DaemonService` | `Starting service Intent { … }` | AC-10 |
| 8 | `adb shell am force-stop com.aether` | (no output) | AC-10 |
| 9 | `adb shell netstat -tunp > /tmp/net_after.txt` | post-run snapshot | AC-12 |
| 10 | `adb logcat -d > /tmp/aether_logcat.txt` | captured log | AC-4/5/9/10 |
| 11 | `diff /tmp/net_before.txt /tmp/net_after.txt \| grep -i com.aether \|\| echo "NO-NET-OK"` | `NO-NET-OK` | AC-12 |
| 12 | `adb shell am instrument -w -e class com.aether.NativeContractTest com.aether.test/androidx.test.runner.AndroidJUnitRunner` | `OK (5 tests)` | AC-7/8 |

## Expected logcat content (step 10)

| AC | expected lines (tag : message) |
|---|---|
| AC-4 | `AetherApp: onCreate` → `AetherJNI: RegisterNatives rc=0 entries=11` → `AetherJNI: native[00] name=ac fnPtr=0x…` … `native[10] name=update fnPtr=0x…` (11 lines) → `AetherApp: native loaded` → `AetherEntry: onCreate` → `AetherEntry: onResume` |
| AC-5 | `AetherEntry: onStop` when the activity leaves the foreground; `AetherJNI: stub_void called` when any stub native is invoked |
| AC-9 | `AetherPC: call method=test arg=x` |
| AC-10 | `AetherDaemon: onStartCommand`, then `AetherDaemon: onTaskRemoved` after `am force-stop` |
| AC-12 | no `com.aether` rows in the netstat diff |

## fnPtr table (A2 artifact)

The static table is already recorded (11/11 rows, D-A1.2 closed): `phase-a1/evidence/fnptr_table.txt` holds idx / registered name / stub symbol / address / section, plus the `kMethods` relocations that carry the registered pointers. Step 10 confirms it on device: the 11 `AetherJNI: native[NN] name=… fnPtr=0x…` lines must (a) appear in kMethods order and (b) be spaced by 24 bytes, because the logged values are runtime addresses (`fnPtr=%p`), i.e. **static address + the library load base** — read that base from `adb shell cat /proc/<pid>/maps | grep libaether.so` before comparing absolute values. Full procedure: A2.8.

## A2.1 — Device requirement (locked)

| item | locked value | why |
|---|---|---|
| ABI | **arm64-v8a** | `ndk { abiFilters += "arm64-v8a" }` in `aether-engine/app/build.gradle.kts` — the APK contains no other ABI, so no other device/emulator can install it |
| Android API | **28 or higher** (`minSdk = 28`) | `minSdk = 28` in the same file; the emulator fallback image below is API 28 |
| adb | required, non-root | `adb` shell user only; **root is NOT required** |
| Alternative | emulator, **arm64 API 28+** | use only if no physical device is available — see `docs/decisions/a2-device-spec.md` for the exact `avdmanager` / `emulator` commands |
| Do NOT use | x86_64 emulator | ABI mismatch: the APK has no `x86_64` slice and `adb install` will fail |

**Evidence of device (record verbatim in `phase-a2/evidence/`):**

```
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.build.version.sdk
adb devices -l
```

## A2.2 — Pre-flight commands (record verbatim)

```
adb devices -l
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell uname -a
```

Record the raw output of all five in the A2 evidence bundle. Expected: `ro.product.cpu.abi` = `arm64-v8a`, `ro.build.version.sdk` ≥ `28`. Any other ABI or SDK < 28 = **stop and report**, do not install.

## A2.3 — Install + launch

```
adb install -r app-debug.apk
adb shell pm list packages | grep com.aether
adb logcat -c
adb shell am start -n com.aether/.Entry
adb logcat -d > /tmp/a2_logcat_start.txt
```

- `adb install -r` must print `Success`; the package line must be `package:com.aether`.
- Use the APK from the A2 build (`aether-engine/app/build/outputs/apk/debug/app-debug.apk`); record its sha256 with the pre-flight output.
- `/tmp/a2_logcat_start.txt` must contain `AetherJNI: RegisterNatives rc=0 entries=11` and the 11 `native[NN]` lines (AC-4 / AC-5).

## A2.4 — Provider dispatch

```
adb shell content call --uri content://com.aether.pc.P0 --method test --arg x
adb shell content call --uri content://com.aether.syscall --method ping
```

Capture: the returned `Bundle` **and** the logcat lines from tag `AetherPC` (and `AetherSyscall` for the second call). Expected reply bundle: `Bundle[{method=test, arg=x, reply=aether-echo}]`. Writes `/tmp/a2_logcat_provider.txt`.

**AC mapping (ledger vs order wording):** the Phase 1 DoD ledger (`docs/phase1-dod.md`) maps *provider echo → AC-9* and the instrumented contract test → AC-7/AC-8. The order header for this section reads "AC-7 / AC-8"; the ledger mapping above is authoritative, and both commands are retained as ordered.

**Operator note (recorded from the manifest, not fixed here):** every provider/service target in A2.4–A2.6 is declared `android:exported="false"` (`AndroidManifest.xml` lines 36, 39, 55, 60). `adb shell` runs as the `shell` UID, so a non-root shell may be refused with `SecurityException` / `Permission Denial`. If that happens: **record the refusal verbatim as the finding of the step** and stop that step — do **not** edit the manifest or add a debug-only exported variant without an order. AC-9/AC-10 require an exported reachable target or a root/instrumentation caller; that decision is the Commander's, not this document's.

## A2.5 — Daemon task-removed

```
adb shell am startservice -n com.aether/.helper.DaemonService
adb shell am force-stop com.aether
adb logcat -d | grep AetherDaemon
```

Expected tag `AetherDaemon` order: `onStartCommand` (service creation), then `onTaskRemoved` after `am force-stop` (source: `aether-engine/app/src/main/kotlin/com/aether/helper/DaemonService.kt`, lines 10 and 14). Writes `/tmp/a2_logcat_daemon.txt`. Same `exported="false"` operator note as A2.4 applies (manifest line 36).

Capture `logcat -d` **before** the buffer is reused; if `onTaskRemoved` is absent, take a second `adb logcat -d` immediately after the force-stop and record both.

## A2.6 — ProxyService bind

```
adb shell am startservice -n com.aether/.helper.ProxyService$P0
adb logcat -d | grep -E 'ProxyService|Aether'
```

Expected tag `AetherProxyService`: `onStartCommand` (source: `ProxyService.kt` line 10, `START_STICKY`; `onBind` at line 14 returns `null`). Writes `/tmp/a2_logcat_proxyservice.txt`. Same `exported="false"` operator note as A2.4 (manifest line 39).

## A2.7 — Network snapshot (AC-12 runtime)

```
# Before launch:
adb shell netstat -tunp > /tmp/net_before.txt
# After  launch (i.e. after A2.3–A2.6 have run):
adb shell netstat -tunp > /tmp/net_after.txt
diff /tmp/net_before.txt /tmp/net_after.txt | grep com.aether || echo NO-NET-OK
```

Expected: the literal line `NO-NET-OK`. Any `com.aether` row in the diff = **AC-12 runtime fails**; record the raw diff.

## A2.8 — fnPtr runtime confirmation

Compare the static table (`phase-a1/evidence/fnptr_table.txt`) against the running process:

1. runtime module load base:
   `adb shell cat /proc/<pid>/maps | grep libaether` → take the **lowest** mapping start of `libaether.so` as the load base;
   `<pid>` from `adb shell pidof com.aether`.
2. logcat evidence: `RegisterNatives rc=0 entries=11` and the 11 `AetherJNI: native[NN] name=… fnPtr=0x…` lines.
3. formula: **`runtime_addr = load_base + static_offset`**, so for each row
   `expected_fnPtr[i] = load_base + static_offset[i]` with the static offsets from the table
   (`ac` 0xf20, `aior` 0xf38, `awl` 0xf50, `chl` 0xf68, `djp` 0xf70, `eio` 0xf80, `i` 0xf98, `ic` 0xfb0, `ilil` 0xfc8, `jpo` 0xfdc, `update` 0xff4).

Report deltas explicitly: for each of the 11 rows, `name | static offset | load base | expected | observed | match?`. The 11 rows must appear in `kMethods` order and be spaced by **24 bytes** (JNINativeMethod stride). Result artifact: `/tmp/a2_fnptr_runtime.txt` (11 rows).

## A2.9 — A2 evidence checklist (produce per item)

| # | artifact | produced by |
|---|---|---|
| 1 | `/tmp/a2_logcat_start.txt` | A2.3 |
| 2 | `/tmp/a2_logcat_provider.txt` | A2.4 |
| 3 | `/tmp/a2_logcat_daemon.txt` | A2.5 |
| 4 | `/tmp/net_before.txt` | A2.7 (before launch) |
| 5 | `/tmp/net_after.txt` | A2.7 (after launch) |
| 6 | `NO-NET-OK` line | A2.7 diff |
| 7 | runtime fnPtr table (**11 rows**) | A2.8 |

The A2.2 pre-flight output and the A2.6 proxy-service logcat are captured in the same directory alongside these items (suggested names `a2_preflight.txt`, `a2_logcat_proxyservice.txt`; final file names are fixed on execution day). All of them are copied into `phase-a2/evidence/` (currently empty by design) and returned as the A2 evidence bundle for audit.

## Close condition

A2 opens when Commander provides a device. A2 closes only when every item of the runtime DoD (AC-4, AC-5, AC-9, AC-10, AC-12 runtime, on-device fnPtr confirmation) has raw evidence in `phase-a2/evidence/`. Phase 2 stays closed until A2 closes (`docs/phase2-plan.md`).
