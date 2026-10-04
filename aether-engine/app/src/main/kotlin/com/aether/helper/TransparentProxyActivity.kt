package com.aether.helper

import android.app.Activity
import android.os.Bundle
import android.util.Log

open class TransparentProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("AetherTransparent", "onCreate")
    }

    class P0 : TransparentProxyActivity()
    class P1 : TransparentProxyActivity()
    class P2 : TransparentProxyActivity()
    class P3 : TransparentProxyActivity()
}
