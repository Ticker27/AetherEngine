package com.aether.host.target

/**
 * Identity of the single guest release currently accepted by Aether.
 * Package/version identity is verified from the supplied 56.31.0 APK manifest and
 * the installed package metadata. This does not establish signer trust or launch support.
 */
object TargetApkContract {
    const val PACKAGE_NAME = "com.miniclip.eightballpool"
    const val VERSION_NAME = "56.31.0"
    const val VERSION_CODE = 4035L
}
