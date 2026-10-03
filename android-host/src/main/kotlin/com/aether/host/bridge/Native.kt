package com.aether.host.bridge

import androidx.annotation.Keep

/**
 * Native contract — JNI bridge to libaether.so
 *
 * Phase 1 Bootstrap:
 * - System.loadLibrary("aether") in init
 * - JNI_OnLoad -> RegisterNatives
 * - 4 methods grouped: lifecycle (initialize, shutdown), runtime (getVersion, runtimeState)
 *
 * Thread-safety: native side guards state machine with mutex.
 * No business logic here — pure JNI declaration.
 */
@Keep
object Native {

    init {
        System.loadLibrary("aether")
    }

    /**
     * Lifecycle: NEW -> INITIALIZED
     * Returns false if already initialized or native init fails.
     */
    @Keep
    @JvmStatic
    external fun initialize(): Boolean

    /**
     * Lifecycle: INITIALIZED/RUNNING -> STOPPING -> STOPPED
     * Idempotent — safe to call multiple times.
     */
    @Keep
    @JvmStatic
    external fun shutdown()

    /**
     * Runtime diagnostic — never returns null.
     */
    @Keep
    @JvmStatic
    external fun getVersion(): String

    /**
     * Runtime state query — returns one of:
     * "new", "initialized", "running", "stopping", "stopped"
     * Never returns null.
     */
    @Keep
    @JvmStatic
    external fun runtimeState(): String
}
