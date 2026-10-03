package com.aether.host

import org.junit.Test
import org.junit.Assert.*

class NativeTest {
    @Test
    fun testNativeClassExists() {
        // Contract test — class should exist
        assertNotNull(Native::class.java)
    }

    @Test
    fun testMethodSignatures() {
        val methods = Native::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue(methods.contains("initialize"))
        assertTrue(methods.contains("shutdown"))
        assertTrue(methods.contains("getVersion"))
        assertTrue(methods.contains("runtimeState"))
    }
}
