package com.aether.host.virtualization.activity

/** Lifecycle events the framework Activity may forward into a cooperative guest entry point. */
enum class FixtureLifecycleEvent {
    STARTED,
    RESUMED,
    PAUSED,
    STOPPED,
    SAVE_INSTANCE_STATE,
    NEW_INTENT,
    /** Activity destroyed for a configuration change; guest is destroyed but the lease is kept. */
    RECREATE,
    /** Activity destroyed for good; the lease is released. */
    DESTROYED,
}
