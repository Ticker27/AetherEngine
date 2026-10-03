package com.aether.host.virtualization.activity

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind

/** Categories identify which manifest-declared proxy slot owns a guest Activity. */
enum class ProxyActivityFamily {
    STANDARD,
    TRANSPARENT,
    PENDING,
}

data class ProxyActivitySlot(
    val family: ProxyActivityFamily,
    val index: Int,
    val landscape: Boolean = false,
) {
    init {
        require(index in 0..3) { "Proxy Activity slot must be in the range P0..P3" }
    }

    val id: String
        get() = "${family.name.lowercase()}-p$index${if (landscape) "-landscape" else ""}"
}

/**
 * Android Activity shell used by the host's statically declared proxy components.
 * It relays Activity lifecycle events to HostInitializer. It does not instantiate an
 * arbitrary Android Activity from another APK; that requires a separate guest adapter and
 * Android component virtualization layer.
 */
abstract class VirtualActivity : Activity() {
    protected abstract val proxySlot: ProxyActivitySlot
    protected open val showUnattachedPlaceholder: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (showUnattachedPlaceholder) showPlaceholder()
        dispatch("created", savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        dispatch("started")
    }

    override fun onResume() {
        super.onResume()
        dispatch("resumed")
    }

    override fun onPause() {
        dispatch("paused")
        super.onPause()
    }

    override fun onStop() {
        dispatch("stopped")
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        dispatch("save_instance_state", outState)
    }

    override fun onDestroy() {
        dispatch("destroyed")
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatch("new_intent")
    }

    private fun dispatch(lifecycle: String, savedState: Bundle? = null) {
        (application as? AetherApplication)?.hostInitializer?.dispatch(
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
        val guestPackage = (application as? AetherApplication)
            ?.hostInitializer
            ?.loadedGuest
            ?.packageName
        val message = if (guestPackage == null) {
            "Aether virtual Activity slot ${proxySlot.id}\nNo trusted guest APK is attached."
        } else {
            "Guest code for $guestPackage is loaded.\nAn Activity adapter is required to render guest UI."
        }
        setContentView(TextView(this).apply {
            text = message
            textSize = 16f
            setPadding(32, 64, 32, 32)
        })
    }
}
