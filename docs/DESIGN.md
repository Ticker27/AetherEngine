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
| `"I"` | image picker (activity result 1000) | `ImageCompressor` → กลับทาง `imageResult` |
| `"J"` | zip + share (4 KB buffer → chooser) | `ZipShare` → `AetherFileProvider` (cacheDir) |
| `"K"` | APK picker → install | activity result 1001 → code `"H"` |
| `"L"` | device fingerprint (reflection อ่านอย่างเดียว) | `DeviceRegistry` |

## 3. Native engine (`libaether.so`)

- **ไม่มี** symbol export รูปแบบ `Java_com_aether_*` — ผูก method ทั้ง 10 ตัวตอน `JNI_OnLoad` ผ่าน `RegisterNatives`
- **Defensive load**: `System.loadLibrary` ถูกครอบ try/catch — .so มีปัญหาไม่ทำให้แอปตาย (ตรวจที่ `Native.isAvailable()` ก่อนเรียก) (chl djp ac awl i eio aior ic ilil pjowqpxe — ตรงกับตารางที่ยืนยันจากหลักฐาน)
- **decoder**: XOR-stream แบบ stateful
  `stream(i) = (K[(i+seed) & 3] + 31·i) & 0xFF`, `plain[i] = enc[i] ^ stream(i)` โดย `K = {5A,C3,11,9E}`
- **`ilil(int)`** — dispatcher 6 สาขา; คีย์ที่รองรับ:
  `0xA11301, 0xA11501, 0xA11520, 0xA11521, 0xA21501, 0xA31501` (ไม่รู้จัก → `null`)
- **`djp(int)`** — ตาราง blob เข้ารหัสใน `.rodata` (id 0 = engine config, id 1 = profile name, id 2 = device/profile JSON, id 3 = proxy descriptors; อื่น ๆ → `null`)
- **`chl(byte[])`** — CRC32 (bitwise, poly `0xEDB88320`) เทียบ 4 ไบต์ท้ายแบบ big-endian
- **`ic(Context)`** — engine init: อ่าน `getPackageName()` ผ่าน JNI แล้วเรียกกลับไประดับ Java
  (`AetherCore.persistEngineVersion()`), log ข้อความ decoded จากตาราง `ilil`
- **`awl/aior/ac/eio`** — sink/log hook (ในแบบจำลองเต็มคือ callback ของ container)
- **`i(int)` / `pjowqpxe(O,O,O)`** — int config / 3-arg sink (ตรง sig `(I)V` และ `(LOO)V` ของ live table)

## 4. Multi-process / keep-alive

```
Main process (com.aether)                 :helper process
├─ App / Entry                            ├─ SystemCallProvider (exported, call("VM", …))
├─ VStubActivity (container-only)         │  + ProxyBroadcastReceiver (goAsync → core)
├─ TransparentProxyActivity               │    └─ ตอบ Bundle + Binder (_S_|_server_)
├─ ProxyPendingActivity                   └─ DaemonService
├─ ProxyVpnService (BIND_VPN_SERVICE)         ├─ NotificationChannel "com.aether.engine"
└─ VStubActivity                              ├─ startForeground(10000, notif)
                                              ├─ startService(DaemonInnerService)
                                              └─ AlarmManager.setExactAndAllowWhileIdle(now+60s → self)

:p0   ProxyActivity$P0/P0L · ProxyService$P0 · ProxyContentProvider$P0 · ProxyJobService$P0
:p1   ProxyActivity$P1/P1L · ProxyService$P1 · ProxyContentProvider$P1 · ProxyJobService$P1
:p2   ProxyActivity$P2/P2L · ProxyService$P2 · ProxyContentProvider$P2 · ProxyJobService$P2
:p3   ProxyActivity$P3/P3L · ProxyService$P3 · ProxyContentProvider$P3 · ProxyJobService$P3
```

## 4b. Dart UI (A4)

```
RootShell (NavigationBar)
├─ Home      : engine console (codes 0/A/B/D/F/L/I/J/K) + container list (G) + launch (M)
├─ Settings  : ภาษา (EN/Filipino/Melayu/Español) · device info (L) · keep-alive/network notes
└─ About     : เวอร์ชัน · community links (E → browser)

i18n  : lib/i18n/l10n.dart — ตารางข้อความ 4 ภาษา (ไม่พึ่ง package ภายนอก)
net   : lib/net/api_client.dart — HttpClient พร้อม findProxy จาก http_proxy/NO_PROXY (ไม่มี endpoint)
icons : ใช้ Material icons แทน svg assets (ลด dependency — จุดต่างเชิงโครงสร้างเล็กน้อย)
```

## 5. Container — ติดตั้ง & เปิด guest ใน sandbox (A5)

```
ติดตั้ง (code "K" เลือกไฟล์ → "H"):
  parse APK (pkg/version/sha256) → copy → root/data/app/<pkg>/base.apk
  → เทียบ pin (TargetRegistry: 8BP = 56.31.0) → pin=match|mismatch(want=…)
  → เขียน root/data/app/<pkg>/package.conf (PackageSetting+states) + root/system/uid.conf (alloc uid ~10000)
  → root/data/user/0/<pkg>/lib  (ที่วาง module .so)
  → handoff ให้ system installer ด้วย content://com.aether.fileprovider/guest/<pkg>
     (แก้เคสจริง: file:// → FileUriExposedException ทำให้ติดตั้งไม่จบ)

เปิด (code "M") — ใน container เท่านั้น (ห้ามเปิดแอปที่ติดตั้งนอก):
  adopt legacy containers/*.apk → sandbox (migration)
  → มี sandbox apk? ไม่มี → "not-installed-in-sandbox"
  → เทียบ pin: ไม่ตรง → "blocked:version-mismatch have=… need=56.31.0" (parity: สภาพล็อก)
  → markLaunched ใน package.conf → VStubActivity
      → GuestLauncher:
         1. GuestManifest (AXML parser ในตัว) → launcher activity class
         2. PathClassLoader(sandbox base.apk, root/data/user/0/<pkg>/lib, host loader)   ← ตรงหลักฐาน
         3. โหลดคลาส → สร้างอินสแตนซ์ Activity
         4. (best-effort) inject ทรัพยากร guest: AssetManager.addAssetPath
         5. attach + onCreate ผ่าน reflection (รายงาน stage ที่ติดจริง)
         6. ยก decorView ของ guest ขึ้น host

Sandbox layout (ปรับตามดัมป์ runtime จริงของต้นแบบ 2026-10-16):
  root/system/{uid.conf, user.conf, shared-user.conf}
  root/data/app/<pkg>/{base.apk, package.conf, lib/}
  root/data/user/0/<pkg>/ · root/data/user_de/0/<pkg>/ · root/cache/ · root/proc/0/cmdline (fake)
Data layer (parity):
  files/<sha256>            แคช content-addressed (ดัมป์จริง 90 ไฟล์: png/jpg/binary) — ของเรา seed ไอคอนเป้าหมาย + index.json
  assets/targets/*.png      3 ไอคอนเป้าหมาย (สร้างเอง) · assets/community/*.svg 5 ไอคอนชุมชน (วาดเอง) · assets/data/targets.json (schema entry)
สถานะบาร์ใน UI จาก code "G": not-installed | match | mismatch (pin 56.31.0)
```

---

## 6. Guest launch ladder — ชั้นที่พิสูจน์ได้จริงบนอุปกรณ์ (2026-10-16)

```
[✓] install 56.31.0 → sandbox (sha256/package.conf/uid=10000)        log: install: … pin=match uid=10000
[✓] sandbox tree ตาม layout จริง (system confs · data/app/<pkg>/{base.apk,lib,package.conf} · user[_de] · proc · cache)
[✓] launch ใน container เท่านั้น (ไม่มี external fallback)            log: VStub(...) → …
[✓] guest class จาก sandbox: com.miniclip.eightballpool.EightBallPoolActivity   (stack: EightBallPoolActivity.onCreate)
[✓] attach ผ่าน reflection (19-param signature)
[✓] แตก native libs ของ guest: 20 libs / 102 MB (รวม libgame-BPM-…-Module-4035.so 85.5 MB)
[✓] guest onCreate ถึง native init ของตัวเกม → qwerty.asdfgh.zxcvbn.lkjhgf()
[✗] ขอบเขตที่เหลือ: native init ของเกมเอง (packer/anti-tamper ของเกม) ต้อง
    replicate framework-level hooks เพิ่ม: LoadedApk/Application forgery, PMS/AMS proxy,
    ลำดับ preload ของ module — เทียบเท่าแกนของ VA framework เต็มรูป
```

## 7. Fake framework — port จาก KOS (parity com.kos.engine.fake)

```
com.aether.fake.FakeCore            ← com.kos.engine.fake.FakeCore (init)
com.aether.fake.hook.HookManager    ← com.kos.engine.fake.hook.HookManager
com.aether.fake.delegate.AppInstrumentation
                                    ← com.kos.engine.fake.delegate.AppInstrumentation
com.aether.fake.service.IActivityManagerProxy
                                    ← IActivityManagerProxy + IBActivityManagerService$Stub$Proxy
com.aether.fake.service.IPackageManagerProxy
                                    ← IPackageManagerProxy + BPackageManager (forge จาก sandbox)
```
ติดตั้งใน `GuestLauncher` หลังสร้าง guest PathClassLoader ก่อน boot guest components (เฉพาะ :p0):
1. `ActivityThread.mInstrumentation` → AppInstrumentation (guest newActivity ผ่าน guest loader)
2. `ActivityManager.IActivityManagerSingleton` → proxy (log component ของ guest + passthrough)
3. `ActivityThread.sPackageManager` → proxy (NameNotFound → forge PackageInfo จาก sandbox conf)
4. `ActivityThread.mH` callback → HCallbackLogger (โครง redirect; consume=false)

สถานะ Round 1: ติดตั้ง/มีผลจริง (device-proof), API ครบสำหรับต่อ Round 2
(redirect ไป IBActivityThread/proxy :pN ตาม KOS)

## 8. Sandbox layout parity — วัดจาก kos_engine จริง (2026-10-20)

```
kos_engine/                        ← ต้นแบบ (live, 8BP รันอยู่)        ─ AetherEngine files/root/ ( unserer)
├ cache/{empty.apk,junit.apk}      stub APK                             ✅ cache/{empty,junit}.apk (empty-zip 22B)
├ data/app-staging/                staging ของ PMS ติดตั้ง              ✅ data/app-staging/
├ data/app/<pkg>/{lib,package.conf}                                     ✅ data/app/<pkg>/{base.apk,lib,package.conf,oat}
├ data/user/0/<pkg>/…              files,cache,databases,shared_prefs,   ✅ ครบชุด + app_textures,
│                                  no_backup,app_textures,               ✅ app_plugin_workspace_p0,
│                                  app_plugin_workspace_p0,              ✅ app_plugin_odex_p0,
│                                  app_plugin_odex_p0,app_webview…       ✅ app_webview_V_0_P0_<pkg>
├ data/user_de/0/<pkg>/                                                  ✅
├ profiles/cur/0/<pkg> · ref/0/<pkg>   ART profile redirection           ✅ profiles/{cur,ref}/0/<pkg>
├ proc/ (fake cmdline)                                                   ✅ proc/0/cmdline
└ system/{uid,user,shared-user,     conf ชุดเต็ม                        ✅ + xposed-module.conf,
          xposed-module,developer-module}.conf                          ✅ developer-module.conf
```
จุดต่างโดยเจตนา: conf ของเราเป็น JSON (อ่าน/ทดสอบง่าย) — ต้นแบบเป็น binary serialization ของ BPackage;
`oat/arm64/base.odex` เกิดจาก ART ตอนโหลดจริง (ทั้งคู่มี)

## 9. Install source = device install (A9)

เปลี่ยนแหล่งที่มา: guest APK อ่านจาก **PackageManager ของแอปที่ติดตั้งบนเครื่อง**
(`ApplicationInfo.sourceDir` + `splitSourceDirs`) — ไม่ดึงจากไฟล์ใน /sdcard หรือ SAF
```
code "N" → AetherCore.installFromDevice(pkg)
  → copy base.apk (+split_<name>.apk ทั้งหมด) → root/data/app/<pkg>/
  → sha256 + versionName/Code จริงจาก PackageInfo → package.conf (source="device:<pkg>")
  → markReadOnly → PathClassLoader ใช้ base+split ทั้งชุด, natives แตกจากทุก apk
UI: การ์ดเกมมีปุ่ม "Install from device" (l10n 4 ภาษา) · debug hook: --ez install_device true
```
ปิดช่องเดิม: fixture ทดสอบไม่ใช้ไฟล์ /sdcard/Download อีก — ใช้ของที่ติดตั้งจริงบนเครื่องเท่านั้น

## 10. Version subsystem (สมบูรณ์ก่อนชั้น plugin — A10)

```
TargetRegistry.Target = { id, name, pkg, pinVersion, allowedVersions[], versionPolicy(exact|listed|any), moduleVersion, icon }
VersionUtil.evaluate(version, pin, allowed, policy) → Result{state: match|allowed|mismatch|unknown, reason}
```
หลักฐานที่ใช้ตั้งนโยบาย:
- 8 Ball Pool: pin **56.31.0** (เป้าหมาย reference) · listed **{56.31.0, 56.31.1, 56.32.0}**
  (56.32.0 = เวอร์ชันที่ติดตั้งจริงบนเครื่อง และตรงกับ entry ฝั่งต้นแบบ; 56.31.1 = เคสที่เคยพบใน install log)
- Carrom 19.5.2 exact-listed · Soccer Stars 37.1.1

ผลผูกเข้า 3 จุด: install (pin=Result), package.conf (pin), launch gate (block เฉพาะ state=mismatch),
targetsSnapshot (status/statusReason/policy/allowed → UI chip เขียว=match · น้ำเงิน=allowed · ส้ม=mismatch)

## 11. Plugin/module pipeline (parity KOS — A11)

หลักฐาน (live KOS 2026-10-20 + ไฟล์จริงจาก CDN):
```
กดเล่น → GET https://koscheat.xyz/apk/50003657687a67084f6d691678e9a422cfbb6b72.bin
        (ไฟล์ = APK จริง: classes.dex + libmkos.so 13.7MB + libbytehook + libshadowhook)
        sha256(file) = acc706395e30… = loader_com.miniclip.eightballpool ใน KOS_App_Settings.xml  ← ตัวตน "loader"
        เก็บ cache: /data/user/0/com.kos/cache/<serverId>.bin   (VM config pathToApk ชี้ไฟล์นี้)
        กระจาย: guest data/app_plugin_workspace_p0 → apk ที่ VM โหลดเข้าเกสต์
```
AetherEngine (`com.aether.container.PluginInstaller`):
- code "O" / step 3.9 ใน GuestLauncher / background ตอนกด "M"
- `root/cache/<sha256>.bin` (self-record — KOS ใช้ serverId: deviation ที่ตั้งใจ) + verify sha ตัวเอง
- distribute → `root/data/user/0/<pkg>/app_plugin_workspace_p0/plugin.apk`
- บันทึก `setting.plugin = {url, path, sha256, size, downloadedAt, id, workspace{file,size,at}}`
- snapshot/UI: pluginReady/pluginSha + ปุ่ม "Plugin"

## 12. Log/dump ในตัว + LOG VIEW export (parity KOS — A12)

ดัมป์จริงของ KOS (ตรวจเมื่อ 2026-10-20, แนบจากเครื่อง):
```
<epoch_ms> [KOS-DIRECT] process=<proc> pid=<pid> <ข้อความ>
com.kos.txt                                   host reader  (virtual=host)
<pkg>-YYYY-MM-DD_HH-mm-ss-SSS-0.txt           guest session (virtual=<pkg>/0, :p0 "game reader")
  เปิด:  "[LOGGER] capture started; file=… virtual=…; raw logcat is best-effort; …"
  ปิด:   "game process ended; draining final logcat records"
เก็บล่าสุด 3 game logs + com.kos.txt · Settings → LOG VIEW → Export Recent Logs (share)
```
AetherEngine (`com.aether.util.LogDump`):
- รูปแบบบรรทัด `<ms> [AE-DIRECT] process=<Application.getProcessName()> pid=<pid> …`
- `logs/com.aether.txt` (host) + `logs/<pkg>-<ts>-0.txt` ต่อ session (เปิดโดย GuestLauncher step แรก, ปิดใน VStubActivity พร้อม final status)
- rotate อัตโนมัติเก็บ 3 ไฟล์ล่าสุด · export = zip → `AetherFileProvider` → share chooser "Export Recent Logs"
- code "P" + Settings row (l10n ×4) + debug hook `--ez export_logs true`

## 13. Plugin + Loader (A13) — parity KOS, ออกแบบจากหลักฐานสด

```
กด Play → PluginInstaller.ensurePlugin
   cache/<sha256>.bin (app cache)
   → workspace/app_plugin_workspace_p0/plugin_<epoch>.apk + lib/*.so + oat/arm64/
   → prefs loader_<pkg> = sha256  · cache/aether_vm_config.properties
→ PluginLoader.load: DexClassLoader(apk, oat, lib) → System.load(*.so) → entry.init(Context)/bind(bridge)
→ FakeCore→HookManager → GuestContext → Adoption → Application → providers → Activity
```
สัญญาปลั๊กอิน: `assets/aether-plugin.json {"entry":…}` (ไม่บังคับ) · entry = `static init(Context)` หรือ `Entry(Context)+bind(bridge)` · ไม่มี entry = module .so ล้วน
รายละเอียด/ failure modes/ test plan: `docs/PLUGIN-LOADER.md`

## 14. "ความฉลาด" ตามระบบ KOS (A14 — ยังไม่ประกอบ/ยังไม่ push รอ call)

state machine + threading ที่เพิ่ม (เทียบพฤติกรรม KOS สด):
```
RunOffMain                          (ใหม่) — งานหนักไม่แตะ main เลยทุกจุด
PluginInstaller.ensurePlugin        → state machine: verify sha256 ของ cache สดทุกครั้ง (KOS: loader_ identity)
                                        reuse ผิด → ดาวน์โหลดใหม่อัตโนมัติ; ล้าง cache bin เก่าให้เหลือชุดเดียว
PluginInstaller.materialize         → ล้าง plugin/legacy เก่าใน workspace ก่อนเขียน (เหลือ plugin_<epoch>.apk ตัวเดียว)
                                        + lib/*.so + oat/arm64 + loader_<pkg> + aether_vm_config.properties
PluginLoader.load                   → off-main (DexClassLoader → oat/arm64, System.load lib, entry/bridge)
AetherCore.installFromDevice        → off-main (join 10 นาที) — copy 174MB จาก PackageManager sourceDir
AetherCore.installFromUri (H/K)     → off-main (join 10 นาที)
AetherCore.postInit (bootstrap)     → worker หลังหน้าจอพร้อม (ensure trees + seed icons)
VStubActivity.onCreate              → worker "aether-vsstub-boot": install_device → launchInto → export → endSession;
                                       UI อัปเดตผลผ่าน runOnUiThread ("preparing …")
```
สถานะข้อความ activity และ error ทั้งหมดคงปรัชญา `container:*` เดิม (ไม่เปิดแอปภายนอก)

## Level of parity

| ชั้น | สถานะ | หมายเหตุ |
|---|---|---|
| Entry chain + core bootstrap | ✅ ตรงโครง | init/postInit สองขั้น + native call ตอน attachBaseContext |
| Code-table dispatcher | ✅ ตรงโครง | code ชุดเดียว 8 รายการ |
| Native alignment | ✅ 16KB | `-Wl,-z,max-page-size=16384` (ตรงมาตรฐานเดียวกับ engine) — กันอุปกรณ์ 16KB pages |
| Defensive load | ✅ | `Native.isAvailable()` + `isAvailable` guard (pure-Java mode ถ้า .so โหลดไม่ได้) |
| Native dynamic registration | ✅ ตรงโครง | ไม่มี `Java_*`; 10 methods + sig ตรงกับ live table ที่ยืนยันไว้ |
| String/blob encryption | ✅ กลไกเดียวกัน (ขนาดเล็กกว่า) | XOR-stream decoder; ตารางเล็กใน `.rodata` |
| Helper process + keep-alive | ✅ ตรงโครง | provider + FG daemon + inner + exact alarm + ProxyBroadcastReceiver |
| Container install | ✅ A5 | parse→sandbox(sha256,self-record)→package.conf/uid.conf→content:// handoff |
| Container launch | ✅ A5 (in-container) | PathClassLoader จาก sandbox apk + guest libDir + attach/onCreate — ไม่มี external fallback |
| Target pin (56.31.0) | ✅ A5 | TargetRegistry + บล็อกเมื่อ version-mismatch (parity: สภาพล็อก) |
| Guest manifest (AXML) | ✅ A5 | parser ในตัว อ่าน launcher activity จาก apk ใน sandbox |
| Sandbox layout (real) | ✅ A6 | root/system confs · root/data/app/<pkg>/{base.apk,package.conf,lib} · user/user_de · proc/0/cmdline (fake) · cache |
| Content cache files/<sha256> | ✅ A6 | ContentCache + seed ไอคอนจาก assets (+index.json ส่วนเพิ่มของเรา) |
| Data assets | ✅ A6 | ไอคอนเป้าหมาย ×3 · svg ชุมชน ×5 (ของเราเอง — ไม่ลอกไฟล์ต้นฉบับ) · targets.json |
| Proxy components `:p0–:p3` (Activity+L/Service/Provider/JobService) | ✅ A2a | โครงครบทุก process — stub ไม่ route เข้า target จริง, exported=false (แบบจำลองตั้ง true) |
| VPN service (BIND_VPN_SERVICE, Always-On meta) | ✅ A2a | structural stub — ไม่ establish tunnel |
| Browser ในแอป (WebView machinery) | ✅ A2b | JS on · mixed-always · safe-browsing off · cookie manager — exported=false |
| Zip share + file provider | ✅ A2b | provider เขียนเอง อ่านได้เฉพาะ cacheDir (กัน path traversal) |
| Image picker + compress 128² q85 ≤10 KB | ✅ A2b | ครบกลไก พร้อมส่งผลกลับ Dart |
| License / login key / seller API | ❌ ตัด | ไม่มีในโปรเจกต์นี้โดยเจตนา |
| Anti-root / anti-debug / RWX packing | ❌ ตัด | ไม่เกี่ยวกับกลไกที่ต้องการศึกษา |
