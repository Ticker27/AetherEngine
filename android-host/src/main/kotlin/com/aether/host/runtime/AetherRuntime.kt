package com.aether.host.runtime

import android.content.Context
import android.util.Log
import com.aether.host.bootstrap.HostInitializer
import com.aether.host.bootstrap.HostRuntimeInitializer
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger

/**
 * AetherRuntime — bootstrap coordinator (Phase 2)
 *
 * Registers the three host modules in a fixed order:
 *
 *   1. LoaderModule  — guest APK loading (DynamicApkLoader path)
 *   2. NativeModule  — libaether.so runtime state (initialize/shutdown)
 *   3. HostModule    — proxy component lifecycle (VirtualActivity/HostLifecycle)
 *
 * This class does not replace [HostInitializer]; it composes it.
 */
object AetherRuntime {
    private const val TAG = "AetherRuntime"

    enum class Module { LOADER, NATIVE, HOST }

    enum class RuntimeState { NEW, BOOTING, READY, FAILED, STOPPED }

    @Volatile
    var state: RuntimeState = RuntimeState.NEW
        private set

    @Volatile
    private var bootContext: Context? = null

    private val registeredModules = LinkedHashSet<Module>()

    @Synchronized
    fun bootstrap(context: Context): Boolean {
        when (state) {
            RuntimeState.READY -> return true
            RuntimeState.STOPPED -> return false
            RuntimeState.NEW, RuntimeState.FAILED -> Unit
            RuntimeState.BOOTING -> return false
        }
        state = RuntimeState.BOOTING
        registeredModules.clear()
        bootContext = context.applicationContext

        Log.i(TAG, "bootstrap: sequence start (pid=${android.os.Process.myPid()})")

        runCatching {
            require(HostFeature.entries.contains(HostFeature.DYNAMIC_APK_LOADING))
            registeredModules.add(Module.LOADER)
            Log.i(TAG, "bootstrap: LOADER registered (dynamicApkLoading=${flagger.isEnabled(HostFeature.DYNAMIC_APK_LOADING)})")
        }.onFailure { error -> return fail("LOADER", error) }

        val initializer = runtimeInitializer()
        if (!initializer.initialize()) {
            return fail("NATIVE", IllegalStateException("HostInitializer.initialize() failed"))
        }
        registeredModules.add(Module.NATIVE)
        Log.i(TAG, "bootstrap: NATIVE registered")

        HostLifecycle.attach(initializer)
        registeredModules.add(Module.HOST)
        Log.i(TAG, "bootstrap: HOST registered")

        state = RuntimeState.READY
        Log.i(TAG, "bootstrap: sequence complete — modules=$registeredModules")
        return true
    }

    fun registeredModules(): List<Module> = registeredModules.toList()
    fun applicationContext(): Context? = bootContext

    @Synchronized
    fun shutdown() {
        if (state == RuntimeState.STOPPED || state == RuntimeState.NEW) return
        Log.i(TAG, "shutdown: sequence start")
        if (Module.HOST in registeredModules) {
            HostLifecycle.detach()
            registeredModules.remove(Module.HOST)
        }
        if (Module.NATIVE in registeredModules) {
            runtimeInitializer().shutdown()
            registeredModules.remove(Module.NATIVE)
        }
        registeredModules.remove(Module.LOADER)
        state = RuntimeState.STOPPED
        Log.i(TAG, "shutdown: sequence complete")
    }

    private val defaultInitializerProvider: () -> HostRuntimeInitializer = {
        val ctx = checkNotNull(bootContext) { "bootstrap context must not be null" }
        (ctx.applicationContext as? com.aether.host.AetherApplication)?.hostInitializer
            ?: HostInitializer(ctx.applicationContext as android.app.Application)
    }

    internal var initializerProvider: () -> HostRuntimeInitializer = defaultInitializerProvider

    private fun runtimeInitializer(): HostRuntimeInitializer = initializerProvider()

    @Synchronized
    internal fun resetForTesting() {
        HostLifecycle.detach()
        registeredModules.clear()
        bootContext = null
        state = RuntimeState.NEW
        initializerProvider = defaultInitializerProvider
    }

    private fun fail(module: String, error: Throwable): Boolean {
        state = RuntimeState.FAILED
        Log.e(TAG, "bootstrap: $module registration failed", error)
        return false
    }
}
