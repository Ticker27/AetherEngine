package com.aether.host.virtualization.components.provider

/**
 * The same-process RPC contract for [SystemCallProvider].
 *
 * A provider `call()` is the narrowest IPC surface Android offers: one method name, one optional
 * String argument, one optional Bundle, one Bundle back. It is also the surface that turns into
 * a remote attack the moment the provider is exported without a permission.
 *
 * This object holds the whole contract as data so it is testable without an Android runtime:
 * the method table, the argument shape, and the routing into the native message bridge. The
 * provider itself only marshals.
 *
 * Contract rules:
 * - The method table is closed. An unknown name is rejected before anything is dispatched.
 * - `extras` is never read; nothing in this contract accepts structured input.
 * - Every accepted call produces exactly one response Bundle, success or failure.
 */
object SystemCallContract {

    /** One accepted RPC method. */
    data class Method(
        val name: String,
        val bridgeMethod: String,
        val argumentRequired: Boolean,
        val description: String,
    )

    /**
     * The closed method table. `bridgeMethod` is the name registered on the native
     * `MessageBridge`; it is never taken from the caller.
     */
    val methods: List<Method> = listOf(
        Method(
            name = "runtime.status",
            bridgeMethod = "runtime.status",
            argumentRequired = false,
            description = "Native runtime state machine snapshot.",
        ),
        Method(
            name = "runtime.version",
            bridgeMethod = "runtime.version",
            argumentRequired = false,
            description = "Native runtime build identifier.",
        ),
        Method(
            name = "runtime.ping",
            bridgeMethod = "runtime.ping",
            argumentRequired = false,
            description = "Native bridge liveness probe.",
        ),
    )

    private val byName: Map<String, Method> = methods.associateBy { it.name }

    /** Every accepted method name, for diagnostics and for the structure verifier. */
    @JvmStatic
    fun methodNames(): List<String> = methods.map { it.name }

    /** The bridge method for [name], or `null` when the name is not in the table. */
    @JvmStatic
    fun bridgeMethodFor(name: String?): String? = name?.let { byName[it]?.bridgeMethod }

    /** `true` when [name] is an accepted method. */
    @JvmStatic
    fun isAccepted(name: String?): Boolean = name != null && byName.containsKey(name)

    /** Result of validating one incoming `call()`. */
    sealed interface Validation {
        data class Accepted(val method: Method) : Validation
        data class Rejected(val reason: String) : Validation
    }

    /**
     * Validates an incoming `call()`. Order matters: name first, then argument shape, so an
     * unknown method never reaches argument handling.
     */
    @JvmStatic
    fun validate(method: String?, arg: String?): Validation {
        if (method.isNullOrEmpty()) return Validation.Rejected("missing method")
        val entry = byName[method] ?: return Validation.Rejected("unknown method: $method")
        if (entry.argumentRequired && arg.isNullOrEmpty()) {
            return Validation.Rejected("method $method requires an argument")
        }
        if (!entry.argumentRequired && !arg.isNullOrEmpty()) {
            return Validation.Rejected("method $method takes no argument")
        }
        return Validation.Accepted(entry)
    }

    /** Builds the response Bundle payload for a rejected call, as a key/value map. */
    @JvmStatic
    fun rejectionPayload(reason: String): Map<String, String> = mapOf(
        KEY_OK to "false",
        KEY_ERROR to reason,
    )

    const val KEY_OK: String = "ok"
    const val KEY_ERROR: String = "error"
    const val KEY_DATA: String = "data"
}
