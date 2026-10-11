package com.aether.host.virtualization.flags

import java.util.concurrent.ConcurrentHashMap

/**
 * Host-only features. A flag is a rollout switch, not a security boundary.
 *
 * Ordering rule: every capability that touches another process, another app, or a framework
 * member outside the public SDK is gated here and defaults to disabled. Trusted APK
 * verification is still required before any code is loaded, even after
 * [DYNAMIC_APK_LOADING] is enabled.
 */
enum class HostFeature {
    /** Load a verified guest APK into a guest-first class loader. */
    DYNAMIC_APK_LOADING,

    /** Host the cooperative first-party fixture entry point inside a proxy Activity. */
    COOPERATIVE_FIXTURE_UI,

    /** Establish the host VPN tunnel (requires the system consent dialog). */
    PROXY_VPN_SERVICE,

    /** Open the internal WebView browser. */
    INTERNAL_WEB_BROWSER,

    /** Accept same-process `call()` RPC on the internal system-call provider. */
    SYSTEM_CALL_IPC,

    /** Keep the host daemon alive across task removal with a foreground notification. */
    DAEMON_KEEPALIVE,

    /** Allow descriptor-driven lookup of framework members listed in the reflection allowlist. */
    REFLECTIVE_FRAMEWORK_ACCESS,

    /** Restart a proxy slot after an unexpected host-side death. */
    SLOT_RESTART,
}

/**
 * Process-local feature flags for optional host capabilities.
 *
 * Flags are per-process and non-persistent. Nothing here grants a guest more authority than
 * the host process already holds; the host is not a sandbox.
 */
object flagger {
    private val overrides = ConcurrentHashMap<HostFeature, Boolean>()

    @JvmStatic
    fun isEnabled(feature: HostFeature): Boolean = overrides[feature] ?: false

    @JvmStatic
    fun setEnabled(feature: HostFeature, enabled: Boolean) {
        overrides[feature] = enabled
    }

    /** Snapshot for diagnostics; never contains secrets, only feature names and their state. */
    @JvmStatic
    fun snapshot(): Map<String, Boolean> =
        HostFeature.entries.associate { it.name to (overrides[it] ?: false) }

    @JvmStatic
    fun enabledCount(): Int = HostFeature.entries.count { isEnabled(it) }

    internal fun resetForTests() {
        overrides.clear()
    }
}
