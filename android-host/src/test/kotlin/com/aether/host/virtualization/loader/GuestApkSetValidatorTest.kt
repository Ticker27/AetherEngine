package com.aether.host.virtualization.loader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GuestApkSetValidatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val metadata = HashMap<File, GuestArchiveMetadata>()
    private val policy = GuestApkTrustPolicy(setOf(SIGNER, OTHER_SIGNER), PACKAGE, "1.0.0", 1L)
    private fun validator() = GuestApkSetValidator(policy) { file, _ -> metadata.getValue(file) }

    @Test fun `ordering is canonical and digest includes names and each member`() {
        val base = archive("base", GuestTestAxml.manifest())
        val english = archive("en", GuestTestAxml.manifest(split = "config.en"))
        val density = archive("density", GuestTestAxml.manifest(split = "config.xhdpi"))
        val first = validator().validate(GuestApkSet(base, listOf(
            GuestApkSplit("config.xhdpi", density), GuestApkSplit("config.en", english),
        )))
        val second = validator().validate(GuestApkSet(base, listOf(
            GuestApkSplit("config.en", english), GuestApkSplit("config.xhdpi", density),
        )))
        assertEquals(listOf("", "config.en", "config.xhdpi"), first.members.map { it.name })
        assertEquals(first.sha256, second.sha256)
        assertNotEquals(first.sha256, validator().validate(GuestApkSet(base)).sha256)
        assertNotEquals(first.base.apkSha256, first.sha256)
        val pairs = first.members.map { it.name to it.apkSha256 }
        assertNotEquals(first.sha256, GuestApkSetValidator.packageSetDigest(pairs.map {
            if (it.first == "config.en") "config.fr" to it.second else it
        }))
        assertNotEquals(first.sha256, GuestApkSetValidator.packageSetDigest(pairs.map {
            if (it.first == "config.en") it.first to "f".repeat(64) else it
        }))
    }

    @Test fun `wrong logical name is rejected even with trusted metadata`() {
        val base = archive("base", GuestTestAxml.manifest())
        val split = archive("en", GuestTestAxml.manifest(split = "config.en"))
        assertRejects("AXML identity", base, GuestApkSplit("config.fr", split))
    }

    @Test fun `wrong package or version cannot borrow trusted base metadata`() {
        val base = archive("base", GuestTestAxml.manifest())
        val split = archive("en", GuestTestAxml.manifest(split = "config.en", versionCode = 2))
        assertRejects("metadata differ", base, GuestApkSplit("config.en", split))
        metadata[split] = GuestArchiveMetadata(PACKAGE, "1.0.0", 2, setOf(SIGNER))
        assertRejects("trust", base, GuestApkSplit("config.en", split))
        metadata[split] = GuestArchiveMetadata("com.aether.other", "1.0.0", 1, setOf(SIGNER))
        assertRejects("trust", base, GuestApkSplit("config.en", split))
        metadata[split] = GuestArchiveMetadata(PACKAGE, "1.1.0", 1, setOf(SIGNER))
        assertRejects("trust", base, GuestApkSplit("config.en", split))
    }

    @Test fun `individually pinned but different signer sets are rejected`() {
        val base = archive("base", GuestTestAxml.manifest())
        val split = archive("en", GuestTestAxml.manifest(split = "config.en"))
        metadata[split] = GuestArchiveMetadata(PACKAGE, "1.0.0", 1, setOf(OTHER_SIGNER))
        assertRejects("different signer sets", base, GuestApkSplit("config.en", split))
        metadata[split] = GuestArchiveMetadata(PACKAGE, "1.0.0", 1, emptySet())
        assertRejects("trust", base, GuestApkSplit("config.en", split))
    }

    @Test fun `duplicate names repeated file repeated content and absent files are rejected`() {
        val base = archive("base", GuestTestAxml.manifest())
        val split = archive("en", GuestTestAxml.manifest(split = "config.en"))
        assertRejects("Duplicate", base, GuestApkSplit("config.en", split), GuestApkSplit("config.en", split))
        assertRejects("Repeated APK file", base, GuestApkSplit("config.en", base))
        val sameBytes = temporary.newFile("identical.apk").also { it.writeBytes(base.readBytes()) }
        assertRejects("Repeated APK content", base, GuestApkSplit("config.en", sameBytes))
        assertRejects("base APK", File(temporary.root, "missing-base.apk"))
        assertRejects("not a readable", base, GuestApkSplit("config.en", File(temporary.root, "missing-split.apk")))
    }

    @Test fun `invalid base declarations feature splits feature dependencies and split code fail closed`() {
        assertRejects("base declares", archive("splitAsBase", GuestTestAxml.manifest(split = "config.en")))
        val base = archive("base", GuestTestAxml.manifest())
        val feature = archive("feature", GuestTestAxml.manifest(split = "config.en", feature = true))
        assertRejects("feature splits", base, GuestApkSplit("config.en", feature))
        val dependent = archive("dependent", GuestTestAxml.manifest(split = "config.en", configForSplit = "feature"))
        assertRejects("feature splits", base, GuestApkSplit("config.en", dependent))
        val code = archive("code", GuestTestAxml.manifest(split = "config.en", hasCode = true))
        assertRejects("hasCode=false", base, GuestApkSplit("config.en", code))
        val dex = archive("dex", GuestTestAxml.manifest(split = "config.en"), "classes2.dex")
        assertRejects("DEX code", base, GuestApkSplit("config.en", dex))
        assertRejects("depends", archive("dependency", GuestTestAxml.manifest(usesSplit = true)))
    }

    @Test fun `native capability requests fail closed and native entries are inspection only`() {
        val base = archive("base", GuestTestAxml.manifest(), "lib/arm64-v8a/libguest.so")
        val error = assertThrows(GuestApkLoadException::class.java) {
            validator().validate(GuestApkSet(base, requiresNativeLibraries = true))
        }
        assertTrue(error.message.orEmpty().contains("Native library capability"))
        assertEquals(listOf("lib/arm64-v8a/libguest.so"), validator().validate(GuestApkSet(base)).base.nativeLibraryEntries)
    }

    @Test fun `validation snapshots signers and request splits instead of retaining mutable input`() {
        val base = archive("base", GuestTestAxml.manifest())
        val pins = mutableSetOf(SIGNER)
        val snapshot = GuestArchiveMetadata(PACKAGE, "1.0.0", 1, pins)
        pins.clear()
        metadata[base] = snapshot
        val splits = mutableListOf<GuestApkSplit>()
        val set = GuestApkSet(base, splits)
        splits.add(GuestApkSplit("config.absent", File("missing.apk")))
        assertEquals(1, validator().validate(set).members.size)
        assertEquals(setOf(SIGNER), snapshot.signerSha256)
    }

    @Test fun `split may omit versionName but cannot invent a new version`() {
        val base = archive("base", GuestTestAxml.manifest())
        val split = archive("en", GuestTestAxml.manifest(split = "config.en", versionName = null))
        metadata[split] = GuestArchiveMetadata(PACKAGE, null, 1, setOf(SIGNER))
        val set = validator().validate(GuestApkSet(base, listOf(GuestApkSplit("config.en", split))))
        assertEquals("1.0.0", set.members.last().versionName)
    }

    @Test fun `rehash and revalidation notice changed private archive bytes`() {
        val base = archive("base", GuestTestAxml.manifest())
        val first = validator().validate(GuestApkSet(base)).sha256
        base.writeBytes("not an apk".toByteArray())
        assertNotEquals(first, GuestApkSetValidator.sha256(base))
        assertThrows(GuestApkLoadException::class.java) { validator().validate(GuestApkSet(base)) }
    }

    @Test fun `limits reject excessive requests traversal entries and oversized manifests`() {
        val base = archive("base", GuestTestAxml.manifest())
        val tooMany = (0..GuestApkLimits.MAX_SPLITS).map { GuestApkSplit("config.s$it", base) }
        assertThrows(GuestApkLoadException::class.java) { validator().validate(GuestApkSet(base, tooMany)) }
        assertRejects("Invalid requested", base, GuestApkSplit("../bad", base))
        assertRejects("entry name", archive("traversal", GuestTestAxml.manifest(), "../payload"))
        assertRejects("Manifest exceeds", archive("huge", ByteArray(GuestApkLimits.MAX_MANIFEST_BYTES + 1)))
    }

    private fun assertRejects(message: String, base: File, vararg splits: GuestApkSplit) {
        val error = assertThrows(GuestApkLoadException::class.java) {
            validator().validate(GuestApkSet(base, splits.toList()))
        }
        assertTrue("Expected '$message', got '${error.message}'", error.message.orEmpty().contains(message))
    }

    private fun archive(name: String, manifest: ByteArray, extraEntry: String? = null): File {
        val file = temporary.newFile("$name.apk")
        ZipOutputStream(file.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("AndroidManifest.xml")); output.write(manifest); output.closeEntry()
            if (extraEntry != null) {
                output.putNextEntry(ZipEntry(extraEntry)); output.write(byteArrayOf(1, 2, 3)); output.closeEntry()
            }
        }
        metadata[file] = GuestArchiveMetadata(PACKAGE, "1.0.0", 1, setOf(SIGNER))
        return file
    }

    private companion object {
        const val PACKAGE = "com.aether.fixture"
        val SIGNER = "a".repeat(64)
        val OTHER_SIGNER = "b".repeat(64)
    }
}
