# Phase A2 plan (plan only — not executed in this round)

Runtime DoD: AC-4, AC-5, AC-9, AC-10, AC-12 (runtime netstat), plus the fnPtr table that static analysis cannot produce (see `phase-a1/evidence/fnptr_table.txt` → UNABLE: stripped `.so`, no local AArch64 disassembler; background in `docs/decisions/unstripped-debug.md`).

## Preconditions

- arm64-v8a device or emulator, API 28 or higher — the APK ships `arm64-v8a` only (`abiFilters`), so an x86 emulator cannot install it.
- `adb` reachable from the operator host; `aether-engine/app/build/outputs/apk/debug/app-debug.apk` present (from `./gradlew :app:assembleDebug`).

## Device checklist — run in order

All commands run from the `aether-engine/` directory unless stated.

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
| 10 | `adb logcat -d > aether_logcat.txt` | captured log | AC-4/5/9/10 |
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

From step 10, parse the 11 `AetherJNI: native[NN] name=… fnPtr=0x…` lines into a `name | address | section` table (section resolved with `readelf -SW`, since the shipped `.so` is stripped — see `docs/decisions/unstripped-debug.md`). This closes the `UNABLE` entry in `phase-a1/evidence/fnptr_table.txt`.

## Close condition

A2 opens when Commander provides a device.