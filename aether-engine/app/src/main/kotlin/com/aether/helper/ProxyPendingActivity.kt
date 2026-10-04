package com.aether.helper

import android.app.Activity
import android.os.Bundle
import android.util.Log

open class ProxyPendingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("AetherProxyPending", "onCreate")
    }

    class P0 : ProxyPendingActivity()
    class P1 : ProxyPendingActivity()
    class P2 : ProxyPendingActivity()
    class P3 : ProxyPendingActivity()
}
