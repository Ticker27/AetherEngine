package com.aether.host.bootstrap

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.aether.host.bridge.Native
import com.aether.host.BuildConfig
import com.aether.host.target.TargetApkContract
import com.aether.host.virtualization.activity.CooperativeFixtureController
import com.aether.host.virtualization.activity.ProxyActivityP0
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger
import com.aether.host.virtualization.loader.DynamicApkLoader
import com.aether.host.virtualization.loader.GuestApkSet
import com.aether.host.virtualization.loader.GuestApkTrustPolicy
import com.aether.host.virtualization.loader.LoadedGuestApk
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

/** Lifecycle stages reported by the host's proxy Android components. */
enum class HostComponentKind {
    HOST,
    ACTIVITY,
    SERVICE,
    JOB_SERVICE,
    BROADCAST_RECEIVER,
    CONTENT_PROVIDER,
    VPN_SERVICE,
}

data class HostComponentEvent(
    /** The framework component instance. Listeners must not retain it past DESTROY/STOP. */
    val owner: Any,
    val kind: HostComponentKind,
    val slot: Int?,
    val lifecycle: String,
    val proxyType: String? = null,
    val jobId: Int? = null,
    val intent: Intent? = null,
    val savedState: Bundle? = null,
    val guestPackageName: String? = null,
)

fun interface HostComponentListener {
    fun onComponentEvent(event: HostComponentEvent)
}

enum class HostInitializerState {
    NEW,
    READY,
    FAILED,
    STOPPED,
}

/**
 * Process-level owner for Aether's custom native runtime, trusted guest code loader,
 * and proxy-component lifecycle events. This is not an Android sandbox: any loaded guest
 * executes with the host process identity and permissions.
 */
class HostInitializer(application: Application) : HostRuntimeInitializer {
    private val appContext = application.applicationContext
    private val listeners = CopyOnWriteArraySet<HostComponentListener>()
    private val activitySlots = VirtualActivitySlotRegistry()
    private val guestLoaders = mutableMapOf<String, DynamicApkLoader>()
    val fixtureController = CooperativeFixtureController()

    @Volatile
    var state: HostInitializerState = HostInitializerState.NEW
        private set

    @Volatile
    var loadedGuest: LoadedGuestApk? = null
        private set

    @Synchronized
    override fun initialize(): Boolean {
        when (state) {
            HostInitializerState.READY -> return true
            HostInitializerState.STOPPED -> return false
            HostInitializerState.NEW, HostInitializerState.FAILED -> Unit
        }

        return try {
            val initialized = Native.initialize()
            val runtimeState = Native.runtimeState()
            if (initialized || runtimeState == "initialized" || runtimeState == "running") {
                state = HostInitializerState.READY
                Log.i(TAG, "Host ready: version=${Native.getVersion()}, runtime=$runtimeState")
                true
            } else {
                state = HostInitializerState.FAILED
                Log.e(TAG, "Native runtime rejected initialization: $runtimeState")
                false
            }
        } catch (error: LinkageError) {
            state = HostInitializerState.FAILED
            Log.e(TAG, "Could not load the Aether native library", error)
            false
        } catch (error: Exception) {
            state = HostInitializerState.FAILED
            Log.e(TAG, "Host initialization failed", error)
            false
        }
    }

    /**
     * Loads the selected 8 Ball Pool 56.31.0 APK only when the process-local feature switch
     * is enabled and explicit signer pins are supplied. Call on a worker thread.
     */
    fun loadTargetApk(apkFile: File, trustPolicy: GuestApkTrustPolicy): LoadedGuestApk =
        loadTargetApk(GuestApkSet(apkFile), trustPolicy)

    /** Package-set loading is not an arbitrary Android Activity launch capability. */
    @Synchronized
    fun loadTargetApk(apkSet: GuestApkSet, trustPolicy: GuestApkTrustPolicy): LoadedGuestApk {
        check(state == HostInitializerState.READY) { "Host must be initialized before loading a guest" }
        check(flagger.isEnabled(HostFeature.DYNAMIC_APK_LOADING)) {
            "Dynamic APK loading is disabled"
        }

        require(trustPolicy.expectedPackageName == TargetApkContract.PACKAGE_NAME &&
            trustPolicy.expectedVersionName == TargetApkContract.VERSION_NAME &&
            trustPolicy.expectedVersionCode == TargetApkContract.VERSION_CODE) {
            "Target route requires the exact target trust profile"
        }
        return loadGuest(apkSet, trustPolicy)
    }

    /** Debug-only first-party loader route; no arbitrary app or native Activity attachment. */
    @Synchronized
    fun loadCooperativeFixtureApk(apkFile: File, policy: GuestApkTrustPolicy): LoadedGuestApk {
        check(BuildConfig.DEBUG) { "Fixture UI is unavailable in release builds" }
        check(state == HostInitializerState.READY) { "Host native bootstrap must be ready" }
        check(flagger.isEnabled(HostFeature.DYNAMIC_APK_LOADING)) { "Dynamic APK loading is disabled" }
        require(policy.expectedPackageName == "com.aether.fixture" &&
            policy.expectedVersionName == "1.0.0" && policy.expectedVersionCode == 1L) {
            "Fixture route requires its explicit first-party profile"
        }
        return loadGuest(GuestApkSet(apkFile), policy)
    }

    /** Called on main after a verified fixture was loaded on a worker. */
    @Synchronized
    fun prepareFixtureLaunch(guest: LoadedGuestApk): Intent {
        check(BuildConfig.DEBUG && flagger.isEnabled(HostFeature.COOPERATIVE_FIXTURE_UI)) {
            "Cooperative fixture UI is disabled"
        }
        check(state == HostInitializerState.READY && loadedGuest === guest) { "Guest session is not ready" }
        val token = fixtureController.prepare(guest)
        return Intent(appContext, ProxyActivityP0::class.java)
            .putExtra(CooperativeFixtureController.EXTRA_TOKEN, token)
    }

    private fun loadGuest(apkSet: GuestApkSet, policy: GuestApkTrustPolicy): LoadedGuestApk {
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { "Guest APK IO requires a worker thread" }
        check(!fixtureController.isLeased()) { "Cannot replace a guest while a proxy lease is active" }
        val key = listOf(policy.expectedPackageName, policy.expectedVersionName,
            policy.expectedVersionCode.toString(), policy.trustedSignerSha256.sorted().joinToString(","))
            .joinToString("|")
        val loader = guestLoaders.getOrPut(key) { DynamicApkLoader(appContext, policy) }
        val guest = loader.load(apkSet)
        loadedGuest = guest
        dispatch(
            HostComponentEvent(
                owner = this,
                kind = HostComponentKind.HOST,
                slot = null,
                lifecycle = EVENT_GUEST_LOADED,
                guestPackageName = guest.packageName,
            ),
        )
        return guest
    }

    override fun addComponentListener(listener: HostComponentListener) {
        listeners += listener
    }

    override fun removeComponentListener(listener: HostComponentListener) {
        listeners -= listener
    }

    /** Returns lifecycle snapshots for the fixed P0..P3 proxy pools without retaining Activities. */
    fun virtualActivitySlotSnapshots(): List<VirtualActivitySlotSnapshot> = activitySlots.snapshot()

    /** Called by the non-exported proxy components to relay lifecycle into the host. */
    fun dispatch(event: HostComponentEvent) {
        val enrichedEvent = if (event.guestPackageName == null) {
            event.copy(guestPackageName = loadedGuest?.packageName)
        } else {
            event
        }
        activitySlots.record(enrichedEvent)
        listeners.forEach { listener ->
            try {
                listener.onComponentEvent(enrichedEvent)
            } catch (error: Exception) {
                Log.e(TAG, "Host component listener failed for ${event.lifecycle}", error)
            }
        }
    }

    @Synchronized
    override fun shutdown() {
        if (state == HostInitializerState.STOPPED) return
        loadedGuest?.let { guest ->
            dispatch(
                HostComponentEvent(
                    owner = this,
                    kind = HostComponentKind.HOST,
                    slot = null,
                    lifecycle = EVENT_GUEST_UNLOADED,
                    guestPackageName = guest.packageName,
                ),
            )
        }
        // Revoke synchronously so no new guest callback or acquisition can start, even before the
        // posted teardown below runs on main.
        fixtureController.revoke()
        val cleanup = Runnable {
            runCatching { fixtureController.shutdown() }
                .onFailure { Log.e(TAG, "Fixture cleanup failed after lease revocation", it) }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) cleanup.run()
        else android.os.Handler(android.os.Looper.getMainLooper()).post(cleanup)
        loadedGuest = null
        guestLoaders.clear()
        activitySlots.clear()

        try {
            if (state == HostInitializerState.READY) Native.shutdown()
        } catch (error: LinkageError) {
            Log.w(TAG, "Native shutdown failed", error)
        } catch (error: Exception) {
            Log.w(TAG, "Native shutdown failed", error)
        } finally {
            state = HostInitializerState.STOPPED
        }
    }

    companion object {
        private const val TAG = "HostInitializer"
        const val EVENT_GUEST_LOADED = "guest_loaded"
        const val EVENT_GUEST_UNLOADED = "guest_unloaded"
    }
}
