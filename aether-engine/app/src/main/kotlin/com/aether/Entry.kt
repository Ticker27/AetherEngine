package com.aether

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class Entry : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("AetherEntry", "onCreate")
    }
    override fun onResume()  { super.onResume();  Log.i("AetherEntry", "onResume") }
    override fun onStop()    { super.onStop();    Log.i("AetherEntry", "onStop") }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        Log.i("AetherEntry", "onActivityResult req=$req res=$res")
    }
    override fun onRequestPermissionsResult(req: Int, perm: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perm, res)
        Log.i("AetherEntry", "onRequestPermissionsResult req=$req")
    }
}
