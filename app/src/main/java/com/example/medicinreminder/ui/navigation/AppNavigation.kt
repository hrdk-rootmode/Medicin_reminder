package com.example.medicinreminder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.medicinreminder.ui.screens.TodayScreen
import com.example.medicinreminder.ui.screens.ScanAddScreen
import com.example.medicinreminder.ui.screens.MyMedicinesScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    appContainer: com.example.medicinreminder.data.repository.AppContainer
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Today.route
    ) {
        composable(Screen.Today.route) {
            TodayScreen(
                navController = navController,
                appContainer = appContainer
            )
        }
        // Keep simple add route
        composable(Screen.ScanAdd.route) {
            ScanAddScreen(
                navController = navController,
                appContainer = appContainer,
                editMedicineId = null
            )
        }

        // Optional route to edit an existing medicine by id: scan_add?editId=123
        composable("scan_add?editId={editId}") { backStackEntry ->
            val editIdArg = backStackEntry.arguments?.getString("editId")
            val editId = editIdArg?.toLongOrNull()?.takeIf { it > 0 }
            ScanAddScreen(
                navController = navController,
                appContainer = appContainer,
                editMedicineId = editId
            )
        }
        composable(Screen.MyMedicines.route) {
            MyMedicinesScreen(
                navController = navController,
                appContainer = appContainer
            )
        }
    }
}
