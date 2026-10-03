package com.aether.host.virtualization.activity

/** Standard proxy Activity pool: P0..P3 plus fixed-landscape slots P0_L..P3_L. */
abstract class ProxyActivity : VirtualActivity() {
    final override val proxySlot: ProxyActivitySlot
        get() = ProxyActivitySlot(ProxyActivityFamily.STANDARD, slotIndex, landscape)

    protected abstract val slotIndex: Int
    protected open val landscape: Boolean = false
}

class ProxyActivityP0 : ProxyActivity() {
    override val slotIndex = 0
}

class ProxyActivityP1 : ProxyActivity() {
    override val slotIndex = 1
}

class ProxyActivityP2 : ProxyActivity() {
    override val slotIndex = 2
}

class ProxyActivityP3 : ProxyActivity() {
    override val slotIndex = 3
}

class ProxyActivityP0_L : ProxyActivity() {
    override val slotIndex = 0
    override val landscape = true
}

class ProxyActivityP1_L : ProxyActivity() {
    override val slotIndex = 1
    override val landscape = true
}

class ProxyActivityP2_L : ProxyActivity() {
    override val slotIndex = 2
    override val landscape = true
}

class ProxyActivityP3_L : ProxyActivity() {
    override val slotIndex = 3
    override val landscape = true
}
