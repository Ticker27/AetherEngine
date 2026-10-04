package com.aether.host.bootstrap

/**
 * Narrow host-initialization surface used by the runtime bootstrap coordinator.
 * Keeping this contract separate from Android's Application-backed implementation makes
 * lifecycle ordering testable without invoking the native runtime.
 */
interface HostRuntimeInitializer {
    fun initialize(): Boolean
    fun shutdown()
    fun addComponentListener(listener: HostComponentListener)
    fun removeComponentListener(listener: HostComponentListener)
}
