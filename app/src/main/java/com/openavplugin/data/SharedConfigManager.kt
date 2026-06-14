package com.openavplugin.data

import android.content.Context
import com.openavplugin.data.db.AppRule
import com.openavplugin.util.Logger
import org.json.JSONObject
import java.io.File

class SharedConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "OpenAVPlugin-Config"
        private const val CONFIG_FILE = "openavplugin_rules.json"
    }

    fun saveRule(rule: AppRule) {
        val json = loadAllRules()
        val before = json.length()
        val appJson = JSONObject().apply {
            put("packageName", rule.packageName)
            put("appName", rule.appName)
            put("cameraEnabled", rule.cameraEnabled)
            put("micEnabled", rule.micEnabled)
            put("cameraSourceType", rule.cameraSourceType.name)
            put("micSourceType", rule.micSourceType.name)
            put("cameraSourcePath", rule.cameraSourcePath ?: "")
            put("micSourcePath", rule.micSourcePath ?: "")
        }
        json.put(rule.packageName, appJson)
        val after = json.length()
        Logger.i(TAG, "Rule save: ${rule.packageName} — file had $before apps, now $after apps")
        saveAllRules(json)
        Logger.i(TAG, "Rule saved: ${rule.packageName} camera=${rule.cameraEnabled} mic=${rule.micEnabled}")
    }

    fun removeRule(packageName: String) {
        val json = loadAllRules()
        json.remove(packageName)
        saveAllRules(json)
    }

    fun getRule(packageName: String): JSONObject? {
        val json = loadAllRules()
        return json.optJSONObject(packageName)
    }

    fun getAllRules(): JSONObject {
        return loadAllRules()
    }

    private fun loadAllRules(): JSONObject {
        return try {
            val file = getConfigFile()
            if (file.exists()) {
                JSONObject(file.readText())
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun saveAllRules(json: JSONObject) {
        try {
            val file = getConfigFile()
            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
            Logger.i(TAG, "Config written: ${file.absolutePath} (${file.length()}B, ${json.length()} apps)")
            // Write to SharedPreferences for XSharedPreferences access
            val prefs = context.getSharedPreferences("openavplugin_rules", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("rules", json.toString()).apply()
            Logger.i(TAG, "Config saved to SharedPreferences")
            // Also write to files dir for HookEntry direct access
            val hookFile = java.io.File(context.filesDir, "openavplugin_rules.json")
            hookFile.writeText(json.toString(2))
            hookFile.setReadable(true, false)
            Logger.i(TAG, "Config saved to files dir: ${hookFile.absolutePath}")
            // Copy to /data/local/tmp/ via su — must finish for HookEntry to see it
            try {
                val tmpPath = "/data/local/tmp/openavplugin_rules.json"
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp ${hookFile.absolutePath} $tmpPath && chmod 644 $tmpPath"))
                proc.waitFor()
                if (proc.exitValue() == 0) {
                    Logger.i(TAG, "Config copied to $tmpPath via su")
                } else {
                    Logger.w(TAG, "su cp failed with exit code ${proc.exitValue()}")
                }
            } catch (e: Exception) {
                Logger.w(TAG, "su copy failed: ${e.message}")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write config: ${e.message}")
        }
    }

    private fun getConfigFile(): File {
        // Root of external storage — accessible cross-process with MANAGE_EXTERNAL_STORAGE
        return File(android.os.Environment.getExternalStorageDirectory(), CONFIG_FILE)
    }
}
