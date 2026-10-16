# AetherEngine — Design

เอกสารนี้อธิบาย **กลไกและโครงสร้าง** ของ AetherEngine ซึ่งออกแบบโดย "ยกโครง"' จากแบบจำลองอ้างอิง
(reference container engine) แล้วลดความซับซ้อนลง — ทุกอย่างในรีโปนี้ยืนยันได้จาก source code ในรีโปเอง

---

## 1. Entry chain

```
Launcher
  └─► App.attachBaseContext(ctx)
        ├─ AetherCore.init(ctx)     # core bootstrap (double-checked, __cxa_guard-style flag)
        └─ Native.ic(ctx)           # native engine init → libaether.so!JNI_OnLoad→RegisterNatives→icImpl
  └─► App.onCreate()
        ├─ AetherCore.postInit(ctx) # อ่าน container registry
        └─ log engine version
  └─► Entry.onCreate()
        ├─ a0() ขอ POST_NOTIFICATIONS (API 33+, requestCode 1002)
        ├─ b0() ตรวจ notification channel ของ engine (channel id com.aether.engine)
        └─ ผูกปุ่ม UI → CommandRouter.dispatch(code, arg)
```

## 2. Code-table dispatcher

`CommandRouter` เทียบ code แบบ switch — โครงเดียวกับ dispatcher ที่รับ code จาก UI/ช่องทางภายนอก:

| code | งาน | ปลายทาง |
|---|---|---|
| `"0"` | blob decrypt | `Native.djp(int)` |
| `"A"` | toast | `Toast.makeText` |
| `"B"` | prefs put (Pair<String,String>) | `SharedPreferences` (`com.aether`) |
| `"D"` | prefs get | `SharedPreferences` |
| `"E"` | เปิด browser ภายนอก | `Intent.ACTION_VIEW` |
| `"F"` | cached string (get/set) | static field |
| `"G"` | list แอปใน container | `AetherCore.listInstalled` |
| `"H"` | ติดตั้งเข้า container | `AetherCore.installFromUri` → `VPackageInstaller` |

## 3. Native engine (`libaether.so`)

- **ไม่มี** symbol export รูปแบบ `Java_com_aether_*` — ผูก method ทั้ง 8 ตัวตอน `JNI_OnLoad` ผ่าน `RegisterNatives`
- **decoder**: XOR-stream แบบ stateful
  `stream(i) = (K[(i+seed) & 3] + 31·i) & 0xFF`, `plain[i] = enc[i] ^ stream(i)` โดย `K = {5A,C3,11,9E}`
- **`ilil(int)`** — dispatcher 6 สาขา; คีย์ที่รองรับ:
  `0xA11301, 0xA11501, 0xA11520, 0xA11521, 0xA21501, 0xA31501` (ไม่รู้จัก → `null`)
- **`djp(int)`** — ตาราง blob เข้ารหัสใน `.rodata` (id 0 = engine config JSON, id 1 = profile name; อื่น ๆ → `null`)
- **`chl(byte[])`** — CRC32 (bitwise, poly `0xEDB88320`) เทียบ 4 ไบต์ท้ายแบบ big-endian
- **`ic(Context)`** — engine init: อ่าน `getPackageName()` ผ่าน JNI แล้วเรียกกลับไประดับ Java
  (`AetherCore.persistEngineVersion()`), log ข้อความ decoded จากตาราง `ilil`
- **`awl/aior/ac/eio`** — sink/log hook (ในแบบจำลองเต็มคือ callback ของ container)

## 4. Multi-process / keep-alive

```
Main process (com.aether)                :helper process
├─ App / Entry                    ├─ SystemCallProvider (exported, call("VM", …))
├─ VStubActivity                         │    └─ ตอบ Bundle + Binder (_S_|_server_)
└─ (UI + dispatcher)                     └─ DaemonService
                                              ├─ NotificationChannel "com.aether.engine"
                                              ├─ startForeground(10000, notif)
                                              ├─ startService(DaemonInnerService)  # foreground จับคู่
                                              └─ AlarmManager.setExactAndAllowWhileIdle(now+60s, PendingIntent→self)
```

## 5. Container

```
เลือก APK (ACTION_GET_CONTENT)
  └─ CommandRouter "H" → AetherCore.installFromUri → VPackageInstaller.install
        ├─ copy → filesDir/containers/<name>.apk
        ├─ PackageManager.getPackageArchiveInfo → ตรวจว่าเป็น APK
        ├─ JSONObject {pkg, version, apk, installedAt} → registry (SharedPreferences "com.aether.container")
        └─ ACTION_VIEW (application/vnd.android.package-archive) ให้ system installer ทำงาน

เปิดแอปใน container
  └─ AetherCore.launchInContainer(pkg) → VStubActivity
        └─ อ่าน extra "target" → getLaunchIntentForPackage → startActivity
```

---

## Level of parity

| ชั้น | สถานะ | หมายเหตุ |
|---|---|---|
| Entry chain + core bootstrap | ✅ ตรงโครง | init/postInit สองขั้น + native call ตอน attachBaseContext |
| Code-table dispatcher | ✅ ตรงโครง | code ชุดเดียว 8 รายการ |
| Native dynamic registration | ✅ ตรงโครง | ไม่มี `Java_*`, ใช้ `RegisterNatives` |
| String/blob encryption | ✅ กลไกเดียวกัน (ขนาดเล็กกว่า) | XOR-stream decoder; ตารางเล็กใน `.rodata` |
| Helper process + keep-alive | ✅ ตรงโครง | provider + FG daemon + inner + exact alarm |
| Container install/launch | ⚠️ ลดรูป | registry + stub — ไม่ fork process, ไม่ hook ระบบ |
| Multi-process sandbox (`:pN` × 4) | ❌ ตัด | ลดความซับซ้อน |
| License / login key / seller API | ❌ ตัด | ไม่มีในโปรเจกต์นี้โดยเจตนา |
| Anti-root / anti-debug / RWX packing | ❌ ตัด | ไม่เกี่ยวกับกลไกที่ต้องการศึกษา |
