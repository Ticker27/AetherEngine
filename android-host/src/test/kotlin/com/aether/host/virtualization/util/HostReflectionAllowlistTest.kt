package com.aether.host.virtualization.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The allowlist is the enforcement boundary, so the tests assert that it is *closed*, not that it
 * contains particular entries. An allowlist test that enumerates today's entries passes even when
 * someone adds an unjustified one tomorrow.
 */
class HostReflectionAllowlistTest {

    @Test
    fun tableIsEmptyAtS1() {
        assertTrue(
            "S1 declares no hidden framework member; the table must stay empty until one is justified",
            HostReflectionAllowlist.all().isEmpty(),
        )
        assertEquals(0, HostReflectionAllowlist.size())
    }

    @Test
    fun unknownMembersAreNeverAllowed() {
        assertFalse(
            HostReflectionAllowlist.isAllowed("android/app/Activity", "attachBaseContext", listOf("Landroid/content/Context;")),
        )
        assertFalse(
            HostReflectionAllowlist.isAllowed("android/os/IActivityManager$Stub", "asInterface", emptyList()),
        )
        assertNull(
            HostReflectionAllowlist.resolve("android/app/Activity", "recreate", emptyList()),
        )
    }

    @Test
    fun entryDescriptorIsBuiltFromItsOwnFields() {
        val entry = HostReflectionAllowlist.Entry(
            declaringClass = "android/app/Activity",
            methodName = "attachBaseContext",
            parameterTypes = listOf("Landroid/content/Context;"),
            returnType = "V",
            reason = "test only",
        )
        assertEquals("attachBaseContext(Landroid/content/Context;)V", entry.descriptor)
    }
}
