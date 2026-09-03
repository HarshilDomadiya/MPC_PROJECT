package com.example.plantmonitor.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.plantmonitor.ui.screens.*
import com.example.plantmonitor.viewmodel.*

object Destinations {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val PLANT_LIST = "plant_list"
    const val ADD_PLANT = "add_plant"
    const val CLAIM_DEVICE = "claim_device"
    const val PLANT_DASHBOARD = "plant_dashboard/{plantId}"
    const val PLANT_SETTINGS = "plant_settings/{plantId}"
    const val PLANT_HISTORY = "plant_history/{plantId}"

    fun dashboard(plantId: String) = "plant_dashboard/$plantId"
    fun settings(plantId: String) = "plant_settings/$plantId"
    fun history(plantId: String) = "plant_history/$plantId"
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = viewModel(),
    plantListViewModel: PlantListViewModel = viewModel(),
    dashboardViewModel: PlantDashboardViewModel = viewModel(),
    historyViewModel: PlantHistoryViewModel = viewModel()
) {
    val navController = rememberNavController()
    val startDestination = if (authViewModel.isLoggedIn.value) Destinations.PLANT_LIST else Destinations.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destinations.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToSignUp = { navController.navigate(Destinations.SIGNUP) },
                onLoginSuccess = {
                    navController.navigate(Destinations.PLANT_LIST) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.SIGNUP) {
            SignUpScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = { navController.popBackStack() },
                onSignUpSuccess = {
                    navController.navigate(Destinations.PLANT_LIST) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.PLANT_LIST) {
            PlantListScreen(
                authViewModel = authViewModel,
                plantListViewModel = plantListViewModel,
                onSelectPlant = { plantId ->
                    navController.navigate(Destinations.dashboard(plantId))
                },
                onAddPlant = {
                    navController.navigate(Destinations.ADD_PLANT)
                },
                onClaimDevice = {
                    navController.navigate(Destinations.CLAIM_DEVICE)
                },
                onLogout = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.ADD_PLANT) {
            AddPlantScreen(
                plantListViewModel = plantListViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        composable(Destinations.CLAIM_DEVICE) {
            ClaimDeviceScreen(
                plantListViewModel = plantListViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.PLANT_DASHBOARD,
            arguments = listOf(navArgument("plantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val plantId = backStackEntry.arguments?.getString("plantId") ?: ""
            PlantDashboardScreen(
                plantId = plantId,
                dashboardViewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { pId ->
                    navController.navigate(Destinations.settings(pId))
                },
                onNavigateToHistory = { pId ->
                    navController.navigate(Destinations.history(pId))
                }
            )
        }

        composable(
            route = Destinations.PLANT_SETTINGS,
            arguments = listOf(navArgument("plantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val plantId = backStackEntry.arguments?.getString("plantId") ?: ""
            PlantSettingsScreen(
                plantId = plantId,
                dashboardViewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.PLANT_HISTORY,
            arguments = listOf(navArgument("plantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val plantId = backStackEntry.arguments?.getString("plantId") ?: ""
            PlantHistoryScreen(
                plantId = plantId,
                historyViewModel = historyViewModel,
                dashboardViewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
