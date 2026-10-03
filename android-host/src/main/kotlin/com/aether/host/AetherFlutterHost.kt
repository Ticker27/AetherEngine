package com.aether.host

import android.content.Context
import android.util.Log
import com.aether.host.bridge.Native

/**
 * Flutter add-to-app host — Phase 1 version compiles without Flutter SDK.
 *
 * Design for CI safety:
 * - No direct import of io.flutter.* to allow android-host:assembleDebug without Flutter SDK
 * - Uses reflection to load FlutterEngine and MethodChannel when Flutter is available (Phase 3)
 * - Fallback to no-op with logging when Flutter not present — ensures Phase 1 CI passes
 *
 * Phase 3 will replace reflection with direct Flutter embedding:
 *   FlutterEngine, DartExecutor, MethodChannel("aether/runtime")
 *
 * Architecture (final):
 * Dart (MethodChannel "aether/runtime") -> Kotlin bridge -> JNI -> libaether.so
 */
class AetherFlutterHost(
    private val context: Context
) {
    companion object {
        private const val TAG = "AetherFlutterHost"
        const val CHANNEL_NAME = "aether/runtime"
        private const val FLUTTER_ENGINE_CLASS = "io.flutter.embedding.engine.FlutterEngine"
        private const val FLUTTER_DART_ENTRYPOINT_CLASS = "io.flutter.embedding.engine.dart.DartExecutor\$DartEntrypoint"
        private const val FLUTTER_METHOD_CHANNEL_CLASS = "io.flutter.plugin.common.MethodChannel"
    }

    // Holds FlutterEngine as Any? to avoid direct dependency
    private var flutterEngineRef: Any? = null
    private var methodChannelRef: Any? = null
    private var isFlutterAvailable: Boolean = false

    init {
        isFlutterAvailable = checkFlutterAvailability()
        Log.i(TAG, "Flutter available: $isFlutterAvailable")
    }

    private fun checkFlutterAvailability(): Boolean {
        return try {
            Class.forName(FLUTTER_ENGINE_CLASS)
            true
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "FlutterEngine not found — running in Phase 1 stub mode (expected before Phase 3)")
            false
        }
    }

    /**
     * Create and warm up FlutterEngine via reflection if available.
     * Phase 1: returns no-op and logs.
     * Phase 3: real FlutterEngine creation.
     */
    @Synchronized
    fun warmup(): Any? {
        flutterEngineRef?.let { return it }

        if (!isFlutterAvailable) {
            Log.i(TAG, "warmup() stub — Flutter not available in Phase 1")
            return null
        }

        return try {
            val engineClass = Class.forName(FLUTTER_ENGINE_CLASS)
            val engineConstructor = engineClass.getConstructor(Context::class.java)
            val engine = engineConstructor.newInstance(context.applicationContext)

            // DartExecutor entrypoint via reflection
            val dartExecutorField = engineClass.getMethod("getDartExecutor")
            val dartExecutor = dartExecutorField.invoke(engine)

            val dartEntrypointClass = Class.forName(FLUTTER_DART_ENTRYPOINT_CLASS)
            val createDefaultMethod = dartEntrypointClass.getMethod("createDefault")
            val entrypoint = createDefaultMethod.invoke(null)

            val executeMethod = dartExecutor.javaClass.getMethod(
                "executeDartEntrypoint",
                dartEntrypointClass
            )
            executeMethod.invoke(dartExecutor, entrypoint)

            // Register channels
            val binaryMessengerMethod = dartExecutor.javaClass.getMethod("getBinaryMessenger")
            val messenger = binaryMessengerMethod.invoke(dartExecutor)
            registerChannelsReflective(messenger)

            flutterEngineRef = engine
            Log.i(TAG, "FlutterEngine warmed up via reflection: ${engine.hashCode()}")
            engine
        } catch (e: Exception) {
            Log.e(TAG, "Failed to warmup FlutterEngine via reflection", e)
            null
        }
    }

    fun attach(activityContext: Context): Any? {
        Log.i(TAG, "attach() called from ${activityContext.javaClass.simpleName}")
        return warmup()
    }

    fun detach() {
        Log.i(TAG, "detach() — clearing MethodChannel handler via reflection if available")
        try {
            if (methodChannelRef != null) {
                val setHandlerMethod = methodChannelRef!!.javaClass.getMethod(
                    "setMethodCallHandler",
                    Class.forName("io.flutter.plugin.common.MethodChannel\$MethodCallHandler")
                )
                setHandlerMethod.invoke(methodChannelRef, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "detach() reflection failed (expected in stub mode): ${e.message}")
        } finally {
            methodChannelRef = null
        }
    }

    fun destroy() {
        Log.i(TAG, "destroy() FlutterEngine")
        try {
            flutterEngineRef?.let { engine ->
                val destroyMethod = engine.javaClass.getMethod("destroy")
                destroyMethod.invoke(engine)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying engine", e)
        } finally {
            flutterEngineRef = null
            methodChannelRef = null
        }
    }

    fun onResume() {
        Log.d(TAG, "onResume")
    }

    fun onPause() {
        Log.d(TAG, "onPause")
    }

    /**
     * Reflection-based MethodChannel registration.
     * When Flutter is unavailable, this is no-op.
     * When available, registers handler for:
     * - version -> Native.getVersion()
     * - state -> Native.runtimeState()
     * - initialize -> Native.initialize()
     * - shutdown -> Native.shutdown()
     * - ping -> pong:state
     */
    private fun registerChannelsReflective(messenger: Any?) {
        if (messenger == null) {
            Log.w(TAG, "BinaryMessenger null — cannot register channels")
            return
        }

        if (!isFlutterAvailable) {
            Log.i(TAG, "registerChannels() stub — Flutter not available")
            return
        }

        try {
            val methodChannelClass = Class.forName(FLUTTER_METHOD_CHANNEL_CLASS)
            val constructor = methodChannelClass.getConstructor(
                Class.forName("io.flutter.plugin.common.BinaryMessenger"),
                String::class.java
            )
            val channel = constructor.newInstance(messenger, CHANNEL_NAME)

            // Create MethodCallHandler proxy via dynamic proxy
            val handlerInterface = Class.forName("io.flutter.plugin.common.MethodChannel\$MethodCallHandler")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                handlerInterface.classLoader,
                arrayOf(handlerInterface)
            ) { _, method, args ->
                if (method.name == "onMethodCall") {
                    val call = args[0]
                    val result = args[1]

                    val callMethodField = call.javaClass.getMethod("method")
                    val methodName = callMethodField.invoke(call) as String
                    Log.d(TAG, "MethodChannel call: $methodName")

                    try {
                        val resultClass = result.javaClass
                        val successMethod = resultClass.getMethod("success", Any::class.java)
                        val notImplementedMethod = resultClass.getMethod("notImplemented")
                        val errorMethod = resultClass.getMethod("error", String::class.java, String::class.java, Any::class.java)

                        when (methodName) {
                            "version" -> {
                                val v = Native.getVersion()
                                successMethod.invoke(result, v)
                            }
                            "state" -> {
                                val s = Native.runtimeState()
                                successMethod.invoke(result, s)
                            }
                            "initialize" -> {
                                val ok = Native.initialize()
                                successMethod.invoke(result, ok)
                            }
                            "shutdown" -> {
                                Native.shutdown()
                                successMethod.invoke(result, null)
                            }
                            "ping" -> {
                                successMethod.invoke(result, "pong:${Native.runtimeState()}")
                            }
                            else -> {
                                notImplementedMethod.invoke(result)
                            }
                        }
                    } catch (e: Exception) {
                        try {
                            val resultClass = result.javaClass
                            val errorMethod = resultClass.getMethod("error", String::class.java, String::class.java, Any::class.java)
                            if (e is UnsatisfiedLinkError) {
                                errorMethod.invoke(result, "UNSATISFIED_LINK", e.message, null)
                            } else {
                                errorMethod.invoke(result, "NATIVE_ERROR", e.message, Log.getStackTraceString(e))
                            }
                        } catch (ignored: Exception) {
                            Log.e(TAG, "Failed to send error result", ignored)
                        }
                    }
                }
                null
            }

            val setHandlerMethod = methodChannelClass.getMethod("setMethodCallHandler", handlerInterface)
            setHandlerMethod.invoke(channel, proxy)
            methodChannelRef = channel

            Log.i(TAG, "MethodChannel $CHANNEL_NAME registered via reflection")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register MethodChannel via reflection", e)
        }
    }

    fun getEngine(): Any? = flutterEngineRef
    fun isAvailable(): Boolean = isFlutterAvailable
}
