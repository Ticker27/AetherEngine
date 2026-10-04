package com.aether.host.target

/**
 * Identity of the single guest release currently accepted by Aether.
 * Values follow the 8 Ball Pool 56.30.0 listing and must be checked against its APK
 * manifest when that exact binary is available.
 */
object TargetApkContract {
    const val PACKAGE_NAME = "com.miniclip.eightballpool"
    const val VERSION_NAME = "56.30.0"
    const val VERSION_CODE = 4028L
}
