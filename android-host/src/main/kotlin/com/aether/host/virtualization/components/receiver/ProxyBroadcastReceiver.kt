package com.aether.host.virtualization.components.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind

/** Non-exported receiver that forwards explicit host broadcasts to registered listeners. */
class ProxyBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AetherApplication ?: return
        app.hostInitializer.dispatch(
            HostComponentEvent(
                owner = this,
                kind = HostComponentKind.BROADCAST_RECEIVER,
                slot = null,
                lifecycle = "received",
                proxyType = "broadcast",
                intent = intent,
            ),
        )
    }
}
