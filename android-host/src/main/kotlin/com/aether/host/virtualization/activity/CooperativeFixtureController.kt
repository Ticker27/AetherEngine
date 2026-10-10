package com.aether.host.virtualization.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import com.aether.guest.api.GuestEntryPoint
import com.aether.host.virtualization.loader.LoadedGuestApk
import java.util.UUID

/**
 * Owns the single cooperative fixture session and its lease.
 *
 * Responsibilities are split by concern:
 * - Lease bookkeeping (token, owner, phase) lives here.
 * - Entry-point invocation is a single `when` over [FixtureLifecycleEvent].
 * - Storage exposure is delegated to [FixtureStorageAdapter].
 *
 * All methods must run on the main thread except [revoke].
 */
class CooperativeFixtureController {
    private enum class Phase { PREPARED, CREATED, STARTED, RESUMED, PAUSED, STOPPED, CLOSED }

    private class Session(val token: String, val guest: LoadedGuestApk) {
        var entry: GuestEntryPoint? = null
        var owner: String? = null
        var phase = Phase.PREPARED
    }

    @Volatile private var session: Session? = null
    @Volatile private var stopping = false

    /** Immediate revocation from any thread; destruction is performed later on main. */
    fun revoke() { stopping = true }

    fun isLeased(): Boolean = session != null

    fun hasSession(): Boolean { mainThread(); return session != null }

    fun prepare(guest: LoadedGuestApk): String {
        mainThread()
        check(!stopping && session == null) { "Fixture controller stopped or proxy slot already leased" }
        require(guest.packageName == "com.aether.fixture" && guest.versionName == "1.0.0" &&
            guest.versionCode == 1L) { "Only the cooperative first-party fixture can use this route" }
        require(guest.supportsCooperativeDex) { "Native guest execution is unsupported" }
        val token = UUID.randomUUID().toString()
        session = Session(token, guest)
        return token
    }

    /** Acquires the lease for [owner] and creates the guest view. Failure leaves no lease behind. */
    fun create(token: String, owner: String, hostUiContext: Context, state: Bundle?): View {
        val current = requireSession(token)
        check(current.phase == Phase.PREPARED && current.owner == null) { "Fixture lease already acquired" }
        current.owner = owner
        return try {
            val type = current.guest.classLoader.loadClass("com.aether.fixture.FixtureEntryPoint")
            require(GuestEntryPoint::class.java.isAssignableFrom(type)) { "Guest API identity mismatch" }
            val entry = type.getDeclaredConstructor().newInstance() as GuestEntryPoint
            current.entry = entry
            state?.classLoader = current.guest.classLoader
            val view = entry.onCreate(hostUiContext, FixtureStorageAdapter(current.guest.fileSystem), state)
            current.phase = Phase.CREATED
            view
        } catch (failure: Throwable) {
            // Only the owner that acquired the lease may release it.
            if (current.owner == owner) releaseAcquired(current)
            throw failure
        }
    }

    /** Routes one lifecycle event to the guest. Only the lease owner may forward. */
    fun forward(
        token: String,
        owner: String,
        event: FixtureLifecycleEvent,
        state: Bundle? = null,
        intent: Intent? = null,
    ) {
        val current = requireSession(token)
        check(current.owner == owner) { "Fixture lease owner mismatch" }
        val entry = checkNotNull(current.entry) { "Fixture entry point not created" }
        when (event) {
            FixtureLifecycleEvent.STARTED -> {
                check(current.phase in setOf(Phase.CREATED, Phase.STOPPED))
                entry.onStart(); current.phase = Phase.STARTED
            }
            FixtureLifecycleEvent.RESUMED -> {
                check(current.phase in setOf(Phase.STARTED, Phase.PAUSED))
                entry.onResume(); current.phase = Phase.RESUMED
            }
            FixtureLifecycleEvent.PAUSED -> {
                check(current.phase == Phase.RESUMED)
                entry.onPause(); current.phase = Phase.PAUSED
            }
            FixtureLifecycleEvent.STOPPED -> {
                check(current.phase in setOf(Phase.STARTED, Phase.PAUSED))
                entry.onStop(); current.phase = Phase.STOPPED
            }
            FixtureLifecycleEvent.SAVE_INSTANCE_STATE -> entry.onSaveInstanceState(checkNotNull(state))
            FixtureLifecycleEvent.NEW_INTENT -> entry.onNewIntent(checkNotNull(intent))
            FixtureLifecycleEvent.RECREATE -> recreate(current, entry)
            FixtureLifecycleEvent.DESTROYED -> releaseAcquired(current)
        }
    }

    /** Releases the lease only when [token] and [owner] both match the current session. */
    fun release(token: String, owner: String) {
        mainThread()
        val current = session ?: return
        if (current.token != token || current.owner != owner) return
        releaseAcquired(current)
    }

    /** Process-level shutdown: revokes and closes whatever session exists. Final for this instance. */
    fun shutdown() {
        mainThread()
        stopping = true
        session?.let(::releaseAcquired)
    }

    /**
     * Closes any session and clears the stopped state so the controller can serve a new session.
     * Intended for tests and for an explicit host restart; production shutdown uses [shutdown].
     */
    fun reset() {
        mainThread()
        session?.let(::releaseAcquired)
        stopping = false
    }

    private fun recreate(current: Session, entry: GuestEntryPoint) {
        // Detach the owner first. If the guest fails to destroy, the session cannot be safely
        // reattached, so it is closed here instead of being left PREPARED and blocking launches.
        current.entry = null
        current.owner = null
        try {
            entry.onDestroy()
        } catch (failure: Throwable) {
            releaseAcquired(current)
            throw failure
        }
        current.phase = Phase.PREPARED
    }

    /** Closes the session and always invokes guest onDestroy exactly once. */
    private fun releaseAcquired(current: Session) {
        if (session === current) session = null
        current.phase = Phase.CLOSED
        current.owner = null
        val entry = current.entry
        current.entry = null
        runCatching { entry?.onDestroy() }
    }

    private fun requireSession(token: String): Session {
        mainThread()
        check(!stopping) { "Fixture session revoked" }
        return checkNotNull(session?.takeIf { it.token == token }) { "Fixture session missing or stale" }
    }

    private fun mainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Fixture lifecycle requires the main thread" }
    }

    companion object {
        const val EXTRA_TOKEN = "com.aether.host.fixture.SESSION"
    }
}
