package com.aether.util;

import android.os.Looper;

import java.util.concurrent.Callable;

/**
 * RunOffMain — ตัวช่วยให้งานหนัก (ดาวน์โหลด/คัดลอก/dex/zip) ไม่กิน main thread
 * ใช้รูปแบบเดียวกันทุกจุด: ถ้าอยู่บน main → สร้าง worker แล้ว join ตามเวลาที่ยอมรับ
 * (timeout แต่ละ flow กำหนดที่ call site ตามขนาดงานจริง)
 */
public final class RunOffMain {

    private RunOffMain() {}

    /** Callable เวอร์ชัน (มีค่าคืน) — ถ้า join ไม่ทัน/throwable คืน fallback ที่กำหนด */
    public static <T> T run(Callable<T> job, long joinMs, T fallback, String name) {
        if (Looper.myLooper() == null || Looper.myLooper() != Looper.getMainLooper()) {
            try {
                return job.call();
            } catch (Throwable t) {
                android.util.Log.w("AetherEngine", name + ": " + t);
                return fallback;
            }
        }
        final Object[] out = new Object[1];
        final Throwable[] err = new Throwable[1];
        Thread th = new Thread(() -> {
            try {
                out[0] = job.call();
            } catch (Throwable t) {
                err[0] = t;
            }
        }, "aether-" + name);
        th.start();
        try {
            th.join(joinMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (err[0] != null) {
            android.util.Log.w("AetherEngine", name + ": " + err[0]);
            return fallback;
        }
        // join หมดเวลาแต่ job ยังวิ่ง — คืน fallback แล้วปล่อยให้ worker จบเอง (ไม่ interrupt)
        return fallback;
    }

    /** Runnable เวอร์ชัน (ไม่มีค่าคืน) — กรณีผลลัพธ์อยู่ใน state อื่นอยู่แล้ว */
    public static void run(long joinMs, String name, Runnable job) {
        run(() -> {
            job.run();
            return Boolean.TRUE;
        }, joinMs, Boolean.TRUE, name);
    }
}
