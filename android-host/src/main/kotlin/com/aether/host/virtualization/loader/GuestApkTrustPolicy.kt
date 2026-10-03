package com.aether.host.virtualization.loader

import java.util.Locale

/**
 * Explicit trust pins for a single guest package.
 *
 * APK code executes in the host process with the host UID and permissions. A package
 * name alone is not a trust decision; at least one trusted signing-certificate SHA-256
 * fingerprint must be configured by the host operator.
 */
class GuestApkTrustPolicy(
    val expectedPackageName: String,
    trustedSignerSha256: Set<String>,
) {
    val trustedSignerSha256: Set<String> = trustedSignerSha256
        .map(::normalizeFingerprint)
        .toSet()

    init {
        require(expectedPackageName.isNotBlank()) { "Expected package name must not be blank" }
        require(this.trustedSignerSha256.isNotEmpty()) {
            "At least one trusted signer SHA-256 fingerprint is required"
        }
        require(this.trustedSignerSha256.all { it.matches(SHA256_PATTERN) }) {
            "Signer fingerprints must contain exactly 64 hexadecimal characters"
        }
    }

    /** Returns normalized signer fingerprints if the archive matches this trust policy. */
    fun verify(packageName: String, signerSha256: Set<String>): Set<String> {
        if (packageName != expectedPackageName) {
            throw UntrustedGuestApkException(
                "Package mismatch: expected $expectedPackageName, found $packageName",
            )
        }

        val normalizedSigners = signerSha256.map(::normalizeFingerprint).toSet()
        if (normalizedSigners.isEmpty() || !trustedSignerSha256.containsAll(normalizedSigners)) {
            throw UntrustedGuestApkException("APK signer is not in the configured trust set")
        }
        return normalizedSigners
    }

    companion object {
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

        private fun normalizeFingerprint(value: String): String = value
            .replace(":", "")
            .replace(" ", "")
            .lowercase(Locale.ROOT)
    }
}

class UntrustedGuestApkException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
