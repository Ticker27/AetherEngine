package com.aether.helper

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.util.Log

open class ProxyVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("AetherVpn", "onStartCommand")
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? {
        Log.i("AetherVpn", "onBind")
        return super.onBind(intent)
    }
}
