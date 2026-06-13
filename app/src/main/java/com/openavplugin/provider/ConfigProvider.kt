package com.openavplugin.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * ContentProvider that shares the hook config with Xposed-injected processes.
 * HookEntry reads config via ContentResolver instead of file I/O.
 */
class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("packageName", "config"))
        // Read from SharedPreferences (same source as XSharedPreferences)
        val prefs = context!!.getSharedPreferences("openavplugin_rules", 0)
        val rulesJson = prefs.getString("rules", null) ?: return cursor
        try {
            val json = org.json.JSONObject(rulesJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val appConfig = json.optJSONObject(pkg)
                if (appConfig != null) {
                    cursor.addRow(arrayOf(pkg, appConfig.toString()))
                }
            }
        } catch (_: Exception) { }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/config"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
