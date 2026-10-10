package com.aether.host.virtualization.loader

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S1 acceptance: the CI-built first-party APK takes the real loader path. */
@RunWith(AndroidJUnit4::class)
class FixtureGuestLoaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testAssetContext: Context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun fixtureApkLoadsThroughTrustDexAndVfsPath() {
        val fixtureApk = copyFixtureToPrivateStorage()
        val signingFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            fixtureApk.absolutePath,
            signingFlags or PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS,
        ) ?: error("fixture APK metadata could not be read")
        val signerPins = fixtureSignerPins(packageInfo)
        assertTrue("fixture must have a readable signer", signerPins.isNotEmpty())
        val policy = GuestApkTrustPolicy(
            profile = GuestApkTrustProfile(
                packageName = "com.aether.fixture",
                versionName = "1.0.0",
                versionCode = 1L,
                trustedSignerSha256 = signerPins,
            ),
        )

        val loader = DynamicApkLoader(context, policy)
        val loaded = loader.load(fixtureApk)
        val loadedAgain = loader.load(fixtureApk)

        assertEquals("com.aether.fixture", loaded.packageName)
        assertEquals("1.0.0", loaded.versionName)
        assertEquals(1L, loaded.versionCode)
        assertEquals(loaded.apkSha256, loadedAgain.apkSha256)
        assertEquals(loaded.apkFile, loadedAgain.apkFile)
        assertFalse("cached fixture APK must be read-only", loaded.apkFile.canWrite())
        assertNotNull(loaded.classLoader.loadClass("com.aether.fixture.FixtureApplication"))
        assertNotNull(loaded.classLoader.loadClass("com.aether.fixture.FixtureActivity"))
        assertNotNull(loaded.classLoader.loadClass("com.aether.fixture.FixtureService"))
        assertNotNull(loaded.classLoader.loadClass("com.aether.fixture.FixtureReceiver"))
        assertTrue("guest VFS root must exist", loaded.fileSystem.exists(""))

        val activity = packageInfo.activities.orEmpty().single()
        assertEquals("com.aether.fixture.FixtureActivity", activity.name)
        assertEquals("com.aether.fixture.FixtureService", packageInfo.services.orEmpty().single().name)
        assertEquals("com.aether.fixture.FixtureReceiver", packageInfo.receivers.orEmpty().single().name)
        ZipFile(fixtureApk).use { zip ->
            assertNotNull(zip.getEntry("AndroidManifest.xml"))
            assertNotNull(zip.getEntry("assets/fixture.txt"))
            assertNotNull(zip.getEntry("classes.dex"))
        }
    }

    @Test
    fun fixtureTrustProfileRejectsWrongSigner() {
        val fixtureApk = copyFixtureToPrivateStorage()
        val signingFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            fixtureApk.absolutePath,
            signingFlags,
        ) ?: error("fixture APK metadata could not be read")
        val actualSigner = fixtureSignerPins(packageInfo).single()
        val wrongSigner = "0".repeat(64).let { if (it == actualSigner) "1".repeat(64) else it }
        val policy = GuestApkTrustPolicy(
            profile = GuestApkTrustProfile(
                packageName = "com.aether.fixture",
                versionName = "1.0.0",
                versionCode = 1L,
                trustedSignerSha256 = setOf(wrongSigner),
            ),
        )

        try {
            DynamicApkLoader(context, policy).load(fixtureApk)
            throw AssertionError("fixture with an untrusted signer must be rejected")
        } catch (error: GuestApkLoadException) {
            assertTrue(error.message.orEmpty().contains("trust", ignoreCase = true))
        }
    }

    private fun copyFixtureToPrivateStorage(): File {
        val destination = File(context.cacheDir, "s1-fixture-guest.apk")
        testAssetContext.assets.open("fixture-guest.apk").use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun fixtureSignerPins(packageInfo: PackageInfo): Set<String> {
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures.orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
        }.toSet()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
