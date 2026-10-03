package com.aether.host

import android.app.Application

/**
 * Lifecycle owner ระดับแอปพลิเคชัน
 * ห้ามใส่ business logic — จัดการเฉพาะ lifecycle ของ process
 */
class AetherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Phase 3: FlutterEngine จะถูกสร้าง/อุ่นเครื่องที่นี่ผ่าน AetherFlutterHost
    }
}
