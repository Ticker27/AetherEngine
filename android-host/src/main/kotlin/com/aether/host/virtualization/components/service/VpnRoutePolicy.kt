package com.aether.host.virtualization.components.service

/**
 * Route policy for [ProxyVpnService], as pure data.
 *
 * A `VpnService` tunnel is a routing decision, and every routing decision is a place where a
 * mistake leaks traffic: an over-broad route captures the host's own control channel, a missing
 * `disallowApplication` keeps the host out of its own tunnel, and a DNS server that is not
 * reachable turns every lookup into a black hole.
 *
 * The policy holds the route set and validates it before any `Builder` call happens, so the
 * checks run in plain unit tests. The service only marshals what the policy approved.
 */
class VpnRoutePolicy(
    val sessionName: String,
    val addresses: List<String>,
    val dnsServers: List<String>,
    val searchDomains: List<String>,
    val routes: List<Route>,
    val mtu: Int,
    val excludedPackages: List<String>,
) {
    /** One CIDR route, e.g. `0.0.0.0/0`. */
    data class Route(val address: String, val prefixLength: Int)

    /** Validation outcome. */
    sealed interface Result {
        data object Valid : Result
        data class Invalid(val problems: List<String>) : Result
    }

    /** Validates the policy. Every problem is reported, not just the first. */
    fun validate(): Result {
        val problems = mutableListOf<String>()
        if (addresses.isEmpty()) problems += "at least one interface address is required"
        addresses.forEach { if (!isIpv4(it)) problems += "not an IPv4 address: $it" }
        if (dnsServers.isEmpty()) problems += "at least one DNS server is required"
        dnsServers.forEach { if (!isIpv4(it)) problems += "not an IPv4 DNS server: $it" }
        if (mtu !in MIN_MTU..MAX_MTU) problems += "mtu $mtu outside $MIN_MTU..$MAX_MTU"
        routes.forEach { route ->
            if (route.prefixLength !in 0..32) {
                problems += "prefix length ${route.prefixLength} outside 0..32"
            }
            if (!isIpv4(route.address)) problems += "not an IPv4 route address: ${route.address}"
        }
        if (routes.none { it.prefixLength == 0 }) {
            problems += "a default route (prefix length 0) is required to capture traffic"
        }
        if (excludedPackages.isEmpty()) {
            problems += "the host package must be excluded from its own tunnel"
        }
        return if (problems.isEmpty()) Result.Valid else Result.Invalid(problems)
    }

    /** `true` when the policy passes validation. */
    fun isValid(): Boolean = validate() is Result.Valid

    companion object {
        const val MIN_MTU: Int = 576
        const val MAX_MTU: Int = 1500

        /**
         * The default host policy: capture everything except the host's own package, with
         * public resolvers. Deliberately narrow — no IPv6, no per-app split rules.
         */
        fun defaultPolicy(hostPackage: String): VpnRoutePolicy = VpnRoutePolicy(
            sessionName = "aether-host",
            addresses = listOf("10.111.222.1"),
            dnsServers = listOf("1.1.1.1", "8.8.8.8"),
            searchDomains = emptyList(),
            routes = listOf(Route("0.0.0.0", 0)),
            mtu = 1500,
            excludedPackages = listOf(hostPackage),
        )

        /** Minimal shape check for dotted-quad IPv4; no DNS resolution, no parsing library. */
        fun isIpv4(value: String): Boolean {
            val parts = value.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && part.toInt() in 0..255
            }
        }
    }
}
