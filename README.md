# AetherEngine

โปรเจกต์ Android container-engine แบบ **Flutter host + native engine** — ออกแบบโครงสร้างตาม
**แบบจำลองอ้างอิง (reference model)** ที่ศึกษามาก่อนหน้า โดย **ตัดออกเฉพาะระบบ login key / license / token**
ส่วนอื่นคงโครง **เต็มรูปแบบ** และจัดวางเป็นเฟสเพื่อให้ CI ตรวจทุกขั้นก่อนเติมส่วนถัดไป

- **applicationId / namespace:** `com.aether` · **version 1.0.0**
- **Flutter 3.24.5** (stable) + **AGP 8.5.2** + **Gradle 8.7** + **JDK 17**
- **ABI:** `arm64-v8a` เท่านั้น · minSdk 28 / compileSdk 34 / targetSdk 34
- **Native:** `libaether.so` (CMake) — ผูก JNI ผ่าน `JNI_OnLoad → RegisterNatives`

## โครงสร้าง

```
AetherEngine/
├── lib/                          # Dart (Flutter UI + channel bridge)
│   ├── main.dart
│   ├── core/engine_channel.dart  # MethodChannel "com.aether/engine" — code table "0".."H"
│   └── ui/home_page.dart
├── android/
│   ├── settings.gradle / build.gradle / gradle.properties
│   ├── gradlew + gradle/wrapper/  (Gradle 8.7)
│   └── app/
│       ├── build.gradle           # flutter plugin + externalNativeBuild (CMake)
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/aether/
│           │   ├── App.java              # Application — attachBaseContext → core init + Native.ic
│           │   ├── Entry.java            # FlutterActivity + MethodChannel → CommandRouter
│           │   ├── CommandRouter.java    # code table "0","A","B","D","E","F","G","H"
│           │   ├── helper/               # Native · SystemCallProvider · DaemonService · DaemonInnerService
│           │   └── container/            # AetherCore · VPackageInstaller · VStubActivity · AppLifecycleLogger
│           ├── cpp/                      # CMakeLists.txt + aether.cpp (JNI_OnLoad, ilil/djp/chl, ic)
│           └── res/                      # styles (LaunchTheme/NormalTheme), launch_background, strings
├── docs/DESIGN.md                # กลไก + data-flow + level of parity
└── .github/workflows/build.yml   # CI: pub get → analyze → build apk → smoke → artifact
```

## กลไกหลัก

| กลไก | ที่อยู่ |
|---|---|
| Entry chain — Launcher → `App.attachBaseContext` (core init + `Native.ic`) → `App.onCreate` | `App.java`, `AetherCore.java` |
| Flutter ↔ Java — `MethodChannel "com.aether/engine"`, method name = code ในตาราง | `Entry.java`, `lib/core/engine_channel.dart` |
| Code table — `"0"` blob · `"A"` toast · `"B"`/`"D"` prefs · `"E"` url · `"F"` cached · `"G"` list · `"H"` install | `CommandRouter.java` |
| Native dynamic — ไม่มี `Java_*`; `JNI_OnLoad → RegisterNatives` (8 methods) | `aether.cpp` |
| `ilil` multi-branch decryptor · `djp` blob table (`.rodata`) · `chl` CRC32 verifier | `aether.cpp` |
| `:helper` — IPC bridge (`SystemCallProvider.call`) + keep-alive daemon (FGS + exact alarm) | `helper/*` |
| Container — registry + ติดตั้ง APK + stub เปิดแอป | `container/*` |

## ตัดออกโดยเจตนา (เฉพาะระบบ login key)

❌ login/logIn native · token/keydata/subscription · seller API · topup · device-lock · BANNED flow
❌ Firebase telemetry ของต้นแบบ (ไม่มีในโปรเจกต์นี้เช่นกัน)

## CI (เป็นเฟส)

ทุก push รัน: `flutter pub get` → `flutter analyze` (gate) → `flutter build apk --debug --target-platform android-arm64`
→ smoke check (`libaether.so`, `libflutter.so`, `classes.dex`, `assets/flutter_assets`) → artifact `AetherEngine-debug-apk`

## เฟสการประกอบ

- **A1** ✅ Flutter host + channel bridge + native engine เดิม + CI Flutter — *build gate*
- **A2** คิวถัดไป: proxy multi-process (`:p0–:p3`), VPN service, browser ในแอป, zip/picker
- **A3** native เต็มชุด (10 methods + table ขยาย), **A4** Dart UI เต็ม (container/settings/about + i18n + assets)

> structural model เพื่อการศึกษา — ไม่ใช่ container framework เต็มรูป (ไม่ hook AMS/PMS) ดู [docs/DESIGN.md](docs/DESIGN.md)
