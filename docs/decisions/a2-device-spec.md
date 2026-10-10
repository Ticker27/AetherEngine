# A2 device spec (locked)

**Decision (D-G.3):** Phase A2 runs on **arm64-v8a, Android API 28 or higher, adb access, root not
required**. No device and no emulator was started in this round — this file is a spec, not a run log.

## Locked values

| item | locked value | source in this repository |
|---|---|---|
| CPU ABI | **arm64-v8a** | `aether-engine/app/build.gradle.kts`: `ndk { abiFilters += "arm64-v8a" }` — the APK ships exactly one ABI slice |
| Android API | **28 or higher** | `aether-engine/app/build.gradle.kts`: `minSdk = 28` (`compileSdk = 35`, `targetSdk = 35`) |
| adb | required | install + launch + `content call` / `am startservice` / `netstat` / `logcat` steps |
| root | **not required** | no ordered command needs root; see the `exported="false"` operator note in `docs/phase-a2-plan.md` § A2.4 |
| emulator | allowed as fallback | only an **arm64** system image; see below |

## Device evidence (record raw output in the A2 evidence bundle)

```
adb devices -l
adb shell getprop ro.product.cpu.abi      # must print: arm64-v8a
adb shell getprop ro.build.version.sdk    # must be >= 28
```

A device that fails either check is not eligible: the APK has no other ABI slice and `minSdk = 28`
means older platforms are not installable.

## Fallback — arm64 emulator (only if no physical device)

```
avdmanager create avd -n a2 -k "system-images;android-28;google_apis;arm64-v8a"
emulator -avd a2 -no-window -no-audio &
```

- The system image must be installed first (`sdkmanager "system-images;android-28;google_apis;arm64-v8a"`).
- The trailing `&` backgrounds the emulator on the **operator host on execution day**; nothing in this
  round starts it, and `docs/phase-a2-plan.md` stays unexecuted until A2 opens.
- The emulator ABI must then be verified with the same `getprop ro.product.cpu.abi` evidence command as
  a physical device.

## Note — ABI mismatch is the one hard stop

An **x86_64** emulator cannot be used. The APK contains only `lib/arm64-v8a/libaether.so`
(`aether-engine/app/build.gradle.kts`, `abiFilters`; the artifact listing in
`phase-a1/evidence/fnptr_table.txt` shows that single slice), so `adb install` fails on an x86_64 image.
If only an x86_64 emulator is available, **report UNABLE and stop** — do not add an ABI, change
`abiFilters`, or rebuild for another architecture without an order.

## Status

Locked for A2. Executed on A2 day only; results go to `phase-a2/evidence/` (see
`docs/phase-a2-plan.md` § A2.9).
