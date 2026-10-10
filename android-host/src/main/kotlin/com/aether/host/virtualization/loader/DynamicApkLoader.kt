package com.aether.host.virtualization.loader

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.aether.host.virtualization.filesystem.GuestVirtualFileSystem
import com.android.apksig.ApkVerifier
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Result of loading a verified guest APK's DEX code into the host process. */
data class LoadedGuestApk internal constructor(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkFile: File,
    val classLoader: GuestClassLoaderProxy,
    val fileSystem: GuestVirtualFileSystem,
    val apkSha256: String,
    val signerSha256: Set<String>,
    val splitApks: List<LoadedGuestApkSplit> = emptyList(),
    val packageSetSha256: String = apkSha256,
    /** Logical member name + ZIP entry; inspection only, never extracted or linked. */
    val nativeLibraryEntries: List<String> = emptyList(),
) {
    /** Eligibility only, not proof that arbitrary guest code is safe or cooperative. */
    val supportsCooperativeDex: Boolean get() = nativeLibraryEntries.isEmpty()
    val unsupportedCapabilities: Set<UnsupportedGuestCapability> = java.util.Collections.unmodifiableSet(
        UnsupportedGuestCapability.values().toSet(),
    )
}

/**
 * Copies an explicitly selected APK into private, read-only host storage, verifies the
 * exact target package/version and signer pins, then creates a DexClassLoader for its DEX code.
 *
 * This is a code loader, not an Android sandbox or a complete Activity virtualization
 * engine. Loaded code runs inside this process with the host UID and permissions. The returned
 * class-loader proxy only controls Java class delegation, and the returned file-system facade
 * only governs calls made through that facade; neither intercepts arbitrary guest code. Native
 * libraries, Android resources, package-manager virtualization, and Activity attachment remain
 * unsupported.
 */
class DynamicApkLoader(
    context: Context,
    private val trustPolicy: GuestApkTrustPolicy,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val loadedByDigest = mutableMapOf<String, LoadedGuestApk>()

    /**
     * Loads an APK only after signature/package verification. The source can be a
     * user-selected file; it is copied before parsing to avoid loading from mutable shared
     * storage. Call from a worker thread because copy, hashing, and DEX optimization do I/O.
     */
    fun load(sourceApk: File): LoadedGuestApk = load(GuestApkSet(sourceApk))

    /**
     * Base + explicitly named, code-free base config splits only. Neither this lock nor read-only
     * files isolate hostile same-UID code: it can chmod/replace files after our last check. Source
     * snapshots, bounded copies, canonical private paths and revalidation reduce ordinary TOCTOU;
     * this loader is only for cooperative first-party test guests. It does not execute an entrypoint.
     */
    fun load(apkSet: GuestApkSet): LoadedGuestApk = synchronized(lock) {
        val incoming = ArrayList<File>()
        try {
            val validator = GuestApkSetValidator(trustPolicy, ::readArchiveMetadata)
            val requested = validator.requestedMembers(apkSet)
            val privateDirectory = ensurePrivateDirectory(appContext.noBackupFilesDir.canonicalFile, GUEST_APK_DIRECTORY)
            var copiedBytes = 0L
            val staged = requested.map { (name, source) ->
                val file = File.createTempFile("guest-", ".apk", privateDirectory)
                incoming.add(file)
                copiedBytes += copyAndSync(source, file)
                checkGuest(copiedBytes <= GuestApkLimits.MAX_SET_BYTES, "Package set exceeds size limit")
                name to file
            }
            val verified = validator.validate(staged.asApkSet())
            val cachedMembers = verified.members.map { member ->
                val cachedApk = checkedChild(privateDirectory, "${member.apkSha256}.apk")
                if (!cachedApk.exists() && !member.apkFile.renameTo(cachedApk)) {
                    checkGuest(cachedApk.exists(), "Could not atomically finalize guest APK")
                }
                checkGuest(cachedApk.isFile && cachedApk.canRead(), "Cached guest APK is not a readable file")
                checkGuest(!cachedApk.canWrite() || cachedApk.setReadOnly(), "Could not make cached guest APK read-only")
                checkGuest(!cachedApk.canWrite(), "Cached guest APK remains writable")
                checkGuest(GuestApkSetValidator.sha256(cachedApk) == member.apkSha256,
                    "Private guest APK cache failed integrity verification")
                member.name to cachedApk
            }
            // Always parse and apply mandatory signer policy to every cached member, including
            // when returning an existing LoadedGuestApk. Never trust a digest-derived filename.
            val cached = validator.validate(cachedMembers.asApkSet())
            checkGuest(cached.sha256 == verified.sha256, "Private package set cache failed integrity verification")
            cached.members.zip(verified.members).forEach { (current, original) ->
                checkGuest(current.manifest == original.manifest && current.versionName == original.versionName &&
                    current.signerSha256 == original.signerSha256, "Cached archive metadata changed")
            }
            loadedByDigest[cached.sha256]?.let { return@synchronized it }
            val optimizedRoot = ensurePrivateDirectory(appContext.codeCacheDir.canonicalFile, DEX_CACHE_DIRECTORY)
            val optimizedDirectory = ensurePrivateDirectory(optimizedRoot, cached.sha256)
            val base = cached.base
            val guestFileSystem = GuestVirtualFileSystem.forGuest(
                noBackupFilesDir = appContext.noBackupFilesDir,
                packageName = base.manifest.packageName,
                versionCode = base.manifest.versionCode,
                // Existing VFS parameter name retained, but identity is now the whole package set.
                apkSha256 = cached.sha256,
            )
            val guest = LoadedGuestApk(
                packageName = base.manifest.packageName,
                versionName = base.versionName,
                versionCode = base.manifest.versionCode,
                apkFile = base.apkFile,
                classLoader = GuestClassLoaderProxy(
                    cached.members.joinToString(File.pathSeparator) { it.apkFile.absolutePath },
                    optimizedDirectory.absolutePath,
                    appContext.classLoader,
                ),
                fileSystem = guestFileSystem,
                apkSha256 = base.apkSha256,
                signerSha256 = base.signerSha256,
                splitApks = java.util.Collections.unmodifiableList(cached.members.drop(1).map {
                    LoadedGuestApkSplit(it.name, it.apkFile, it.apkSha256)
                }),
                packageSetSha256 = cached.sha256,
                nativeLibraryEntries = java.util.Collections.unmodifiableList(cached.members.flatMap { member ->
                    member.nativeLibraryEntries.map { "${member.name.ifEmpty { "base" }}:$it" }
                }),
            )
            loadedByDigest[cached.sha256] = guest
            guest
        } catch (error: GuestApkLoadException) {
            throw error
        } catch (error: Exception) {
            throw GuestApkLoadException("Guest APK set could not be loaded", error)
        } finally {
            incoming.forEach { if (it.exists()) it.delete() }
        }
    }

    private fun List<Pair<String, File>>.asApkSet(): GuestApkSet = GuestApkSet(
        first().second, drop(1).map { GuestApkSplit(it.first, it.second) },
    )

    private fun checkedChild(parent: File, name: String): File {
        val child = File(parent, name).absoluteFile
        checkGuest(child.canonicalFile == child, "Private guest storage must not contain symbolic links")
        return child
    }

    private fun ensurePrivateDirectory(parent: File, name: String): File {
        val directory = checkedChild(parent, name)
        checkGuest(directory.exists() || directory.mkdir(), "Could not create private guest directory")
        checkGuest(checkedChild(parent, name).isDirectory, "Private guest storage path is not a directory")
        return directory
    }

    private fun copyAndSync(source: File, destination: File): Long {
        val length = source.length()
        val modified = source.lastModified()
        var total = 0L
        FileOutputStream(destination).use { output ->
            // Android 14 requires DEX inputs read-only. The sole already-open writer finishes
            // the snapshot before any parser/classloader observes it.
            checkGuest(destination.setReadOnly() && !destination.canWrite(), "Could not make staged APK read-only")
            source.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    checkGuest(total <= GuestApkLimits.MAX_APK_BYTES, "APK exceeds size limit")
                    output.write(buffer, 0, count)
                }
            }
            output.fd.sync()
        }
        checkGuest(total == length && source.length() == length && source.lastModified() == modified,
            "Source APK changed while being copied")
        return total
    }

    private fun readArchiveMetadata(apk: File, manifest: GuestManifestIdentity): GuestArchiveMetadata {
        val result = try {
            ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
                .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
                .build().verify()
        } catch (error: LinkageError) {
            throw GuestApkLoadException("APK signature verifier is unavailable on this runtime", error)
        } catch (error: Exception) {
            throw GuestApkLoadException("APK cryptographic signature verification failed", error)
        }
        checkGuest(result.isVerified && result.signerCertificates.isNotEmpty(),
            "APK cryptographic signatures did not verify")
        val verifiedSigners = result.signerCertificates.map { sha256(it.encoded) }.toSet()
        if (manifest.splitName != null) {
            // PM cannot reliably parse standalone config splits. Metadata comes from the actual
            // bounded AXML reader only after verifying the bytes, not from requested split names.
            return GuestArchiveMetadata(manifest.packageName, manifest.versionName, manifest.versionCode, verifiedSigners)
        }
        val info = readPackageInfo(apk)
        checkGuest(signerDigests(info) == verifiedSigners, "PackageManager and cryptographic signer sets differ")
        return GuestArchiveMetadata(info.packageName, info.versionName, info.compatVersionCode(), verifiedSigners)
    }

    private fun readPackageInfo(apk: File): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            legacySignatureFlag()
        }
        return appContext.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw GuestApkLoadException(
                "PackageManager could not read archive metadata; malformed APK or unsupported standalone split",
            )
    }

    private fun PackageInfo.compatVersionCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else legacyVersionCode(this)

    @Suppress("DEPRECATION")
    private fun legacyVersionCode(packageInfo: PackageInfo): Long = packageInfo.versionCode.toLong()

    private fun signerDigests(packageInfo: PackageInfo): Set<String> {
        val signatures: Array<Signature> = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                legacySignatures(packageInfo)
            }
        ) ?: emptyArray()

        if (signatures.isEmpty()) {
            throw GuestApkLoadException("APK archive has no readable signing certificates")
        }
        return signatures.map { signature -> sha256(signature.toByteArray()) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun legacySignatureFlag(): Int = PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun legacySignatures(packageInfo: PackageInfo): Array<Signature>? = packageInfo.signatures

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private companion object {
        const val GUEST_APK_DIRECTORY = "aether-guest-apks"
        const val DEX_CACHE_DIRECTORY = "aether-guest-dex"
    }
}
