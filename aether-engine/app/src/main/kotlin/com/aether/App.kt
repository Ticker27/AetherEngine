package com.aether

import android.app.Application
import android.util.Log
import com.aether.helper.Native

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("AetherApp", "onCreate")
        Native()  // trigger loadLibrary + JNI_OnLoad
        Log.i("AetherApp", "native loaded")
    }
}
