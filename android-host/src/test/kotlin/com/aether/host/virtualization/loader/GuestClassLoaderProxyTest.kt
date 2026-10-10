package com.aether.host.virtualization.loader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestClassLoaderProxyTest {
    @Test
    fun `framework and Aether bridge namespaces are parent first`() {
        listOf(
            "java.lang.Object",
            "javax.crypto.Cipher",
            "android.app.Activity",
            "com.android.internal.os.RuntimeInit",
            "dalvik.system.DexClassLoader",
            "org.json.JSONObject",
            "org.w3c.dom.Node",
            "org.xmlpull.v1.XmlPullParser",
            "kotlin.Unit",
            "kotlinx.coroutines.Job",
            "com.aether.host.bridge.Native",
            "com.aether.guest.api.GuestEntryPoint",
            "com.aether.guest.api.GuestStorage",
        ).forEach { className ->
            assertTrue(className, GuestClassLoaderProxy.isParentFirstForTests(className))
        }
    }

    @Test
    fun `guest and third party classes are guest first`() {
        listOf(
            "com.miniclip.eightballpool.GameActivity",
            "com.google.android.gms.ads.AdView",
            "androidx.lifecycle.Lifecycle",
        ).forEach { className ->
            assertFalse(className, GuestClassLoaderProxy.isParentFirstForTests(className))
        }
    }
}
