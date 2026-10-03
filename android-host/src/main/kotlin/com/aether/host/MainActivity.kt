package com.aether.host

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView
import com.aether.host.bridge.Native

/** Entry point for the host container; guest components run only through private proxies. */
class MainActivity : Activity() {
    private var flutterHost: AetherFlutterHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity.onCreate()")

        val root = FrameLayout(this)
        val textView = TextView(this).apply {
            textSize = 14f
            setPadding(32, 64, 32, 32)
            text = buildHostStatus()
        }
        root.addView(textView)
        setContentView(root)
    }

    private fun buildHostStatus(): String {
        val app = application as? AetherApplication
            ?: return "Aether host application is unavailable"
        val initialized = app.tryInitializeNative()
        val initializer = app.hostInitializer
        val version = try {
            Native.getVersion()
        } catch (error: LinkageError) {
            "unavailable: ${error.message}"
        } catch (error: Exception) {
            "unavailable: ${error.message}"
        }
        val nativeState = try {
            Native.runtimeState()
        } catch (error: LinkageError) {
            "unavailable: ${error.message}"
        } catch (error: Exception) {
            "unavailable: ${error.message}"
        }
        val guestPackage = initializer.loadedGuest?.packageName ?: "none"

        return """
            Aether Host Container
            ────────────────────
            hostState -> ${initializer.state}
            initialized -> $initialized
            nativeVersion -> $version
            nativeState -> $nativeState
            loadedGuest -> $guestPackage

            Guest APK loading is disabled by default and requires a trusted signer pin.
        """.trimIndent()
    }

    override fun onDestroy() {
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

    companion object {
        private const val TAG = "MainActivity"
    }
}
