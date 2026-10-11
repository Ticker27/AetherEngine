package com.aether.host.virtualization.flags

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlaggerTest {
    @After
    fun reset() {
        flagger.resetForTests()
    }

    @Test
    fun `host capabilities default to disabled`() {
        assertFalse(flagger.isEnabled(HostFeature.DYNAMIC_APK_LOADING))
        assertFalse(flagger.isEnabled(HostFeature.PROXY_VPN_SERVICE))
        assertFalse(flagger.isEnabled(HostFeature.INTERNAL_WEB_BROWSER))
    }

    @Test
    fun `every capability defaults to disabled`() {
        // The rule is not "these three"; it is "all of them". A new flag that shipped enabled
        // would fail here.
        HostFeature.entries.forEach { feature ->
            assertFalse("$feature must default to disabled", flagger.isEnabled(feature))
        }
    }

    @Test
    fun `process-sensitive capabilities are gated`() {
        // Each of these reaches another process, another app, or a hidden framework member.
        listOf(
            HostFeature.SYSTEM_CALL_IPC,
            HostFeature.DAEMON_KEEPALIVE,
            HostFeature.REFLECTIVE_FRAMEWORK_ACCESS,
            HostFeature.SLOT_RESTART,
            HostFeature.PROXY_VPN_SERVICE,
        ).forEach { feature ->
            assertFalse("$feature must default to disabled", flagger.isEnabled(feature))
            flagger.setEnabled(feature, true)
            assertTrue(feature.toString(), flagger.isEnabled(feature))
        }
    }

    @Test
    fun `feature switches are isolated by capability`() {
        flagger.setEnabled(HostFeature.DYNAMIC_APK_LOADING, true)

        assertTrue(flagger.isEnabled(HostFeature.DYNAMIC_APK_LOADING))
        assertFalse(flagger.isEnabled(HostFeature.PROXY_VPN_SERVICE))
    }

    @Test
    fun `snapshot reports every capability exactly once`() {
        flagger.setEnabled(HostFeature.SYSTEM_CALL_IPC, true)

        val snapshot = flagger.snapshot()
        assertEquals(HostFeature.entries.size, snapshot.size)
        assertEquals(true, snapshot[HostFeature.SYSTEM_CALL_IPC.name])
        assertEquals(false, snapshot[HostFeature.DAEMON_KEEPALIVE.name])
        assertEquals(1, flagger.enabledCount())
    }

    @Test
    fun `reset clears every override`() {
        HostFeature.entries.forEach { flagger.setEnabled(it, true) }
        assertEquals(HostFeature.entries.size, flagger.enabledCount())

        flagger.resetForTests()
        assertEquals(0, flagger.enabledCount())
    }
}
