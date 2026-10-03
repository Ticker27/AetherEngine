# AetherEngine

Flutter add-to-app + custom JNI bridge (planned). Current stage: **Checkpoint 1 — Android Host Skeleton**.

## Structure (Checkpoint 1)

- `android-host/` — Android host (Kotlin): `AetherApplication` (Application), `MainActivity` (launcher Activity)
- `.github/workflows/ci.yml` — CI gate: structure check + assembleDebug + test

## Roadmap

1. Checkpoint 1 — Android Host Skeleton ✅ (current)
2. Native bootstrap — `Native.kt`, `System.loadLibrary("aether")`, `JNI_OnLoad`, `RegisterNatives`
3. Native runtime state — NEW → INITIALIZED → RUNNING → STOPPING → STOPPED
4. Flutter host — FlutterEngine (add-to-app), `MethodChannel("aether/runtime")`
5. Message bridge protocol
6. Assets & testing (ABI arm64-v8a)

Scope discipline: each checkpoint lands only its own files; Native/JNI/Flutter artifacts are explicitly excluded until their checkpoint is approved.
