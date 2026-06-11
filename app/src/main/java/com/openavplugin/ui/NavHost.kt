package com.openavplugin.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openavplugin.ui.apps.AppConfigScreen
import com.openavplugin.ui.apps.AppListScreen
import com.openavplugin.ui.home.HomeScreen
import com.openavplugin.ui.permissions.PermissionGuideScreen
import com.openavplugin.ui.permissions.PermissionScreen
import com.openavplugin.ui.settings.SettingsScreen
import com.openavplugin.ui.sources.SourceManagerScreen

@Composable
fun OpenAVPluginNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToApps = { navController.navigate("apps") },
                onNavigateToSources = { navController.navigate("sources") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPermissionGuide = { navController.navigate("permission_guide") }
            )
        }

        composable("apps") {
            AppListScreen(
                onNavigateBack = { navController.popBackStack() },
                onAppClick = { packageName ->
                    navController.navigate("app_config/$packageName")
                }
            )
        }

        composable(
            route = "app_config/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            AppConfigScreen(
                packageName = packageName,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("sources") {
            SourceManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("permissions") {
            PermissionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("permission_guide") {
            PermissionGuideScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
