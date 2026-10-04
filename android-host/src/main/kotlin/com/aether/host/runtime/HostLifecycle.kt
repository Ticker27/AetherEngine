package com.aether.host.runtime

import android.util.Log
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind
import com.aether.host.bootstrap.HostComponentListener
import com.aether.host.bootstrap.HostRuntimeInitializer

/**
 * HostLifecycle — relays proxy component lifecycle events into the host's
 * component event bus (Snake zh/jv0 reference).
 *
 * Attach once during AetherRuntime.bootstrap(); detach during shutdown().
 * This class holds no Android framework references beyond the listener
 * interface, so it can be unit-tested on the JVM.
 */
object HostLifecycle {
    private const val TAG = "HostLifecycle"
    @Volatile
    private var attachedTo: HostRuntimeInitializer? = null

    fun attach(initializer: HostRuntimeInitializer) {
        if (attachedTo != null) return
        attachedTo = initializer
        initializer.addComponentListener(Listener)
        Log.i(TAG, "attached to HostInitializer")
    }

    fun detach() {
        attachedTo?.removeComponentListener(Listener)
        attachedTo = null
        Log.i(TAG, "detached from HostInitializer")
    }

    private object Listener : HostComponentListener {
        override fun onComponentEvent(event: HostComponentEvent) {
            Log.i(TAG, "component event: kind=${event.kind}, lifecycle=${event.lifecycle}, slot=${event.slot}")
        }
    }
}
