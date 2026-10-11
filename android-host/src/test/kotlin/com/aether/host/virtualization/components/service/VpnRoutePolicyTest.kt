package com.aether.host.virtualization.components.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route policy checks. A tunnel that captures traffic it cannot forward is worse than no tunnel,
 * so every rejection path is asserted.
 */
class VpnRoutePolicyTest {

    @Test
    fun defaultPolicyIsValidAndExcludesTheHost() {
        val policy = VpnRoutePolicy.defaultPolicy("com.aether.host")
        assertEquals(VpnRoutePolicy.Result.Valid, policy.validate())
        assertTrue(policy.isValid())
        assertTrue(policy.excludedPackages.contains("com.aether.host"))
        assertTrue(policy.routes.any { it.prefixLength == 0 })
    }

    @Test
    fun rejectsPolicyWithoutDefaultRoute() {
        val policy = VpnRoutePolicy(
            sessionName = "a",
            addresses = listOf("10.0.0.1"),
            dnsServers = listOf("1.1.1.1"),
            searchDomains = emptyList(),
            routes = listOf(VpnRoutePolicy.Route("10.0.0.0", 8)),
            mtu = 1500,
            excludedPackages = listOf("com.aether.host"),
        )
        val result = policy.validate()
        assertTrue(result is VpnRoutePolicy.Result.Invalid)
        assertTrue(
            (result as VpnRoutePolicy.Result.Invalid).problems
                .any { it.contains("default route") },
        )
    }

    @Test
    fun rejectsPolicyThatWouldCaptureTheHostItself() {
        val policy = VpnRoutePolicy(
            sessionName = "a",
            addresses = listOf("10.0.0.1"),
            dnsServers = listOf("1.1.1.1"),
            searchDomains = emptyList(),
            routes = listOf(VpnRoutePolicy.Route("0.0.0.0", 0)),
            mtu = 1500,
            excludedPackages = emptyList(),
        )
        val result = policy.validate()
        assertTrue(result is VpnRoutePolicy.Result.Invalid)
        assertTrue((result as VpnRoutePolicy.Result.Invalid).problems.any { it.contains("excluded") })
    }

    @Test
    fun rejectsEmptyAddressesAndDns() {
        val policy = VpnRoutePolicy(
            sessionName = "a",
            addresses = emptyList(),
            dnsServers = emptyList(),
            searchDomains = emptyList(),
            routes = listOf(VpnRoutePolicy.Route("0.0.0.0", 0)),
            mtu = 1500,
            excludedPackages = listOf("com.aether.host"),
        )
        val problems = (policy.validate() as VpnRoutePolicy.Result.Invalid).problems
        assertTrue(problems.any { it.contains("address") })
        assertTrue(problems.any { it.contains("DNS") })
    }

    @Test
    fun rejectsMtuOutsideTheUsableRange() {
        val policy = VpnRoutePolicy.defaultPolicy("com.aether.host").copy(mtu = 100)
        val problems = (policy.validate() as VpnRoutePolicy.Result.Invalid).problems
        assertTrue(problems.any { it.startsWith("mtu") })
        assertTrue(VpnRoutePolicy.defaultPolicy("com.aether.host").copy(mtu = VpnRoutePolicy.MAX_MTU).isValid())
        assertTrue(VpnRoutePolicy.defaultPolicy("com.aether.host").copy(mtu = VpnRoutePolicy.MIN_MTU).isValid())
    }

    @Test
    fun rejectsBadAddressesAndPrefixLengths() {
        val policy = VpnRoutePolicy(
            sessionName = "a",
            addresses = listOf("not-an-ip"),
            dnsServers = listOf("999.1.1.1"),
            searchDomains = emptyList(),
            routes = listOf(VpnRoutePolicy.Route("10.0.0.0", 33)),
            mtu = 1500,
            excludedPackages = listOf("com.aether.host"),
        )
        val problems = (policy.validate() as VpnRoutePolicy.Result.Invalid).problems
        assertTrue(problems.any { it.contains("not-an-ip") })
        assertTrue(problems.any { it.contains("999.1.1.1") })
        assertTrue(problems.any { it.contains("33") })
    }

    @Test
    fun ipv4CheckIsStrictAboutShape() {
        assertTrue(VpnRoutePolicy.isIpv4("0.0.0.0"))
        assertTrue(VpnRoutePolicy.isIpv4("255.255.255.255"))
        assertTrue(VpnRoutePolicy.isIpv4("10.111.222.1"))
        assertFalse(VpnRoutePolicy.isIpv4("256.1.1.1"))
        assertFalse(VpnRoutePolicy.isIpv4("1.1.1"))
        assertFalse(VpnRoutePolicy.isIpv4("1.1.1.1.1"))
        assertFalse(VpnRoutePolicy.isIpv4("a.b.c.d"))
        assertFalse(VpnRoutePolicy.isIpv4("::1"))
        assertFalse(VpnRoutePolicy.isIpv4(""))
        assertFalse(VpnRoutePolicy.isIpv4("01.1.1.1x"))
    }
}
