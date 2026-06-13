package com.openavplugin.root

import android.content.Context
import android.content.pm.PackageManager
import com.openavplugin.util.Logger
import java.io.File

object RootChecker {
    private const val TAG = "RootChecker"

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

    fun isLSPosedActive(context: Context): Boolean {
        // Method 1: Check if XposedBridge is loaded in current process
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            Logger.i(TAG, "Detected: XposedBridge loaded in process")
            return true
        } catch (_: ClassNotFoundException) {
            Logger.d(TAG, "XposedBridge not in current process")
        }

        // Method 2: Check for hook status file (written by HookEntry)
        val statusFile = java.io.File(context.filesDir, "hook_status.txt")
        if (statusFile.exists() && statusFile.length() > 0) {
            Logger.i(TAG, "Detected: hook_status.txt exists")
            return true
        }

        // Method 3: Check LSPosed packages
        val lspPkgs = listOf("org.lsposed.manager", "org.lsposed.lspd")
        for (pkg in lspPkgs) {
            if (checkPackageInstalled(context, pkg)) {
                Logger.i(TAG, "Detected: package $pkg installed")
                return true
            }
        }

        return false
    }

    private fun checkPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
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

    private fun checkModulesDirectory(): Boolean {
        return try {
            val modulesDir = File("/data/adb/modules")
            if (!modulesDir.exists() || !modulesDir.isDirectory) return false
            val files = modulesDir.listFiles() ?: return false
            files.any { it.name.contains("lsposed", ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }
}
