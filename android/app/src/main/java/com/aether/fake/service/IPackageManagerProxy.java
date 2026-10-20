package com.aether.fake.service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * IPackageManagerProxy — PMS ปลอมระดับบริการ (โครงเดียวกับ
 * com.kos.engine.fake.service.IPackageManagerProxy + BPackageManager ของ KOS)
 *
 * กลไก: แทน static IPackageManager ที่ ActivityThread ใช้
 *   - ถาม getPackageInfo/getApplicationInfo ของ pkg เป้าหมาย
 *   - ถ้าระบบมีจริง → คืนของจริง
 *   - ถ้าไม่มี (NameNotFound) → คืน PackageInfo/ApplicationInfo ที่ประกอบจาก sandbox conf ของเรา
 *     (versionName, sourceDir = sandbox base.apk, dataDir = sandbox user dir)
 *   = "ติดตั้งใน container โดยไม่ต้องติดตั้งในระบบ" — แกนของ VirtualApp/BlackBox
 */
public final class IPackageManagerProxy implements InvocationHandler {

    private static final String TAG = "AetherEngine";

    private final Object base;
    private static volatile boolean sHooked;
    private static volatile String sTargetPkg;
    private static volatile String sSandboxApk;
    private static volatile String sSandboxData;
    private static volatile String sSandboxVersion;

    private IPackageManagerProxy(Object base) {
        this.base = base;
    }

    public static boolean install(String targetPkg, String sandboxApk, String sandboxData,
                                  String sandboxVersion) {
        if (sHooked) return true;
        sTargetPkg = targetPkg;
        sSandboxApk = sandboxApk;
        sSandboxData = sandboxData;
        sSandboxVersion = sandboxVersion;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method getpm = at.getDeclaredMethod("getPackageManager");
            getpm.setAccessible(true);
            Object base = getpm.invoke(null);
            if (base == null) {
                android.util.Log.w(TAG, "fake/pms: ActivityThread.getPackageManager = null");
                return false;
            }
            Class<?> ipm = Class.forName("android.content.pm.IPackageManager");
            Object proxy = Proxy.newProxyInstance(ipm.getClassLoader(),
                    new Class<?>[]{ipm}, new IPackageManagerProxy(base));

            // แทน static cache ที่ ActivityThread ใช้ (sPackageManager)
            boolean set = false;
            try {
                Field f = at.getDeclaredField("sPackageManager");
                f.setAccessible(true);
                f.set(null, proxy);
                set = true;
            } catch (Throwable t) {
                android.util.Log.w(TAG, "fake/pms: set sPackageManager failed: " + t);
            }
            sHooked = set;
            android.util.Log.i(TAG, "fake/pms: IPackageManager proxy installed=" + set
                    + " target=" + targetPkg + " @ " + sandboxVersion);
            return set;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake/pms: install failed: " + t);
            return false;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        try {
            // นโยบาย: target pkg → ตอบจาก sandbox เสมอ (guest เห็น version เดียวกับ sandbox ไม่ใช่ของเครื่องนอก)
            if (sTargetPkg != null && args != null && args.length > 0
                    && sTargetPkg.equals(args[0])
                    && (name.equals("getPackageInfo") || name.equals("getApplicationInfo"))) {
                Object forged = forge(method.getReturnType());
                if (forged != null) {
                    android.util.Log.i(TAG, "fake/pms: answer " + name
                            + "(" + sTargetPkg + ") from sandbox " + sSandboxVersion);
                    return forged;
                }
            }
        } catch (Throwable t) {
            throw t;
        }
        try {
            return method.invoke(base, args);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getCause();
        }
    }

    /** ประกอบผลลัพธ์จาก sandbox conf (โครง BPackage แบบย่อ) */
    private static Object forge(Class<?> retType) {
        try {
            if (retType == android.content.pm.PackageInfo.class) {
                android.content.pm.PackageInfo pi = new android.content.pm.PackageInfo();
                pi.packageName = sTargetPkg;
                pi.versionName = sSandboxVersion;
                android.content.pm.ApplicationInfo ai = guestAi();
                pi.applicationInfo = ai;
                return pi;
            }
            if (retType == android.content.pm.ApplicationInfo.class) {
                return guestAi();
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake/pms: forge failed: " + t);
        }
        return null;
    }

    private static android.content.pm.ApplicationInfo guestAi() {
        android.content.pm.ApplicationInfo ai = new android.content.pm.ApplicationInfo();
        ai.packageName = sTargetPkg;
        ai.sourceDir = sSandboxApk;
        ai.publicSourceDir = sSandboxApk;
        ai.dataDir = sSandboxData;
        ai.nativeLibraryDir = sSandboxData + "/../app/" + sTargetPkg + "/lib";
        ai.flags = 0;
        return ai;
    }
}
