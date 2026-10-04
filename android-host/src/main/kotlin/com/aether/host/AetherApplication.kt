package com.aether.host

import android.app.Application
import android.util.Log
import com.aether.host.bootstrap.HostInitializer

/** Android process owner for the virtualization host. */
class AetherApplication : Application() {
    @Volatile
    private var initializer: HostInitializer? = null

    val hostInitializer: HostInitializer
        get() = initializer ?: synchronized(this) {
            initializer ?: HostInitializer(this).also { initializer = it }
        }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Aether host process started: pid=${android.os.Process.myPid()}")
        if (!hostInitializer.initialize()) {
            Log.e(TAG, "Host started in degraded mode; native runtime is unavailable")
        }
    }

    /** Backwards-compatible entry point used by the bootstrap Activity and tests. */
    fun tryInitializeNative(): Boolean = hostInitializer.initialize()

    override fun onTerminate() {
        try {
            initializer?.shutdown()
        } catch (error: Exception) {
            Log.e(TAG, "Host shutdown failed", error)
        } finally {
            super.onTerminate()
        }
    }

    companion object {
        private const val TAG = "AetherApplication"
    }
}
