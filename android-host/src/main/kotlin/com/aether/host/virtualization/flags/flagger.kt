package com.aether.host.virtualization.flags

import java.util.concurrent.ConcurrentHashMap

/** Host-only features. A flag is a rollout switch, not a security boundary. */
enum class HostFeature {
    DYNAMIC_APK_LOADING,
    PROXY_VPN_SERVICE,
    INTERNAL_WEB_BROWSER,
}

/**
 * Process-local feature flags for optional host capabilities.
 * All capabilities default to disabled; trusted APK verification is still required
 * before code can be loaded, even after DYNAMIC_APK_LOADING is enabled.
 */
object flagger {
    private val overrides = ConcurrentHashMap<HostFeature, Boolean>()

    @JvmStatic
    fun isEnabled(feature: HostFeature): Boolean = overrides[feature] ?: false

    @JvmStatic
    fun setEnabled(feature: HostFeature, enabled: Boolean) {
        overrides[feature] = enabled
    }

    internal fun resetForTests() {
        overrides.clear()
    }
}
