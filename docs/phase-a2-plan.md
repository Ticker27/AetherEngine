# Phase A2 plan (plan only — not executed in this round)

Runtime DoD: AC-4, AC-5, AC-9, AC-10, AC-12 (runtime netstat), plus the on-device confirmation of the fnPtr table whose static form is already closed (`phase-a1/evidence/fnptr_table.txt` → 11/11 rows since D-C1.2; background in `docs/decisions/unstripped-debug.md`).

## Preconditions

- arm64-v8a device or emulator, API 28 or higher — the APK ships `arm64-v8a` only (`abiFilters`), so an x86 emulator cannot install it.
- `adb` reachable from the operator host; `aether-engine/app/build/outputs/apk/debug/app-debug.apk` present (from `./gradlew :app:assembleDebug`).

## Runtime checklist — run in order

All commands run from the `aether-engine/` directory unless stated. Column **AC** maps each command to the acceptance criterion its output closes; the logcat lines behind AC-4/5/9/10/12 are listed under "Expected logcat content". The combined form `adb logcat -c && adb shell am start -n com.aether/.Entry` is steps 4+5 below.

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

The static table is already recorded (11/11 rows, D-A1.2 closed): `phase-a1/evidence/fnptr_table.txt` holds idx / registered name / stub symbol / address / section, plus the `kMethods` relocations that carry the registered pointers. Step 10 confirms it on device: the 11 `AetherJNI: native[NN] name=… fnPtr=0x…` lines must (a) appear in kMethods order and (b) be spaced by 24 bytes, because the logged values are runtime addresses (`fnPtr=%p`), i.e. **static address + the library load base** — read that base from `adb shell cat /proc/<pid>/maps | grep libaether.so` before comparing absolute values.

## Close condition

A2 opens when Commander provides a device.