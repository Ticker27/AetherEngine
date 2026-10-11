package com.aether.host.bridge

import androidx.annotation.Keep

/**
 * Native contract — JNI bridge to libaether.so
 *
 * Aether's custom JNI lane, independent from FlutterJNI/libflutter.so:
 * - System.loadLibrary("aether") loads libaether.so
 * - JNI_OnLoad -> RegisterNatives
 * - Four verified host methods: lifecycle (initialize, shutdown), runtime (getVersion, runtimeState)
 * - One dispatch method: routing a named request into the native MessageBridge
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

    /**
     * Routes one named request into the native MessageBridge and returns its JSON response.
     *
     * The method name is validated against the closed [SystemCallContract] table before it ever
     * reaches here, so the native side never receives a caller-supplied method name that the host
     * has not reviewed. Never returns null; a failure is reported as a JSON error object.
     */
    @Keep
    @JvmStatic
    external fun dispatchRequest(method: String, payloadJson: String): String
}
