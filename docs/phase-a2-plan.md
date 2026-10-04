# Phase A2 plan (plan only — not executed in this round)

Runtime DoD: AC-4, AC-5, AC-9, AC-10, AC-12 (runtime netstat), plus the fnPtr table that static analysis cannot produce (see `phase-a1/evidence/fnptr_table.txt` → UNABLE: stripped `.so` + no local AArch64 disassembler).

## Preconditions

- arm64-v8a device or emulator, API 28 or higher — the APK ships `arm64-v8a` only (`abiFilters`), so an x86 emulator cannot install it.
- `adb` reachable from the operator host.

## Steps

1. `cd aether-engine && ./gradlew :app:assembleDebug`
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb logcat -c` then `adb shell am start -n com.aether/.Entry`
4. `adb shell content call --uri content://com.aether.pc.P0 --method test --arg x` → expect the `aether-echo` bundle
5. `adb shell am startservice -n com.aether/.helper.DaemonService`, then `adb shell am force-stop com.aether` → `onTaskRemoved`
6. `adb shell netstat -tunp` before and after the run; diff; assert `NO-NET-OK`
7. `adb logcat -d` → collect `AetherJNI` lines: `RegisterNatives rc=… entries=11` and `native[NN] name=… fnPtr=…` → build the fnPtr table (name | address | section)
8. `adb shell am instrument -w -e class com.aether.NativeContractTest com.aether.test/androidx.test.runner.AndroidJUnitRunner` (androidTest, 5 tests)

## Close condition

A2 opens when Commander provides a device.