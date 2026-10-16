package com.aether.proxy;

import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;

/**
 * ProxyVpnService — stub VPN ของ container (main process, BIND_VPN_SERVICE)
 *
 * โครงเดียวกับแบบจำลอง: มี VpnService เตรียมไว้ผูกกับ tunnel ของ container
 * ที่นี่เป็น structural stub — ไม่ establish tunnel จริง (ไม่แตะเครือข่าย)
 */
public class ProxyVpnService extends VpnService {

    public static final String TAG = "AetherEngine";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.i(TAG, "ProxyVpnService.onStartCommand action="
                + (intent != null ? intent.getAction() : null));
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        android.util.Log.i(TAG, "ProxyVpnService.onBind (structural stub — no tunnel)");
        return null;
    }
}
