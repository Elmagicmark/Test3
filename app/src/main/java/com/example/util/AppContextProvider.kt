package com.example.util

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * Zero-config way to obtain an application [Context] from places that don't
 * have one handy — e.g. [CertificateManager] needs a real Context to persist
 * its CA keystore to disk, but the proxy's raw socket thread that ends up
 * calling into it has no Context of its own.
 *
 * Registering a ContentProvider is a standard Android trick: the system calls
 * [onCreate] (with [getContext] already populated) automatically at process
 * start, before any other app component runs — no custom Application subclass
 * or manifest android:name on <application> required.
 */
internal object AppContextHolder {
    lateinit var context: Context
        private set

    val isReady: Boolean get() = ::context.isInitialized

    fun set(ctx: Context) {
        if (!::context.isInitialized) context = ctx.applicationContext
    }
}

class AppContextProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { AppContextHolder.set(it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
