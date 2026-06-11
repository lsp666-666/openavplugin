package com.openavplugin.data

import android.content.Context
import android.os.Environment
import com.openavplugin.data.db.AppRule
import org.json.JSONObject
import java.io.File

class SharedConfigManager(private val context: Context) {

    companion object {
        private const val CONFIG_FILE = "openavplugin_rules.json"
    }

    fun saveRule(rule: AppRule) {
        val json = loadAllRules()
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
        saveAllRules(json)
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
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getConfigFile(): File {
        val dir = File(context.getExternalFilesDir(null), "")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, CONFIG_FILE)
    }
}
