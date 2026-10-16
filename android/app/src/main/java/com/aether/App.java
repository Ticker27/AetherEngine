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
        AetherCore.init(base);          // parity: core init
        Native.ic(base);               // parity: ic() = engine native init hook (RegisterNatives หลัง System.load)
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AetherCore.postInit(this);      // parity: core post-init
        Log.i("AetherEngine", "App.onCreate: engine=" + AetherCore.engineVersion());
    }
}
