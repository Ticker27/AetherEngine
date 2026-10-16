package com.aether;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.aether.helper.DaemonService;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

/**
 * Entry — LAUNCHER Activity ของ AetherEngine (Flutter host)
 *
 * โครงเดียวกับแบบจำลอง: FlutterActivity + MethodChannel dispatcher
 *   method name "คือ" code ในตาราง → ส่งต่อเข้า CommandRouter (Java) ทันที
 *   "0" blob · "A" toast · "B" prefs put · "D" prefs get · "E" open url
 *   "F" cached · "G" list container · "H" install apk
 *
 * ตัดออกตาม scope: logIn / token / license — ไม่มีในตาราง
 */
public class Entry extends FlutterActivity {

    private static final String CHANNEL = "com.aether/engine";
    private static final int REQ_NOTIF = 1002;
    private static final int REQ_IMAGE = 1000;
    private static final int REQ_APK = 1001;

    private MethodChannel channel;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine engine) {
        super.configureFlutterEngine(engine);

        channel = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(), CHANNEL);
        channel.setMethodCallHandler((call, result) -> {
            try {
                Object arg = call.arguments;
                arg = adaptArgument(call.method, arg);
                Object out = CommandRouter.dispatch(Entry.this, call.method, arg);
                result.success(out == null ? "" : out.toString());
            } catch (Throwable t) {
                result.error("AETHER", t.getClass().getSimpleName(), t.getMessage());
            }
        });
    }

    /** แปลง argument จาก Dart ให้ตรงกับชนิดที่ CommandRouter ต้องการ */
    private static Object adaptArgument(String method, Object arg) {
        if ("B".equals(method) && arg instanceof java.util.List) {
            java.util.List<?> l = (java.util.List<?>) arg;
            return new android.util.Pair<>(String.valueOf(l.get(0)), String.valueOf(l.get(1)));
        }
        if ("H".equals(method) && arg instanceof String) {
            return Uri.parse((String) arg);
        }
        return arg;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        a0(); // parity: ขอ POST_NOTIFICATIONS (API 33+)
        b0(); // parity: ตรวจ notification channel ของ engine
    }

    private void a0() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void b0() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        android.app.NotificationChannel ch = nm.getNotificationChannel(DaemonService.CHANNEL_ID);
        android.util.Log.i("AetherEngine", "Entry.onCreate: channel=" + (ch != null ? ch.getImportance() : "absent"));
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;

        if (req == REQ_IMAGE) {
            // parity: decode → centerCrop 128×128 → JPEG q85 → ลดทีละ 5 จน ≤10 KB แล้วส่งผลกลับ
            final android.net.Uri uri = data.getData();
            new Thread(() -> {
                String payload;
                try {
                    byte[] jpeg = com.aether.media.ImageCompressor.compress(Entry.this, uri);
                    java.io.File out = new java.io.File(getCacheDir(), "pick_" + System.currentTimeMillis() + ".jpg");
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) { fos.write(jpeg); }
                    payload = out.getAbsolutePath() + "|" + jpeg.length + "B";
                } catch (Exception e) {
                    payload = "ERR:" + e.getMessage();
                }
                final String p = payload;
                runOnUiThread(() -> { if (channel != null) channel.invokeMethod("imageResult", p); });
            }).start();
        } else if (req == REQ_APK) {
            CommandRouter.dispatch(this, "H", data.getData());
        }
    }
}
