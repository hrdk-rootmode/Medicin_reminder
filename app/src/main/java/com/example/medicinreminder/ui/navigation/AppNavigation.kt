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
        composable(Screen.ScanAdd.route) {
            ScanAddScreen(
                navController = navController,
                appContainer = appContainer
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
