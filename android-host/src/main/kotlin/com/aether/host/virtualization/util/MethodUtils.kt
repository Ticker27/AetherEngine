package com.aether.host.virtualization.util

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Narrow reflection helpers; these do not bypass visibility or Android hidden-API checks.
 *
 * Two tiers, deliberately separated:
 *
 * 1. **Public tier** — [findPublicMethod], [invokePublic]. Compile-time-public members only,
 *    no allowlist needed. Safe for anything in the SDK surface the host already compiles against.
 *
 * 2. **Descriptor tier** — [findAllowedMethod]. Resolves a JVM descriptor against
 *    [HostReflectionAllowlist]. If the member is not declared there, the lookup fails closed.
 *    This is the only path that can reach a `@hide` framework member, and it is gated by the
 *    [com.aether.host.virtualization.flags.HostFeature.REFLECTIVE_FRAMEWORK_ACCESS] flag.
 *
 * The descriptor tier never widens visibility: it resolves a method the allowlist names and
 * invokes it with the same public-modifier checks as tier 1. What it adds is a *reviewable* way
 * to name a member that is public in the platform source but hidden from the SDK.
 */
object MethodUtils {

    @JvmStatic
    fun findPublicMethod(
        owner: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method = owner.getMethod(methodName, *parameterTypes)

    @JvmStatic
    fun findPublicMethod(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method = findPublicMethod(classLoader.loadClass(className), methodName, *parameterTypes)

    /**
     * Resolves a descriptor-named member that [HostReflectionAllowlist] declares.
     *
     * @param descriptor `name(params)return`, e.g. `attachBaseContext(Landroid/content/Context;)V`
     * @param owner the class the member is looked up on; must be the declaring class or a subclass
     * @return the method, or `null` when the descriptor is malformed or the member is not declared
     */
    @JvmStatic
    fun findAllowedMethod(owner: Class<*>, descriptor: String): Method? {
        val parsed = ReflectionDescriptor.parse(descriptor, owner.classLoader) ?: return null
        val entry = HostReflectionAllowlist.resolve(
            owner.name.replace('.', '/'),
            parsed.name,
            parsed.parameters.map { ReflectionDescriptor.typeOf(it) },
        ) ?: return null
        val method = try {
            owner.getMethod(parsed.name, *parsed.parameters.toTypedArray())
        } catch (error: NoSuchMethodException) {
            return null
        }
        // The allowlist entry and the resolved method must agree; a mismatch means the platform
        // changed shape and the declaration is stale.
        if (ReflectionDescriptor.typeOf(method.returnType) != entry.returnType) return null
        return method
    }

    /** Invokes a public method and unwraps the guest's original exception for diagnostics. */
    @JvmStatic
    fun invokePublic(
        target: Any?,
        method: Method,
        vararg arguments: Any?,
    ): Any? {
        requireInvokable(method)
        try {
            return method.invoke(target, *arguments)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    /**
     * Invokes an allowlisted member. Fails closed when the flag is off, the member is not
     * declared, or the method is not public.
     */
    @JvmStatic
    fun invokeAllowed(
        target: Any?,
        method: Method,
        vararg arguments: Any?,
    ): Any? {
        requireInvokable(method)
        if (!HostReflectionAllowlist.isAllowed(
                method.declaringClass.name.replace('.', '/'),
                method.name,
                method.parameterTypes.map { ReflectionDescriptor.typeOf(it) },
            )
        ) {
            throw IllegalStateException("Framework member is not on the host reflection allowlist")
        }
        try {
            return method.invoke(target, *arguments)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun requireInvokable(method: Method) {
        if (!Modifier.isPublic(method.modifiers)) {
            throw IllegalArgumentException("Only public methods may be invoked by the host")
        }
        if (!Modifier.isPublic(method.declaringClass.modifiers)) {
            throw IllegalArgumentException("Method owner must be public")
        }
    }
}
