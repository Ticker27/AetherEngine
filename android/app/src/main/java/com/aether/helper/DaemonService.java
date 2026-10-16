package com.aether.helper;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;

/**
 * parity: DaemonService ของแบบจำลอง (:helper process, foreground keep-alive)
 *
 * ต้นแบบ (ถอดจาก static analysis):
 *   c()  → NotificationChannel channel id ของ engine → startForeground(10000, notif)
 *          → startService(DaemonInnerService)  [foreground double-service กัน kill]
 *   b(long delay) → AlarmManager.setExactAndAllowWhileIdle / setAndAllowWhileIdle
 *          → PendingIntent.getService(DaemonService)  [reschedule ตัวเองหลังถูก kill]
 *
 * AetherEngine: เก็บกลไกเดิมทั้งหมด (channel id, startForeground id, alarm reschedule)
 * เปลี่ยน channel id เป็น "com.aether.engine" (แบบจำลอง: engine channel)
 */
public class DaemonService extends Service {

    public static final String CHANNEL_ID = "com.aether.engine";
    private static final int FOREGROUND_ID = 10000;   // parity: startForeground(10000)

    @Override
    public void onCreate() {
        super.onCreate();
        c();                       // parity: c() จากต้นแบบ
        startInner();              // parity: startService(DaemonInnerService)
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // parity: b(delay) — ตั้ง alarm ยิงตัวเองซ้ำเมื่อโดน kill
        b(60_000L);
        return START_STICKY;
    }

    /** parity: c() — foreground notification บน channel ของ engine */
    private void c() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
        if (ch == null) {
            ch = new NotificationChannel(CHANNEL_ID, "AetherEngine Service Channel", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AetherEngine")
                .setContentText("engine keep-alive")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .build();
        startForeground(FOREGROUND_ID, n);
    }

    private void startInner() {
        try {
            startService(new Intent(this, DaemonInnerService.class));
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "inner start: " + e.getMessage());
        }
    }

    /** parity: b(long) — AlarmManager reschedule ตัวเองแบบ exact-while-idle */
    void b(long delay) {
        AlarmManager am = getSystemService(AlarmManager.class);
        long at = SystemClock.elapsedRealtime() + delay;
        PendingIntent pi = PendingIntent.getService(
                this, 1, new Intent(this, DaemonService.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
