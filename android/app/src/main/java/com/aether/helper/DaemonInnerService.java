package com.aether.helper;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * parity: DaemonInnerService ของแบบจำลอง
 * ต้นแบบ: inner service คู่ของ DaemonService — foreground double-service technique
 * เพื่อให้ Android 8+ ไม่ kill ทั้งสองตัวเองง่าย ๆ :helper process เดียวกัน
 */
public class DaemonInnerService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        // ต้นแบบ: startForeground(id2, notification) บน channel เดียวกับ DaemonService
        startForeground(10001,
                new Notification.Builder(this, DaemonService.CHANNEL_ID)
                        .setContentTitle("AetherEngine inner")
                        .setSmallIcon(android.R.drawable.sym_def_app_icon)
                        .build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
