package com.aether.container;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * parity: engine core singleton ของแบบจำลอง — ฉบับลดความซับซ้อน
 *
 * แบบจำลอง: engine core init()/postInit() ผูก lifecycle เข้ากับ process
 *   clone และรันแอปอื่นทั้ง process ใน sandbox :p0-p3
 *
 * AetherEngine (level of parity: structural):
 *   - เก็บโครง init → postInit + จุดเรียก native engine (Native.ic จาก App.attachBaseContext)
 *   - container = registry (SharedPreferences) เก็บรายชื่อแอปที่ติดตั้งเข้า container
 *     ติดตั้ง = ย้าย APK เข้า filesDir/containers/ + enable launcher จริง (ดู VPackageInstaller)
 *   - ตัด: Binder hooking, AMS/PMS proxy, multi-process sandbox
 * ดู docs/DESIGN.md ส่วน "level of parity"
 */
public final class AetherCore {

    private static volatile boolean sInit = false;   // parity: __cxa_guard flag ของ singleton ในต้นแบบ
    private static Context sCtx;

    private static final String PREFS = "com.aether.container";
    private static final String KEY_REGISTRY = "installed";
    private static final String KEY_ENGINE = "engine_version";

    private AetherCore() {}

    /** parity: core init */
    public static void init(Context ctx) {
        if (sInit) return;
        synchronized (AetherCore.class) {
            if (sInit) return;                 // parity: double-checked init เหมือน __cxa_guard
            // ในช่วง Application.attachBaseContext: getApplicationContext() ยังเป็น null — fallback เป็น ctx ตรง ๆ
            Context appCtx = ctx.getApplicationContext();
            sCtx = appCtx != null ? appCtx : ctx;
            if (ctx instanceof Application) {
                ((Application) ctx).registerActivityLifecycleCallbacks(new AppLifecycleLogger());
            }
            sInit = true;
            android.util.Log.i("AetherEngine", "AetherCore.init done");
        }
    }

    /** parity: core post-init: อ่าน registry ของ container + fingerprint (อ่านอย่างเดียว) + seed ไอคอนเข้า files/<sha256> */
    public static void postInit(Context ctx) {
        List<JSONObject> apps = listRegistry(ctx);
        android.util.Log.i("AetherEngine", "AetherCore.postInit: " + apps.size() + " container app(s)");
        android.util.Log.i("AetherEngine", "device: " + com.aether.util.DeviceRegistry.summary(ctx));
        seedTargetIcons(ctx);

        // ให้โครง sandbox ครบ parity (kos_engine layout) และตรงกับ guest ที่มีอยู่จริง
        try {
            SandboxStore.ensureBaseLayout(ctx);
            int n = 0;
            for (TargetRegistry.Target t : TargetRegistry.all()) {
                if (SandboxStore.guestApk(ctx, t.pkg).exists()) {
                    SandboxStore.ensureGuestLayout(ctx, t.pkg);
                    n++;
                }
            }
            android.util.Log.i("AetherEngine", "sandbox: base layout ready, guests=" + n);
        } catch (Throwable t) {
            android.util.Log.w("AetherEngine", "sandbox ensure: " + t);
        }
    }

    /** seed ไอคอนเป้าหมายจาก assets → ContentCache (files/<sha256>) — กลไกเดียวกับแคช asset ของต้นแบบ */
    private static void seedTargetIcons(Context ctx) {
        for (TargetRegistry.Target t : TargetRegistry.all()) {
            try (java.io.InputStream in = ctx.getAssets().open("flutter_assets/assets/targets/" + t.icon)) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                String sha = ContentCache.putBytes(ctx, bos.toByteArray(), t.icon);
                android.util.Log.i("AetherEngine", "icon cached: " + t.id + " → files/"
                        + (sha != null ? sha.substring(0, 12) + "…" : "(fail)"));
            } catch (Exception e) {
                android.util.Log.w("AetherEngine", "icon seed: " + t.id + " — " + e.getMessage());
            }
        }
    }

    public static String engineVersion() { return "AetherEngine 1.0.0 (structural model)"; }

    public static Context coreContext() { return sCtx; }

    /** parity: core service lookup → IBinder (server lookup ข้าม process) — AetherEngine: engine in-process, คืน null */
    public static IBinder mainProcessBinder() { return null; }

    /** parity: "G" → core package/uid check — รายการแอปใน container (จาก registry) */
    public static List<JSONObject> listInstalled(Context ctx) {
        return listRegistry(ctx);
    }

    /** parity: "H" → core install routine — VirtualInstaller: install APK เข้า container */
    public static String installFromUri(Context ctx, Uri apkUri) {
        return VPackageInstaller.install(ctx, apkUri);
    }

    /**
     * parity: ProxyActivity$P0 launch — เปิด guest จาก sandbox ใน container ของเรา (ไม่มี external fallback)
     * ลำดับ: adopt (โฟลว์เก่า) → มี sandbox apk? → เทียบ pin → เปิด VStubActivity
     */
    public static String launchInContainer(Context ctx, String targetPkg) {
        adoptLegacyApkIfAny(ctx, targetPkg);

        java.io.File apk = SandboxStore.guestApk(ctx, targetPkg);
        if (!apk.exists()) return "not-installed-in-sandbox";

        JSONObject st = SandboxStore.readPackageSetting(ctx, targetPkg);
        String ver = st != null ? st.optString("versionName", "?") : "?";
        TargetRegistry.Target t = TargetRegistry.byPkg(targetPkg);
        if (t != null) {
            VersionUtil.Result vr = VersionUtil.evaluate(ver, t.pinVersion, t.allowedVersions, t.versionPolicy);
            if ("mismatch".equals(vr.state)) {
                // parity: สภาพ "ล็อก" เมื่อเวอร์ชันไม่อยู่ในนโยบาย (หลักฐาน: กดแล้วไม่เข้า)
                return "blocked:version-mismatch have=" + ver + " policy=" + t.versionPolicy
                        + " allowed=" + java.util.Arrays.toString(t.allowedVersions);
            }
        }

        SandboxStore.markLaunched(ctx, targetPkg, System.currentTimeMillis());
        SandboxStore.writeProcCmdline(ctx, targetPkg);   // fake /proc/0/cmdline (parity จากดัมป์จริง)
        Intent it = new Intent(ctx, VStubActivity.class);
        it.putExtra("target", targetPkg);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(it);
        return "launched-in-container:" + (t != null ? t.name : targetPkg) + "@" + ver;
    }

    /**
     * ติดตั้งเข้า sandbox จาก "แอปที่ติดตั้งอยู่บนเครื่อง" (source of truth = PackageManager)
     * หลักฐาน: ai.sourceDir + pi.splitSourceDirs/splitNames — ไม่ดึงจากไฟล์ภายนอก
     */
    public static String installFromDevice(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkg, 0);
            if (pi == null || pi.applicationInfo == null) return "device:no-app-info";

            String srcBase = pi.applicationInfo.sourceDir;
            if (srcBase == null || !new java.io.File(srcBase).exists()) return "device:source-missing";

            SandboxStore.ensureBaseLayout(ctx);
            SandboxStore.ensureGuestLayout(ctx, pkg);

            File dstBase = SandboxStore.guestApk(ctx, pkg);
            copyFile(new java.io.File(srcBase), dstBase);
            VPackageInstaller.markReadOnly(dstBase);

            JSONArray splitArr = new JSONArray();
            String[] splitDirs = pi.applicationInfo.splitSourceDirs;
            String[] splitNames = pi.splitNames;
            if (splitDirs != null && splitDirs.length > 0) {
                for (int i = 0; i < splitDirs.length; i++) {
                    String sp = splitDirs[i];
                    if (sp == null || !new java.io.File(sp).exists()) continue;
                    String nm = (splitNames != null && i < splitNames.length && splitNames[i] != null)
                            ? splitNames[i] : ("split_" + i);
                    File dst = new File(SandboxStore.guestDir(ctx, pkg), nm + ".apk");
                    copyFile(new java.io.File(sp), dst);
                    VPackageInstaller.markReadOnly(dst);
                    JSONObject o = new JSONObject();
                    o.put("name", nm);
                    o.put("file", dst.getName());
                    o.put("sha256", VPackageInstaller.sha256(dst));
                    splitArr.put(o);
                }
            }

            String ver = pi.versionName == null ? "?" : pi.versionName;
            long vc = pi.getLongVersionCode();
            String sha = VPackageInstaller.sha256(dstBase);

            TargetRegistry.Target t = TargetRegistry.byPkg(pkg);
            String pin = "n/a";
            if (t != null) {
                VersionUtil.Result vr = VersionUtil.evaluate(ver, t.pinVersion, t.allowedVersions, t.versionPolicy);
                pin = vr.toString();
            }

            JSONObject setting = new JSONObject();
            setting.put("apkPath", dstBase.getAbsolutePath());
            setting.put("versionName", ver);
            setting.put("versionCode", vc);
            setting.put("sha256", sha);
            setting.put("installedAt", System.currentTimeMillis());
            setting.put("source", "device:" + pkg);
            setting.put("pin", pin);
            setting.put("splits", splitArr);
            JSONObject states = new JSONObject();
            states.put("installed", true);
            states.put("stopped", false);
            states.put("hidden", false);
            states.put("launched", false);
            setting.put("states", states);
            SandboxStore.writePackageSetting(ctx, pkg, setting);
            SandboxStore.allocUid(ctx, pkg);

            android.util.Log.i("AetherEngine", "install-from-device: " + pkg + "@" + ver
                    + " splits=" + splitArr.length() + " pin=" + pin
                    + " sha256=" + sha.substring(0, 12) + "…");
            return "installed-from-device:" + pkg + "@" + ver + " pin=" + pin;
        } catch (Throwable t) {
            android.util.Log.w("AetherEngine", "install-from-device failed", t);
            return "ERR:" + describeErr(t);
        }
    }

    private static String describeErr(Throwable t) {
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private static void copyFile(java.io.File src, java.io.File dst) throws Exception {
        java.io.File d = dst.getParentFile();
        if (d != null && !d.exists()) d.mkdirs();
        try (java.io.InputStream in = new java.io.FileInputStream(src);
             java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /** migration: APK ที่เคยคัดลอกไว้ที่ filesDir/containers (โฟลว์เดิม) → adopt เข้า sandbox root */
    private static void adoptLegacyApkIfAny(Context ctx, String pkg) {
        java.io.File dst = SandboxStore.guestApk(ctx, pkg);
        if (dst.exists()) return;
        java.io.File legacyDir = new java.io.File(ctx.getFilesDir(), "containers");
        java.io.File[] files = legacyDir.listFiles();
        if (files == null) return;
        PackageManager pm = ctx.getPackageManager();
        for (java.io.File f : files) {
            if (!f.getName().endsWith(".apk")) continue;
            PackageInfo pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
            if (pi == null || !pkg.equals(pi.packageName)) continue;
            try {
                SandboxStore.ensureBaseLayout(ctx);
                java.io.File ddir = dst.getParentFile();
                if (ddir != null && !ddir.exists()) ddir.mkdirs();
                try (java.io.InputStream in = new java.io.FileInputStream(f);
                     java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                VPackageInstaller.markReadOnly(dst);   // Android ≥14: dex ที่เขียนได้โหลดไม่ได้
                JSONObject setting = new JSONObject();
                setting.put("apkPath", dst.getAbsolutePath());
                setting.put("versionName", pi.versionName == null ? "?" : pi.versionName);
                setting.put("versionCode", pi.getLongVersionCode());
                setting.put("sha256", VPackageInstaller.sha256(f));
                setting.put("installedAt", System.currentTimeMillis());
                setting.put("source", "adopted-from-containers");
                SandboxStore.writePackageSetting(ctx, pkg, setting);
                SandboxStore.allocUid(ctx, pkg);
                android.util.Log.i("AetherEngine", "adopted legacy apk → sandbox: " + f.getName());
            } catch (Exception e) {
                android.util.Log.w("AetherEngine", "adopt failed: " + e);
            }
            return;
        }
    }

    /** snapshot ของเป้าหมายทั้ง 3 (สถานะจาก sandbox + ข้อมูลแอปที่ติดตั้งนอกเป็นข้อมูลเสริม) */
    public static String targetsSnapshot(Context ctx) {
        JSONArray arr = new JSONArray();
        for (TargetRegistry.Target t : TargetRegistry.all()) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", t.id);
                o.put("name", t.name);
                o.put("pkg", t.pkg);
                o.put("pin", t.pinVersion);
                o.put("moduleVersion", t.moduleVersion);
                JSONObject conf = SandboxStore.readPackageSetting(ctx, t.pkg);
                String ver = conf != null ? conf.optString("versionName", null) : null;
                o.put("sandboxVersion", ver == null ? JSONObject.NULL : ver);
                String sha = conf != null ? conf.optString("sha256", null) : null;
                o.put("sha256", sha == null ? JSONObject.NULL : sha);
                String dv = installedVersion(ctx, t.pkg);
                o.put("deviceVersion", dv == null ? JSONObject.NULL : dv);
                o.put("policy", t.versionPolicy);
                o.put("allowed", new org.json.JSONArray(java.util.Arrays.asList(t.allowedVersions)));
                String status;
                if (ver == null) status = "not-installed";
                else {
                    VersionUtil.Result vr = VersionUtil.evaluate(ver, t.pinVersion, t.allowedVersions, t.versionPolicy);
                    status = vr.state;
                    o.put("statusReason", vr.reason);
                }
                o.put("status", status);
            } catch (Exception e) {
                o = new JSONObject();
                try { o.put("error", String.valueOf(e)); } catch (Exception ignored) {}
            }
            arr.put(o);
        }
        return arr.toString();
    }

    private static String installedVersion(Context ctx, String pkg) {
        try {
            return ctx.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Exception e) {
            return null;
        }
    }

    // ── registry (แทน virtual package manager ของต้นแบบ ฉบับเล็ก) ──

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void addToRegistry(Context ctx, JSONObject entry) {
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString(KEY_REGISTRY, "[]"));
            arr.put(entry);
            prefs(ctx).edit().putString(KEY_REGISTRY, arr.toString()).apply();
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "registry add: " + e.getMessage());
        }
    }

    static List<JSONObject> listRegistry(Context ctx) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString(KEY_REGISTRY, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (Exception ignored) {}
        return out;
    }

    /** เก็บ engine version ไว้ใน prefs (parity: ต้นแบบเก็บ env config ใน pack) */
    public static void persistEngineVersion() {
        if (sCtx != null) prefs(sCtx).edit().putString(KEY_ENGINE, engineVersion()).apply();
    }

    public static PackageInfo resolveRegistered(Context ctx, String targetPkg) {
        try { return ctx.getPackageManager().getPackageInfo(targetPkg, 0); }
        catch (Exception e) { return null; }
    }
}
