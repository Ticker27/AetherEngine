package com.aether.host

import android.app.Application
import android.util.Log

/**
 * Application lifecycle owner — no business logic.
 *
 * Responsibilities:
 * - Process-level initialization
 * - Lazy native initialization (guarded)
 * - Prepare FlutterEngine in Phase 3 via AetherFlutterHost
 */
class AetherApplication : Application() {

    companion object {
        private const val TAG = "AetherApp"
    }

    private var isNativeInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AetherApplication.onCreate() — process=${android.os.Process.myPid()}")

        // Phase 1: eager init disabled — let MainActivity or FlutterHost decide
        // Phase 3: AetherFlutterHost warmup will be here
        // tryInitializeNative()
    }

    /**
     * Explicit native init — called by HostInitializer or FlutterHost.
     * Guarded to prevent double init.
     */
    @Synchronized
    fun tryInitializeNative(): Boolean {
        if (isNativeInitialized) {
            Log.w(TAG, "Native already initialized — skipping")
            return true
        }
        return try {
            val result = Native.initialize()
            isNativeInitialized = result
            Log.i(TAG, "Native.initialize() -> $result, version=${Native.getVersion()}, state=${Native.runtimeState()}")
            result
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libaether.so", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Native.initialize() failed", e)
            false
        }
    }

    override fun onTerminate() {
        try {
            if (isNativeInitialized) {
                Native.shutdown()
                Log.i(TAG, "Native.shutdown() completed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shutdown error", e)
        } finally {
            super.onTerminate()
        }
    }
}
