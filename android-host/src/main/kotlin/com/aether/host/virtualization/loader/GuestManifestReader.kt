package com.aether.host.virtualization.loader

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Small bounded reader for compiled Android binary XML, not a general resource resolver.
 * Resource references for identity fields, text XML, unknown chunks, and malformed encodings
 * fail closed. No hidden Android APIs; PackageManager must independently supply archive metadata.
 */
internal object GuestManifestReader {
    fun read(bytes: ByteArray): GuestManifestIdentity = Reader(bytes).read()

    private class Reader(private val bytes: ByteArray) {
        private var strings: List<String>? = null
        private val elements = ArrayList<Pair<String?, String>>()
        private val namespaces = ArrayList<Pair<Int, Int>>()
        private var rootAttributes: Map<Pair<String?, String>, Value>? = null
        private var applicationSeen = false
        private var hasCode = true
        private var usesSplit = false
        private var rootClosed = false
        private var resourceMapSeen = false

        fun read(): GuestManifestIdentity {
            checkGuest(bytes.size in 8..GuestApkLimits.MAX_MANIFEST_BYTES, "Invalid binary manifest size")
            checkGuest(u16(0) == 3 && u16(2) == 8 && uint(4) == bytes.size.toLong(), "Manifest is not supported binary AXML")
            var cursor = 8
            var chunks = 0
            while (cursor < bytes.size) {
                checkGuest(++chunks <= 16_384, "Too many manifest chunks")
                range(cursor, 8, bytes.size)
                val type = u16(cursor)
                val header = u16(cursor + 2)
                val size = uint(cursor + 4)
                checkGuest(header >= 8 && size >= header && size <= bytes.size - cursor, "Invalid AXML chunk bounds")
                val end = cursor + size.toInt()
                when (type) {
                    1 -> {
                        checkGuest(strings == null && rootAttributes == null, "Duplicate or late AXML string pool")
                        strings = stringPool(cursor, header, end)
                    }
                    0x180 -> {
                        checkGuest(!resourceMapSeen && strings != null && rootAttributes == null && header == 8 &&
                            (end - cursor - 8) % 4 == 0, "Invalid AXML resource map")
                        resourceMapSeen = true
                        checkGuest((end - cursor - 8) / 4 <= strings!!.size, "Oversized AXML resource map")
                    }
                    0x100, 0x101 -> {
                        nodeHeader(cursor, header, end, 8)
                        val prefix = int(cursor + header)
                        val uri = int(cursor + header + 4)
                        text(prefix)
                        text(uri)
                        if (type == 0x100) {
                            checkGuest(namespaces.size < 64, "Too many manifest namespaces")
                            namespaces.add(prefix to uri)
                        } else {
                            checkGuest(namespaces.lastOrNull() == prefix to uri, "Unbalanced manifest namespaces")
                            namespaces.removeAt(namespaces.lastIndex)
                        }
                    }
                    0x102 -> startElement(cursor, header, end)
                    0x103 -> {
                        nodeHeader(cursor, header, end, 8)
                        val element = optionalText(int(cursor + header)) to text(int(cursor + header + 4))
                        checkGuest(elements.lastOrNull() == element, "Unbalanced manifest elements")
                        elements.removeAt(elements.lastIndex)
                        if (elements.isEmpty()) rootClosed = true
                    }
                    else -> throw GuestApkLoadException("Unsupported binary manifest chunk: $type")
                }
                cursor = end
            }
            checkGuest(rootClosed && elements.isEmpty() && namespaces.isEmpty(), "Incomplete binary manifest")
            val attributes = rootAttributes ?: throw GuestApkLoadException("Manifest root is missing")
            val packageName = attributes[null to "package"]?.string()
                ?: throw GuestApkLoadException("Manifest package is missing")
            val low = attributes[ANDROID to "versionCode"]?.integer()
                ?: throw GuestApkLoadException("Manifest version code is missing")
            val major = attributes[ANDROID to "versionCodeMajor"]?.integer() ?: 0L
            checkGuest(major <= Int.MAX_VALUE.toLong(), "Unsupported manifest version code major")
            return GuestManifestIdentity(
                packageName = packageName,
                versionName = attributes[ANDROID to "versionName"]?.string(),
                versionCode = (major shl 32) or low,
                splitName = attributes[null to "split"]?.string(),
                featureSplit = declaration(attributes, "isFeatureSplit")?.boolean() ?: false,
                configForSplit = declaration(attributes, "configForSplit")?.string(),
                hasCode = hasCode,
                usesSplit = usesSplit,
            )
        }

        private fun declaration(attributes: Map<Pair<String?, String>, Value>, name: String): Value? {
            val plain = attributes[null to name]
            val android = attributes[ANDROID to name]
            checkGuest(plain == null || android == null, "Ambiguous manifest declaration: $name")
            return plain ?: android
        }

        private fun startElement(start: Int, header: Int, end: Int) {
            nodeHeader(start, header, end, 20)
            checkGuest(!rootClosed && elements.size < 64, "Invalid manifest depth or extra root")
            val extension = start + header
            val namespace = optionalText(int(extension))
            val name = text(int(extension + 4))
            val attributeStart = u16(extension + 8)
            val attributeSize = u16(extension + 10)
            val count = u16(extension + 12)
            checkGuest(attributeStart >= 20 && attributeSize == 20 && count <= 256, "Invalid manifest attributes")
            val offset = extension + attributeStart
            range(offset, count * attributeSize, end)
            val attributes = HashMap<Pair<String?, String>, Value>()
            repeat(count) { index ->
                val position = offset + index * attributeSize
                val key = optionalText(int(position)) to text(int(position + 4))
                val raw = optionalText(int(position + 8))
                checkGuest(u16(position + 12) == 8 && byte(position + 14) == 0, "Invalid AXML typed value")
                val value = Value(byte(position + 15), uint(position + 16), raw)
                checkGuest(attributes.put(key, value) == null, "Duplicate manifest attribute")
            }
            if (elements.isEmpty()) {
                checkGuest(namespace == null && name == "manifest" && rootAttributes == null, "Invalid manifest root")
                rootAttributes = attributes
            } else if (elements.size == 1 && namespace == null) {
                if (name == "application") {
                    checkGuest(!applicationSeen, "Duplicate manifest application")
                    applicationSeen = true
                    hasCode = attributes[ANDROID to "hasCode"]?.boolean() ?: true
                }
                if (name == "uses-split") usesSplit = true
            }
            elements.add(namespace to name)
        }

        private inner class Value(private val type: Int, private val data: Long, private val raw: String?) {
            fun string(): String {
                checkGuest(type == 3 && data <= Int.MAX_VALUE, "Manifest identity requires a literal string; resource references unsupported")
                val value = text(data.toInt())
                checkGuest(raw == null || raw == value, "Conflicting raw AXML string value")
                return value
            }

            fun integer(): Long {
                checkGuest(type == 0x10 || type == 0x11, "Manifest identity requires a literal integer")
                if (raw != null) {
                    val parsed = if (raw.startsWith("0x", true)) raw.drop(2).toLongOrNull(16) else raw.toLongOrNull()
                    checkGuest(parsed == data, "Conflicting raw AXML integer value")
                }
                return data
            }

            fun boolean(): Boolean {
                checkGuest(type == 0x12 && (data == 0L || data == 1L || data == 0xffffffffL), "Invalid AXML boolean")
                val value = data != 0L
                checkGuest(raw == null || raw == value.toString(), "Conflicting raw AXML boolean value")
                return value
            }
        }

        private fun stringPool(start: Int, header: Int, end: Int): List<String> {
            checkGuest(header == 28, "Unsupported AXML string pool header")
            range(start, header, end)
            val count = uint(start + 8)
            val styles = uint(start + 12)
            val flags = uint(start + 16)
            val dataStart = uint(start + 20)
            val styleStart = uint(start + 24)
            checkGuest(count <= 8192 && styles == 0L && styleStart == 0L && flags and 0x101L.inv() == 0L,
                "Unsupported or excessive AXML string pool")
            checkGuest(dataStart >= header + count * 4 && dataStart <= end - start, "Invalid AXML string pool offsets")
            range(start + header, count.toInt() * 4, end)
            val utf8 = flags and 0x100L != 0L
            var decodedCharacters = 0L
            return List(count.toInt()) { index ->
                val relative = uint(start + header + index * 4)
                checkGuest(relative < end - start - dataStart, "Invalid AXML string offset")
                var position = start + dataStart.toInt() + relative.toInt()
                val (characters, next) = length(position, utf8, end)
                decodedCharacters += characters
                checkGuest(decodedCharacters <= GuestApkLimits.MAX_MANIFEST_BYTES, "Manifest string allocation exceeds limit")
                position = next
                val byteCount: Int
                if (utf8) {
                    val result = length(position, true, end)
                    byteCount = result.first
                    position = result.second
                } else {
                    checkGuest(characters <= GuestApkLimits.MAX_MANIFEST_BYTES / 2, "Excessive AXML string length")
                    byteCount = characters * 2
                }
                range(position, byteCount + if (utf8) 1 else 2, end)
                checkGuest(if (utf8) byte(position + byteCount) == 0 else u16(position + byteCount) == 0,
                    "Unterminated AXML string")
                val charset = if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE
                val value = try {
                    charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, position, byteCount)).toString()
                } catch (error: Exception) {
                    throw GuestApkLoadException("Invalid manifest string encoding", error)
                }
                checkGuest(value.length == characters && !value.contains('\u0000'), "Invalid manifest string length")
                value
            }
        }

        private fun length(position: Int, utf8: Boolean, end: Int): Pair<Int, Int> {
            range(position, if (utf8) 1 else 2, end)
            val first = if (utf8) byte(position) else u16(position)
            val mask = if (utf8) 0x80 else 0x8000
            if (first and mask == 0) return first to (position + if (utf8) 1 else 2)
            range(position, if (utf8) 2 else 4, end)
            val size = if (utf8) ((first and 0x7f) shl 8) or byte(position + 1)
                else ((first and 0x7fff) shl 16) or u16(position + 2)
            checkGuest(size <= GuestApkLimits.MAX_MANIFEST_BYTES, "Excessive manifest string length")
            return size to (position + if (utf8) 2 else 4)
        }

        private fun nodeHeader(start: Int, header: Int, end: Int, extensionSize: Int) {
            checkGuest(strings != null && header == 16, "Invalid AXML node header")
            range(start, header + extensionSize, end)
        }

        private fun optionalText(index: Int): String? = if (index == -1) null else text(index)
        private fun text(index: Int): String {
            val pool = strings ?: throw GuestApkLoadException("Manifest string pool missing")
            checkGuest(index >= 0 && index < pool.size, "Invalid manifest string index")
            return pool[index]
        }

        private fun range(position: Int, size: Int, end: Int) {
            checkGuest(position >= 0 && size >= 0 && position <= end && size <= end - position,
                "Binary manifest field exceeds bounds")
        }
        private fun byte(position: Int): Int {
            range(position, 1, bytes.size)
            return bytes[position].toInt() and 0xff
        }
        private fun u16(position: Int): Int = byte(position) or (byte(position + 1) shl 8)
        private fun int(position: Int): Int = u16(position) or (u16(position + 2) shl 16)
        private fun uint(position: Int): Long = int(position).toLong() and 0xffffffffL
    }

    private const val ANDROID = "http://schemas.android.com/apk/res/android"
}
