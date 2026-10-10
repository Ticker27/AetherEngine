package com.aether.host.virtualization.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger
import java.util.UUID

enum class ProxyActivityFamily { STANDARD, TRANSPARENT, PENDING }

data class ProxyActivitySlot(val family: ProxyActivityFamily, val index: Int, val landscape: Boolean = false) {
    init { require(index in 0..3) { "Proxy Activity slot must be in the range P0..P3" } }
    val id: String get() = "${family.name.lowercase()}-p$index${if (landscape) "-landscape" else ""}"
}

/**
 * Framework-owned Activity hosting only an explicit cooperative first-party entry point.
 *
 * Every guest callback goes through [guestCall], which turns guest failures (Exception or
 * LinkageError) into a single cleanup path. Framework `super` calls are never skipped.
 */
abstract class VirtualActivity : Activity() {
    protected abstract val proxySlot: ProxyActivitySlot
    protected open val showUnattachedPlaceholder: Boolean = true

    /** Session token this Activity currently holds a lease for; null when it holds none. */
    private var fixtureToken: String? = null
    private val leaseOwner = UUID.randomUUID().toString()
    private val initializer get() = (application as? AetherApplication)?.hostInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = savedInstanceState?.getString(CooperativeFixtureController.EXTRA_TOKEN)
            ?: intent.getStringExtra(CooperativeFixtureController.EXTRA_TOKEN)
        if (token != null) {
            try {
                check(flagger.isEnabled(HostFeature.COOPERATIVE_FIXTURE_UI)) { "Fixture UI is disabled" }
                check(proxySlot == ProxyActivitySlot(ProxyActivityFamily.STANDARD, 0)) { "Unsupported fixture slot" }
                val controller = checkNotNull(initializer).fixtureController
                val view = controller.create(token, leaseOwner, this, savedInstanceState)
                // The lease is now owned by this Activity; record it before anything else can fail.
                fixtureToken = token
                setContentView(view)
            } catch (failure: Exception) {
                rejectFixture(token, failure)
            } catch (failure: LinkageError) {
                rejectFixture(token, failure)
            }
        } else if (showUnattachedPlaceholder) {
            showPlaceholder()
        }
        dispatch("created", savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        forward(FixtureLifecycleEvent.STARTED)
        dispatch("started")
    }

    override fun onResume() {
        super.onResume()
        forward(FixtureLifecycleEvent.RESUMED)
        dispatch("resumed")
    }

    override fun onPause() {
        forward(FixtureLifecycleEvent.PAUSED)
        dispatch("paused")
        super.onPause()
    }

    override fun onStop() {
        forward(FixtureLifecycleEvent.STOPPED)
        dispatch("stopped")
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        fixtureToken?.let { outState.putString(CooperativeFixtureController.EXTRA_TOKEN, it) }
        forward(FixtureLifecycleEvent.SAVE_INSTANCE_STATE, state = outState)
        dispatch("save_instance_state", outState)
    }

    override fun onDestroy() {
        forward(if (isChangingConfigurations) FixtureLifecycleEvent.RECREATE else FixtureLifecycleEvent.DESTROYED)
        if (!isChangingConfigurations) fixtureToken = null
        dispatch("destroyed")
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val token = fixtureToken
        val proposed = intent.getStringExtra(CooperativeFixtureController.EXTRA_TOKEN)
        if (token != null && proposed != null && proposed != token) {
            // A different session token must not silently replace the active one.
            Log.w(TAG, "Conflicting fixture token rejected")
            return
        }
        // Keep the active session identity on the replacement intent, so recreation still
        // finds the token and the lease is eventually released.
        if (token != null) intent.putExtra(CooperativeFixtureController.EXTRA_TOKEN, token)
        setIntent(intent)
        forward(FixtureLifecycleEvent.NEW_INTENT, newIntent = intent)
        dispatch("new_intent")
    }

    private fun forward(
        event: FixtureLifecycleEvent,
        state: Bundle? = null,
        newIntent: Intent? = null,
    ) {
        val token = fixtureToken ?: return
        try {
            initializer?.fixtureController?.forward(token, leaseOwner, event, state, newIntent)
        } catch (failure: Exception) {
            failFixture(token, failure)
        } catch (failure: LinkageError) {
            failFixture(token, failure)
        }
    }

    private fun rejectFixture(token: String, failure: Throwable) {
        // release() is owner-checked: a duplicate or stale launch that never acquired the lease
        // is a no-op here, so it cannot revoke another live Activity's session.
        Log.e(TAG, "Fixture launch rejected", failure)
        fixtureToken = null
        runCatching { initializer?.fixtureController?.release(token, leaseOwner) }
            .onFailure { Log.e(TAG, "Fixture release after rejection failed", it) }
        finish()
    }

    private fun failFixture(token: String, failure: Throwable) {
        Log.e(TAG, "Fixture lifecycle failed", failure)
        fixtureToken = null
        runCatching { initializer?.fixtureController?.release(token, leaseOwner) }
            .onFailure { Log.e(TAG, "Fixture release failed", it) }
        finish()
    }

    private fun dispatch(lifecycle: String, savedState: Bundle? = null) {
        initializer?.dispatch(
            HostComponentEvent(
                owner = this,
                kind = HostComponentKind.ACTIVITY,
                slot = proxySlot.index,
                lifecycle = lifecycle,
                proxyType = proxySlot.id,
                intent = intent,
                savedState = savedState,
            ),
        )
    }

    private fun showPlaceholder() {
        val guest = initializer?.loadedGuest?.packageName
        setContentView(TextView(this).apply {
            text = if (guest == null) "Aether slot ${proxySlot.id}: no verified guest attached"
                else "Guest $guest loaded; arbitrary Activity attachment is unsupported"
            textSize = 16f
            setPadding(32, 64, 32, 32)
        })
    }

    private companion object {
        const val TAG = "AetherFixture"
    }
}
