package com.example.medicinreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.example.medicinreminder.billing.BillingManager
import com.example.medicinreminder.data.repository.AppContainer
import com.example.medicinreminder.data.worker.MedicineSyncScheduler
import com.example.medicinreminder.ui.navigation.AppNavigation
import com.example.medicinreminder.ui.theme.MedicinReminderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer
    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        try {
            appContainer = AppContainer(application)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AppContainer", e)
            finish()
            return
        }

        runCatching {
            MedicineSyncScheduler.schedulePeriodicSync(applicationContext)
        }.onFailure { error ->
            Log.e(tag, "Failed to schedule medicine sync", error)
        }
        
        // Initialize entitlement and billing
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appContainer.entitlementRepository.initializeEntitlement()
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize entitlement", e)
            }
        }
        
        try {
            appContainer.billingManager.initialize { ready ->
                // Billing manager ready
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize billing", e)
        }
        
        try {
            setContent {
                MedicinReminderTheme {
                    MedicineReminderApp(appContainer)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to set content", e)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appContainer.billingManager.destroy()
    }
}

@Composable
fun MedicineReminderApp(appContainer: AppContainer) {
    val navController = rememberNavController()
    AppNavigation(
        navController = navController,
        appContainer = appContainer
    )
}