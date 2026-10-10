package com.aether.host.virtualization.loader

import dalvik.system.DexClassLoader

/**
 * Class-loader policy for the verified guest DEX path. Framework and Aether API classes are
 * always shared from the host; other classes must resolve from the verified guest APK.
 * Missing guest dependencies never silently bind unrelated host classes. No hidden-API reflection or native-library
 * search path is enabled.
 *
 * This does not attach Android components or resources. A guest Activity still cannot be
 * instantiated as a framework Activity using this loader alone.
 */
class GuestClassLoaderProxy internal constructor(
    dexPath: String,
    optimizedDirectory: String,
    private val hostParent: ClassLoader,
) : DexClassLoader(
    dexPath,
    optimizedDirectory,
    null,
    hostParent,
) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        synchronized(this) {
            val loaded = findLoadedClass(name) ?: if (isHostOwnedClass(name)) {
                // Never allow guest DEX to shadow framework or Aether bridge classes.
                hostParent.loadClass(name)
            } else {
                loadGuestFirst(name)
            }
            if (resolve && loaded.classLoader === this) resolveClass(loaded)
            loaded
        }

    private fun loadGuestFirst(name: String): Class<*> = findClass(name)

    private fun isHostOwnedClass(name: String): Boolean =
        PARENT_FIRST_PREFIXES.any { prefix -> name.startsWith(prefix) }

    internal companion object {
        private val PARENT_FIRST_PREFIXES = listOf(
            "java.",
            "javax.",
            "android.",
            "com.android.",
            "dalvik.",
            "org.json.",
            "org.w3c.",
            "org.xml.",
            "org.xmlpull.",
            "kotlin.",
            "kotlinx.",
            "com.aether.host.",
            "com.aether.guest.api.",
        )

        /** Exposed to unit tests so delegation policy cannot silently drift. */
        fun isParentFirstForTests(className: String): Boolean =
            PARENT_FIRST_PREFIXES.any { prefix -> className.startsWith(prefix) }
    }
}
