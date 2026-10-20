package com.aether.container;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.view.View;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * GuestLauncher — เปิด guest "ใน container ของเราเอง" จาก sandbox APK (ไม่มี external fallback เด็ดขาด)
 *
 * ลำดับกลไก (parity: PathClassLoader + libDir ของ guest):
 *   1. sandbox apk:  root/data/app/<pkg>/base.apk
 *   2. อ่าน manifest ของ guest (AXML) → launcher activity class
 *   3. PathClassLoader(apkPath, root/data/user/0/<pkg>/lib, hostLoader)
 *   4. โหลดคลาส launcher → สร้างอินสแตนซ์ Activity
 *   5. (พยายาม) inject ทรัพยากรของ guest ผ่าน AssetManager.addAssetPath (ถ้าถูกจำกัด → degrade)
 *   6. attach + onCreate ผ่าน reflection (รายงานขั้นที่ไปถึงจริง — ไม่เดา)
 *   7. ยก decorView ของ guest ขึ้นหน้าจอ host
 *
 * ทุกขั้นมีผลลัพธ์เป็นข้อความ stage:... เพื่อให้ UI/log บอกได้ว่าติดที่ชั้นไหน (ไม่แสดงความสำเร็จเท็จ)
 */
public final class GuestLauncher {

    public static final String TAG = "AetherEngine";

    /** ถอด InvocationTargetException ให้เห็น cause จริง (สำคัญต่อการวินิจฉัย stage) */
    private static String describe(Throwable t) {
        Throwable c = t;
        if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
            c = t.getCause();
        }
        return c.getClass().getName() + ": " + c.getMessage();
    }

    /** แตก lib/*.so ของ guest จาก APK → sandbox lib dir (Unity/IL2CPP หา nativeLibraryDir) */
    private static void prepareGuestNatives(File apk, File libDir) {
        try {
            File[] have = libDir.listFiles((d, f) -> f.endsWith(".so"));
            if (have != null && have.length > 0) return;   // ทำครั้งเดียว (นับเฉพาะ .so)
            int n = 0, failed = 0; long total = 0;
            File[] allApks = apk.getParentFile() != null
                    ? apk.getParentFile().listFiles((d, f) -> f.endsWith(".apk")) : null;
            java.util.List<File> apkList = new java.util.ArrayList<>();
            if (allApks != null) java.util.Collections.addAll(apkList, allApks);
            if (apkList.isEmpty()) apkList.add(apk);
            for (File oneApk : apkList)
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(oneApk)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (!name.startsWith("lib/arm64-v8a/") || !name.endsWith(".so")) continue;
                    File out = new File(libDir, name.substring(name.lastIndexOf('/') + 1));
                    if (copyEntry(zf, e, out)) { n++; total += out.length(); }
                    else { failed++; }   // ไฟล์เดียวพัง ต้องไม่ล้มทั้งชุด
                }
            }
            android.util.Log.i(TAG, "guest natives extracted: " + n + " ok, " + failed + " failed, "
                    + (total / 1024 / 1024) + " MB → " + libDir);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "native extract failed: " + t);
        }
    }

    private static boolean sBypassTried = false;
    private static java.io.File sLastSession;

    public static java.io.File lastSession() {
        return sLastSession;
    }

    /** เปิด hiddenapi exemptions เพื่อให้ reflection แตะ method ของ framework ได้
     *  (double-reflection ผ่าน VMRuntime.setHiddenApiExemptions — เทคนิคที่ framework plugin ใช้กันทั่วไป)
     *  ถ้าไม่สำเร็จ → ไม่เป็นไร ดำเนินต่อและรายงาน stage ที่ไปถึง */
    private static synchronized void tryHiddenApiBypass() {
        if (sBypassTried) return;
        sBypassTried = true;
        try {
            java.lang.reflect.Method forName = Class.class.getDeclaredMethod("forName", String.class);
            java.lang.reflect.Method getDeclaredMethod =
                    Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntime = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            java.lang.reflect.Method getRuntime = (java.lang.reflect.Method)
                    getDeclaredMethod.invoke(vmRuntime, "getRuntime", (Object) null);
            Object runtime = getRuntime.invoke(null);
            java.lang.reflect.Method setExemptions = (java.lang.reflect.Method)
                    getDeclaredMethod.invoke(vmRuntime, "setHiddenApiExemptions", (Object) new Class[]{String[].class});
            setExemptions.invoke(runtime, (Object) new String[]{"L"});
            android.util.Log.i(TAG, "hiddenapi exemptions set");
        } catch (Throwable t) {
            android.util.Log.w(TAG, "hiddenapi bypass unavailable (เดินต่อ): " + t);
        }
    }

    private GuestLauncher() {}

    /** @return status string ของการ launch ใน container */
    public static String launchInto(Activity host, String pkg) {
        tryHiddenApiBypass();

        File apk = SandboxStore.guestApk(host, pkg);
        if (!apk.exists()) return "container:not-installed(sandbox apk missing)";
        sLastSession = com.aether.util.LogDump.startSession(host, pkg);
        com.aether.util.LogDump.stage(sLastSession,
                "[session] launch " + pkg + " apk=" + apk.length() + "B");
        if (apk.canWrite()) VPackageInstaller.markReadOnly(apk);   // กัน SecurityException บน Android ≥14

        try {
            // 2) manifest ของ guest → launcher class
            GuestManifest gm;
            try {
                gm = GuestManifest.parse(apk.getAbsolutePath());
            } catch (Exception e) {
                return "container:manifest-parse-failed:" + e.getMessage();
            }
            String launcher = gm.launcherActivity;
            if (launcher == null && !gm.activities.isEmpty()) launcher = gm.activities.get(0);
            if (launcher == null) return "container:no-launcher-activity";
            String className = launcher.startsWith(".") ? gm.packageName + launcher : launcher;

            // 3) แตก native libs ของ guest + PathClassLoader (apk + libDir ของ guest)
            File libDir = SandboxStore.guestLibDir(host, pkg);
            prepareGuestNatives(apk, libDir);   // สแกนทุก apk ใน guestDir (base+split)
            com.aether.util.LogDump.stage(sLastSession, "[session] natives prepared lib=" + libDir);
            // รวม base.apk + split APKs ทั้งหมดใน guestDir (source = device install)
            StringBuilder cp = new StringBuilder(apk.getAbsolutePath());
            File[] guestApks = SandboxStore.guestDir(host, pkg).listFiles(
                    (dir, name) -> name.endsWith(".apk"));
            if (guestApks != null) {
                for (File f : guestApks) {
                    if (f.getAbsolutePath().equals(apk.getAbsolutePath())) continue;
                    cp.append(':').append(f.getAbsolutePath());
                }
            }
            // ใช้ DexClassLoader + optimizedDirectory ชัดเจน → ART ไม่ไปงอก oat ใน data/app/<pkg> (ลดชั้น/ขนาด)
            File guestOat = new File(new File(new File(SandboxStore.root(host), "cache"), "oat"), pkg);
            if (!guestOat.exists()) guestOat.mkdirs();
            ClassLoader guestLoader = new dalvik.system.DexClassLoader(
                    cp.toString(), guestOat.getAbsolutePath(), libDir.getAbsolutePath(),
                    GuestLauncher.class.getClassLoader());
            android.util.Log.i(TAG, "guest oat dir=" + guestOat);
            android.util.Log.i(TAG, "guest: PathClassLoader apk=" + apk.getAbsolutePath()
                    + " lib=" + libDir.getAbsolutePath());
            com.aether.util.LogDump.stage(sLastSession, "[session] classloader ready (base+splits)");

            // 3.9) plugin/module pipeline (parity KOS: กดเล่น → ดาวน์โหลด .bin → cache → กระจาย apk เข้า workspace)
            try {
                String pl = PluginInstaller.ensurePlugin(host, pkg, 30000L);
                android.util.Log.i(TAG, "plugin: " + pl);
                com.aether.util.LogDump.stage(sLastSession, "[plugin] ensure → " + pl);

                // โหลดปลั๊กอินเข้า process นี้ (apk + lib + oat) — ไม่ทำเกสต์ล้มถ้าไม่มี/โหลดไม่ได้
                PluginLoader.Result lr = PluginLoader.load(host, pkg, guestLoader);
                android.util.Log.i(TAG, "plugin loader: " + lr);
                com.aether.util.LogDump.stage(sLastSession, "[plugin] loader → " + lr);
            } catch (Throwable t) {
                android.util.Log.w(TAG, "plugin ensure: " + t);
                com.aether.util.LogDump.stage(sLastSession, "[plugin] error " + t);
            }

            // 4pre) fake framework (parity KOS: FakeCore → HookManager)
            //       instrumentation + IAMS proxy + IPMS proxy + HCallback — เฉพาะ process นี้ (:p0)
            try {
                com.aether.fake.hook.HookManager.install(host, pkg, guestLoader);
                com.aether.util.LogDump.stage(sLastSession, "[session] fake framework installed");
            } catch (Throwable t) {
                android.util.Log.w(TAG, "fake framework install: " + t);
            }

            // 4) โหลดคลาส launcher
            Class<?> actCls;
            try {
                actCls = guestLoader.loadClass(className);
            } catch (Throwable t) {
                return "container:class-load-failed(" + className + "):" + describe(t);
            }
            if (!Activity.class.isAssignableFrom(actCls)) {
                return "container:not-an-activity(" + className + ")";
            }

            // 5) ทรัพยากรของ guest (best-effort — บาง API จำกัด hidden)
            Resources guestRes = null;
            try {
                AssetManager am = AssetManager.class.getDeclaredConstructor().newInstance();
                Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
                Object cookie = addAssetPath.invoke(am, apk.getAbsolutePath());
                int c = cookie instanceof Integer ? (Integer) cookie : 0;
                if (c != 0) {
                    DisplayMetrics dm = host.getResources().getDisplayMetrics();
                    guestRes = new Resources(am, dm, host.getResources().getConfiguration());
                    android.util.Log.i(TAG, "guest resources injected (cookie=" + c + ")");
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "guest resources inject skipped: " + t);
            }

            // 5a) เตรียม environment ให้ guest loader: HOME ของ sandbox + <HOME>/Library (ctor chain ใช้ getenv("HOME"))
            try {
                File home = SandboxStore.guestUserDataDir(host, pkg);
                com.aether.helper.Native.setEnv("HOME", home.getAbsolutePath());
                File lib = new File(home, "Library");
                if (!lib.exists()) lib.mkdirs();
                android.util.Log.i(TAG, "guest HOME=" + home + " (Library ready)");
            } catch (Throwable t) {
                android.util.Log.w(TAG, "guest HOME setup: " + t);
            }

            // 5b) guest ApplicationInfo — ชี้ทุก dir เข้า sandbox (Unity/IL2CPP ใช้ค่านี้)
            android.content.pm.ApplicationInfo guestAi = null;
            try {
                android.content.pm.PackageInfo gpi =
                        host.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (gpi != null && gpi.applicationInfo != null) {
                    guestAi = gpi.applicationInfo;
                    guestAi.sourceDir = apk.getAbsolutePath();
                    guestAi.publicSourceDir = apk.getAbsolutePath();
                    guestAi.nativeLibraryDir = libDir.getAbsolutePath();
                    guestAi.dataDir = SandboxStore.guestUserDataDir(host, pkg).getAbsolutePath();
                    android.util.Log.i(TAG, "guest appInfo: src=" + guestAi.sourceDir
                            + " nativeLib=" + guestAi.nativeLibraryDir + " data=" + guestAi.dataDir);
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "guest appInfo build failed: " + t);
            }

            // 6a) ลำดับเดียวกับ framework จริง: createApp+attachBaseContext → providers → onCreate
            ProcessAdoption.adoptStatic(host, pkg, guestAi, guestLoader,
                    SandboxStore.guestApk(host, pkg).getAbsolutePath());
            android.content.Context guestCtx = new GuestContext(host, pkg, guestAi, guestLoader, guestRes,
                    SandboxStore.guestUserDataDir(host, pkg));
            android.app.Application guestApp = attachGuestApplication(gm, guestLoader, guestCtx);
            com.aether.util.LogDump.stage(sLastSession, "[session] application attached");
            bootGuestProviders(gm, guestLoader, guestCtx, guestAi, pkg);
            com.aether.util.LogDump.stage(sLastSession, "[session] providers done");
            finishGuestApplication(gm, guestCtx, guestApp);
            loadModuleIfPresent(guestLoader, libDir);

            // 6) สร้าง + attach + onCreate (reflection; adapter ตาม signature ของ API)
            Activity guest;
            try {
                Constructor<?> ctor = actCls.getDeclaredConstructor();
                ctor.setAccessible(true);
                guest = (Activity) ctor.newInstance();
            } catch (Throwable t) {
                android.util.Log.w(TAG, "instantiate failed", t);
                return "container:instantiate-failed:" + describe(t);
            }

            try {
                attachGuest(host, guest, className, pkg, guestRes, guestAi);
            } catch (Throwable t) {
                android.util.Log.w(TAG, "attach failed", t);
                return "container:attach-failed:" + describe(t);
            }

            try {
                // onCreate เป็น protected — ต้อง invoke ผ่าน reflection
                java.lang.reflect.Method oc = Activity.class.getDeclaredMethod("onCreate", android.os.Bundle.class);
                oc.setAccessible(true);
                oc.invoke(guest, (Object) null);
            } catch (Throwable t) {
                android.util.Log.w(TAG, "guest onCreate failed", t);
                return "container:guest-onCreate-failed:" + describe(t);
            }

            // 7) ยก decorView ของ guest ขึ้น host (ถ้ามี)
            try {
                View decor = guest.getWindow() != null ? guest.getWindow().getDecorView() : null;
                if (decor != null) {
                    host.setContentView(decor);
                    return "container:launched(" + gm.packageName + "/" + className + ")";
                }
                return "container:booted-no-ui(" + className + ")";
            } catch (Throwable t) {
                return "container:ui-attach-failed:" + describe(t);
            }
        } catch (Throwable t) {
            return "container:launch-error:" + describe(t);
        }
    }

    /** copy 1 entry → ไฟล์ (retry: ลบไฟล์ค้าง, setWritable, สุดท้าย log) */
    private static boolean copyEntry(java.util.zip.ZipFile zf, java.util.zip.ZipEntry e, File out) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try (java.io.InputStream in = zf.getInputStream(e);
                 java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                return true;
            } catch (Throwable t) {
                if (attempt == 0 && out.exists() && !out.delete()) {
                    android.util.Log.w(TAG, "native extract: cannot delete stale " + out.getName());
                }
                if (attempt == 1) {
                    //noinspection ResultOfMethodCallIgnored
                    out.getParentFile().setWritable(true, false);
                }
                if (attempt == 2) {
                    android.util.Log.w(TAG, "native extract skip " + out.getName() + ": " + t);
                }
            }
        }
        return false;
    }

    /** boot guest providers — สำคัญ: packer (เช่น Deku*) init ที่ชั้น provider ก่อน Application เสมอ */
    private static void bootGuestProviders(GuestManifest gm, ClassLoader loader,
                                           android.content.Context guestCtx,
                                           android.content.pm.ApplicationInfo guestAi, String pkg) {
        for (int i = 0; i < gm.providerNames.size(); i++) {
            String raw = gm.providerNames.get(i);
            String full = raw.startsWith(".") ? pkg + raw : raw;
            String auth = i < gm.providerAuthorities.size() ? gm.providerAuthorities.get(i) : null;
            try {
                Class<?> cls = loader.loadClass(full);
                Object provider = cls.getDeclaredConstructor().newInstance();
                if (provider instanceof android.content.ContentProvider) {
                    android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
                    info.name = full;
                    info.authority = auth;
                    info.packageName = pkg;
                    info.applicationInfo = guestAi;
                    java.lang.reflect.Method attach = android.content.ContentProvider.class
                            .getDeclaredMethod("attachInfo", android.content.Context.class,
                                    android.content.pm.ProviderInfo.class);
                    attach.setAccessible(true);
                    attach.invoke(provider, guestCtx, info);
                    java.lang.reflect.Method oc = android.content.ContentProvider.class
                            .getDeclaredMethod("onCreate");
                    oc.setAccessible(true);
                    oc.invoke(provider);
                    android.util.Log.i(TAG, "guest provider booted: " + full);
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "guest provider skip " + full + ": " + describe(t));
            }
        }
    }

    /** ขั้นที่ 1 ของ Application: สร้าง + attachBaseContext (framework ทำก่อน providers เสมอ) */
    private static android.app.Application attachGuestApplication(GuestManifest gm, ClassLoader loader,
                                                                  android.content.Context guestCtx) {
        String appName = gm.applicationName;
        if (appName == null || appName.isEmpty()) {
            android.util.Log.i(TAG, "guest app: (no application class)");
            return null;
        }
        String full = appName.startsWith(".") ? gm.packageName + appName : appName;
        try {
            Class<?> appCls = loader.loadClass(full);
            android.app.Application guestApp =
                    (android.app.Application) appCls.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method attach =
                    android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext",
                            android.content.Context.class);
            attach.setAccessible(true);
            attach.invoke(guestApp, guestCtx);
            android.util.Log.i(TAG, "guest application attached: " + full);
            return guestApp;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "guest application attach failed (" + full + ")", t);
            return null;
        }
    }

    /** ขั้นที่ 2 ของ Application: onCreate + ผูก instance เข้าระบบ (หลัง providers) */
    private static void finishGuestApplication(GuestManifest gm, android.content.Context guestCtx,
                                               android.app.Application guestApp) {
        if (guestApp == null) return;
        try {
            guestApp.onCreate();
            ProcessAdoption.adoptApplication(guestApp);
            android.util.Log.i(TAG, "guest application booted: " + gm.applicationName);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "guest application onCreate failed", t);
        }
    }

    /** โหลด module .so ของ guest (ถ้ามี) เข้า guest loader — parity: module ของแบบจำลอง */
    private static void loadModuleIfPresent(ClassLoader guestLoader, File libDir) {
        File mod = new File(libDir, "libgame-module.so");
        if (!mod.exists()) return;
        try {
            java.lang.reflect.Method load0 = Runtime.class.getDeclaredMethod(
                    "load0", ClassLoader.class, String.class);
            load0.setAccessible(true);
            Object target = java.lang.reflect.Modifier.isStatic(load0.getModifiers())
                    ? null : Runtime.getRuntime();
            load0.invoke(target, guestLoader, mod.getAbsolutePath());
            android.util.Log.i(TAG, "module loaded (guest loader): " + mod);
        } catch (Throwable t) {
            try {
                System.load(mod.getAbsolutePath());
                android.util.Log.i(TAG, "module loaded (host loader): " + mod);
            } catch (Throwable t2) {
                android.util.Log.w(TAG, "module load failed: " + t2);
            }
        }
    }

    /** attach ด้วย reflection — เดิมเป็น signature ที่ยาวและเปลี่ยนตาม API; กรอกตามชนิดพารามิเตอร์จริง */
    private static void attachGuest(Activity host, Activity guest, String className, String pkg,
                                    Resources guestRes, android.content.pm.ApplicationInfo guestAi) throws Exception {
        Class<?> atCls = Class.forName("android.app.ActivityThread");
        Object mainThread = atCls.getMethod("currentActivityThread").invoke(null);
        Object instrumentation = atCls.getMethod("getInstrumentation").invoke(mainThread);

        Object token = null;
        try {
            Method getTok = Activity.class.getMethod("getActivityToken");
            token = getTok.invoke(host);
        } catch (Throwable ignored) { /* hidden API อาจถูกจำกัด — ปล่อยเป็น null */ }

        Application app = host.getApplication();
        Intent intent = new Intent();
        intent.setClassName(host, className);

        ActivityInfo info = new ActivityInfo();
        info.packageName = pkg;
        info.name = className;
        info.applicationInfo = guestAi != null ? guestAi : host.getApplicationInfo();

        for (Method m : Activity.class.getDeclaredMethods()) {
            if (!"attach".equals(m.getName())) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 0 || pt[0] != Context.class) continue;
            Object[] args = new Object[pt.length];
            boolean ok = true;
            for (int i = 0; i < pt.length; i++) {
                Class<?> t = pt[i];
                if (t == Context.class) args[i] = host;
                else if (t == atCls) args[i] = mainThread;
                else if (t == android.app.Instrumentation.class) args[i] = instrumentation;
                else if (t == android.os.IBinder.class) args[i] = token;
                else if (t == int.class) args[i] = 0;
                else if (t == boolean.class) args[i] = false;
                else if (t == Application.class) args[i] = app;
                else if (t == Intent.class) args[i] = intent;
                else if (t == ActivityInfo.class) args[i] = info;
                else if (t == CharSequence.class) args[i] = className;
                else if (t == Activity.class) args[i] = null;
                else if (t == String.class) args[i] = null;
                else if (t == android.content.res.Configuration.class) {
                    args[i] = host.getResources().getConfiguration();
                } else if (t.isPrimitive()) {
                    args[i] = 0;
                } else {
                    args[i] = null; // Window / Callback / VoiceInteractor / NonConfigurationInstances ฯลฯ
                }
            }
            if (!ok) continue;
            m.setAccessible(true);
            try {
                m.invoke(guest, args);
                android.util.Log.i(TAG, "guest attach OK via " + m.getParameterCount() + " params");
                if (guestRes != null) {
                    try {
                        java.lang.reflect.Field f = Activity.class.getDeclaredField("mResources");
                        f.setAccessible(true);
                        f.set(guest, guestRes);
                    } catch (Throwable ignored) {}
                }
                return;
            } catch (Throwable t) {
                android.util.Log.w(TAG, "attach try(" + pt.length + " params) failed: " + t);
            }
        }
        throw new IllegalStateException("no usable attach signature");
    }
}
