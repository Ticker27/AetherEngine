package com.aether.host.virtualization.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `descriptor lookup fails closed when the member is not allowlisted`() {
        // The allowlist is empty at S1, so no descriptor resolves — including one that names a
        // perfectly real, perfectly public method.
        assertNull(MethodUtils.findAllowedMethod(MethodUtilsFixture::class.java, "greet(Ljava/lang/String;)Ljava/lang/String;"))
        assertNull(MethodUtils.findAllowedMethod(MethodUtilsFixture::class.java, "notADescriptor"))
    }

    @Test
    fun `invokeAllowed refuses a method that is not on the allowlist`() {
        val fixture = MethodUtilsFixture()
        val method = MethodUtils.findPublicMethod(fixture.javaClass, "greet", String::class.java)
        assertThrows(IllegalStateException::class.java) {
            MethodUtils.invokeAllowed(fixture, method, "Aether")
        }
    }

    @Test
    fun `invokeAllowed still refuses non-public methods`() {
        // Even if an allowlist entry existed, the public-modifier gate runs first.
        val method = MethodUtilsFixture::class.java.getDeclaredMethod("hidden")
        assertThrows(IllegalArgumentException::class.java) {
            MethodUtils.invokeAllowed(MethodUtilsFixture(), method)
        }
    }

    @Test
    fun `invokePublic unwraps the target exception`() {
        val fixture = MethodUtilsFixture()
        val method = MethodUtils.findPublicMethod(fixture.javaClass, "explode")
        val thrown = assertThrows(IllegalStateException::class.java) {
            MethodUtils.invokePublic(fixture, method)
        }
        assertEquals("guest failure", thrown.message)
    }
}

class MethodUtilsFixture {
    fun greet(name: String): String = "hello $name"

    fun explode() {
        throw IllegalStateException("guest failure")
    }

    @Suppress("unused")
    private fun hidden(): String = "hidden"
}
