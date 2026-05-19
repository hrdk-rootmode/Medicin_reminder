package com.example.medicinreminder.ui.navigation

sealed class Screen(val route: String) {
    object Today : Screen("today")
    object ScanAdd : Screen("scan_add")
    object MyMedicines : Screen("my_medicines")
}
