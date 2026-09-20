package com.example.newworkspace.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.newworkspace.ui.dashboard.DashboardScreen

private const val DASHBOARD_ROUTE = "dashboard"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = DASHBOARD_ROUTE) {
        composable(DASHBOARD_ROUTE) {
            DashboardScreen()
        }
    }
}
