package com.openavplugin.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.File

class HookEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        private const val TAG = "OpenAVPlugin"
        var modulePath: String? = null
    }

    private var cameraHooker: CameraHooker? = null
    private var audioHooker: AudioHooker? = null

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        try {
            java.io.File("/data/local/tmp", "openavplugin_module_path.txt").writeText(startupParam.modulePath)
        } catch (_: Exception) { }
        XposedBridge.log("$TAG: initZygote — modulePath=$modulePath")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName

        // Don't hook our own app
        if (packageName == "com.openavplugin") return

        hookLog("=== Loaded in $packageName (v4: multi-path) ===")

        val config = loadAppConfig(packageName)
        if (config == null) {
            hookLog("No config for $packageName — skipping")
            return
        }

        hookLog("Hooking $packageName")

        // Camera hook
        if (config.optBoolean("cameraEnabled", false)) {
            val cameraConfig = CameraHooker.CameraHookConfig(
                sourceType = config.optString("cameraSourceType", "local"),
                sourcePath = config.optString("cameraSourcePath", null),
                width = parseResolutionWidth(config.optString("cameraResolution", "1280x720")),
                height = parseResolutionHeight(config.optString("cameraResolution", "1280x720"))
            )
            cameraHooker = CameraHooker(lpparam, cameraConfig)
            try {
                cameraHooker?.hook()
                hookLog("Camera hook installed")
            } catch (e: Exception) {
                hookLog("Camera hook FAILED: ${e.message}")
            }
        }

        // Microphone hook
        if (config.optBoolean("micEnabled", false)) {
            audioHooker = AudioHooker(lpparam, packageName)
            try {
                audioHooker?.hook()
                hookLog("Audio hook installed")
            } catch (e: Exception) {
                hookLog("Audio hook FAILED: ${e.message}")
            }
        }
    }

    private fun loadAppConfig(packageName: String): JSONObject? {
        // Try multiple paths — Android may use /data/data/ or /data/user/0/
        val paths = listOf(
            "/data/local/tmp/openavplugin_rules.json",
            "/data/data/com.openavplugin/files/openavplugin_rules.json",
            "/data/user/0/com.openavplugin/files/openavplugin_rules.json",
            "/sdcard/openavplugin_rules.json",
            "/storage/emulated/0/openavplugin_rules.json"
        )
        for (path in paths) {
            val f = java.io.File(path)
            if (f.exists() && f.canRead()) {
                try {
                    val json = JSONObject(f.readText())
                    val appConfig = json.optJSONObject(packageName)
                    if (appConfig != null) {
                        hookLog("Config found: $path — camera=${appConfig.optBoolean("cameraEnabled")} mic=${appConfig.optBoolean("micEnabled")}")
                        return appConfig
                    }
                    hookLog("Config file $path exists but no entry for $packageName (${json.length()} apps)")
                } catch (e: Exception) {
                    hookLog("Error reading $path: ${e.message}")
                }
            } else {
                XposedBridge.log("$TAG: Path $path exists=${f.exists()} canRead=${f.canRead()}")
            }
        }
        hookLog("No config found for $packageName in any path")
        return null
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

    private fun hookLog(msg: String) {
        XposedBridge.log("$TAG: $msg")
        // Write status file so main app can verify injection
        try {
            val statusFile = File("/data/data/com.openavplugin/files", "hook_status.txt")
            statusFile.writeText(msg)
            statusFile.setReadable(true, false)
        } catch (_: Exception) { }
    }
}
