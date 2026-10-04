package com.aether.host.runtime

import android.app.Application
import com.aether.host.bootstrap.HostComponentListener
import com.aether.host.bootstrap.HostInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherRuntimeTest {

    private class FakeHostInitializer(
        delegate: HostInitializer = HostInitializer(FakeApplication()),
        var initResult: Boolean = true,
    ) : HostInitializer by delegate {
        var initCalls = 0
        var shutdownCalls = 0
        val listeners = mutableListOf<HostComponentListener>()

        override fun initialize(): Boolean {
            initCalls++
            return initResult
        }

        override fun shutdown() {
            shutdownCalls++
            delegate.shutdown()
        }

        override fun addComponentListener(listener: HostComponentListener) {
            listeners += listener
        }

        override fun removeComponentListener(listener: HostComponentListener) {
            listeners -= listener
        }
    }

    private class FakeApplication : Application()

    private fun freshRuntime(): AetherRuntime {
        AetherRuntime.shutdown()
        return AetherRuntime
    }

    @Test
    fun bootstrap_registersModulesInOrder() {
        val runtime = freshRuntime()
        val fake = FakeHostInitializer()
        AetherRuntime.initializerProvider = { fake }

        val context = FakeApplication()
        assertTrue(runtime.bootstrap(context))
        assertEquals(RuntimeState.READY, runtime.state)
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
        val context = FakeApplication()

        assertTrue(runtime.bootstrap(context))
        assertTrue(runtime.bootstrap(context))
        assertEquals(1, fake.initCalls)
    }

    @Test
    fun bootstrap_nativeFailure_setsFailed() {
        val runtime = freshRuntime()
        val fake = FakeHostInitializer(initResult = false)
        AetherRuntime.initializerProvider = { fake }

        assertFalse(runtime.bootstrap(FakeApplication()))
        assertEquals(RuntimeState.FAILED, runtime.state)
        assertEquals(listOf(AetherRuntime.Module.LOADER), runtime.registeredModules())
    }

    @Test
    fun shutdown_reversesOrder_andDetachesHost() {
        val runtime = freshRuntime()
        val fake = FakeHostInitializer()
        AetherRuntime.initializerProvider = { fake }
        val context = FakeApplication()

        assertTrue(runtime.bootstrap(context))
        runtime.shutdown()

        assertEquals(RuntimeState.STOPPED, runtime.state)
        assertEquals(1, fake.shutdownCalls)
        assertTrue(runtime.registeredModules().isEmpty())
        assertTrue(fake.listeners.isEmpty())
    }
}
