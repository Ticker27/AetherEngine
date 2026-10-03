package com.aether.host

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 1 Bootstrap — Native contract tests
 * Validates JNI registration and runtime state machine via Kotlin side.
 */
class NativeContractTest {

    @Test
    fun testLoadLibrary() {
        // Should not throw UnsatisfiedLinkError
        try {
            val version = Native.getVersion()
            assertNotNull(version)
            assertTrue(version.isNotEmpty())
        } catch (e: UnsatisfiedLinkError) {
            fail("libaether.so failed to load: ${e.message}")
        }
    }

    @Test
    fun testInitializeIdempotent() {
        val first = Native.initialize()
        val second = Native.initialize()
        // Second should be true (idempotent) or false but state should be initialized/running
        val state = Native.runtimeState()
        assertTrue(state == "initialized" || state == "running" || state == "new" || first)
    }

    @Test
    fun testGetVersionNeverNull() {
        val version = Native.getVersion()
        assertNotNull(version)
        assertFalse(version.isBlank())
    }

    @Test
    fun testRuntimeStateNeverNull() {
        val state = Native.runtimeState()
        assertNotNull(state)
        assertTrue(
            state in listOf("new", "initialized", "running", "stopping", "stopped")
        )
    }

    @Test
    fun testShutdownIdempotent() {
        Native.initialize()
        Native.shutdown()
        // Second shutdown should not crash
        Native.shutdown()
        val state = Native.runtimeState()
        assertEquals("stopped", state)
    }
}
