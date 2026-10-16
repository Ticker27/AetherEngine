# AetherEngine

โปรเจกต์ Android container-engine แบบ **Flutter host + native engine** — ออกแบบโครงสร้างตาม
**แบบจำลองอ้างอิง (reference model)** ที่ศึกษามาก่อนหน้า โดย **ตัดออกเฉพาะระบบ login key / license / token**
ส่วนอื่นคงโครง **เต็มรูปแบบ** และจัดวางเป็นเฟสเพื่อให้ CI ตรวจทุกขั้นก่อนเติมส่วนถัดไป

- **applicationId / namespace:** `com.aether` · **version 1.0.0**
- **Flutter 3.24.5** (stable) + **AGP 8.5.2** + **Gradle 8.7** + **JDK 17**
- **ABI:** `arm64-v8a` เท่านั้น · minSdk 28 / compileSdk 34 / targetSdk 34
- **Native:** `libaether.so` (CMake, 16KB page-aligned) — ผูก JNI ผ่าน `JNI_OnLoad → RegisterNatives`

## โครงสร้าง

```
AetherEngine/
├── assets/                        # targets/*.png · community/*.svg · data/targets.json
├── lib/                          # Dart (Flutter UI + channel bridge)
│   ├── main.dart                 # RootShell: Home · Settings · About (NavigationBar)
│   ├── core/engine_channel.dart  # MethodChannel "com.aether/engine" — code table "0".."M"
│   ├── i18n/l10n.dart            # 4 ภาษา: EN · Filipino · Melayu · Español
│   ├── net/api_client.dart       # proxy-aware HttpClient (http_proxy/NO_PROXY — ไม่มี endpoint)
│   └── ui/                       # home (console+containers) · settings · about
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
| Code table — `"0"` blob · `"A"` toast · `"B"`/`"D"` prefs · `"E"` url · `"F"` cached · `"G"` list · `"H"`/`"K"` install · `"I"` pick · `"J"` zip-share · `"L"` device · `"M"` launch | `CommandRouter.java` |
| Native dynamic — ไม่มี `Java_*`; `JNI_OnLoad → RegisterNatives` (10 methods ตรง live table) | `aether.cpp` |
| `ilil` multi-branch decryptor · `djp` blob table (`.rodata`) · `chl` CRC32 verifier | `aether.cpp` |
| `:helper` — IPC bridge (`SystemCallProvider.call`) + keep-alive daemon (FGS + exact alarm) | `helper/*` |
| Container — registry + ติดตั้ง APK + stub เปิดแอป | `container/*` |

## ตัดออกโดยเจตนา (เฉพาะระบบ login key)

❌ login/logIn native · token/keydata/subscription · seller API · topup · device-lock · BANNED flow
❌ Firebase telemetry ของต้นแบบ (ไม่มีในโปรเจกต์นี้เช่นกัน)

## CI (เป็นเฟส)

ทุก push รัน: `flutter pub get` → `flutter analyze` (gate) → `flutter build apk --release --target-platform android-arm64` (AOT)
→ smoke: `.so`+assets ใน APK · proxy components ใน merged manifest · native 10 methods ใน `.so` (NUL-scan) · 16KB page alignment (`libaether.so` ENFORCE ≥0x4000)
→ artifact `AetherEngine-apk`

## เฟสการประกอบ

- **A1** ✅ Flutter host + channel bridge + native engine เดิม + CI Flutter — *build gate*
- **A2a** ✅ proxy `:p0–:p3` ครบ + VPN + permission battery + CI manifest gate
- **A2b** ✅ browser ในแอป + zip share/provider + image picker/compress (codes I/J/K)
- **A3** ✅ native เต็มชุด 10 methods + blob table ×4 + DeviceRegistry + gate native
- **A6** ✅ data layer ตามดัมป์จริง: sandbox layout (root/system confs · data/app/<pkg>/{base.apk,package.conf,lib} · user[_de] · fake proc) · ContentCache files/<sha256> · ไอคอน/assets ของเราเอง
- **A5** ✅ deep install/launch: TargetRegistry pin (8BP=56.31.0) · sandbox root + conf 6 ไฟล์ · GuestManifest(AXML) · GuestLauncher(PathClassLoader ใน container — ไม่เปิดแอปนอก) · content:// handoff แก้ FileUriExposedException
- **A4** ✅ Dart UI เต็ม — Home (console + container list + launch) · Settings (ภาษา/เครื่อง) · About (ชุมชน) + i18n 4 ภาษา + proxy-aware client + code "M"

> เสร็จครบ 4 เฟส — โครงเต็มรูปแบบ (ตัดเฉพาะระบบ login key) โดยทุกเฟสผ่าน CI gate

> structural model เพื่อการศึกษา — ไม่ใช่ container framework เต็มรูป (ไม่ hook AMS/PMS) ดู [docs/DESIGN.md](docs/DESIGN.md)
