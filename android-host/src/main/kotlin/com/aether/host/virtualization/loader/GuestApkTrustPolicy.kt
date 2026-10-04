package com.aether.host.virtualization.loader

import com.aether.host.target.TargetApkContract
import java.util.Locale

/**
 * Exact package/version gate plus explicit signer pins for the selected target APK.
 * The version identity is deliberately fixed to APKPure's 8 Ball Pool 56.30.0 listing;
 * signer SHA-256 pins still have to come from the actual APK certificate.
 */
class GuestApkTrustPolicy(
    trustedSignerSha256: Set<String>,
) {
    val expectedPackageName: String = TargetApkContract.PACKAGE_NAME
    val expectedVersionName: String = TargetApkContract.VERSION_NAME
    val expectedVersionCode: Long = TargetApkContract.VERSION_CODE

    val trustedSignerSha256: Set<String> = trustedSignerSha256
        .map(::normalizeFingerprint)
        .toSet()

    init {
        require(this.trustedSignerSha256.isNotEmpty()) {
            "At least one trusted signer SHA-256 fingerprint is required"
        }
        require(this.trustedSignerSha256.all { it.matches(SHA256_PATTERN) }) {
            "Signer fingerprints must contain exactly 64 hexadecimal characters"
        }
    }

    /** Returns normalized signer pins only when package, exact release, and signers match. */
    fun verify(
        packageName: String,
        versionName: String?,
        versionCode: Long,
        signerSha256: Set<String>,
    ): Set<String> {
        if (packageName != expectedPackageName) {
            throw UntrustedGuestApkException(
                "Package mismatch: expected $expectedPackageName, found $packageName",
            )
        }
        if (versionName != expectedVersionName || versionCode != expectedVersionCode) {
            throw UntrustedGuestApkException(
                "Version mismatch: expected $expectedVersionName ($expectedVersionCode), " +
                    "found ${versionName ?: "<missing>"} ($versionCode)",
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
