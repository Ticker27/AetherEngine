package com.aether.host.virtualization.loader

import java.io.File
import java.util.Collections

/** Explicit input only: no directory discovery, installed-package lookup, or inferred split names. */
class GuestApkSet(
    val baseApk: File,
    splits: List<GuestApkSplit> = emptyList(),
    val requiresNativeLibraries: Boolean = false,
) {
    val splits: List<GuestApkSplit> = Collections.unmodifiableList(splits.toList())
}

data class GuestApkSplit(val name: String, val apkFile: File)

data class LoadedGuestApkSplit(val name: String, val apkFile: File, val apkSha256: String)

enum class UnsupportedGuestCapability {
    NATIVE_LIBRARIES, ANDROID_RESOURCES, ANDROID_COMPONENT_LAUNCH, SANDBOX_ISOLATION,
}

class GuestApkLoadException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** Adapter snapshots signed archive identity; base is also cross-checked with PackageManager. */
internal class GuestArchiveMetadata(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    signerSha256: Set<String>,
) {
    val signerSha256: Set<String> = Collections.unmodifiableSet(signerSha256.toSet())
}

internal data class GuestManifestIdentity(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val splitName: String?,
    val featureSplit: Boolean,
    val configForSplit: String?,
    val hasCode: Boolean,
    val usesSplit: Boolean,
)

internal data class VerifiedGuestApkMember(
    val name: String, // Empty only for the base; never used as a storage path.
    val apkFile: File,
    val apkSha256: String,
    val manifest: GuestManifestIdentity,
    val versionName: String,
    val signerSha256: Set<String>,
    val nativeLibraryEntries: List<String>,
)

internal class VerifiedGuestApkSet(members: List<VerifiedGuestApkMember>, val sha256: String) {
    val members: List<VerifiedGuestApkMember> = Collections.unmodifiableList(members.toList())
    val base: VerifiedGuestApkMember get() = members.first()
}

internal object GuestApkLimits {
    const val MAX_SPLITS = 32
    const val MAX_APK_BYTES = 256L * 1024 * 1024
    const val MAX_SET_BYTES = 1024L * 1024 * 1024
    const val MAX_ENTRIES = 10_000
    const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 1024 * 1024
}
