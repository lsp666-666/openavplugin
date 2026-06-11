package com.openavplugin.root

import java.io.File

object RootChecker {
    fun isRooted(): Boolean {
        return checkSuExists() || checkMagisk() || checkBusyBox()
    }

    private fun checkSuExists(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkMagisk(): Boolean {
        return try {
            File("/sbin/magisk").exists() ||
            File("/data/adb/magisk").exists() ||
            File("/data/adb/modules").exists()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkBusyBox(): Boolean {
        return try {
            File("/system/xbin/busybox").exists() ||
            File("/system/bin/busybox").exists()
        } catch (e: Exception) {
            false
        }
    }

    fun isLSPosedActive(): Boolean {
        return try {
            // 方式1: 检查 XposedBridge 类是否加载（最可靠）
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            // 方式2: 检查 LSPosed Manager 应用
            checkLSPosedManager() ||
            // 方式3: 检查系统属性
            checkLSPosedProperties() ||
            // 方式4: 检查文件路径（备用）
            checkLSPosedFiles()
        }
    }

    private fun checkLSPosedManager(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages"))
            val reader = process.inputStream.bufferedReader()
            val packages = reader.readText()
            packages.contains("org.lsposed.manager") ||
            packages.contains("org.lsposed.lspd") ||
            packages.contains("com.android.shell.lsposed")
        } catch (e: Exception) {
            false
        }
    }

    private fun checkLSPosedProperties(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "persist.sys.lspd"))
            val reader = process.inputStream.bufferedReader()
            val value = reader.readText().trim()
            value.isNotEmpty() && value != "false"
        } catch (e: Exception) {
            false
        }
    }

    private fun checkLSPosedFiles(): Boolean {
        return try {
            File("/data/adb/lspd").exists() ||
            File("/data/adb/modules/lsposed").exists() ||
            File("/data/adb/modules/lsposed_manager").exists()
        } catch (e: Exception) {
            false
        }
    }
}
