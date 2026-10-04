package com.aether.host.virtualization.filesystem

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Raised when a guest-scoped path is invalid or escapes its private backing directory. */
class GuestFileSystemException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * App-level virtual filesystem facade for host-owned guest adapters.
 *
 * Paths are relative to a per-APK directory below app-private no-backup storage. Traversal,
 * absolute paths, and canonical paths outside that root are rejected. This works on the host's
 * minSdk without relying on hidden Android APIs. It is deliberately not an OS mount or a
 * sandbox: arbitrary in-process guest code can bypass this facade and shares the host UID.
 */
class GuestVirtualFileSystem internal constructor(rootDirectory: File) {
    private val root: File

    init {
        try {
            val absoluteRoot = rootDirectory.absoluteFile
            if (absoluteRoot.canonicalFile.path != absoluteRoot.path) {
                throw GuestFileSystemException("Guest storage root must not contain a symbolic link")
            }
            if (!absoluteRoot.exists() && !absoluteRoot.mkdirs()) {
                throw GuestFileSystemException("Could not create guest storage root")
            }
            if (!absoluteRoot.isDirectory) {
                throw GuestFileSystemException("Guest storage root is not a directory")
            }
            root = absoluteRoot.canonicalFile
        } catch (error: GuestFileSystemException) {
            throw error
        } catch (error: IOException) {
            throw GuestFileSystemException("Could not initialize guest storage root", error)
        }
    }

    /** Opens a guest-relative file for reading after resolving it beneath the backing root. */
    fun openInput(path: String): InputStream {
        val file = resolve(path, allowRoot = false)
        if (!file.isFile) throw GuestFileSystemException("Guest input is not a regular file")
        return try {
            FileInputStream(file)
        } catch (error: IOException) {
            throw GuestFileSystemException("Could not open guest input", error)
        }
    }

    /**
     * Opens a guest-relative file for writing. Parent directories are created within the
     * backing root and the final path is checked again before opening.
     */
    fun openOutput(path: String, append: Boolean = false): OutputStream {
        val components = parsePath(path, allowRoot = false)
        if (components.size > 1) makeDirectories(components.dropLast(1).joinToString("/"))
        val file = resolveComponents(components)
        if (file.isDirectory) throw GuestFileSystemException("Guest output points to a directory")
        return try {
            FileOutputStream(file, append)
        } catch (error: IOException) {
            throw GuestFileSystemException("Could not open guest output", error)
        }
    }

    /** Creates each directory component and rejects existing paths that canonicalize elsewhere. */
    fun makeDirectories(path: String) {
        val components = parsePath(path, allowRoot = true)
        var current = root
        for (component in components) {
            val next = checkedCanonicalChild(current, component)
            try {
                if (!next.exists() && !next.mkdir()) {
                    throw GuestFileSystemException("Could not create guest directory")
                }
                val verified = checkedCanonicalChild(current, component)
                if (!verified.isDirectory) {
                    throw GuestFileSystemException("Guest path component is not a directory")
                }
                current = verified
            } catch (error: GuestFileSystemException) {
                throw error
            } catch (error: IOException) {
                throw GuestFileSystemException("Could not create guest directory", error)
            }
        }
    }

    /** Lists names in a guest-relative directory and rejects entries that resolve outside root. */
    fun list(path: String = ""): List<String> {
        val directory = resolve(path, allowRoot = true)
        if (!directory.isDirectory) throw GuestFileSystemException("Guest list target is not a directory")
        val entries = directory.listFiles()
            ?: throw GuestFileSystemException("Could not list guest directory")
        try {
            entries.forEach { entry ->
                val canonical = entry.canonicalFile
                ensureInsideRoot(canonical)
                if (canonical.path != entry.absoluteFile.path) {
                    throw GuestFileSystemException("Symbolic links are not allowed in guest storage")
                }
            }
        } catch (error: GuestFileSystemException) {
            throw error
        } catch (error: IOException) {
            throw GuestFileSystemException("Could not inspect guest directory", error)
        }
        return entries.map { entry -> entry.name }.sorted()
    }

    /** Returns whether a guest-relative path exists. */
    fun exists(path: String): Boolean = resolve(path, allowRoot = true).exists()

    /** Deletes one guest-relative file or empty directory; the backing root cannot be deleted. */
    fun delete(path: String): Boolean {
        val file = resolve(path, allowRoot = false)
        if (!file.exists()) return false
        if (!file.delete()) throw GuestFileSystemException("Could not delete guest path")
        return true
    }

    private fun resolve(path: String, allowRoot: Boolean): File =
        resolveComponents(parsePath(path, allowRoot))

    private fun resolveComponents(components: List<String>): File {
        var current = root
        for (component in components) current = checkedCanonicalChild(current, component)
        return current
    }

    private fun checkedCanonicalChild(parent: File, component: String): File {
        try {
            val child = File(parent, component).absoluteFile
            val canonical = child.canonicalFile
            ensureInsideRoot(canonical)
            if (canonical.path != child.path) {
                throw GuestFileSystemException("Symbolic links are not allowed in guest storage")
            }
            return canonical
        } catch (error: GuestFileSystemException) {
            throw error
        } catch (error: IOException) {
            throw GuestFileSystemException("Could not resolve guest path", error)
        }
    }

    private fun parsePath(path: String, allowRoot: Boolean): List<String> {
        if (path.isEmpty() && allowRoot) return emptyList()
        if (path.isEmpty() || path.startsWith('/') || path.startsWith('\\') ||
            path.contains('\\') || path.contains('\u0000') || DRIVE_PREFIX.containsMatchIn(path)
        ) {
            throw GuestFileSystemException("Guest paths must be non-empty relative paths")
        }
        val components = path.split('/')
        if (components.any { it.isEmpty() || it == "." || it == ".." }) {
            throw GuestFileSystemException("Guest path contains an invalid component")
        }
        return components
    }

    private fun ensureInsideRoot(candidate: File) {
        val rootPath = root.path
        val candidatePath = candidate.path
        if (candidatePath != rootPath && !candidatePath.startsWith(rootPath + File.separator)) {
            throw GuestFileSystemException("Guest path escapes its private storage root")
        }
    }

    internal companion object {
        private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
        private val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

        /** Creates a dedicated no-backup storage root for one verified guest APK digest. */
        fun forGuest(
            noBackupFilesDir: File,
            packageName: String,
            versionCode: Long,
            apkSha256: String,
        ): GuestVirtualFileSystem {
            require(PACKAGE_NAME_PATTERN.matches(packageName)) { "Invalid guest package name" }
            require(versionCode > 0) { "Guest version code must be positive" }
            require(SHA256_PATTERN.matches(apkSha256)) { "Guest APK digest must be a SHA-256 hex value" }

            val appRoot = noBackupFilesDir.canonicalFile
            if (!appRoot.isDirectory) {
                throw GuestFileSystemException("App-private no-backup directory is unavailable")
            }
            var current = appRoot
            val components = listOf("aether-guest-data", packageName, versionCode.toString(), apkSha256)
            for (component in components) {
                val next = checkedChild(appRoot, current, component)
                try {
                    if (!next.exists() && !next.mkdir()) {
                        throw GuestFileSystemException("Could not create guest storage directory")
                    }
                    val verified = checkedChild(appRoot, current, component)
                    if (!verified.isDirectory) {
                        throw GuestFileSystemException("Guest storage component is not a directory")
                    }
                    current = verified
                } catch (error: GuestFileSystemException) {
                    throw error
                } catch (error: IOException) {
                    throw GuestFileSystemException("Could not create guest storage directory", error)
                }
            }
            return GuestVirtualFileSystem(current)
        }

        private fun checkedChild(root: File, parent: File, component: String): File {
            val child = File(parent, component).absoluteFile
            val canonical = child.canonicalFile
            val rootPath = root.path
            if (canonical.path != rootPath && !canonical.path.startsWith(rootPath + File.separator)) {
                throw GuestFileSystemException("Guest storage path escapes app-private storage")
            }
            if (canonical.path != child.path) {
                throw GuestFileSystemException("Guest storage path contains a symbolic link")
            }
            return canonical
        }
    }
}
