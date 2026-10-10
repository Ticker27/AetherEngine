package com.aether.host

import android.app.Application
import android.util.Log
import com.aether.host.bootstrap.HostInitializer
import com.aether.host.runtime.AetherRuntime

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
        if (!AetherRuntime.bootstrap(this)) {
            Log.e(TAG, "Host started in degraded mode; native runtime is unavailable")
        }
    }

    /** Backwards-compatible entry point used by the bootstrap Activity and tests. */
    fun tryInitializeNative(): Boolean = hostInitializer.initialize()

    override fun onTerminate() {
        try {
            AetherRuntime.shutdown()
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
