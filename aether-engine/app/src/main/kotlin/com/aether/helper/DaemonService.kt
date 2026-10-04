package com.aether.helper

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class DaemonService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("AetherDaemon", "onStartCommand")
        return START_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i("AetherDaemon", "onTaskRemoved")
        // Phase 1: no restart
    }
    override fun onDestroy() { super.onDestroy(); Log.i("AetherDaemon", "onDestroy") }
    override fun onBind(intent: Intent?): IBinder? { Log.i("AetherDaemon", "onBind"); return null }

    class DaemonInnerService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }
}
