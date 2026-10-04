package com.aether.host.virtualization.activity

/** Activity slots reserved for host-created PendingIntent flows. */
abstract class ProxyPendingActivity : VirtualActivity() {
    final override val proxySlot: ProxyActivitySlot
        get() = ProxyActivitySlot(ProxyActivityFamily.PENDING, slotIndex)

    protected abstract val slotIndex: Int
}

class ProxyPendingActivityP0 : ProxyPendingActivity() {
    override val slotIndex = 0
}

class ProxyPendingActivityP1 : ProxyPendingActivity() {
    override val slotIndex = 1
}

class ProxyPendingActivityP2 : ProxyPendingActivity() {
    override val slotIndex = 2
}

class ProxyPendingActivityP3 : ProxyPendingActivity() {
    override val slotIndex = 3
}
