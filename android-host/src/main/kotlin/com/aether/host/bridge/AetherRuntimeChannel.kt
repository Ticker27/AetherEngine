package com.aether.host.bridge

import com.aether.host.bootstrap.HostInitializer
import com.aether.host.virtualization.components.provider.SystemCallContract
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Android side of the Dart platform-message bridge.
 *
 * Call path: Dart MethodChannel -> Flutter engine -> this handler -> HostInitializer /
 * the host's separate JNI facade. Flutter's own FlutterJNI/libflutter.so path is not
 * routed through this custom Aether JNI bridge.
 */
class AetherRuntimeChannel(
    private val hostInitializer: HostInitializer,
) {
    private var methodChannel: MethodChannel? = null

    fun attach(engine: FlutterEngine) {
        detach()
        methodChannel = MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL_NAME).also { channel ->
            channel.setMethodCallHandler { call, result -> handleMethodCall(call, result) }
        }
    }

    fun detach() {
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "version" -> result.success(Native.getVersion())
                "state" -> result.success(Native.runtimeState())
                "initialize" -> result.success(hostInitializer.initialize())
                "shutdown" -> {
                    hostInitializer.shutdown()
                    result.success(null)
                }
                "ping" -> result.success("pong:${Native.runtimeState()}")
                "dispatch" -> {
                    val method = call.argument<String>("method")
                    val payload = call.argument<String>("payload") ?: "{}"
                    // The method name must be one the host has reviewed; the contract owns the
                    // table and the native bridge never sees a caller-supplied name.
                    if (!SystemCallContract.isAccepted(method)) {
                        result.error(
                            ERROR_UNKNOWN_METHOD,
                            "method is not in the host system-call contract",
                            method,
                        )
                    } else {
                        result.success(Native.dispatchRequest(checkNotNull(method), payload))
                    }
                }
                else -> result.notImplemented()
            }
        } catch (error: LinkageError) {
            result.error(
                ERROR_NATIVE_LINK,
                "Aether native library is unavailable",
                error.javaClass.simpleName,
            )
        } catch (error: Exception) {
            result.error(
                ERROR_NATIVE_CALL,
                "Aether runtime operation failed",
                error.javaClass.simpleName,
            )
        }
    }

    companion object {
        const val CHANNEL_NAME = "aether/runtime"
        private const val ERROR_NATIVE_LINK = "UNSATISFIED_LINK"
        private const val ERROR_NATIVE_CALL = "NATIVE_ERROR"
        private const val ERROR_UNKNOWN_METHOD = "UNKNOWN_METHOD"
    }
}
