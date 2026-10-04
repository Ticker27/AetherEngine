package com.aether.host.virtualization.components.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind

/**
 * Explicitly started, in-process host service. It is non-sticky and does not create a
 * foreground notification or restart itself; callers own its lifetime.
 */
open class DaemonService : Service() {
    private val localBinder by lazy(LazyThreadSafetyMode.NONE) { LocalBinder() }

    inner class LocalBinder : Binder() {
        fun service(): DaemonService = this@DaemonService
    }

    override fun onCreate() {
        super.onCreate()
        dispatch("created")
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatch("started", intent)
        return START_NOT_STICKY
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
                slot = null,
                lifecycle = event,
                proxyType = "daemon",
                intent = intent,
            ),
        )
    }
}

/** Compatibility service component with the same safe, process-local behavior. */
class DaemonInnerService : DaemonService()
