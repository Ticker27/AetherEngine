package com.aether.host.virtualization.loader

/** Immutable identity and signer contract for one accepted guest APK. */
data class GuestApkTrustProfile(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val trustedSignerSha256: Set<String>,
)
