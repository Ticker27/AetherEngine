package com.aether.host

import android.app.Activity
import android.os.Bundle

/**
 * Activity = lifecycle owner เท่านั้น
 * ห้ามใส่ business logic — งาน native/Flutter อยู่ที่ Native และ AetherFlutterHost
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 3: attach FlutterActivity/FlutterFragment ผ่าน AetherFlutterHost
    }
}
