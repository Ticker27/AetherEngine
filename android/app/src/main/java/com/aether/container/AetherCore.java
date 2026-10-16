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
            sCtx = ctx.getApplicationContext();
            ((Application) sCtx).registerActivityLifecycleCallbacks(new AppLifecycleLogger());
            sInit = true;
            android.util.Log.i("AetherEngine", "AetherCore.init done");
        }
    }

    /** parity: core post-init: อ่าน registry ของ container ที่ติดตั้งไว้ */
    public static void postInit(Context ctx) {
        List<JSONObject> apps = listRegistry(ctx);
        android.util.Log.i("AetherEngine", "AetherCore.postInit: " + apps.size() + " container app(s)");
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
    public static void installFromUri(Context ctx, Uri apkUri) {
        VPackageInstaller.install(ctx, apkUri);
    }

    /** parity: ProxyActivity$P0 launch — เปิดแอปเป้าหมายผ่าน stub ของ container */
    public static void launchInContainer(Context ctx, String targetPkg) {
        Intent it = new Intent(ctx, VStubActivity.class);
        it.putExtra("target", targetPkg);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(it);
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
