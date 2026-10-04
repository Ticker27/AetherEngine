package com.aether

import com.aether.helper.Native
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Phase 1 — Native contract tests.
 *
 * Validates that [Native] declares exactly the 11 external methods that
 * JNI_OnLoad registers via RegisterNatives(kMethods, 11) in jni_bridge.cpp.
 */
class NativeContractTest {

    /** name -> JNI descriptor; must mirror kMethods[] in jni_bridge.cpp */
    private val expected: Map<String, String> = mapOf(
        "ac" to "(Ljava/lang/Object;Ljava/lang/Object;)V",
        "aior" to "(Ljava/lang/String;Ljava/lang/String;)V",
        "awl" to "(Ljava/lang/String;)V",
        "chl" to "([B)Z",
        "djp" to "(I)[B",
        "eio" to "()V",
        "i" to "(I)V",
        "ic" to "(Landroid/content/Context;)V",
        "ilil" to "(I)Ljava/lang/String;",
        "jpo" to "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
        "update" to "(Ljava/lang/Object;Ljava/lang/reflect/Method;)V"
    )

    private fun nativeMethods() = Native::class.java.declaredMethods.filter {
        Modifier.isNative(it.modifiers)
    }

    private fun jniType(c: Class<*>): String = when {
        c == Void.TYPE -> "V"
        c == java.lang.Boolean.TYPE -> "Z"
        c == java.lang.Integer.TYPE -> "I"
        c.isArray -> "[" + jniType(c.componentType)
        else -> "L" + c.name.replace('.', '/') + ";"
    }

    private fun descriptor(m: java.lang.reflect.Method): String {
        val params = m.parameterTypes.joinToString("") { jniType(it) }
        return "($params)${jniType(m.returnType)}"
    }

    @Test
    fun nativeMethodCountIs11() {
        assertEquals(11, nativeMethods().size)
    }

    @Test
    fun nativeMethodNamesMatchKMethods() {
        assertEquals(expected.keys, nativeMethods().map { it.name }.toSet())
    }

    @Test
    fun jniDescriptorsMatchKMethods() {
        assertEquals(expected, nativeMethods().associate { it.name to descriptor(it) })
    }

    @Test
    fun allNativeMethodsArePublicInstance() {
        nativeMethods().forEach { m ->
            assertTrue(m.name, Modifier.isPublic(m.modifiers))
            assertFalse(m.name, Modifier.isStatic(m.modifiers))
        }
    }

    @Test
    fun noReferenceTargetObfuscatedNames() {
        val banned = setOf("b8", "jv0", "vx", "z10", "yu0", "pjowqpxe")
        val declared = Native::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue("obfuscated name present", declared.intersect(banned).isEmpty())
    }
}
