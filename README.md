# AetherEngine

Android container-engine sample ที่ออกแบบโครงสร้างตาม **แบบจำลองอ้างอิง (reference model)** ที่ศึกษามาก่อนหน้า
โดย **ลดความซับซ้อน** และ **ไม่มีระบบ login key / license / token ใด ๆ**

- **applicationId / namespace:** `com.aether`
- **version:** `1.0.0` (versionCode 1)
- **ABI:** `arm64-v8a` เท่านั้น
- **minSdk 28 / compileSdk 34 / targetSdk 34**
- **ไม่มี external dependency** (ไม่มี androidx / firebase / retrofit)
- **ไม่มี gradle wrapper ในรีโป** — CI ติดตั้ง Gradle 8.7 เอง (ตามนโยบาย assemble ผ่าน Actions)

## โครงสร้าง

```
AetherEngine/
├── app/src/main/
│   ├── java/com/aether/
│   │   ├── App.java                  # Application — attachBaseContext → AetherCore.init + Native.ic(ctx)
│   │   ├── MainActivity.java         # LAUNCHER Activity + code-table dispatcher
│   │   ├── CommandRouter.java        # code table: "0","A","B","D","E","F","G","H"
│   │   ├── helper/
│   │   │   ├── Native.java           # JNI bridge (8 methods) → libaether.so ผ่าน RegisterNatives
│   │   │   ├── SystemCallProvider.java  # :helper — call() based IPC bridge
│   │   │   ├── DaemonService.java    # :helper — foreground keep-alive + AlarmManager reschedule
│   │   │   └── DaemonInnerService.java
│   │   └── container/
│   │       ├── AetherCore.java       # engine core: init/postInit + container registry
│   │       ├── VPackageInstaller.java   # ติดตั้ง APK เข้า container (copy + registry + system installer)
│   │       ├── VStubActivity.java    # stub ที่เปิดแอปเป้าหมาย
│   │       └── AppLifecycleLogger.java
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   └── aether.cpp                # JNI_OnLoad + RegisterNatives + ilil/djp/chl + ic
│   ├── AndroidManifest.xml
│   └── res/ (strings, layout)
├── docs/DESIGN.md                    # คำอธิบายกลไก + data-flow diagrams
└── .github/workflows/build.yml       # assemble APK + artifact
```

## กลไกหลัก (ย่อ)

| กลไก | ที่อยู่ |
|---|---|
| **Entry chain** — Launcher → `App.attachBaseContext` (core init + native `ic`) → `App.onCreate` (postInit) | `App.java`, `AetherCore.java` |
| **Code-table dispatcher** — 8 คำสั่ง ("0","A","B","D","E","F","G","H") รวม prefs/toast/blob/install | `CommandRouter.java`, `MainActivity.java` |
| **JNI แบบ dynamic** — ไม่มี symbol `Java_*`; ผูกผ่าน `JNI_OnLoad` → `RegisterNatives` | `aether.cpp` |
| **`ilil(int)`** — string decryptor แบบ multi-branch dispatcher | `aether.cpp` |
| **`djp(int)`** — blob decryptor จากตาราง .rodata | `aether.cpp` |
| **`chl(byte[])`** — CRC32 verifier | `aether.cpp` |
| **`:helper` process** — IPC bridge (`SystemCallProvider.call`) | `SystemCallProvider.java` |
| **Keep-alive** — foreground service บน channel ของ engine + inner service + exact alarm reschedule | `DaemonService.java` |
| **Container** — registry ของแอปที่ติดตั้ง + stub เปิดแอป | `AetherCore.java`, `VPackageInstaller.java`, `VStubActivity.java` |

## ไม่มีในโปรเจกต์นี้ (โดยเจตนา)

- ❌ ระบบ login key / license / subscription / token / seller หรือ backend API ใด ๆ
- ❌ analytics, push, VPN, badge, storage permission ขนาดใหญ่ (เหลือ 7 permissions ที่จำเป็น)
- ❌ multi-process sandbox 4 ตัว (รัน stub ใน main process เดียว)
- ❌ obfuscation ชั้นรหัส/ชื่อที่อ่านไม่ออก

## Build (GitHub Actions)

workflow `build.yml`: JDK 17 → Android SDK → Gradle 8.7 → `gradle assembleDebug` → smoke check `lib/arm64-v8a/libaether.so` → artifact `AetherEngine-debug-apk`

## ขอบเขตของแบบจำลอง

เป็น **structural model** เพื่อการศึกษา: เก็บ "กลไกและโครงสร้าง" (entry chain, code table, native registration,
decryptor pattern, helper/keep-alive, container registry) แต่ **ไม่ใช่** container framework เต็มรูป —
ไม่มีการ hook AMS/PMS/Binder และไม่มีการรันแอปอื่นใน isolated process ดูรายละเอียดใน [docs/DESIGN.md](docs/DESIGN.md)
