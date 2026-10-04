package com.aether.host.bootstrap

/** Lifecycle stage recorded for one manifest-declared P0..P3 proxy Activity slot. */
enum class VirtualActivitySlotPhase {
    CREATED,
    STARTED,
    RESUMED,
    PAUSED,
    STOPPED,
    DESTROYED,
}

/** Snapshot intentionally contains no Activity reference, so the registry cannot retain UI owners. */
data class VirtualActivitySlotSnapshot(
    val proxyType: String,
    val slot: Int,
    val phase: VirtualActivitySlotPhase,
    val generation: Long,
    val guestPackageName: String?,
)

/**
 * Process-local lifecycle index for proxy Activity slots. It records host-framework lifecycle
 * state only; guest Activity callbacks are not invoked until a target-specific adapter exists.
 */
internal class VirtualActivitySlotRegistry {
    private val snapshots = linkedMapOf<String, VirtualActivitySlotSnapshot>()

    private companion object {
        private val PROXY_TYPE_PATTERN = Regex("^(standard|transparent|pending)-p([0-3])(-landscape)?$")
    }

    @Synchronized
    fun record(event: HostComponentEvent) {
        if (event.kind != HostComponentKind.ACTIVITY) return
        val slot = event.slot ?: return
        if (slot !in 0..3) return

        val proxyType = event.proxyType ?: return
        val match = PROXY_TYPE_PATTERN.matchEntire(proxyType) ?: return
        if (match.groupValues[2].toInt() != slot) return
        if (match.groupValues[1] != "standard" && match.groupValues[3].isNotEmpty()) return
        val previous = snapshots[proxyType]
        val phase = event.lifecycle.toPhase() ?: return
        val generation = if (event.lifecycle == "created") {
            (previous?.generation ?: 0L) + 1L
        } else {
            previous?.generation ?: 1L
        }
        snapshots[proxyType] = VirtualActivitySlotSnapshot(
            proxyType = proxyType,
            slot = slot,
            phase = phase,
            generation = generation,
            guestPackageName = event.guestPackageName ?: previous?.guestPackageName,
        )
    }

    @Synchronized
    fun snapshot(): List<VirtualActivitySlotSnapshot> = snapshots.values
        .sortedWith(compareBy(VirtualActivitySlotSnapshot::slot, VirtualActivitySlotSnapshot::proxyType))

    @Synchronized
    fun clear() = snapshots.clear()

    private fun String.toPhase(): VirtualActivitySlotPhase? = when (this) {
        "created" -> VirtualActivitySlotPhase.CREATED
        "started" -> VirtualActivitySlotPhase.STARTED
        "resumed" -> VirtualActivitySlotPhase.RESUMED
        "paused" -> VirtualActivitySlotPhase.PAUSED
        "stopped" -> VirtualActivitySlotPhase.STOPPED
        "destroyed" -> VirtualActivitySlotPhase.DESTROYED
        else -> null
    }
}
