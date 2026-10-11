package com.aether.host.virtualization.components.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind

/**
 * Private, inert ContentProvider base for reserved guest-provider slots.
 * No guest provider is invoked and no data is exposed until a trusted provider adapter exists.
 */
abstract class ProxyContentProvider : ContentProvider() {
    protected abstract val slotIndex: Int?
    protected open val providerKind: String = "guest"

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        dispatch("queried", uri)
        return MatrixCursor(projection?.map { it }?.toTypedArray() ?: arrayOf("_id"))
    }

    override fun getType(uri: Uri): String? {
        dispatch("type", uri)
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        dispatch("inserted", uri)
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        dispatch("deleted", uri)
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        dispatch("updated", uri)
        return 0
    }

    /** Lifecycle fan-out for this provider. Visible to subclasses, invisible outside the module. */
    protected fun dispatch(event: String, uri: Uri) {
        val app = context?.applicationContext as? AetherApplication ?: return
        app.hostInitializer.dispatch(
            HostComponentEvent(
                owner = this,
                kind = HostComponentKind.CONTENT_PROVIDER,
                slot = slotIndex,
                lifecycle = event,
                proxyType = "$providerKind-${slotIndex?.let { "p$it" } ?: "system"}",
                intent = Intent().setData(uri),
            ),
        )
    }
}

class ProxyContentProviderP0 : ProxyContentProvider() {
    override val slotIndex: Int? = 0
}

class ProxyContentProviderP1 : ProxyContentProvider() {
    override val slotIndex: Int? = 1
}

class ProxyContentProviderP2 : ProxyContentProvider() {
    override val slotIndex: Int? = 2
}

class ProxyContentProviderP3 : ProxyContentProvider() {
    override val slotIndex: Int? = 3
}
