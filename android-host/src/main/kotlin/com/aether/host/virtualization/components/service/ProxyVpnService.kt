package com.aether.host.virtualization.components.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger

/**
 * Android VPN-service entry point.
 *
 * The route decision lives in [VpnRoutePolicy]; this class only marshals. Nothing is established
 * unless all three hold:
 *
 * 1. [HostFeature.PROXY_VPN_SERVICE] is enabled,
 * 2. the user granted the system VPN consent dialog (`VpnService.prepare` returned null), and
 * 3. the route policy validates.
 *
 * A failure of any one of them stops the service rather than degrading to a partial tunnel. A
 * half-configured VPN is worse than no VPN: it captures traffic it cannot forward.
 *
 * `protect()` is applied to the host's own sockets so the host's control traffic never re-enters
 * the tunnel it is managing.
 */
class ProxyVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!flagger.isEnabled(HostFeature.PROXY_VPN_SERVICE)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val policy = VpnRoutePolicy.defaultPolicy(packageName)
        when (val validation = policy.validate()) {
            is VpnRoutePolicy.Result.Invalid -> {
                Log.e(TAG, "refusing to establish tunnel: ${validation.problems}")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            VpnRoutePolicy.Result.Valid -> Unit
        }

        // A non-null return means the user has not consented yet; the consent activity is the
        // caller's responsibility and must be started from an Activity context.
        if (VpnService.prepare(this) != null) {
            Log.w(TAG, "VPN consent not granted; tunnel not established")
            dispatch("consent-required", intent)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        dispatch("started", intent)
        establish(policy)
        return START_STICKY
    }

    override fun onRevoke() {
        dispatch("revoked")
        teardown()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardown()
        dispatch("destroyed")
        super.onDestroy()
    }

    /** Applies the policy through `VpnService.Builder`. Idempotent. */
    private fun establish(policy: VpnRoutePolicy) {
        if (tunnel != null) return
        val builder = Builder()
            .setSession(policy.sessionName)
            .setMtu(policy.mtu)
        policy.addresses.forEach { builder.addAddress(it, 32) }
        policy.dnsServers.forEach { builder.addDnsServer(it) }
        policy.searchDomains.forEach { builder.addSearchDomain(it) }
        policy.routes.forEach { builder.addRoute(it.address, it.prefixLength) }
        // The host must never route through its own tunnel.
        runCatching { policy.excludedPackages.forEach { builder.addDisallowedApplication(it) } }
            .onFailure { Log.w(TAG, "could not exclude packages: ${it.javaClass.simpleName}") }
        tunnel = runCatching { builder.establish() }
            .onFailure { Log.e(TAG, "tunnel establish failed", it) }
            .getOrNull()
        if (tunnel == null) {
            Log.e(TAG, "tunnel is null after establish; stopping")
            stopSelf()
        }
    }

    private fun teardown() {
        runCatching { tunnel?.close() }
        tunnel = null
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

    private companion object {
        const val TAG = "AetherVpn"
    }
}
