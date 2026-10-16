package com.aether;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * parity: com.Entry (LAUNCHER Activity)
 *
 * ต้นแบบ: FlutterActivity (ปลอมตัวเป็น androidx menu class) + MethodChannel dispatcher + 3 init hooks
 *   a0() Firebase, b0() UI thread post, c0() POST_NOTIFICATIONS (requestCode 1002)
 *   d0()/e0() notification channel check ชื่อ channel id ของ engine
 *
 * AetherEngine: ไม่มี Flutter/Firebase — ปุ่ม UI ยิง code เข้า CommandRouter ตรง ๆ (โครง code table เดิม)
 */
public class MainActivity extends android.app.Activity {

    private static final int REQ_NOTIF = 1002;   // parity: requestCode 1002
    private static final int REQ_PICK_APK = 1001; // parity: onActivityResult 1000 (image picker) → ที่นี่: APK picker
    private static final String CHANNEL_ID = "com.aether.engine"; // parity: channel id ของ engine

    private TextView log;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        log = findViewById(R.id.log);

        a0();  // parity: ขอ POST_NOTIFICATIONS (ต้นแบบ c0())
        b0();  // parity: UI post-init + channel check (ต้นแบบ d0()/e0())

        findViewById(R.id.btn_install).setOnClickListener(v -> pickApk());
        findViewById(R.id.btn_list).setOnClickListener(v -> emit("G", null));
        findViewById(R.id.btn_blob).setOnClickListener(v -> emit("0", 0));
        findViewById(R.id.btn_f).setOnClickListener(v -> emit("F", null));
        findViewById(R.id.btn_prefs_b).setOnClickListener(v -> emit("B", new android.util.Pair<>("demo_key", "ok")));
        findViewById(R.id.btn_prefs_d).setOnClickListener(v -> emit("D", "demo_key"));
    }

    /** parity: c0() — request POST_NOTIFICATIONS runtime (API 33+) */
    private void a0() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    /** parity: d0()/e0() — ตรวจ notification channel ของ engine (ใช้ชื่อ channel เดียวกับ DaemonService) */
    private void b0() {
        runOnUiThread(() -> {
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            android.app.NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
            logLine("channel " + CHANNEL_ID + " → " + (ch != null ? ch.getImportance() : "absent"));
        });
    }

    private void pickApk() {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("application/vnd.android.package-archive");
        startActivityForResult(it, REQ_PICK_APK);
    }

    /** parity: onActivityResult(requestCode=1000) ของ Entry — ที่นี่ส่ง Uri เข้า code "H" (install) */
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK_APK && res == RESULT_OK && data != null && data.getData() != null) {
            emit("H", data.getData());
        }
    }

    private void emit(String code, Object arg) {
        Object out = CommandRouter.dispatch(this, code, arg);
        logLine("C(\"" + code + "\") → " + (out == null ? "null" : out.toString()));
    }

    private void logLine(String s) {
        log.append(s + "\n");
        android.util.Log.i("AetherEngine", s);
    }
}
