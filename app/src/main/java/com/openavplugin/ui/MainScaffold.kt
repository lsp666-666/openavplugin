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

    // Restart LogServer on resume if killed in background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                LogServer.start(context)
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
                        val pkg = sub.removePrefix("app_config:")
                        AppConfigScreen(
                            packageName = pkg,
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
                            onAppClick = { pkg -> subScreen = "app_config:$pkg" }
                        )
                        2 -> SourceManagerScreen()
                        3 -> SettingsScreen()
                    }
                }
            }
        }
    }
}
