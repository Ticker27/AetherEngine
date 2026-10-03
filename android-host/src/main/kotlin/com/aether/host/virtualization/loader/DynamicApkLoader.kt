package com.aether.host.virtualization.loader

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Result of loading a verified guest APK's DEX code into the host process. */
data class LoadedGuestApk internal constructor(
    val packageName: String,
    val apkFile: File,
    val classLoader: ClassLoader,
    val apkSha256: String,
    val signerSha256: Set<String>,
)

class GuestApkLoadException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Copies an explicitly selected APK into private, read-only host storage, verifies its
 * package and signer pins, then creates a DexClassLoader for its DEX code.
 *
 * This is a code loader, not an Android sandbox or a complete Activity virtualization
 * engine. Loaded code runs inside this process with the host UID and permissions. Native
 * libraries, guest resources, package-manager virtualization, and arbitrary guest
 * Activity attachment are intentionally not handled here.
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
    fun load(sourceApk: File): LoadedGuestApk = synchronized(lock) {
        if (!sourceApk.isFile || !sourceApk.canRead()) {
            throw GuestApkLoadException("Guest APK is not a readable regular file")
        }

        val privateDirectory = File(appContext.noBackupFilesDir, GUEST_APK_DIRECTORY)
        if (!privateDirectory.exists() && !privateDirectory.mkdirs()) {
            throw GuestApkLoadException("Could not create private guest APK directory")
        }
        if (!privateDirectory.isDirectory) {
            throw GuestApkLoadException("Guest APK storage path is not a directory")
        }

        val incoming = try {
            File.createTempFile("guest-", ".apk", privateDirectory)
        } catch (error: Exception) {
            throw GuestApkLoadException("Could not create private APK staging file", error)
        }

        try {
            copyAndSync(sourceApk, incoming)
            val packageInfo = readPackageInfo(incoming)
            val signers = signerDigests(packageInfo)
            trustPolicy.verify(packageInfo.packageName, signers)
            val apkDigest = sha256(incoming)
            val cachedApk = File(privateDirectory, "$apkDigest.apk")

            if (cachedApk.exists()) {
                if (!cachedApk.isFile) {
                    throw GuestApkLoadException("Cached guest APK path is not a regular file")
                }
                incoming.delete()
                if (cachedApk.canWrite() && !cachedApk.setReadOnly()) {
                    throw GuestApkLoadException("Could not make cached guest APK read-only")
                }
            } else {
                if (!incoming.setReadOnly()) {
                    throw GuestApkLoadException("Could not make staged guest APK read-only")
                }
                if (!incoming.renameTo(cachedApk)) {
                    if (cachedApk.exists()) {
                        incoming.delete()
                    } else {
                        throw GuestApkLoadException("Could not atomically finalize guest APK")
                    }
                }
            }

            val cachedInfo = readPackageInfo(cachedApk)
            val cachedSigners = trustPolicy.verify(cachedInfo.packageName, signerDigests(cachedInfo))
            val cachedDigest = sha256(cachedApk)
            if (cachedDigest != apkDigest) {
                throw GuestApkLoadException("Private guest APK cache failed integrity verification")
            }
            val cachedGuest = loadedByDigest[cachedDigest]
            if (cachedGuest != null) return cachedGuest

            val optimizedDirectory = File(appContext.codeCacheDir, DEX_CACHE_DIRECTORY)
            if (!optimizedDirectory.exists() && !optimizedDirectory.mkdirs()) {
                throw GuestApkLoadException("Could not create DEX optimization directory")
            }
            if (!optimizedDirectory.isDirectory) {
                throw GuestApkLoadException("DEX optimization path is not a directory")
            }

            val guest = try {
                LoadedGuestApk(
                    packageName = cachedInfo.packageName,
                    apkFile = cachedApk,
                    classLoader = DexClassLoader(
                        cachedApk.absolutePath,
                        optimizedDirectory.absolutePath,
                        null,
                        appContext.classLoader,
                    ),
                    apkSha256 = cachedDigest,
                    signerSha256 = cachedSigners,
                )
            } catch (error: Exception) {
                throw GuestApkLoadException("Could not create guest DEX class loader", error)
            }
            loadedByDigest[cachedDigest] = guest
            guest
        } catch (error: GuestApkLoadException) {
            throw error
        } catch (error: UntrustedGuestApkException) {
            throw GuestApkLoadException("Guest APK failed trust verification", error)
        } catch (error: Exception) {
            throw GuestApkLoadException("Guest APK could not be loaded", error)
        } finally {
            if (incoming.exists()) incoming.delete()
        }
    }

    private fun copyAndSync(source: File, destination: File) {
        try {
            FileOutputStream(destination).use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
                output.fd.sync()
            }
        } catch (error: Exception) {
            throw GuestApkLoadException("Could not copy guest APK into private storage", error)
        }
    }

    private fun readPackageInfo(apk: File): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            legacySignatureFlag()
        }
        return appContext.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw GuestApkLoadException("File is not a valid Android APK archive")
    }

    private fun signerDigests(packageInfo: PackageInfo): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            legacySignatures(packageInfo)
        } ?: emptyArray()

        if (signatures.isEmpty()) {
            throw GuestApkLoadException("APK archive has no readable signing certificates")
        }
        return signatures.map { signature -> sha256(signature.toByteArray()) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun legacySignatureFlag(): Int = PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun legacySignatures(packageInfo: PackageInfo): Array<Signature>? = packageInfo.signatures

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

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
