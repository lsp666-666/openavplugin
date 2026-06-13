package com.openavplugin.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHelper(private val activity: ComponentActivity) {

    interface PermissionCallback {
        fun onAllPermissionsGranted()
        fun onPermissionsDenied(deniedPermissions: List<String>)
    }

    private var callback: PermissionCallback? = null

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val denied = permissions.filter { !it.value }.keys.toList()
            if (denied.isEmpty()) {
                callback?.onAllPermissionsGranted()
            } else {
                callback?.onPermissionsDenied(denied)
            }
        }

    private val storagePermissionLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    checkRemainingPermissions()
                } else {
                    callback?.onPermissionsDenied(listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE))
                }
            }
        }

    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                checkRemainingPermissions()
            } else {
                callback?.onPermissionsDenied(listOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

    private var pendingPermissions = mutableListOf<String>()

    fun requestAllPermissions(callback: PermissionCallback) {
        this.callback = callback
        pendingPermissions.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                storagePermissionLauncher.launch(intent)
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pendingPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        checkRemainingPermissions()
    }

    private fun checkRemainingPermissions() {
        val permissionsToRequest = pendingPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            callback?.onAllPermissionsGranted()
        } else if (permissionsToRequest.contains(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasAllPermissions(): Boolean {
        return hasStoragePermission() && hasNotificationPermission() && hasAppListPermission(activity)
    }

    companion object {
        fun hasStoragePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun hasAppListPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // QUERY_ALL_PACKAGES isn't a standard runtime permission;
                // test actual package visibility instead
                try {
                    val pm = context.packageManager
                    val apps = pm.getInstalledApplications(0)
                    val launchable = apps.filter {
                        pm.getLaunchIntentForPackage(it.packageName) != null
                    }
                    launchable.size > 1
                } catch (_: Exception) {
                    false
                }
            } else {
                true
            }
        }
    }
}
