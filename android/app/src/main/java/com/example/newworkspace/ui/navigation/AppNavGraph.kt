package com.example.newworkspace.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.newworkspace.ui.dashboard.DashboardScreen
import com.example.newworkspace.ui.settings.SettingsScreen

private const val DASHBOARD_ROUTE = "dashboard"
private const val SETTINGS_ROUTE = "settings"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = DASHBOARD_ROUTE) {
        composable(DASHBOARD_ROUTE) {
            DashboardScreen(onSettings = { navController.navigate(SETTINGS_ROUTE) })
        }
        composable(SETTINGS_ROUTE) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
