package com.aether.container;

import android.app.Activity;
import android.os.Bundle;

/**
 * ต่อยอดจากโครงต้นแบบ: ต้นฉบับเก็บ callback ผ่าน engine core.h().f(ctx, new App$a(this))
 * AetherEngine: lifecycle logger จับ create/resume ของ activity ทุกตัวที่ผ่าน container
 */
class AppLifecycleLogger implements android.app.Application.ActivityLifecycleCallbacks {
    @Override public void onActivityStarted(android.app.Activity a) {}
    @Override public void onActivityResumed(android.app.Activity a) {
        android.util.Log.i("AetherEngine", "resumed: " + a.getLocalClassName());
    }
    @Override public void onActivityPaused(android.app.Activity a) {}
    @Override public void onActivityStopped(android.app.Activity a) {}
    @Override public void onActivitySaveInstanceState(android.app.Activity a, Bundle out) {}
    @Override public void onActivityDestroyed(android.app.Activity a) {}
    @Override public void onActivityCreated(android.app.Activity a, Bundle b) {
        android.util.Log.i("AetherEngine", "created: " + a.getLocalClassName());
    }
}
