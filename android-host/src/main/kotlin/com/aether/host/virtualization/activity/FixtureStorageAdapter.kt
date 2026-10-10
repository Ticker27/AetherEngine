package com.aether.host.virtualization.activity

import com.aether.guest.api.GuestStorage
import com.aether.host.virtualization.filesystem.GuestVirtualFileSystem

/** Exposes only the guest-sandboxed file operations of the guest's virtual file system. */
internal class FixtureStorageAdapter(private val vfs: GuestVirtualFileSystem) : GuestStorage {
    override fun openInput(path: String) = vfs.openInput(path)
    override fun openOutput(path: String, append: Boolean) = vfs.openOutput(path, append)
    override fun exists(path: String) = vfs.exists(path)
}
