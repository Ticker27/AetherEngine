package com.aether.host.runtime

import android.content.Context
import com.aether.host.bootstrap.HostComponentListener
import com.aether.host.bootstrap.HostRuntimeInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class AetherRuntimeTest {

    private class FakeHostInitializer(
        var initResult: Boolean = true,
    ) : HostRuntimeInitializer {
        var initCalls = 0
        var shutdownCalls = 0
        val listeners = mutableListOf<HostComponentListener>()

        override fun initialize(): Boolean {
            initCalls++
            return initResult
        }

        override fun shutdown() {
            shutdownCalls++
        }

        override fun addComponentListener(listener: HostComponentListener) {
            listeners += listener
        }

        override fun removeComponentListener(listener: HostComponentListener) {
            listeners -= listener
        }
    }

    private fun fakeContext(): Context = Mockito.mock(Context::class.java).also { context ->
        Mockito.`when`(context.applicationContext).thenReturn(context)
    }

    private fun freshRuntime(): AetherRuntime {
        AetherRuntime.resetForTesting()
        return AetherRuntime
    }

    @Test
    fun bootstrap_registersModulesInOrder() {
        val runtime = freshRuntime()
        val fake = FakeHostInitializer()
        AetherRuntime.initializerProvider = { fake }

        assertTrue(runtime.bootstrap(fakeContext()))
        assertEquals(AetherRuntime.RuntimeState.READY, runtime.state)
        assertEquals(
            listOf(AetherRuntime.Module.LOADER, AetherRuntime.Module.NATIVE, AetherRuntime.Module.HOST),
            runtime.registeredModules()
        )
        assertEquals(1, fake.initCalls)
    }

    @Test
    fun bootstrap_isIdempotent() {
        val runtime = freshRuntime()
        val fake = FakeHostInitializer()
        AetherRuntime.initializerProvider = { fake }
        val context = fakeContext()

        assertTrue(runtime.bootstrap(context))
        assertTrue(runtime.bootstrap(context))
        assertEquals(1, fake.initCalls)
    }

    @Test
    fun bootstrap_nativeFailure_setsFailed() {
        val runtime = freshRuntime()
        val fake = FakeHostInitializer(initResult = false)
        AetherRuntime.initializerProvider = { fake }

        assertFalse(runtime.bootstrap(fakeContext()))
        assertEquals(AetherRuntime.RuntimeState.FAILED, runtime.state)
        assertEquals(listOf(AetherRuntime.Module.LOADER), runtime.registeredModules())
    }

    @Test
    fun shutdown_reversesOrder_andDetachesHost() {
        val runtime = freshRuntime()
        val fake = FakeHostInitializer()
        AetherRuntime.initializerProvider = { fake }

        assertTrue(runtime.bootstrap(fakeContext()))
        runtime.shutdown()

        assertEquals(AetherRuntime.RuntimeState.STOPPED, runtime.state)
        assertEquals(1, fake.shutdownCalls)
        assertTrue(runtime.registeredModules().isEmpty())
        assertTrue(fake.listeners.isEmpty())
    }
}
