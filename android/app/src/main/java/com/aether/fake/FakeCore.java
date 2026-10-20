package com.aether.fake;

/**
 * FakeCore — จุดรวม init ของ fake framework (โครงเดียวกับ com.kos.engine.fake.FakeCore)
 *
 * หลักฐานจาก KOS: FakeCore.init() → ReflectCore.set(ActivityThread.class) แล้ว HookManager.init()
 * ที่นี่: ผูก hiddenapi exemptions (มีผลแล้วจาก GuestLauncher) + ให้ HookManager ทำงานต่อ
 *
 * หมายเหตุสถาปัตยกรรม: fake framework ทำงานเฉพาะใน process ที่โฮสต์เกสต์ (:p0)
 * ไม่แตะ main process ของเรา — ตรงกับพาราไดม์ "process ต่อเกสต์" ของตระกูล VirtualApp/BlackBox
 */
public final class FakeCore {

    public static final String TAG = "AetherEngine";

    private static volatile boolean sInit;

    private FakeCore() {}

    public static void init() {
        if (sInit) return;
        synchronized (FakeCore.class) {
            if (sInit) return;
            android.util.Log.i(TAG, "FakeCore.init (fake framework ของโฮสต์เกสต์)");
            sInit = true;
        }
    }

    public static boolean isInit() {
        return sInit;
    }
}
