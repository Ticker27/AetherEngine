package com.aether.host.virtualization.loader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aether.guest.api.GuestEntryPoint
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostInitializer
import com.aether.host.virtualization.activity.ProxyActivityP0
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger
import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cooperative first-party fixture lifecycle tests. These do NOT attach a third-party Activity;
 * they exercise only the fixture entry point hosted by [ProxyActivityP0].
 */
@RunWith(AndroidJUnit4::class)
class FixtureGuestLaunchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context.applicationContext as AetherApplication
    private val host: HostInitializer get() = app.hostInitializer

    @Before
    fun resetFlags() {
        flagger.setEnabled(HostFeature.DYNAMIC_APK_LOADING, false)
        flagger.setEnabled(HostFeature.COOPERATIVE_FIXTURE_UI, false)
    }

    @After
    fun tearDown() {
        // reset(), not shutdown(): shutdown is final and would block every later test in this process.
        instrumentation.runOnMainSync { host.fixtureController.reset() }
        flagger.setEnabled(HostFeature.DYNAMIC_APK_LOADING, false)
        flagger.setEnabled(HostFeature.COOPERATIVE_FIXTURE_UI, false)
    }

    @Test
    fun fixtureRouteRejectsDisabledLoadingFlag() {
        val policy = fixturePolicy()
        flagger.setEnabled(HostFeature.DYNAMIC_APK_LOADING, false)
        assertTrue(host.initialize())
        assertThrows(IllegalStateException::class.java) {
            host.loadCooperativeFixtureApk(File(app.cacheDir, "absent.apk"), policy)
        }
    }

    @Test
    fun hostRouteCreatesFixtureUiAndReleasesLifecycleLease() {
        val guest = loadFixtureGuest()
        val launchIntent = prepareLaunch(guest)
        Log.i("AetherS1", "launch intent hasToken=${launchIntent.hasExtra(
            com.aether.host.virtualization.activity.CooperativeFixtureController.EXTRA_TOKEN)} " +
            "component=${launchIntent.component?.className}")
        ActivityScenario.launch<ProxyActivityP0>(launchIntent).use { scenario ->
            scenario.onActivity { activity ->
                Log.i("AetherS1", "activity started: finishing=${activity.isFinishing} " +
                    "destroyed=${activity.isDestroyed} hasSession=${host.fixtureController.hasSession()}")
            }
            assertFixtureUiVisible(scenario)
            scenario.onActivity { activity ->
                instrumentation.callActivityOnNewIntent(activity, Intent("fixture.NEW_INTENT"))
            }
            scenario.recreate()
            assertFixtureUiVisible(scenario)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
        instrumentation.runOnMainSync { assertFalse(host.fixtureController.hasSession()) }

        val events = readEvents(guest)
        assertEquals(listOf("created", "started", "resumed"), events.take(3))
        assertEquals("destroyed", events.last())
        assertEquals(events.count { it == "created" }, events.count { it == "destroyed" })
        assertTrue("Recreation must create another cooperative instance", events.count { it == "created" } >= 2)
        assertTrue(events.contains("saved") && events.contains("new_intent"))
        assertTrue("Saved state must restore a non-zero resume count",
            events.any { it.startsWith("restored:") && it != "restored:0" })
        Log.i("AetherS1", "fixture lifecycle=$events")
    }

    @Test
    fun duplicateLaunchOfActiveTokenDoesNotRevokeOriginalSession() {
        val guest = loadFixtureGuest()
        val launchIntent = prepareLaunch(guest)
        ActivityScenario.launch<ProxyActivityP0>(launchIntent).use { original ->
            assertFixtureUiVisible(original)
            // Reusing the same token while the lease is held must be rejected without touching
            // the original session.
            ActivityScenario.launch<ProxyActivityP0>(Intent(launchIntent)).use { duplicate ->
                duplicate.onActivity { activity -> assertTrue(activity.isFinishing) }
            }
            instrumentation.runOnMainSync { assertTrue(host.fixtureController.hasSession()) }
            assertFixtureUiVisible(original)
        }
        instrumentation.runOnMainSync { assertFalse(host.fixtureController.hasSession()) }
    }

    // ---- helpers ----

    private fun fixturePolicy(): GuestApkTrustPolicy {
        val expectedPins = instrumentation.context.assets.open("fixture-signer.sha256")
            .bufferedReader().use { setOf(it.readText().trim()) }
        return GuestApkTrustPolicy(GuestApkTrustProfile("com.aether.fixture", "1.0.0", 1L, expectedPins))
    }

    private fun loadFixtureGuest(): com.aether.host.virtualization.loader.LoadedGuestApk {
        val apk = File(context.cacheDir, "launch-fixture.apk")
        instrumentation.context.assets.open("fixture-guest.apk").use { source ->
            apk.outputStream().use { source.copyTo(it) }
        }
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val info = checkNotNull(context.packageManager.getPackageArchiveInfo(apk.path, flags))
        @Suppress("DEPRECATION")
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo!!.apkContentsSigners else info.signatures!!
        val pins = signers.map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        }.toSet()
        assertEquals(fixturePolicy().trustedSignerSha256, pins)

        flagger.setEnabled(HostFeature.DYNAMIC_APK_LOADING, true)
        flagger.setEnabled(HostFeature.COOPERATIVE_FIXTURE_UI, true)
        assertTrue("Host native initialization must run on this ABI", host.initialize())
        Log.i("AetherS1", "nativeReady=true abi=${Build.SUPPORTED_ABIS.joinToString()} api=${Build.VERSION.SDK_INT}")
        val guest = host.loadCooperativeFixtureApk(apk, fixturePolicy())
        if (guest.fileSystem.exists("evidence/lifecycle.txt")) guest.fileSystem.delete("evidence/lifecycle.txt")
        assertSame(GuestEntryPoint::class.java, guest.classLoader.loadClass(GuestEntryPoint::class.java.name))
        assertSame(guest.classLoader, guest.classLoader.loadClass("com.aether.fixture.FixtureEntryPoint").classLoader)
        return guest
    }

    private fun prepareLaunch(guest: com.aether.host.virtualization.loader.LoadedGuestApk): Intent {
        var intent: Intent? = null
        instrumentation.runOnMainSync { intent = host.prepareFixtureLaunch(guest) }
        return checkNotNull(intent)
    }

    private fun assertFixtureUiVisible(scenario: ActivityScenario<ProxyActivityP0>) {
        scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertEquals("aether-fixture-ready", content.getChildAt(0).contentDescription.toString())
            assertTrue(host.fixtureController.hasSession())
        }
    }

    private fun readEvents(guest: com.aether.host.virtualization.loader.LoadedGuestApk): List<String> =
        guest.fileSystem.openInput("evidence/lifecycle.txt").bufferedReader().use { it.readLines() }
}
