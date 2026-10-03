package com.aether.host.virtualization.activity

/** Transparent Activity slots for guest flows that must preserve the host window behind them. */
abstract class TransparentProxyActivity : VirtualActivity() {
    final override val proxySlot: ProxyActivitySlot
        get() = ProxyActivitySlot(ProxyActivityFamily.TRANSPARENT, slotIndex)
    final override val showUnattachedPlaceholder: Boolean = false

    protected abstract val slotIndex: Int
}

class TransparentProxyActivityP0 : TransparentProxyActivity() {
    override val slotIndex = 0
}

class TransparentProxyActivityP1 : TransparentProxyActivity() {
    override val slotIndex = 1
}

class TransparentProxyActivityP2 : TransparentProxyActivity() {
    override val slotIndex = 2
}

class TransparentProxyActivityP3 : TransparentProxyActivity() {
    override val slotIndex = 3
}
