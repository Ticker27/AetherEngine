package com.aether.util;

import android.content.Context;

import java.lang.reflect.Method;

/**
 * DeviceRegistry — device fingerprint ผ่าน reflection
 *
 * หลักฐานของแบบจำลอง: pack เก็บสตริง reflection ไว้ระบุเครื่อง ได้แก่
 *   ro.product.model · ro.product.manufacturer · ro.hardware
 *   android/provider/Settings$Secure + getString(ContentResolver, "android_id")
 *
 * ที่นี่ทำ "อ่านอย่างเดียว" เพื่อแสดงกลไก — ไม่บันทึก ไม่ส่งออก
 * (ไม่มีระบบ device-lock / login key ในโปรเจกต์นี้)
 */
public final class DeviceRegistry {

    private DeviceRegistry() {}

    /** parity: reflection ผ่าน android.os.SystemProperties.get */
    public static String sysProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            Object v = get.invoke(null, key);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** parity: reflection ผ่าน Settings$Secure.getString(ContentResolver, name) */
    public static String androidId(Context ctx) {
        try {
            Class<?> sec = Class.forName("android.provider.Settings$Secure");
            Method getString = sec.getMethod("getString", android.content.ContentResolver.class, String.class);
            Object v = getString.invoke(null, ctx.getContentResolver(), "android_id");
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** สรุปสั้น ๆ สำหรับ log/คำสั่ง "L" (ไม่แสดง android_id เต็ม — เหลือ 4 ตัวท้าย) */
    public static String summary(Context ctx) {
        String id = androidId(ctx);
        String idShort = id.length() > 4 ? "…" + id.substring(id.length() - 4) : "(none)";
        return "model=" + sysProp("ro.product.model")
                + " manu=" + sysProp("ro.product.manufacturer")
                + " hw=" + sysProp("ro.hardware")
                + " id=" + idShort;
    }
}
