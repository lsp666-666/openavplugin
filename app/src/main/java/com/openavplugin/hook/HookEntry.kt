package com.openavplugin.hook

import android.os.Environment
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.File

class HookEntry : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "OpenAVPlugin"
        private const val CONFIG_FILE = "openavplugin_rules.json"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName

        if (packageName == "com.openavplugin") return

        XposedBridge.log("$TAG: Loaded in $packageName")

        val shouldHook = shouldHookApp(packageName)

        if (!shouldHook) {
            XposedBridge.log("$TAG: Skipping $packageName (not in target list)")
            return
        }

        XposedBridge.log("$TAG: Hooking $packageName")

        val config = loadAppConfig(packageName)

        if (config.optBoolean("cameraEnabled", false)) {
            XposedBridge.log("$TAG: Hooking camera for $packageName")
            val cameraHooker = CameraHooker(lpparam)
            cameraHooker.hook()
        }

        if (config.optBoolean("micEnabled", false)) {
            XposedBridge.log("$TAG: Hooking microphone for $packageName")
            val audioHooker = AudioHooker(lpparam)
            audioHooker.hook()
        }
    }

    private fun shouldHookApp(packageName: String): Boolean {
        return try {
            val configFile = getConfigFile()
            if (!configFile.exists()) {
                XposedBridge.log("$TAG: Config file not found")
                return false
            }

            val json = JSONObject(configFile.readText())
            val hasRule = json.has(packageName)
            XposedBridge.log("$TAG: Config check for $packageName: $hasRule")
            hasRule
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Error checking hook config: ${e.message}")
            false
        }
    }

    private fun loadAppConfig(packageName: String): JSONObject {
        return try {
            val configFile = getConfigFile()
            if (!configFile.exists()) return JSONObject()

            val json = JSONObject(configFile.readText())
            json.optJSONObject(packageName) ?: JSONObject()
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Error loading app config: ${e.message}")
            JSONObject()
        }
    }

    private fun getConfigFile(): File {
        val sharedDir = File("/sdcard/Android/data/com.openavplugin/files")
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }
        return File(sharedDir, CONFIG_FILE)
    }
}
