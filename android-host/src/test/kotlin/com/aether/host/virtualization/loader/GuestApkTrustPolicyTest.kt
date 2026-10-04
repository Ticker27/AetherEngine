package com.aether.host.virtualization.loader

import com.aether.host.target.TargetApkContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GuestApkTrustPolicyTest {
    @Test
    fun `target contract is only 8 Ball Pool 56 30 0`() {
        assertEquals("com.miniclip.eightballpool", TargetApkContract.PACKAGE_NAME)
        assertEquals("56.30.0", TargetApkContract.VERSION_NAME)
        assertEquals(4028L, TargetApkContract.VERSION_CODE)
    }

    @Test
    fun `normalizes certificate pins before matching exact target release`() {
        val policy = policy()

        val verified = policy.verify(
            packageName = TargetApkContract.PACKAGE_NAME,
            versionName = TargetApkContract.VERSION_NAME,
            versionCode = TargetApkContract.VERSION_CODE,
            signerSha256 = setOf(CERTIFICATE_WITH_COLONS.uppercase()),
        )

        assertEquals(setOf(CERTIFICATE), verified)
    }

    @Test
    fun `rejects package mismatch`() {
        assertThrows(UntrustedGuestApkException::class.java) {
            policy().verify(
                packageName = "com.miniclip.other",
                versionName = TargetApkContract.VERSION_NAME,
                versionCode = TargetApkContract.VERSION_CODE,
                signerSha256 = setOf(CERTIFICATE),
            )
        }
    }

    @Test
    fun `rejects a different version name even when package and signer match`() {
        assertThrows(UntrustedGuestApkException::class.java) {
            policy().verify(
                packageName = TargetApkContract.PACKAGE_NAME,
                versionName = "56.30.1",
                versionCode = TargetApkContract.VERSION_CODE,
                signerSha256 = setOf(CERTIFICATE),
            )
        }
    }

    @Test
    fun `rejects a different version code even when version name matches`() {
        assertThrows(UntrustedGuestApkException::class.java) {
            policy().verify(
                packageName = TargetApkContract.PACKAGE_NAME,
                versionName = TargetApkContract.VERSION_NAME,
                versionCode = TargetApkContract.VERSION_CODE + 1,
                signerSha256 = setOf(CERTIFICATE),
            )
        }
    }

    @Test
    fun `rejects any untrusted co-signer`() {
        assertThrows(UntrustedGuestApkException::class.java) {
            policy().verify(
                packageName = TargetApkContract.PACKAGE_NAME,
                versionName = TargetApkContract.VERSION_NAME,
                versionCode = TargetApkContract.VERSION_CODE,
                signerSha256 = setOf(CERTIFICATE, OTHER_CERTIFICATE),
            )
        }
    }

    @Test
    fun `requires at least one valid trust pin`() {
        assertThrows(IllegalArgumentException::class.java) {
            GuestApkTrustPolicy(emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            GuestApkTrustPolicy(setOf("not-a-fingerprint"))
        }
    }

    private fun policy() = GuestApkTrustPolicy(setOf(CERTIFICATE))

    companion object {
        private const val CERTIFICATE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        private const val CERTIFICATE_WITH_COLONS = "01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef:01:23:45:67:89:ab:cd:ef"
        private const val OTHER_CERTIFICATE = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    }
}
