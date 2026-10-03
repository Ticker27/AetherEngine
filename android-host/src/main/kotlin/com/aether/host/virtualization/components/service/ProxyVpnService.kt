package com.aether.host.virtualization.components.service

import android.content.Intent
import android.net.VpnService
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger

/**
 * Android VPN-service entry point reserved for an explicitly approved guest adapter.
 * This stub never establishes a tunnel; Android user consent and a concrete VPN policy
 * are required before adding any tunnel behavior.
 */
class ProxyVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!flagger.isEnabled(HostFeature.PROXY_VPN_SERVICE)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        dispatch("started", intent)
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        dispatch("revoked")
        super.onRevoke()
    }

    override fun onDestroy() {
        dispatch("destroyed")
        super.onDestroy()
    }

    private fun dispatch(event: String, intent: Intent? = null) {
        (application as? AetherApplication)?.hostInitializer?.dispatch(
            HostComponentEvent(
                owner = this,
                kind = HostComponentKind.VPN_SERVICE,
                slot = null,
                lifecycle = event,
                proxyType = "vpn",
                intent = intent,
            ),
        )
    }
}
