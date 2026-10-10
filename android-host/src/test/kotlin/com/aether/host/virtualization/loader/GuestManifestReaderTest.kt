package com.aether.host.virtualization.loader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestManifestReaderTest {
    @Test fun `reads UTF8 and UTF16 compiled manifest identity without Android`() {
        listOf(true, false).forEach { utf8 ->
            val base = GuestManifestReader.read(GuestTestAxml.manifest(utf8 = utf8))
            assertEquals("com.aether.fixture", base.packageName)
            assertEquals("1.0.0", base.versionName)
            assertEquals(1L, base.versionCode)
            assertEquals(null, base.splitName)
            assertTrue(base.hasCode)
            val split = GuestManifestReader.read(GuestTestAxml.manifest(split = "config.arm64_v8a", utf8 = utf8))
            assertEquals("config.arm64_v8a", split.splitName)
            assertFalse(split.featureSplit)
            assertFalse(split.hasCode)
        }
    }

    @Test fun `reads actual feature config target and dependency declarations`() {
        val manifest = GuestManifestReader.read(GuestTestAxml.manifest(
            split = "feature", feature = true, configForSplit = "other", usesSplit = true,
        ))
        assertTrue(manifest.featureSplit)
        assertEquals("other", manifest.configForSplit)
        assertTrue(manifest.usesSplit)
    }

    @Test fun `rejects text XML truncated chunks and out of bounds lengths`() {
        assertInvalid("<manifest package=\"com.aether.fixture\"/>".toByteArray())
        val valid = GuestTestAxml.manifest()
        listOf(0, 1, 7, 8, 20, valid.size - 1).forEach { size -> assertInvalid(valid.copyOf(size)) }
        assertInvalid(valid.copyOf().also { it[4] = 0 })
        // String pool chunk size must not wrap or exceed the containing XML.
        assertInvalid(valid.copyOf().also { writeInt(it, 12, Int.MAX_VALUE) })
        // First string offset references outside the pool.
        assertInvalid(valid.copyOf().also { writeInt(it, 36, Int.MAX_VALUE) })
    }

    @Test fun `rejects identity resource references mismatched raw values and duplicate root attributes`() {
        val valid = GuestTestAxml.manifest()
        val root = chunkOffset(valid, 0x102)
        val firstAttribute = root + 36
        assertInvalid(valid.copyOf().also { it[firstAttribute + 15] = 1 })
        val versionCodeAttribute = firstAttribute + 20
        assertInvalid(valid.copyOf().also {
            // versionCode's name and namespace duplicate package.
            writeInt(it, versionCodeAttribute, readInt(it, firstAttribute))
            writeInt(it, versionCodeAttribute + 4, readInt(it, firstAttribute + 4))
        })
        assertInvalid(valid.copyOf().also {
            writeInt(it, firstAttribute + 8, 0) // raw string "android" differs from typed package.
        })
    }

    @Test fun `rejects malformed string encodings and byte lengths`() {
        val valid = GuestTestAxml.manifest()
        val stringStart = readInt(valid, 28) + 8
        assertInvalid(valid.copyOf().also { it[stringStart + 2] = 0xff.toByte() })
        assertInvalid(valid.copyOf().also { it[stringStart + 1] = 0x7f })
    }

    @Test fun `random bytes cannot escape bounds checked exception contract`() {
        val random = java.util.Random(56310)
        repeat(200) {
            val bytes = ByteArray(random.nextInt(256))
            random.nextBytes(bytes)
            assertInvalid(bytes)
        }
    }

    private fun assertInvalid(bytes: ByteArray) {
        assertThrows(GuestApkLoadException::class.java) { GuestManifestReader.read(bytes) }
    }

    private fun chunkOffset(bytes: ByteArray, type: Int): Int {
        var offset = 8
        while (offset < bytes.size) {
            val found = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
            if (found == type) return offset
            offset += readInt(bytes, offset + 4)
        }
        error("Test XML chunk missing")
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (0..3).fold(0) { result, index -> result or ((bytes[offset + index].toInt() and 0xff) shl (index * 8)) }

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}
