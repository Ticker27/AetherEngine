package com.aether.host

import com.aether.host.bridge.AetherRuntimeChannel
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Normal Android launcher Activity hosting the Aether Dart application.
 *
 * FlutterActivity owns the FlutterJNI/engine lifecycle. The Aether runtime channel is
 * an additional, independent path to the host's custom libaether.so subsystem.
 */
class MainActivity : FlutterActivity() {
    private var runtimeChannel: AetherRuntimeChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val hostApplication = application as? AetherApplication ?: return
        runtimeChannel = AetherRuntimeChannel(hostApplication.hostInitializer).also { channel ->
            channel.attach(flutterEngine)
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        runtimeChannel?.detach()
        runtimeChannel = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
