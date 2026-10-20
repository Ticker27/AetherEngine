package com.aether.fake.hook;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.aether.fake.FakeCore;
import com.aether.fake.delegate.AppInstrumentation;
import com.aether.fake.service.IActivityManagerProxy;
import com.aether.fake.service.IPackageManagerProxy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * HookManager — ติดตั้งชุด hook ของ fake framework (โครงเดียวกับ
 * com.kos.engine.fake.hook.HookManager ของ KOS)
 *
 * ลำดับติดตั้ง (parity ตาม KOS FakeCore.init → HookManager):
 *   1) ActivityThread.mInstrumentation → AppInstrumentation
 *   2) IActivityManager  → IActivityManagerProxy (ServiceManager singleton)
 *   3) IPackageManager   → IPackageManagerProxy (ActivityThread.sPackageManager)
 *   4) Handler mH        → HCallback (logger; redirect ActivityRecord ในเฟสถัดไป)
 *
 * กติกา: ติดตั้งเฉพาะ process ที่โฮสต์เกสต์ (:p0) — main process ไม่แตะ
 */
public final class HookManager {

    private static final String TAG = "AetherEngine";

    private static volatile boolean sInstalled;

    private HookManager() {}

    /** HCallback แบบเบา: log message ของ ActivityThread.mH (โครง parity; consume=false) */
    private static final class HCallbackLogger implements Handler.Callback {
        private int n;

        @Override
        public boolean handleMessage(Message msg) {
            if (n < 24) {
                n++;
                android.util.Log.i(TAG, "fake/hcb: msg.what=" + msg.what);
            }
            return false;   // ไม่ consume — ปล่อยให้ handler เดิมทำงาน
        }
    }

    public static boolean install(Context host, String targetPkg, ClassLoader guestLoader) {
        if (sInstalled) return true;
        try {
            FakeCore.init();

            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Method cur = atCls.getDeclaredMethod("currentActivityThread");
            cur.setAccessible(true);
            Object at = cur.invoke(null);
            if (at == null) {
                android.util.Log.w(TAG, "fake: ActivityThread = null (ติดตั้ง hook ไม่ได้)");
                return false;
            }

            // 1) Instrumentation
            boolean instr = false;
            try {
                Method getInstr = atCls.getMethod("getInstrumentation");
                android.app.Instrumentation baseInstr =
                        (android.app.Instrumentation) getInstr.invoke(at);
                AppInstrumentation appInstr = new AppInstrumentation(baseInstr);
                appInstr.setGuest(guestLoader, targetPkg);
                Field f = atCls.getDeclaredField("mInstrumentation");
                f.setAccessible(true);
                f.set(at, appInstr);
                instr = true;
                android.util.Log.i(TAG, "fake: mInstrumentation → AppInstrumentation (base="
                        + (baseInstr != null ? baseInstr.getClass().getSimpleName() : "null") + ")");
            } catch (Throwable t) {
                android.util.Log.w(TAG, "fake: instrumentation swap failed: " + t);
            }

            // 2) IActivityManager
            boolean ams = IActivityManagerProxy.install(targetPkg);

            // 3) IPackageManager (ใช้ path sandbox ของ guest)
            boolean pms = false;
            try {
                String apk = com.aether.container.SandboxStore.guestApk(host, targetPkg).getAbsolutePath();
                String data = com.aether.container.SandboxStore.guestUserDataDir(host, targetPkg).getAbsolutePath();
                String ver = "?";
                org.json.JSONObject conf = com.aether.container.SandboxStore.readPackageSetting(host, targetPkg);
                if (conf != null) ver = conf.optString("versionName", "?");
                pms = IPackageManagerProxy.install(targetPkg, apk, data, ver);
            } catch (Throwable t) {
                android.util.Log.w(TAG, "fake: pms install failed: " + t);
            }

            // 4) mH callback (logger)
            boolean hcb = false;
            try {
                Field f = atCls.getDeclaredField("mH");
                f.setAccessible(true);
                Handler h = (Handler) f.get(at);
                if (h != null) {
                    h.getClass().getMethod("setCallback", Handler.Callback.class)
                            .invoke(h, new HCallbackLogger());
                    hcb = true;
                    android.util.Log.i(TAG, "fake: HCallback attached (mH)");
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "fake: mH callback failed: " + t);
            }

            sInstalled = instr && ams && pms;
            android.util.Log.i(TAG, "fake: HookManager.install instr=" + instr
                    + " ams=" + ams + " pms=" + pms + " hcb=" + hcb
                    + " target=" + targetPkg + " done=" + sInstalled);
            return sInstalled;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake: HookManager.install failed: " + t);
            return false;
        }
    }
}
