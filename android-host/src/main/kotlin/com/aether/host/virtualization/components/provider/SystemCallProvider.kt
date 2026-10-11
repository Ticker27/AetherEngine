package com.aether.host.virtualization.components.provider

import android.net.Uri
import android.os.Bundle
import com.aether.host.bridge.Native
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger

/**
 * Internal system-call provider — the host's same-process RPC entry point.
 *
 * The contract lives in [SystemCallContract]; this class only marshals. It is declared
 * `exported="false"` in the manifest and its authority is `${applicationId}.system.internal`,
 * so only the host's own UID can reach it. That is a deliberate difference from a shipping
 * virtualization host, which typically exports this provider unguarded so that a helper app can
 * drive it: an unguarded exported `call()` is a remote entry point into the engine.
 *
 * Gating: [HostFeature.SYSTEM_CALL_IPC]. With the flag off, every call is rejected before the
 * native bridge is touched, so an accidental enablement cannot widen the surface silently.
 */
class SystemCallProvider : ProxyContentProvider() {
    override val slotIndex: Int? = null
    override val providerKind: String = "system-call"

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        dispatch("call", Uri.parse("system-call://$method"))
        if (!flagger.isEnabled(HostFeature.SYSTEM_CALL_IPC)) {
            return bundleOf(*SystemCallContract.rejectionPayload("system-call IPC is disabled").toTypedArray())
        }
        return when (val validation = SystemCallContract.validate(method, arg)) {
            is SystemCallContract.Validation.Rejected ->
                bundleOf(*SystemCallContract.rejectionPayload(validation.reason).toTypedArray())

            is SystemCallContract.Validation.Accepted -> {
                val bridgeMethod = validation.method.bridgeMethod
                // extras is intentionally unread: this contract accepts no structured input.
                val response = runCatching {
                    Native.dispatchRequest(bridgeMethod, "{}")
                }.getOrElse { error ->
                    "{\"ok\":false,\"error\":\"${error.javaClass.simpleName}\"}"
                }
                bundleOf(
                    SystemCallContract.KEY_OK to "true",
                    SystemCallContract.KEY_DATA to response,
                )
            }
        }
    }

    private fun bundleOf(vararg pairs: Pair<String, String>): Bundle =
        Bundle().apply { pairs.forEach { (key, value) -> putString(key, value) } }
}
