package com.aether.client;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;

import java.lang.reflect.Field;

/**
 * ContextFixer — พอร์ตจาก VirtualApp `client/fixer/ContextFixer.java`
 *
 * หัวใจที่ต้องลอกให้ตรง (ของเดิมใน AetherEngine/เวอร์ชันแรกของผมทำผิด):
 *
 *   ContextImpl มี package name สามตัว และมันทำหน้าที่ต่างกัน:
 *     mPackageName      ← ใช้ตอบ getPackageName()            → ต้องเป็น **ของเกสต์**
 *     mBasePackageName  ← ใช้ตอบ getBasePackageName()          → ต้องเป็น **ของ host**
 *     mOpPackageName    ← ใช้ attribute สิทธิ์ตอนคุยกับระบบ    → ต้องเป็น **ของ host**
 *
 *   ถ้าตั้ง mOpPackageName เป็นของเกสต์ (package ที่ไม่ได้ติดตั้งบนเครื่อง)
 *   ทุกคำขอที่ผ่าน AMS/PMS จะถูกปฏิเสธ → SecurityException / NameNotFoundException
 *   VA แก้ด้วยการตั้ง base/op เป็น host และปล่อย mPackageName เป็นเกสต์
 *
 *   + set mPackageManager = null เพื่อบังคับ ContextImpl ดึง PM ใหม่ (ให้ได้ proxy ของเรา)
 *   + set ContentResolver.mPackageName = host (API 18+)
 */
public final class ContextFixer {

    private static final String TAG = "AetherEngine";

    private ContextFixer() {}

    /**
     * @param context Context ที่จะแก้ (Activity/Application/ContextImpl)
     * @param hostPkg package ของ container (ตัวที่ติดตั้งจริงบนเครื่อง)
     */
    public static void fixContext(Context context, String hostPkg) {
        if (context == null) return;
        try {
            context.getPackageName();       // ปลุก ContextImpl ให้สร้าง field ก่อน
        } catch (Throwable t) {
            return;
        }

        Object impl = unwrap(context);
        if (impl == null) {
            android.util.Log.w(TAG, "fixContext: cannot reach ContextImpl");
            return;
        }

        // 1) บังคับให้ดึง PackageManager ใหม่ → ได้ IPackageManagerProxy ของเรา
        set(impl, "mPackageManager", null);
        try {
            context.getPackageManager();
        } catch (Throwable ignored) {}

        // 2) package identity: base/op = host, mPackageName = guest (ตั้งโดย GuestIdentity)
        set(impl, "mBasePackageName", hostPkg);
        set(impl, "mOpPackageName", hostPkg);

        String guest = GuestIdentity.guestPkg();
        if (guest != null) {
            set(impl, "mPackageName", guest);
            Object ai = get(impl, "mApplicationInfo");
            if (ai instanceof android.content.pm.ApplicationInfo) {
                ((android.content.pm.ApplicationInfo) ai).packageName = guest;
            }
        }

        // 3) ContentResolver ถูกใช้ attribute สิทธิ์เช่นกัน
        try {
            Object resolver = get(impl, "mContentResolver");
            if (resolver != null) set(resolver, "mPackageName", hostPkg);
        } catch (Throwable ignored) {}

        android.util.Log.i(TAG, "fixContext: base/op=" + hostPkg
                + " pkg=" + guest + " impl=" + impl.getClass().getName());
    }

    /** เขียน Resources ของเกสต์ลง ContextImpl (ถ้ามี) */
    public static void fixResources(Context context, android.content.res.Resources res) {
        if (context == null || res == null) return;
        Object impl = unwrap(context);
        if (impl == null) return;
        set(impl, "mResources", res);
        set(impl, "mTheme", null);
    }

    /** เดินลงไปถึง ContextImpl ข้างในสุด */
    private static Object unwrap(Context ctx) {
        Context cur = ctx;
        for (int i = 0; i < 10; i++) {
            if (!(cur instanceof ContextWrapper)) return cur;
            Context base = ((ContextWrapper) cur).getBaseContext();
            if (base == null) return null;
            cur = base;
        }
        return null;
    }

    private static Object get(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void set(Object o, String name, Object v) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(o, v);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fixContext: set " + name + " → " + t);
        }
    }
}
