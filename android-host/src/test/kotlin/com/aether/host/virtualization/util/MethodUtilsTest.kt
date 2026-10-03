package com.aether.host.virtualization.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MethodUtilsTest {
    @Test
    fun `finds and invokes public methods only`() {
        val fixture = MethodUtilsFixture()
        val method = MethodUtils.findPublicMethod(
            fixture.javaClass,
            "greet",
            String::class.java,
        )

        assertEquals("hello Aether", MethodUtils.invokePublic(fixture, method, "Aether"))
    }

    @Test
    fun `does not enable access to private methods`() {
        val method = MethodUtilsFixture::class.java.getDeclaredMethod("hidden")
        assertThrows(IllegalArgumentException::class.java) {
            MethodUtils.invokePublic(MethodUtilsFixture(), method)
        }
    }
}

class MethodUtilsFixture {
    fun greet(name: String): String = "hello $name"

    @Suppress("unused")
    private fun hidden(): String = "hidden"
}
