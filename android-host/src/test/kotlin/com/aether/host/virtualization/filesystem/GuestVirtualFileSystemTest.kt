package com.aether.host.virtualization.filesystem

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestVirtualFileSystemTest {
    @Test
    fun `writes reads lists and deletes only guest-relative files`() {
        val root = Files.createTempDirectory("aether-guest-vfs").toFile()
        val vfs = GuestVirtualFileSystem(root)

        vfs.openOutput("profile/data.bin").use { output -> output.write(byteArrayOf(1, 2, 3)) }
        vfs.openOutput("profile/data.bin", append = true).use { output -> output.write(4) }

        assertTrue(vfs.exists("profile/data.bin"))
        assertEquals(listOf("data.bin"), vfs.list("profile"))
        assertEquals(listOf(1, 2, 3, 4), vfs.openInput("profile/data.bin").use { it.readBytes().map { byte -> byte.toInt() } })
        assertTrue(vfs.delete("profile/data.bin"))
        assertFalse(vfs.exists("profile/data.bin"))
    }

    @Test
    fun `rejects absolute traversal and ambiguous paths`() {
        val root = Files.createTempDirectory("aether-guest-vfs").toFile()
        val vfs = GuestVirtualFileSystem(root)

        listOf("../outside", "profile/../../outside", "/data/local/tmp", "C:\\temp\\x", "a//b", "a/./b")
            .forEach { path ->
                org.junit.Assert.assertThrows(GuestFileSystemException::class.java) {
                    vfs.openOutput(path)
                }
            }
    }

    @Test
    fun `rejects symlinks that point outside the guest root`() {
        val root = Files.createTempDirectory("aether-guest-vfs").toFile()
        val outside = Files.createTempDirectory("aether-vfs-outside")
        val link: Path = root.toPath().resolve("escape")
        Files.createSymbolicLink(link, outside)
        val vfs = GuestVirtualFileSystem(root)

        org.junit.Assert.assertThrows(GuestFileSystemException::class.java) {
            vfs.openOutput("escape/should-not-exist")
        }
        assertFalse(Files.exists(outside.resolve("should-not-exist")))
    }

    @Test
    fun `guest storage roots are partitioned by package version and APK digest`() {
        val appRoot = Files.createTempDirectory("aether-no-backup").toFile()
        val firstDigest = "a".repeat(64)
        val secondDigest = "b".repeat(64)

        val first = GuestVirtualFileSystem.forGuest(
            appRoot,
            "com.miniclip.eightballpool",
            4028L,
            firstDigest,
        )
        val sameApk = GuestVirtualFileSystem.forGuest(
            appRoot,
            "com.miniclip.eightballpool",
            4028L,
            firstDigest,
        )
        val otherApk = GuestVirtualFileSystem.forGuest(
            appRoot,
            "com.miniclip.eightballpool",
            4028L,
            secondDigest,
        )

        first.openOutput("save.bin").use { it.write(10) }
        assertTrue(sameApk.exists("save.bin"))
        assertFalse(otherApk.exists("save.bin"))
    }
}
