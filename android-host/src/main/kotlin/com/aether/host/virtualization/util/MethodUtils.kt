package com.aether.host.virtualization.util

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Narrow reflection helpers; these do not bypass visibility or Android hidden-API checks. */
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

    /** Invokes a public method and unwraps the guest's original exception for diagnostics. */
    @JvmStatic
    fun invokePublic(
        target: Any?,
        method: Method,
        vararg arguments: Any?,
    ): Any? {
        if (!java.lang.reflect.Modifier.isPublic(method.modifiers)) {
            throw IllegalArgumentException("Only public methods may be invoked by the host")
        }
        if (!java.lang.reflect.Modifier.isPublic(method.declaringClass.modifiers)) {
            throw IllegalArgumentException("Method owner must be public")
        }
        try {
            return method.invoke(target, *arguments)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }
}
