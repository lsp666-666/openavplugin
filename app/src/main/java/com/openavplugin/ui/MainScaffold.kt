package com.openavplugin.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openavplugin.R
import com.openavplugin.ui.apps.AppConfigScreen
import com.openavplugin.ui.apps.AppListScreen
import com.openavplugin.ui.home.HomeScreen
import com.openavplugin.ui.permissions.PermissionGuideScreen
import com.openavplugin.ui.permissions.PermissionScreen
import com.openavplugin.ui.settings.SettingsScreen
import com.openavplugin.ui.sources.SourceManagerScreen
import com.openavplugin.util.LogServer
import com.openavplugin.util.Logger

/**
 * State-driven tab scaffold — no NavHost animations.
 *
 * 4 main tabs switch instantly via [Crossfade].
 * Sub-pages (app config, permissions) hide the bottom bar
 * and show a back arrow in their own TopAppBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var subScreen by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permStep by remember { mutableStateOf(0) }
    val permSteps = remember {
        listOf(
            Triple("存储权限", "需要访问存储空间以保存配置文件", "storage"),
            Triple("通知权限", "需要通知权限以保持后台运行", "notification"),
            Triple("应用列表", "需要获取应用列表以配置 Hook", "applist")
        )
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("openavplugin_boot", 0)
        val askedPerms = prefs.getBoolean("asked_permissions", false)
        if (!askedPerms) {
            val missing = mutableListOf<Int>()
            if (!com.openavplugin.permission.PermissionHelper.hasStoragePermission(context)) missing.add(0)
            if (!com.openavplugin.permission.PermissionHelper.hasNotificationPermission(context)) missing.add(1)
            if (!com.openavplugin.permission.PermissionHelper.hasAppListPermission(context)) missing.add(2)
            if (missing.isNotEmpty()) { permStep = missing.first(); showPermissionDialog = true }
            prefs.edit().putBoolean("asked_permissions", true).apply()
        }
    }

    // Sequential permission wizard
    if (showPermissionDialog) {
        val (title, desc, key) = permSteps[permStep]
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(title) },
            text = { Text(desc) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    // When user returns from settings, re-check permissions
                    val prefs = context.getSharedPreferences("openavplugin_boot", 0)
                    prefs.edit().putBoolean("perm_pending", true).apply()
                    // Trigger system dialog
                    when (key) {
                        "storage" -> {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                        "notification" -> {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                        "applist" -> {
                            if (com.openavplugin.permission.PermissionHelper.isMiuiAppListPermissionSupported(context)) {
                                // Navigate to permission guide for MIUI runtime dialog
                                subScreen = "permission_guide"
                            } else {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        }
                    }
                }) { Text("授权") }
            },
            dismissButton = {
                // Skip to next missing permission, or finish
                val remaining = listOf(0, 1, 2).filter { idx ->
                    idx > permStep && when (idx) {
                        0 -> !com.openavplugin.permission.PermissionHelper.hasStoragePermission(context)
                        1 -> !com.openavplugin.permission.PermissionHelper.hasNotificationPermission(context)
                        2 -> !com.openavplugin.permission.PermissionHelper.hasAppListPermission(context)
                        else -> false
                    }
                }
                TextButton(onClick = {
                    if (remaining.isNotEmpty()) {
                        permStep = remaining.first()
                    } else {
                        showPermissionDialog = false
                    }
                }) { Text("跳过") }
            }
        )
    }



    // Restart LogServer on resume, continue permission wizard
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                LogServer.start(context)
                if (!showPermissionDialog) {
                    val prefs = context.getSharedPreferences("openavplugin_boot", 0)
                    if (prefs.getBoolean("perm_pending", false)) {
                        prefs.edit().putBoolean("perm_pending", false).apply()
                        val missing = mutableListOf<Int>()
                        if (!com.openavplugin.permission.PermissionHelper.hasStoragePermission(context)) missing.add(0)
                        if (!com.openavplugin.permission.PermissionHelper.hasNotificationPermission(context)) missing.add(1)
                        if (!com.openavplugin.permission.PermissionHelper.hasAppListPermission(context)) missing.add(2)
                        if (missing.isNotEmpty()) { permStep = missing.first(); showPermissionDialog = true }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showBottomBar = subScreen == null

    val topBarTitle = when {
        subScreen != null -> ""
        selectedTab == 0 -> stringResource(R.string.app_name)
        selectedTab == 1 -> stringResource(R.string.app_management)
        selectedTab == 2 -> stringResource(R.string.source_manager)
        selectedTab == 3 -> stringResource(R.string.settings)
        else -> ""
    }

    Scaffold(
        topBar = {
            if (subScreen == null) {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            Logger.i("Nav", "Tab → Home")
                        },
                        icon = { Icon(Icons.Default.Home, stringResource(R.string.nav_home)) },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            Logger.i("Nav", "Tab → Apps")
                        },
                        icon = { Icon(Icons.Default.List, stringResource(R.string.nav_apps)) },
                        label = { Text(stringResource(R.string.nav_apps)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            Logger.i("Nav", "Tab → Sources")
                        },
                        icon = { Icon(Icons.Default.PlayArrow, stringResource(R.string.nav_sources)) },
                        label = { Text(stringResource(R.string.nav_sources)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = {
                            selectedTab = 3
                            Logger.i("Nav", "Tab → Settings")
                        },
                        icon = { Icon(Icons.Default.Settings, stringResource(R.string.nav_settings)) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val sub = subScreen
            if (sub != null) {
                // ── Sub-page content ──
                when {
                    sub.startsWith("app_config:") -> {
                        val parts = sub.removePrefix("app_config:").split("||", limit = 2)
                        val pkg = parts[0]
                        val name = parts.getOrNull(1)
                        AppConfigScreen(
                            packageName = pkg,
                            appDisplayName = name,
                            onNavigateBack = { subScreen = null }
                        )
                    }
                    sub == "permission_guide" -> {
                        PermissionGuideScreen(
                            onNavigateBack = { subScreen = null }
                        )
                    }
                    sub == "permissions" -> {
                        PermissionScreen(
                            onNavigateBack = { subScreen = null }
                        )
                    }
                }
            } else {
                // ── Main tabs (no animation-driven push) ──
                Crossfade(targetState = selectedTab) { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            onNavigateToPermissionGuide = { subScreen = "permission_guide" }
                        )
                        1 -> AppListScreen(
                            onAppClick = { pkg, name -> subScreen = "app_config:$pkg||$name" }
                        )
                        2 -> SourceManagerScreen()
                        3 -> SettingsScreen()
                    }
                }
            }
        }
    }
}
