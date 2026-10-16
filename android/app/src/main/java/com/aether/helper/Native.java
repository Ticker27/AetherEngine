package com.aether.helper;

import android.content.Context;

/**
 * parity: Native bridge ของแบบจำลอง (JNI bridge ผูกผ่าน RegisterNatives)
 *
 * AetherEngine รวมเหลือ 8 native methods — ตัดออก:
 *   logIn(String,long) / logIn(String,long,boolean)   ← ระบบ login key/license (ไม่เอา)
 *   update(Object,Method)                              ← reflection pool สำหรับ AMS/PMS hook ของ container framework ของแบบจำลอง เต็มรูป
 * และ gcuid(int) ยังเป็น pure Java เหมือนต้นแบบ
 *
 * ไม่มี Java_com_aether_helper_Native_* export — ผูกผ่าน JNI_OnLoad → RegisterNatives
 * ใน libaether.so ให้เหมือนโครงสร้างต้นแบบ (dynamic registration, anti-grep ระดับหนึ่ง)
 */
public final class Native {

    static {
        System.loadLibrary("aether");   // libaether.so
    }

    // ── engine init ────────────────────────────────────────────────
    public static native void ic(Context ctx);          // parity: ic(Context) — engine init hook

    // ── data decrypt (parity: ilil/djp family) ────────────────────
    public static native String ilil(int key);          // string decryptor: key → plain (6-branch dispatcher ต้นแบบ)
    public static native byte[] djp(int id);            // blob decryptor: จาก .rodata table (ต้นแบบ: 496 blobs)
    public static native boolean chl(byte[] data);      // verifier: CRC32 เทียบ blob (ต้นแบบ: checksum/verify)

    // ── engine ops (ต้นแบบ: trampoline/refresh) ───────────────────
    public static native void eio();                    // flush native log buffer
    public static native void awl(String s);            // log/track string (ต้นแบบ: awl(String))
    public static native void aior(String a, String b); // log/track pair (ต้นแบบ: aior(String,String))
    public static native void ac(Object a, Object b);   // generic callback sink (ต้นแบบ: ac(Object,Object))

    // ── pure Java (parity: gcuid เป็น Java ล้วนในต้นแบบ) ─────────
    public static int gcuid(int seed) {
        int h = seed ^ 0x5F3759DF;                      // (ต้นแบบใช้ค่าคงที่กลางๆ ประดิษฐ์เอง — คงกลไก เปลี่ยนค่า)
        h ^= h >>> 16; h *= 0x7FEB352D; h ^= h >>> 15; h *= 0x846CA68B; h ^= h >>> 16;
        return h & 0x7FFFFFFF;
    }

    private Native() {}
}
