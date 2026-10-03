package com.aether.host

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Activity = lifecycle owner only.
 * No business logic — delegates to AetherFlutterHost in Phase 3.
 *
 * Phase 1: Shows native version and runtime state for bootstrap verification.
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var flutterHost: AetherFlutterHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity.onCreate()")

        // Phase 1 verification UI — will be replaced by Flutter in Phase 3
        val root = FrameLayout(this)
        val textView = TextView(this).apply {
            textSize = 14f
            setPadding(32, 64, 32, 32)
            text = buildDebugInfo()
        }
        root.addView(textView)
        setContentView(root)

        // Phase 3: attach Flutter
        // flutterHost = AetherFlutterHost(this)
        // flutterHost?.attach(this)
    }

    private fun buildDebugInfo(): String {
        return try {
            val app = application as? AetherApplication
            val initResult = app?.tryInitializeNative() ?: run {
                try {
                    Native.initialize()
                } catch (e: Exception) {
                    false
                }
            }
            val version = try { Native.getVersion() } catch (e: Exception) { "unavailable: ${e.message}" }
            val state = try { Native.runtimeState() } catch (e: Exception) { "error: ${e.message}" }

            """
            AetherEngine — Phase 1 Bootstrap
            ───────────────────────────────
            initialize() -> $initResult
            getVersion() -> $version
            runtimeState() -> $state

            Next: Phase 3 FlutterEngine + MethodChannel("aether/runtime")
            """.trimIndent()
        } catch (e: Exception) {
            "Bootstrap failed: ${e.message}\n${Log.getStackTraceString(e)}"
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "MainActivity.onDestroy()")
        flutterHost?.detach()
        flutterHost = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        flutterHost?.onResume()
    }

    override fun onPause() {
        flutterHost?.onPause()
        super.onPause()
    }
}
