package com.aether.helper

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

open class ProxyService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("AetherProxyService", "onStartCommand")
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? {
        Log.i("AetherProxyService", "onBind")
        return null
    }

    class P0 : ProxyService()
    class P1 : ProxyService()
    class P2 : ProxyService()
    class P3 : ProxyService()
}
