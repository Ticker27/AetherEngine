package com.aether;

import android.content.Context;
import android.util.Log;

import com.aether.container.AetherCore;
import com.aether.helper.Native;

/**
 * parity: Application entry ของแบบจำลอง
 *
 * ต้นแบบ:
 *   attachBaseContext() { super; core init; engine core.h().f(ctx, cb); }
 *   onCreate()          { super; core post-init; }
 *
 * AetherEngine:
 *   attachBaseContext() → AetherCore.init(this)  ตามด้วย Native.ic(Context) = JNI init hook
 *   onCreate()          → AetherCore.postInit(this)
 * ตัดออก: Firebase init (a0()), notification channel setup ย้ายไปเรียกเมื่อเปิด MainActivity
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
        AetherCore.postInit(this);      // parity: engine core.h().g()
        Log.i("AetherEngine", "App.onCreate: engine=" + AetherCore.engineVersion());
    }
}
