package com.aether.host.virtualization.loader

import com.aether.host.target.TargetApkContract
import java.util.Locale

/**
 * Exact package/version gate plus explicit signer pins for one guest profile.
 *
 * The default constructor remains pinned to [TargetApkContract]. S1 passes an explicit
 * [GuestApkTrustProfile], so the first-party fixture exercises the same verifier without
 * weakening or conflating the production target policy.
 */
class GuestApkTrustPolicy(
    val profile: GuestApkTrustProfile,
) {
    val expectedPackageName: String = profile.packageName
    val expectedVersionName: String = profile.versionName
    val expectedVersionCode: Long = profile.versionCode
    val trustedSignerSha256: Set<String> = profile.trustedSignerSha256
        .map(::normalizeFingerprint)
        .toSet()

    constructor(trustedSignerSha256: Set<String>) : this(
        GuestApkTrustProfile(
            packageName = TargetApkContract.PACKAGE_NAME,
            versionName = TargetApkContract.VERSION_NAME,
            versionCode = TargetApkContract.VERSION_CODE,
            trustedSignerSha256 = trustedSignerSha256,
        ),
    )

    constructor(
        trustedSignerSha256: Set<String>,
        expectedPackageName: String,
        expectedVersionName: String,
        expectedVersionCode: Long,
    ) : this(
        GuestApkTrustProfile(
            packageName = expectedPackageName,
            versionName = expectedVersionName,
            versionCode = expectedVersionCode,
            trustedSignerSha256 = trustedSignerSha256,
        ),
    )

    init {
        require(expectedPackageName.matches(PACKAGE_NAME_PATTERN)) {
            "Expected package name is invalid"
        }
        require(expectedVersionName.isNotBlank()) { "Expected version name is required" }
        require(expectedVersionCode > 0) { "Expected version code must be positive" }
        require(trustedSignerSha256.isNotEmpty()) {
            "At least one trusted signer SHA-256 fingerprint is required"
        }
        require(trustedSignerSha256.all { it.matches(SHA256_PATTERN) }) {
            "Signer fingerprints must contain exactly 64 hexadecimal characters"
        }
    }

    /** Returns normalized signer pins only when identity and signers match exactly. */
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
        private val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

        private fun normalizeFingerprint(value: String): String = value
            .replace(":", "")
            .replace(" ", "")
            .lowercase(Locale.ROOT)
    }
}

class UntrustedGuestApkException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
