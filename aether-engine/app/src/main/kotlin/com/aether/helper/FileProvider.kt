package com.aether.helper

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

open class FileProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Log.i("AetherFileProvider", "onCreate")
        return true
    }
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
