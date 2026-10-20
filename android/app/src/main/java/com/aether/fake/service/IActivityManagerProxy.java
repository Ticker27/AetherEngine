package com.aether.fake.service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * IActivityManagerProxy — proxy ระดับบริการ activity (โครงเดียวกับ
 * com.kos.engine.fake.service.IActivityManagerProxy + IBActivityManagerService$Stub$Proxy ของ KOS)
 *
 * กลไก (ตาม KOS):
 *   ActivityManager.IActivityManagerSingleton (android.util.Singleton)
 *     → get() = IActivityManager เดิม
 *     → set(Proxy.newProxyInstance(IActivityManager, handler)) = ของเรา
 *   handler: ตรวจ method ที่เกี่ยวกับการเริ่ม component — log + delegate (passthrough)
 *   ชั้นถัดไป (round ถัด ๆ ไป): redirect component ของ guest ไป proxy :pN (ตาม KOS IBActivityThread)
 */
public final class IActivityManagerProxy implements InvocationHandler {

    private static final String TAG = "AetherEngine";

    private final Object base;          // IActivityManager ตัวจริง
    private static volatile boolean sHooked;
    private static volatile String sTargetPkg;

    private IActivityManagerProxy(Object base) {
        this.base = base;
    }

    public static boolean install(String targetPkg) {
        if (sHooked) return true;
        try {
            sTargetPkg = targetPkg;

            Class<?> amCls = Class.forName("android.app.ActivityManager");
            Field f = amCls.getDeclaredField("IActivityManagerSingleton");
            f.setAccessible(true);
            Object singleton = f.get(null);
            if (singleton == null) {
                android.util.Log.w(TAG, "fake/ams: IActivityManagerSingleton = null");
                return false;
            }

            Method get = singleton.getClass().getMethod("get");
            get.setAccessible(true);
            Object base = get.invoke(singleton);
            if (base == null) {
                android.util.Log.w(TAG, "fake/ams: base IActivityManager = null");
                return false;
            }

            Class<?> iam = Class.forName("android.app.IActivityManager");
            Object proxy = Proxy.newProxyInstance(iam.getClassLoader(),
                    new Class<?>[]{iam}, new IActivityManagerProxy(base));

            boolean set = false;
            try {
                Method setm = singleton.getClass().getMethod("set", Object.class);
                setm.setAccessible(true);
                setm.invoke(singleton, proxy);
                set = true;
            } catch (Throwable t1) {
                for (Class<?> c = singleton.getClass(); c != null; c = c.getSuperclass()) {
                    try {
                        Field mInst = c.getDeclaredField("mInstance");
                        mInst.setAccessible(true);
                        mInst.set(singleton, proxy);
                        set = true;
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (!set) {
                    android.util.Log.w(TAG, "fake/ams: cannot set singleton (" + singleton.getClass().getName() + ")");
                }
            }
            sHooked = set;
            android.util.Log.i(TAG, "fake/ams: IActivityManager proxy installed=" + set
                    + " target=" + targetPkg);
            return set;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake/ams: install failed: " + t);
            return false;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (name.startsWith("startActivity") || name.equals("startService")
                || name.equals("stopService") || name.equals("bindService")
                || name.equals("getIntentSender") || name.equals("registerReceiver")) {
            try {
                if (args != null) {
                    for (Object a : args) {
                        if (a instanceof android.content.Intent) {
                            android.content.Intent it = (android.content.Intent) a;
                            if (it.getComponent() != null
                                    && sTargetPkg != null
                                    && sTargetPkg.equals(it.getComponent().getPackageName())) {
                                android.util.Log.i(TAG, "fake/ams: " + name
                                        + " guest component → " + it.getComponent().flattenToShortString()
                                        + " (passthrough; redirect ในเฟสถัดไป)");
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            return method.invoke(base, args);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getCause();
        }
    }
}
