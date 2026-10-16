package com.aether.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * ProxyBroadcastReceiver — stub รับ broadcast ของ container (:helper process)
 *
 * parity: แบบจำลองรับ broadcast → goAsync() → ส่งต่อ core
 * ที่นี่: goAsync() บน worker thread แล้ว log + finish (โครงพร้อมต่อยอดแบบเดียวกัน)
 * exported=false เป็นจุดต่างเชิงความปลอดภัยโดยเจตนา (แบบจำลองตั้ง true)
 */
public class ProxyBroadcastReceiver extends BroadcastReceiver {

    public static final String ACTION_STUB = "com.aether.proxy.STUB_BROADCAST";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();                 // parity: goAsync()
        final String action = intent != null ? intent.getAction() : null;
        final String pkg = context != null ? context.getPackageName() : "?";
        new Thread(() -> {
            try {
                Log.i("AetherEngine", "ProxyBroadcastReceiver: action=" + action + " host=" + pkg);
            } finally {
                pr.finish();                                // parity: จบ PendingResult
            }
        }, "aether-proxy-broadcast").start();
    }
}
