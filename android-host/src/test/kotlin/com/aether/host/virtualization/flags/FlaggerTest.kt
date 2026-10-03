package com.aether.host.virtualization.flags

import org.junit.After
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
    fun `feature switches are isolated by capability`() {
        flagger.setEnabled(HostFeature.DYNAMIC_APK_LOADING, true)

        assertTrue(flagger.isEnabled(HostFeature.DYNAMIC_APK_LOADING))
        assertFalse(flagger.isEnabled(HostFeature.PROXY_VPN_SERVICE))
    }
}
