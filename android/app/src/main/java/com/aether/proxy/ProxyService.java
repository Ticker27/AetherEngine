package com.aether.proxy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * ProxyService — stub service ของ container (P0–P3 ต่อ process)
 * โครงเดียวกับแบบจำลอง: VServiceManager route ไป service จริงของ target;
 * ที่นี่ log + คืน START_NOT_STICKY (ไม่ค้าง process เกินจำเป็น)
 */
public class ProxyService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.i("AetherEngine", "ProxyService " + getClass().getSimpleName()
                + " action=" + (intent != null ? intent.getAction() : null));
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        android.util.Log.i("AetherEngine", "ProxyService.onBind " + getClass().getSimpleName());
        return null;
    }

    public static class P0 extends ProxyService {}
    public static class P1 extends ProxyService {}
    public static class P2 extends ProxyService {}
    public static class P3 extends ProxyService {}
}
