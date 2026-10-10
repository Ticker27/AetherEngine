package com.aether.host.virtualization.loader

import java.io.ByteArrayOutputStream

/** Synthetic compiled XML, deliberately not an APK signer/fixture or Android-dependent parser. */
internal object GuestTestAxml {
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    private data class Attribute(val namespace: String?, val name: String, val type: Int, val value: Any)

    fun manifest(
        split: String? = null,
        packageName: String = "com.aether.fixture",
        versionCode: Int = 1,
        versionName: String? = "1.0.0",
        feature: Boolean = false,
        configForSplit: String? = null,
        hasCode: Boolean = split == null,
        usesSplit: Boolean = false,
        utf8: Boolean = true,
    ): ByteArray {
        val attributes = arrayListOf(
            Attribute(null, "package", 3, packageName),
            Attribute(ANDROID, "versionCode", 0x10, versionCode),
        )
        versionName?.let { attributes.add(Attribute(ANDROID, "versionName", 3, it)) }
        split?.let { attributes.add(Attribute(null, "split", 3, it)) }
        if (feature) attributes.add(Attribute(null, "isFeatureSplit", 0x12, -1))
        configForSplit?.let { attributes.add(Attribute(null, "configForSplit", 3, it)) }
        val applicationAttributes = listOf(Attribute(ANDROID, "hasCode", 0x12, if (hasCode) -1 else 0))
        val pool = linkedSetOf("android", ANDROID, "manifest", "application", "uses-split")
        (attributes + applicationAttributes).forEach {
            it.namespace?.let(pool::add)
            pool.add(it.name)
            if (it.type == 3) pool.add(it.value as String)
        }
        val strings = pool.toList()
        fun index(value: String?): Int = if (value == null) -1 else strings.indexOf(value)
        fun chunk(type: Int, header: Int, payload: ByteArray): ByteArray = LittleEndian().apply {
            short(type); short(header); int(8 + payload.size); bytes(payload)
        }.result()
        fun node(type: Int, extension: ByteArray): ByteArray = chunk(type, 16, LittleEndian().apply {
            int(1); int(-1); bytes(extension)
        }.result())
        fun namespace(type: Int): ByteArray = node(type, LittleEndian().apply {
            int(index("android")); int(index(ANDROID))
        }.result())
        fun start(name: String, values: List<Attribute>): ByteArray = node(0x102, LittleEndian().apply {
            int(-1); int(index(name)); short(20); short(20); short(values.size)
            short(0); short(0); short(0)
            values.forEach {
                int(index(it.namespace)); int(index(it.name)); int(-1)
                short(8); byte(0); byte(it.type)
                int(if (it.type == 3) index(it.value as String) else it.value as Int)
            }
        }.result())
        fun end(name: String): ByteArray = node(0x103, LittleEndian().apply {
            int(-1); int(index(name))
        }.result())
        val stringData = LittleEndian()
        val offsets = strings.map {
            val offset = stringData.size
            if (utf8) {
                val encoded = it.toByteArray(Charsets.UTF_8)
                stringData.length8(it.length); stringData.length8(encoded.size)
                stringData.bytes(encoded); stringData.byte(0)
            } else {
                stringData.short(it.length); stringData.bytes(it.toByteArray(Charsets.UTF_16LE)); stringData.short(0)
            }
            offset
        }
        while (stringData.size % 4 != 0) stringData.byte(0)
        val stringPool = chunk(1, 28, LittleEndian().apply {
            int(strings.size); int(0); int(if (utf8) 0x100 else 0)
            int(28 + strings.size * 4); int(0)
            offsets.forEach(::int); bytes(stringData.result())
        }.result())
        val chunks = LittleEndian().apply {
            bytes(stringPool); bytes(namespace(0x100)); bytes(start("manifest", attributes))
            if (usesSplit) { bytes(start("uses-split", emptyList())); bytes(end("uses-split")) }
            bytes(start("application", applicationAttributes)); bytes(end("application"))
            bytes(end("manifest")); bytes(namespace(0x101))
        }.result()
        return chunk(3, 8, chunks)
    }

    private class LittleEndian {
        private val output = ByteArrayOutputStream()
        val size: Int get() = output.size()
        fun byte(value: Int) { output.write(value) }
        fun short(value: Int) { byte(value); byte(value ushr 8) }
        fun int(value: Int) { short(value); short(value ushr 16) }
        fun bytes(value: ByteArray) { output.write(value) }
        fun length8(value: Int) {
            require(value <= 0x7fff)
            if (value >= 0x80) { byte((value ushr 8) or 0x80); byte(value) } else byte(value)
        }
        fun result(): ByteArray = output.toByteArray()
    }
}
