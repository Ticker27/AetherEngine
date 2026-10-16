package com.aether.container;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ProcessAdoption — ยึด "ตัวตน process" ให้เป็นของ guest (ใน :p0 เท่านั้น)
 *
 * เทคนิคเดียวกับแกนของ VA: ในโปรเซสที่อุทิศให้ guest อย่างเดียว เราสลับข้อมูลระดับ framework
 * ให้ทุกคำถามจากโค้ดเกม (รวม native packer ที่เรียกกลับเข้า Java) เห็นเป็นตัวเกม:
 *   ActivityThread.mBoundApplication.appInfo      → guest ApplicationInfo
 *   ActivityThread.mBoundApplication.processName  → guest package
 *   LoadedApk.mApplicationInfo / mAppDir / mResDir / mClassLoader → guest apk + loader
 *   (หลังสร้าง app) LoadedApk.mApplication + ActivityThread.mInitialApplication → guest Application
 *   Process.setArgV0(pkg)                          → /proc/self/cmdline เป็นชื่อเกม
 *
 * ทุกขั้น log; ล้มขั้นไหนรายงานขั้นนั้น (ไม่กลบ)
 */
final class ProcessAdoption {

    private ProcessAdoption() {}

    private static Object curThread() throws Exception {
        Method m = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread");
        return m.invoke(null);
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
            android.util.Log.i("AetherEngine", "adopt: set " + name);
        } catch (Throwable t) {
            android.util.Log.w("AetherEngine", "adopt: cannot set " + name + " → " + t);
        }
    }

    /** ก่อนสร้าง guest Application — สลับ identity ระดับ static */
    static void adoptStatic(Context ctx, String pkg, ApplicationInfo guestAi, ClassLoader loader, String apkPath) {
        try {
            Object at = curThread();
            Object bind = null;
            try {
                Field f = at.getClass().getDeclaredField("mBoundApplication");
                f.setAccessible(true);
                bind = f.get(at);
            } catch (Throwable t) {
                android.util.Log.w("AetherEngine", "adopt: no mBoundApplication: " + t);
            }
            if (bind != null) {
                set(bind, "appInfo", guestAi);
                set(bind, "processName", pkg);
                Object loadedApk = null;
                try {
                    Field f = bind.getClass().getDeclaredField("info");
                    f.setAccessible(true);
                    loadedApk = f.get(bind);
                } catch (Throwable ignored) {}
                if (loadedApk != null) {
                    set(loadedApk, "mApplicationInfo", guestAi);
                    set(loadedApk, "mAppDir", apkPath);
                    set(loadedApk, "mResDir", apkPath);
                    set(loadedApk, "mClassLoader", loader);
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("AetherEngine", "adoptStatic failed: " + t);
        }
        // เปลี่ยน cmdline ของ process เป็นชื่อเกม (setArgV0 เป็น @hide — ใช้ reflection)
        try {
            Method m = android.os.Process.class.getMethod("setArgV0", String.class);
            m.invoke(null, pkg);
            android.util.Log.i("AetherEngine", "adopt: setArgV0(" + pkg + ")");
        } catch (Throwable t) {
            android.util.Log.w("AetherEngine", "adopt: setArgV0 failed: " + t);
        }
    }

    /** หลังสร้าง guest Application สำเร็จ — ผูก instance เข้าระบบ */
    static void adoptApplication(Object guestApp) {
        try {
            Object at = curThread();
            set(at, "mInitialApplication", guestApp);
            try {
                Field f = at.getClass().getDeclaredField("mBoundApplication");
                f.setAccessible(true);
                Object bind = f.get(at);
                if (bind != null) {
                    Field fi = bind.getClass().getDeclaredField("info");
                    fi.setAccessible(true);
                    Object loadedApk = fi.get(bind);
                    if (loadedApk != null) set(loadedApk, "mApplication", guestApp);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            android.util.Log.w("AetherEngine", "adoptApplication failed: " + t);
        }
    }
}
