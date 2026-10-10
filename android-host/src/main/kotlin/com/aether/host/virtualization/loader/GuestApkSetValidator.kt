package com.aether.host.virtualization.loader

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Pure JVM validation; production adapter verifies signatures and cross-checks the base with PM. */
internal class GuestApkSetValidator(
    private val trustPolicy: GuestApkTrustPolicy,
    private val metadataReader: (File, GuestManifestIdentity) -> GuestArchiveMetadata,
) {
    fun requestedMembers(set: GuestApkSet): List<Pair<String, File>> {
        checkGuest(!set.requiresNativeLibraries, "Native library capability is unsupported")
        checkGuest(set.splits.size <= GuestApkLimits.MAX_SPLITS, "Too many requested splits")
        val names = HashSet<String>()
        set.splits.forEach {
            checkGuest(it.name.length <= 128 && SPLIT_NAME.matches(it.name), "Invalid requested split name")
            checkGuest(names.add(it.name), "Duplicate requested split name: ${it.name}")
        }
        val members = listOf("" to set.baseApk) + set.splits.sortedBy { it.name }.map { it.name to it.apkFile }
        val paths = HashSet<String>()
        var bytes = 0L
        members.forEach { (name, file) ->
            checkGuest(file.isFile && file.canRead(), "Requested ${label(name)} is not a readable regular file")
            checkGuest(paths.add(file.canonicalPath), "Repeated APK file in package set")
            checkGuest(file.length() in 1..GuestApkLimits.MAX_APK_BYTES, "APK exceeds size limit or is empty")
            bytes += file.length()
            checkGuest(bytes <= GuestApkLimits.MAX_SET_BYTES, "Package set exceeds size limit")
        }
        return members
    }

    fun validate(set: GuestApkSet): VerifiedGuestApkSet {
        val seenDigests = HashSet<String>()
        var baseVersionName: String? = null
        val members = requestedMembers(set).map { (name, file) ->
            val digest = sha256(file)
            checkGuest(seenDigests.add(digest), "Repeated APK content in package set")
            preflightZip(file)
            val (manifest, nativeEntries) = inspectArchive(file, name.isNotEmpty())
            val metadata = metadataReader(file, manifest)
            // Config splits commonly omit versionName. Only inherit it from the already verified
            // base, never the caller or trust profile. An explicit different name still fails.
            val versionName = metadata.versionName ?: if (name.isNotEmpty()) baseVersionName else null
            val signers = try {
                trustPolicy.verify(metadata.packageName, versionName, metadata.versionCode, metadata.signerSha256)
            } catch (error: UntrustedGuestApkException) {
                throw GuestApkLoadException("Guest ${label(name)} failed trust verification", error)
            }
            checkGuest(manifest.packageName == metadata.packageName && manifest.versionCode == metadata.versionCode,
                "Manifest and PackageManager package/version metadata differ for ${label(name)}")
            checkGuest(manifest.versionName == null || manifest.versionName == versionName,
                "Manifest and PackageManager version name differ for ${label(name)}")
            if (name.isEmpty()) {
                checkGuest(manifest.splitName == null && !manifest.featureSplit && manifest.configForSplit == null,
                    "Requested base declares a split")
                checkGuest(!manifest.usesSplit, "Base depends on unsupported feature splits")
                checkGuest(manifest.versionName != null, "Base manifest has no readable version name")
            } else {
                checkGuest(manifest.splitName == name, "Requested split name differs from AXML identity: $name")
                checkGuest(name.startsWith("config.") && !manifest.featureSplit && !manifest.usesSplit &&
                    manifest.configForSplit.isNullOrEmpty(), "Non-base config or feature splits are unsupported")
                checkGuest(!manifest.hasCode, "Config split must explicitly declare android:hasCode=false")
            }
            checkGuest(sha256(file) == digest, "APK changed during validation")
            if (name.isEmpty()) baseVersionName = versionName
            VerifiedGuestApkMember(name, file, digest, manifest,
                versionName ?: throw GuestApkLoadException("Archive version name is unavailable"),
                java.util.Collections.unmodifiableSet(signers.toSet()), nativeEntries)
        }
        val base = members.first()
        members.drop(1).forEach {
            checkGuest(it.manifest.packageName == base.manifest.packageName &&
                it.manifest.versionCode == base.manifest.versionCode && it.versionName == base.versionName,
                "Package set members have different package/version metadata")
            checkGuest(it.signerSha256 == base.signerSha256, "Package set members have different signer sets")
        }
        return VerifiedGuestApkSet(members, packageSetDigest(members.map { it.name to it.apkSha256 }))
    }

    /** Bound central-directory allocation before ZipFile/apksig; ZIP64/multidisk are unsupported. */
    private fun preflightZip(file: File) = RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        checkGuest(length in 22..GuestApkLimits.MAX_APK_BYTES, "Invalid ZIP archive size")
        val tail = ByteArray(minOf(length, 65_557L).toInt())
        input.seek(length - tail.size)
        input.readFully(tail)
        fun u16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)
        fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or
            (u16(bytes, offset + 2).toLong() shl 16)
        val end = (tail.size - 22 downTo 0).firstOrNull {
            u32(tail, it) == 0x06054b50L && it + 22 + u16(tail, it + 20) == tail.size
        } ?: throw GuestApkLoadException("ZIP end record missing or has trailing data")
        val count = u16(tail, end + 10)
        val directoryBytes = u32(tail, end + 12)
        val offset = u32(tail, end + 16)
        checkGuest(u16(tail, end + 4) == 0 && u16(tail, end + 6) == 0 &&
            u16(tail, end + 8) == count, "Multidisk ZIP is unsupported")
        checkGuest(count in 1..GuestApkLimits.MAX_ENTRIES && directoryBytes <= 8L * 1024 * 1024 &&
            offset + directoryBytes == length - tail.size + end, "ZIP entry count or central directory exceeds limits")
        input.seek(offset)
        val record = ByteArray(46)
        var consumed = 0L
        repeat(count) {
            checkGuest(directoryBytes - consumed >= record.size, "Truncated ZIP central directory")
            input.readFully(record)
            checkGuest(u32(record, 0) == 0x02014b50L && u16(record, 34) == 0 &&
                u16(record, 8) and 1 == 0 && u16(record, 10) in listOf(0, 8) &&
                u32(record, 20) <= GuestApkLimits.MAX_APK_BYTES &&
                u32(record, 24) <= GuestApkLimits.MAX_APK_BYTES && u32(record, 42) < offset,
                "Invalid, encrypted, ZIP64 or oversized ZIP central record")
            val nameSize = u16(record, 28)
            val variableSize = nameSize + u16(record, 30) + u16(record, 32)
            checkGuest(nameSize in 1..1024 && variableSize <= directoryBytes - consumed - record.size,
                "ZIP central record exceeds bounds")
            consumed += record.size + variableSize
            input.seek(offset + consumed)
        }
        checkGuest(consumed == directoryBytes, "ZIP entry count disagrees with central directory")
    }

    private fun inspectArchive(file: File, split: Boolean): Pair<GuestManifestIdentity, List<String>> = ZipFile(file).use { zip ->
        val names = HashSet<String>()
        val nativeEntries = ArrayList<String>()
        var expanded = 0L
        var manifestBytes: ByteArray? = null
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            checkGuest(names.size < GuestApkLimits.MAX_ENTRIES, "APK entry count exceeds limit")
            val name = entry.name
            checkGuest(name.length in 1..1024 && !name.startsWith('/') && !name.contains('\\') &&
                !name.contains('\u0000') && name.split('/').none { it == ".." || it == "." }, "Invalid APK entry name")
            checkGuest(names.add(name), "Duplicate APK ZIP entry")
            checkGuest(entry.size in 0..GuestApkLimits.MAX_APK_BYTES && entry.compressedSize >= 0,
                "APK entry has invalid or excessive size")
            expanded += entry.size
            checkGuest(expanded <= GuestApkLimits.MAX_EXPANDED_BYTES, "APK expanded size exceeds limit")
            checkGuest(!split || !name.endsWith(".dex", ignoreCase = true), "Config split contains DEX code")
            if (!entry.isDirectory && (name.startsWith("lib/") || name.endsWith(".so", ignoreCase = true))) {
                nativeEntries.add(name)
            }
            if (name == "AndroidManifest.xml") {
                checkGuest(entry.size in 1..GuestApkLimits.MAX_MANIFEST_BYTES.toLong(), "Manifest exceeds size limit")
                manifestBytes = zip.getInputStream(entry).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        checkGuest(output.size() + count <= GuestApkLimits.MAX_MANIFEST_BYTES, "Manifest exceeds size limit")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray().also { checkGuest(it.size.toLong() == entry.size, "Manifest size differs from ZIP metadata") }
                }
            }
        }
        GuestManifestReader.read(manifestBytes ?: throw GuestApkLoadException("APK has no AndroidManifest.xml")) to
            java.util.Collections.unmodifiableList(nativeEntries.sorted())
    }

    companion object {
        private val SPLIT_NAME = Regex("[A-Za-z][A-Za-z0-9_.-]*")
        private fun label(name: String): String = if (name.isEmpty()) "base APK" else "split '$name'"

        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    checkGuest(total <= GuestApkLimits.MAX_APK_BYTES, "APK exceeds size limit")
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().toHex()
        }

        /** Domain separated, count + length delimited UTF-8 names and raw SHA-256 bytes; base sorts first. */
        internal fun packageSetDigest(members: List<Pair<String, String>>): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { output ->
                output.writeUTF("aether-guest-apk-set-v1")
                output.writeInt(members.size)
                members.sortedBy { it.first }.forEach { (name, sha) ->
                    checkGuest(sha.matches(Regex("[0-9a-f]{64}")), "Invalid member SHA-256")
                    val logicalName = name.toByteArray(Charsets.UTF_8)
                    output.writeInt(logicalName.size)
                    output.write(logicalName)
                    output.writeInt(32)
                    output.write(ByteArray(32) { sha.substring(it * 2, it * 2 + 2).toInt(16).toByte() })
                }
            }
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).toHex()
        }
    }
}

internal fun checkGuest(condition: Boolean, message: String) {
    if (!condition) throw GuestApkLoadException(message)
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
