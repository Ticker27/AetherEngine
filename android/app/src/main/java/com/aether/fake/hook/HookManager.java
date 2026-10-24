package com.aether.fake.hook;

import android.content.Context;

import com.aether.client.GuestIdentity;
import com.aether.fake.FakeCore;
import com.aether.fake.delegate.AppInstrumentation;
import com.aether.fake.service.IActivityManagerProxy;
import com.aether.fake.service.IPackageManagerProxy;
import com.aether.proxy.ProxyActivity;

/**
 * HookManager — ติดตั้งชุด hook ของ fake framework ใน process ที่ถูกต้อง
 *
 * ลำดับ (parity KOS: FakeCore.init → HookManager):
 *   :pN  (process ของเกสต์)
 *     1) hidden API exemption (Android 9+ บังคับ reflect field ของ framework)
 *     2) ActivityThread.mInstrumentation → AppInstrumentation
 *     3) ActivityManager.IActivityManagerSingleton → IActivityManagerProxy   (intent เกสต์ → proxy :pN)
 *     4) ActivityThread.sPackageManager → IPackageManagerProxy              (PackageInfo/ActivityInfo จาก sandbox)
 *     5) ActivityThread.mH.mCallback → HCallbackStub                        (สลับ activityInfo ก่อน LAUNCH_ACTIVITY)
 *   main process
 *     ติดตั้งเฉพาะ (3) — เพื่อให้ intent ที่ชี้ component ของเกสต์ถูกเปลี่ยนเป็น proxy component
 *     ห้ามติดตั้ง (2)(4)(5) ใน main: จะทำให้ framework ของ host ตอบเป็นเกสต์ (บั๊กที่ AUDIT A3)
 *
 * Hook ต้องติดตั้งใน App.attachBaseContext ไม่ใช่ onCreate — ActivityThread ส่ง
 * LAUNCH_ACTIVITY หลัง Application.onCreate เท่านั้น ถ้าติดช้าไป 1 จังหวะ message แรกจะหลุด
 */
public final class HookManager {

    private static final String TAG = "AetherEngine";

    private static volatile boolean sMainInstalled;
    private static volatile boolean sGuestInstalled;
    private static volatile boolean sHiddenApiTried;
    private static volatile boolean sAms, sPms, sInstr, sHcb;

    private HookManager() {}

    /** เรียกได้ทุก process, idempotent */
    public static void installForProcess(Context ctx) {
        if (GuestIdentity.isStubProcess(ctx)) installGuestHooks(ctx);
        else installMainHooks(ctx);
    }

    private static synchronized void tryHiddenApiBypass() {
        if (sHiddenApiTried) return;
        sHiddenApiTried = true;
        try {
            java.lang.reflect.Method forName = Class.class.getDeclaredMethod("forName", String.class);
            java.lang.reflect.Method getDeclaredMethod =
                    Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntime = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            java.lang.reflect.Method getRuntime = (java.lang.reflect.Method)
                    getDeclaredMethod.invoke(vmRuntime, "getRuntime", (Object) null);
            Object runtime = getRuntime.invoke(null);
            java.lang.reflect.Method setExemptions = (java.lang.reflect.Method)
                    getDeclaredMethod.invoke(vmRuntime, "setHiddenApiExemptions",
                            (Object) new Class[]{String[].class});
            setExemptions.invoke(runtime, (Object) new String[]{"L"});
            android.util.Log.i(TAG, "fake: hiddenapi exemptions set");
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake: hiddenapi bypass unavailable: " + t);
        }
    }

    private static void installMainHooks(Context ctx) {
        if (sMainInstalled) return;
        sMainInstalled = true;
        tryHiddenApiBypass();
        sAms = IActivityManagerProxy.install(ctx);
        android.util.Log.i(TAG, "fake: main hooks ams=" + sAms);
        if (!sAms) scheduleRetry(ctx, false, 0);
    }

    /**
     * attachBaseContext อาจเร็วเกินกว่า framework จะสร้าง IActivityManager/PackageManager
     * → ติดตั้งไม่ได้ในจังหวะนั้น ลองซ้ำบน main thread แบบถี่ ๆ จนสำเร็จ (สูงสุด ~4 วินาที)
     */
    private static void scheduleRetry(final Context ctx, final boolean guest, final int attempt) {
        if (attempt >= 10) {
            android.util.Log.w(TAG, "fake: retry exhausted (guest=" + guest + ") — hooks not installed");
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            boolean stillMissing;
            if (guest) {
                if (!sAms) sAms = IActivityManagerProxy.install(ctx);
                if (!sPms) sPms = IPackageManagerProxy.install(ctx);
                if (!sInstr) sInstr = AppInstrumentation.install(ctx);
                if (!sHcb) sHcb = HCallbackStub.install();
                stillMissing = !(sAms && sPms && sInstr && sHcb);
            } else {
                if (!sAms) sAms = IActivityManagerProxy.install(ctx);
                stillMissing = !sAms;
            }
            if (stillMissing) {
                scheduleRetry(ctx, guest, attempt + 1);
            } else {
                android.util.Log.i(TAG, "fake: hooks ready after retry#" + attempt
                        + " (guest=" + guest + ") ams=" + sAms + " pms=" + sPms
                        + " instr=" + sInstr + " hcb=" + sHcb);
            }
        }, 400);
    }

    private static void installGuestHooks(Context ctx) {
        if (sGuestInstalled) return;
        sGuestInstalled = true;
        tryHiddenApiBypass();
        try { FakeCore.init(); } catch (Throwable t) { android.util.Log.w(TAG, "FakeCore: " + t); }

        sInstr = AppInstrumentation.install(ctx);
        sAms = IActivityManagerProxy.install(ctx);
        sPms = IPackageManagerProxy.install(ctx);
        sHcb = HCallbackStub.install();

        android.util.Log.i(TAG, "fake: guest hooks instr=" + sInstr + " ams=" + sAms
                + " pms=" + sPms + " hcb=" + sHcb);
        if (!(sAms && sPms && sInstr && sHcb)) scheduleRetry(ctx, true, 0);
    }

    /**
     * signature เดิมของ repo (เรียกจาก GuestLauncher) — คงไว้เพื่อไม่ให้ call site เดิมพัง
     * ตอนนี้ไม่ต้องใช้ pkg/loader แล้ว เพราะ hook เก็บ loader จาก GuestIdentity แบบ lazy
     */
    public static boolean install(Context host, String targetPkg, ClassLoader guestLoader) {
        installForProcess(host);
        return sGuestInstalled ? (sInstr || sAms || sHcb) : sAms;
    }

    /** สถานะสำหรับ UI/log — พิสูจน์ว่า hook ติดจริง ไม่ใช่สมมติ */
    public static String status() {
        return "proc=" + (sGuestInstalled ? "guest" : (sMainInstalled ? "main" : "none"))
                + " instr=" + sInstr + " ams=" + sAms + " pms=" + sPms + " hcb=" + sHcb
                + " hiddenApi=" + sHiddenApiTried;
    }

    /** proxy class ที่ใช้เป็น stub ของ process นี้ (สำหรับ log/UI) */
    public static String stubForCurrentProcess(Context ctx) {
        return ProxyActivity.P0.class.getName();
    }
}
