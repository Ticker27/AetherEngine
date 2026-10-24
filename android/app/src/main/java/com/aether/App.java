package com.aether;

import android.content.Context;
import android.util.Log;

import com.aether.container.AetherCore;
import com.aether.helper.Native;

/**
 * parity: Application entry ของแบบจำลอง
 *
 * ต้นแบบ:
 *   attachBaseContext() { super; core init; core callback register; }
 *   onCreate()          { super; core post-init; }
 *
 * AetherEngine:
 *   attachBaseContext() → AetherCore.init(this)  ตามด้วย Native.ic(Context) = JNI init hook
 *   onCreate()          → AetherCore.postInit(this)
 * หมายเหตุ: analytics ไม่มีในโปรเจกต์นี้; ตรวจ notification channel อยู่ใน Entry.onCreate()
 */
public class App extends android.app.Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // สำคัญ: ในช่วง attachBaseContext — getApplicationContext() ยังเป็น null (framework ตั้งค่าให้หลัง attach)
        // จึงต้องส่ง Application นี้เอง (this) เข้า core/native ไม่ใช่ base
        AetherCore.init(this);          // parity: core init
        // ต้องติดตั้ง hook ที่นี่ ไม่ใช่ onCreate: ActivityThread ส่ง LAUNCH_ACTIVITY
        // หลัง Application.onCreate เท่านั้น — ติดช้าไป 1 จังหวะ message แรกจะหลุด hook
        com.aether.fake.hook.HookManager.installForProcess(this);
        if (Native.isAvailable()) {
            Native.ic(this);            // parity: ic() = engine native init hook (RegisterNatives หลัง System.load)
        } else {
            Log.w("AetherEngine", "native engine unavailable — running pure-Java mode");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AetherCore.postInit(this);      // parity: core post-init
        Log.i("AetherEngine", "App.onCreate: engine=" + AetherCore.engineVersion());
        com.aether.util.LogDump.engine(this, "App.onCreate engine=" + AetherCore.engineVersion());
    }
}
