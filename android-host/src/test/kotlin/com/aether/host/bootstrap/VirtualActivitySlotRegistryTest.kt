package com.aether.host.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualActivitySlotRegistryTest {
    @Test
    fun `records guest proxy lifecycle for P0 through P3 without retaining owner`() {
        val registry = VirtualActivitySlotRegistry()
        val activity = Any()
        val lifecycle = listOf(
            "created" to VirtualActivitySlotPhase.CREATED,
            "started" to VirtualActivitySlotPhase.STARTED,
            "resumed" to VirtualActivitySlotPhase.RESUMED,
            "paused" to VirtualActivitySlotPhase.PAUSED,
            "stopped" to VirtualActivitySlotPhase.STOPPED,
            "destroyed" to VirtualActivitySlotPhase.DESTROYED,
        )

        lifecycle.forEach { (eventName, expectedPhase) ->
            registry.record(event(activity, eventName, slot = 0, proxyType = "standard-p0"))
            val snapshot = registry.snapshot().single()
            assertEquals(expectedPhase, snapshot.phase)
            assertEquals(0, snapshot.slot)
            assertEquals("com.miniclip.eightballpool", snapshot.guestPackageName)
        }

        registry.record(event(activity, "created", slot = 3, proxyType = "standard-p3-landscape"))
        assertEquals(listOf(0, 3), registry.snapshot().map(VirtualActivitySlotSnapshot::slot))
        registry.clear()
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun `tracks proxy families independently and increments recreation generations`() {
        val registry = VirtualActivitySlotRegistry()
        val activity = Any()

        registry.record(event(activity, "created", slot = 1, proxyType = "standard-p1"))
        registry.record(event(activity, "created", slot = 1, proxyType = "transparent-p1"))
        registry.record(event(activity, "destroyed", slot = 1, proxyType = "standard-p1"))
        registry.record(event(activity, "created", slot = 1, proxyType = "standard-p1"))

        val states = registry.snapshot().associateBy(VirtualActivitySlotSnapshot::proxyType)
        assertEquals(2, states.size)
        assertEquals(2L, states.getValue("standard-p1").generation)
        assertEquals(VirtualActivitySlotPhase.DESTROYED, states.getValue("transparent-p1").phase)
    }

    @Test
    fun `ignores non-activity events and invalid slots`() {
        val registry = VirtualActivitySlotRegistry()
        registry.record(
            HostComponentEvent(
                owner = Any(),
                kind = HostComponentKind.SERVICE,
                slot = 0,
                lifecycle = "created",
            ),
        )
        registry.record(event(Any(), "created", slot = 4, proxyType = "standard-p4"))
        registry.record(event(Any(), "created", slot = 2, proxyType = "standard-p1"))
        registry.record(event(Any(), "created", slot = 2, proxyType = "custom-slot"))

        assertTrue(registry.snapshot().isEmpty())
    }

    private fun event(
        owner: Any,
        lifecycle: String,
        slot: Int,
        proxyType: String,
    ) = HostComponentEvent(
        owner = owner,
        kind = HostComponentKind.ACTIVITY,
        slot = slot,
        lifecycle = lifecycle,
        proxyType = proxyType,
        guestPackageName = "com.miniclip.eightballpool",
    )
}
