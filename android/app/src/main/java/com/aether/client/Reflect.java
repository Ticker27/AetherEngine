package com.aether.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reflect — helper บาง ๆ สำหรับ hook (แทน mirror/* ของ VirtualApp) */
public final class Reflect {

    private Reflect() {}

    public static Field field(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Field field(Object obj, String name) {
        if (obj == null) return null;
        // ต้องเดินขึ้น hierarchy: field ส่วนใหญ่ declare ไว้บนคลาสแม่
        // เช่น mCallback อยู่บน Handler (ไม่ใช่ ActivityThread$H), mToken อยู่บน Activity (ไม่ใช่คลาสของเกม)
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** หา Method เดินขึ้น hierarchy (get() ของ Singleton อยู่บนคลาสแม่) */
    public static Method method(Object obj, String name, Class<?>... types) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    public static Object get(Class<?> cls, String name) {
        Field f = field(cls, name);
        try { return f == null ? null : f.get(null); } catch (Throwable t) { return null; }
    }

    public static Object get(Object obj, String name) {
        Field f = field(obj, name);
        try { return f == null ? null : f.get(obj); } catch (Throwable t) { return null; }
    }

    public static boolean set(Class<?> cls, String name, Object v) {
        Field f = field(cls, name);
        try { if (f != null) { f.set(null, v); return true; } } catch (Throwable t) { }
        return false;
    }

    public static boolean set(Object obj, String name, Object v) {
        Field f = field(obj, name);
        try { if (f != null) { f.set(obj, v); return true; } } catch (Throwable t) { }
        return false;
    }

    /** Singleton<T>.mInstance — ใช้กับ ActivityManager.IActivityManagerSingleton */
    public static boolean setSingletonInstance(String ownerClassName, String singletonField,
                                               Class<?> iface, java.lang.reflect.InvocationHandler h) {
        try {
            Object singleton = get(Class.forName(ownerClassName), singletonField);
            if (singleton == null) return false;
            Object raw = get(singleton, "mInstance");
            ClassLoader cl = raw != null ? raw.getClass().getClassLoader() : iface.getClassLoader();
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(cl, new Class[]{iface}, h);
            return set(singleton, "mInstance", proxy);
        } catch (Throwable t) {
            android.util.Log.w("AetherEngine", "setSingletonInstance " + singletonField + ": " + t);
            return false;
        }
    }

    public static Object invoke(Object target, String name, Object... args) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != args.length) continue;
                m.setAccessible(true);
                return m.invoke(target, args);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
