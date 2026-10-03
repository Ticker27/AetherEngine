package com.aether.host.virtualization.loader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GuestApkTrustPolicyTest {
    @Test
    fun `normalizes certificate pins before matching`() {
        val policy = GuestApkTrustPolicy(
            expectedPackageName = "com.example.game",
            trustedSignerSha256 = setOf(CERTIFICATE),
        )

        val verified = policy.verify(
            packageName = "com.example.game",
            signerSha256 = setOf(CERTIFICATE_WITH_COLONS.uppercase()),
        )

        assertEquals(setOf(CERTIFICATE), verified)
    }

    @Test
    fun `rejects package mismatch`() {
        val policy = policy()
        assertThrows(UntrustedGuestApkException::class.java) {
            policy.verify("com.example.other", setOf(CERTIFICATE))
        }
    }

    @Test
    fun `rejects any untrusted co-signer`() {
        val policy = policy()
        assertThrows(UntrustedGuestApkException::class.java) {
            policy.verify("com.example.game", setOf(CERTIFICATE, OTHER_CERTIFICATE))
        }
    }

    @Test
    fun `requires at least one valid trust pin`() {
        assertThrows(IllegalArgumentException::class.java) {
            GuestApkTrustPolicy("com.example.game", emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            GuestApkTrustPolicy("com.example.game", setOf("not-a-fingerprint"))
        }
    }

    private fun policy() = GuestApkTrustPolicy(
        expectedPackageName = "com.example.game",
        trustedSignerSha256 = setOf(CERTIFICATE),
    )

    companion object {
        private const val CERTIFICATE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        private const val CERTIFICATE_WITH_COLONS = "01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef"
        private const val OTHER_CERTIFICATE = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    }
}
