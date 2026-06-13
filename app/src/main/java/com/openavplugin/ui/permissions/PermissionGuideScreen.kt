package com.openavplugin.ui.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openavplugin.R
import com.openavplugin.permission.PermissionHelper
import com.openavplugin.util.Logger

data class PermissionItem(
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val actionLabel: String,
    val action: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(listOf<PermissionItem>()) }

    // Intercept system back button
    BackHandler { onNavigateBack() }

    // Auto-refresh on every resume (when returning from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = checkAllPermissions(context)
                val granted = permissions.count { it.isGranted }
                Logger.d("Perm", "Permissions refreshed: $granted/${permissions.size}")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        permissions = checkAllPermissions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permission_guide)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val grantedCount = permissions.count { it.isGranted }
            val totalCount = permissions.size

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (grantedCount == totalCount)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.permission_status_summary, grantedCount, totalCount),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (grantedCount < totalCount) {
                        Text(
                            stringResource(R.string.permission_guide_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            permissions.forEach { permission ->
                PermissionCard(permission = permission)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.xiaomi_tips), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.xiaomi_tips_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCard(permission: PermissionItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (permission.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (permission.isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(permission.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (!permission.isGranted && permission.action != null) {
                TextButton(onClick = { permission.action.invoke() }) {
                    Text(permission.actionLabel)
                }
            }
        }
    }
}

fun checkAllPermissions(context: Context): List<PermissionItem> {
    return listOf(
        checkStoragePermission(context),
        checkNotificationPermission(context),
        checkAppListPermission(context),
        checkAutostartPermission(context),
        checkBatteryOptimization(context),
        checkOverlayPermission(context),
        checkLSPosedModule(context)
    )
}

private fun checkStoragePermission(context: Context): PermissionItem {
    val isGranted = PermissionHelper.hasStoragePermission(context)
    return PermissionItem(
        name = context.getString(R.string.storage_permission),
        description = context.getString(R.string.storage_permission_desc),
        isGranted = isGranted,
        actionLabel = context.getString(R.string.grant_permission),
        action = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    )
}

private fun checkNotificationPermission(context: Context): PermissionItem {
    val isGranted = PermissionHelper.hasNotificationPermission(context)
    return PermissionItem(
        name = context.getString(R.string.notification_permission),
        description = context.getString(R.string.notification_permission_desc),
        isGranted = isGranted,
        actionLabel = context.getString(R.string.grant_permission),
        action = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        }
    )
}

private fun checkAppListPermission(context: Context): PermissionItem {
    val isGranted = PermissionHelper.hasAppListPermission(context)
    return PermissionItem(
        name = context.getString(R.string.app_list_permission),
        description = context.getString(R.string.app_list_permission_desc),
        isGranted = isGranted,
        actionLabel = context.getString(R.string.grant_permission),
        action = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    )
}

private fun checkAutostartPermission(context: Context): PermissionItem {
    val isGranted = try {
        val pm = context.packageManager
        val intent = Intent().apply {
            component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        }
        pm.resolveActivity(intent, 0) != null
    } catch (e: Exception) {
        true
    }
    return PermissionItem(
        name = context.getString(R.string.autostart_permission),
        description = context.getString(R.string.autostart_permission_desc),
        isGranted = isGranted,
        actionLabel = context.getString(R.string.open_settings),
        action = {
            try {
                val intent = Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    )
}

private fun checkBatteryOptimization(context: Context): PermissionItem {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isGranted = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    return PermissionItem(
        name = context.getString(R.string.battery_optimization),
        description = context.getString(R.string.battery_optimization_desc),
        isGranted = isGranted,
        actionLabel = context.getString(R.string.disable),
        action = {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        }
    )
}

private fun checkOverlayPermission(context: Context): PermissionItem {
    val isGranted = Settings.canDrawOverlays(context)
    return PermissionItem(
        name = context.getString(R.string.overlay_permission),
        description = context.getString(R.string.overlay_permission_desc),
        isGranted = isGranted,
        actionLabel = context.getString(R.string.grant_permission),
        action = {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    )
}

private fun checkLSPosedModule(context: Context): PermissionItem {
    val isLSPosedInstalled = try {
        context.packageManager.getPackageInfo("org.lsposed.manager", 0)
        true
    } catch (e: Exception) {
        try {
            context.packageManager.getPackageInfo("org.lsposed.lspd", 0)
            true
        } catch (e2: Exception) {
            false
        }
    }
    return PermissionItem(
        name = "LSPosed",
        description = "LSPosed 框架状态",
        isGranted = isLSPosedInstalled,
        actionLabel = "打开 LSPosed",
        action = null
    )
}
