package com.aether.host.virtualization.components.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind

/** Base for private, fixed-pool service proxies. No guest service is started implicitly. */
abstract class ProxyService : Service() {
    protected abstract val slotIndex: Int

    override fun onCreate() {
        super.onCreate()
        dispatch("created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatch("started", intent)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        dispatch("bound", intent)
        return null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        dispatch("unbound", intent)
        return false
    }

    override fun onDestroy() {
        dispatch("destroyed")
        super.onDestroy()
    }

    private fun dispatch(event: String, intent: Intent? = null) {
        (application as? AetherApplication)?.hostInitializer?.dispatch(
            HostComponentEvent(
                owner = this,
                kind = HostComponentKind.SERVICE,
                slot = slotIndex,
                lifecycle = event,
                proxyType = "service-p$slotIndex",
                intent = intent,
            ),
        )
    }
}

class ProxyServiceP0 : ProxyService() {
    override val slotIndex = 0
}

class ProxyServiceP1 : ProxyService() {
    override val slotIndex = 1
}

class ProxyServiceP2 : ProxyService() {
    override val slotIndex = 2
}

class ProxyServiceP3 : ProxyService() {
    override val slotIndex = 3
}
