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

    private var cameraHooker: CameraHooker? = null
    private var audioHooker: AudioHooker? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName

        // Don't hook our own app
        if (packageName == "com.openavplugin") return

        XposedBridge.log("$TAG: Loaded in $packageName")

        val config = loadAppConfig(packageName)
        if (config == null) {
            XposedBridge.log("$TAG: No config for $packageName — skipping")
            return
        }

        XposedBridge.log("$TAG: Hooking $packageName")

        // Camera hook
        if (config.optBoolean("cameraEnabled", false)) {
            val cameraConfig = CameraHooker.CameraHookConfig(
                sourceType = config.optString("cameraSourceType", "local"),
                sourcePath = config.optString("cameraSourcePath", null),
                width = parseResolutionWidth(config.optString("cameraResolution", "1280x720")),
                height = parseResolutionHeight(config.optString("cameraResolution", "1280x720"))
            )
            cameraHooker = CameraHooker(lpparam, cameraConfig)
            cameraHooker?.hook()
            XposedBridge.log("$TAG: Camera hook installed for $packageName")
        }

        // Microphone hook
        if (config.optBoolean("micEnabled", false)) {
            val audioConfig = AudioHooker.AudioHookConfig(
                sourceType = config.optString("micSourceType", "silence"),
                sourcePath = config.optString("micSourcePath", null),
                sampleRate = config.optInt("micSampleRate", 44100),
                channels = config.optInt("micChannels", 1)
            )
            audioHooker = AudioHooker(lpparam, audioConfig)
            audioHooker?.hook()
            XposedBridge.log("$TAG: Audio hook installed for $packageName")
        }
    }

    private fun loadAppConfig(packageName: String): JSONObject? {
        return try {
            val configFile = getConfigFile()
            if (!configFile.exists()) {
                XposedBridge.log("$TAG: Config file not found")
                return null
            }

            val json = JSONObject(configFile.readText())
            val appConfig = json.optJSONObject(packageName)
            if (appConfig != null) {
                XposedBridge.log("$TAG: Config found for $packageName: $appConfig")
            }
            appConfig
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Error loading config: ${e.message}")
            null
        }
    }

    private fun getConfigFile(): File {
        val sharedDir = File("/sdcard/Android/data/com.openavplugin/files")
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }
        return File(sharedDir, CONFIG_FILE)
    }

    private fun parseResolutionWidth(resolution: String): Int {
        return try {
            resolution.split("x").firstOrNull()?.toIntOrNull() ?: 1280
        } catch (_: Exception) {
            1280
        }
    }

    private fun parseResolutionHeight(resolution: String): Int {
        return try {
            resolution.split("x").lastOrNull()?.toIntOrNull() ?: 720
        } catch (_: Exception) {
            720
        }
    }
}
