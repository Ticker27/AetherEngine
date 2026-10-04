package com.aether.helper

import android.app.Activity
import android.os.Bundle
import android.util.Log

open class ProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("AetherProxy", "onCreate")
    }

    class P0 : ProxyActivity()
    class P1 : ProxyActivity()
    class P2 : ProxyActivity()
    class P3 : ProxyActivity()
}
