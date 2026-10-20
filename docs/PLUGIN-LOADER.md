# Plugin + Loader system — ออกแบบตามกลไก KOS (A13)

> ฐานหลักฐาน: `live-2026-10-20/evidence/` (INDEX v2/v3) — วัดสดจาก KOS 3.7 ตอนกด Play (pids 12449/12491)
> เป้าหมาย: ทำให้ "เริ่มต้นเกม" ได้ด้วย pipeline เดียวกับต้นแบบ — **ไม่พึ่งระบบ login key**

## 1) กลไกต้นแบบ (วัดได้จริง — ลำดับเป๊ะ)

```
[กด Play]
 1. GET  <pluginUrl>                          (koscheat.xyz/apk/<id>.bin)
 2. cache/<id>                                /data/user/0/com.kos/cache/82f8ddb6…  9,533,457 B
    sha256(ไฟล์) = 0553f818…  =  loader_com.miniclip.eightballpool  (KOS_App_Settings.xml)
 3. workspace                                 …/data/user/0/<pkg>/app_plugin_workspace_p0/
      plugin_<epochMs>.apk                    (sha เดียวกับ cache)
      lib/{libbytehook,libmkos,libshadowhook}.so
      oat/plugin_<epochMs>.apk.cur.prof (+ oat/arm64/…)
 4. vm config                                 cache/blackbox_vm_config.properties → pathToApk=cache/<id>
 5. engine เข้า guest: PathClassLoader(sandbox apk) → providers → Application → Activity
      (native ของ engine เองโหลดจาก base.apk!/lib/arm64-v8a — ไม่แตกไฟล์)
```

## 2) สิ่งที่เราสร้าง (map 1:1)

| ขั้น KOS | AetherEngine | ไฟล์ |
|---|---|---|
| cache/<id> | `ctx.getCacheDir()/<sha256>.bin` (self-verify) | `PluginInstaller.appCache()` |
| loader_<pkg> | SharedPreferences `aether_plugin` → `loader_<pkg>` = sha | `PluginInstaller.materialize()` |
| workspace/plugin_<epoch>.apk | `…/app_plugin_workspace_p0/plugin_<epoch>.apk` (setReadOnly) | `PluginInstaller.materialize()` |
| workspace/lib | แตก `lib/arm64-v8a/*.so` ทุกตัว | `PluginInstaller.extractLibs()` |
| workspace/oat | `oat/arm64/` เป็น optimizedDirectory ของ DexClassLoader | `PluginLoader.load()` |
| vm config | `cache/aether_vm_config.properties` (enable_plugin/pathToApk/loader_sha256/g_id) | `PluginInstaller.writeVmConfig()` |
| plugin ทำงานใน guest process | `PluginLoader.load()` เรียกก่อน boot guest | `GuestLauncher` step 3.9 |

## 3) สัญญาของปลั๊กอิน (contract)

```
plugin.apk
├ classes.dex
├ lib/arm64-v8a/*.so
└ assets/aether-plugin.json      (ไม่บังคับ)
   {"entry":"com.example.Entry","abi":"arm64-v8a","minApi":28,"name":"...","version":"..."}
```
- entry API: `public static void init(Context)` **หรือ** `public Entry(Context)` + `bind(Object bridge)`
- ถ้าไม่มี entry → ถือเป็น **module แบบ .so ล้วน** (โหลด native ให้เสร็จแล้วจบ) — ไม่ทำให้เกสต์ล้ม
- bridge ที่ส่งให้: `PluginLoader.Bridge{ ctx, pkg, guestLoader, workspace, callHost(code,arg), log(msg) }`
  `callHost` ใช้ code table เดียวกับ Dart (`"0".."P"`) → ปลั๊กอินสั่งงาน host ได้เท่ากับ UI

## 4) ลำดับรันจริงใน GuestLauncher (:p0)

```
1  LogDump session start
2  extract guest natives (base+split)  → sandbox lib
3  PathClassLoader(guest apk+splits, sandbox lib)
4  PluginInstaller.ensurePlugin()      → cache → workspace(apk/lib/oat) → prefs loader_ → vm config
5  PluginLoader.load()                 → DexClassLoader(apk, oat, lib) → System.load(*.so) → entry
6  FakeCore/HookManager                → instrumentation + IAMS + IPMS + mH
7  GuestContext (sandbox dirs) + setenv HOME
8  guest appInfo (sourceDir/nativeLibDir/dataDir) + ProcessAdoption (7 ขั้น + setArgV0)
9  Application attach → providers → onCreate
10 guest Activity (launch) / VStub
```

## 5) Failure modes (ออกแบบให้ล้มแบบนิ่ม)

| อาการ | พฤติกรรม |
|---|---|
| ดาวน์โหลดไม่ได้/HTTP≠200 | `ensurePlugin → download-failed` — เกสต์ยังบูตต่อได้ (ไม่บล็อก) |
| plugin ไม่มี entry class | `loader → skipped … module-libs-only` — native ถูกโหลดแล้ว |
| .so ตัวใดโหลดไม่ได้ | ข้ามตัวนั้น + log ชื่อ/lib — ตัวอื่นโหลดต่อ |
| dex โหลดไม่ได้ (Android 14 writable) | `markReadOnly()` ก่อนทุกครั้ง (เหมือนฝั่ง guest apk) |

## 6) แผนทดสอบบนเครื่อง (หลัง CI เขียว)

1. ล้าง `cache/*.bin`, `workspace/*` ของ 8BP
2. `am start … VStubActivity --es target com.miniclip.eightballpool`
3. ตรวจ log: `plugin: downloaded:0553f818… (workspace-ok:3lib)` → `plugin loader: loaded/skipped …`
4. ตรวจไฟล์: `workspace/{plugin_<epoch>.apk, lib/*.so, oat/arm64/*}`, prefs `loader_com.miniclip.eightballpool`, `cache/aether_vm_config.properties`
5. ตรวจ session log: `logs/<pkg>-<ts>-0.txt` มีบรรทัด `[plugin] ensure/loader`
