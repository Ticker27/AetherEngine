package com.aether.host.virtualization.components.provider

/**
 * Reserved internal provider endpoint. It exposes no call history or device data; all
 * CRUD operations remain empty until a narrowly scoped, permission-reviewed contract exists.
 */
class SystemCallProvider : ProxyContentProvider() {
    override val slotIndex: Int? = null
    override val providerKind: String = "system-call"
}
