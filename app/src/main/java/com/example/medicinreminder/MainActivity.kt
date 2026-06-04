package com.example.medicinreminder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import com.example.medicinreminder.billing.BillingManager
import com.example.medicinreminder.data.repository.AppContainer
import com.example.medicinreminder.data.settings.LanguagePreferences
import com.example.medicinreminder.data.worker.MedicineSyncScheduler
import com.example.medicinreminder.R
import com.example.medicinreminder.security.DeviceIntegrityChecker
import com.example.medicinreminder.ui.navigation.AppNavigation
import com.example.medicinreminder.ui.theme.MedicinReminderTheme
import com.example.medicinreminder.ui.screens.SecurityBlockScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    private lateinit var appContainer: AppContainer
    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        runBlocking(Dispatchers.IO) {
            val savedLanguageTag = LanguagePreferences.getLanguageTag(applicationContext)
            AppCompatDelegate.setApplicationLocales(
                if (savedLanguageTag.isNullOrBlank()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(savedLanguageTag)
                }
            )
        }

        super.onCreate(savedInstanceState)

        val compromised = runCatching { DeviceIntegrityChecker.isDeviceCompromised(applicationContext) }.getOrDefault(false)
        if (compromised) {
            setContent {
                MedicinReminderTheme {
                    SecurityBlockScreen()
                }
            }
            return
        }
        
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
    val context = LocalContext.current
    val navController = rememberNavController()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }
    var showExactAlarmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                showExactAlarmDialog = true
            }
        }
    }

    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text(stringResource(R.string.allow_medicine_reminders_title)) },
            text = { Text(stringResource(R.string.allow_medicine_reminders_message)) },
            confirmButton = {
                Button(onClick = {
                    showExactAlarmDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        exactAlarmLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                }) {
                    Text(stringResource(R.string.allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }

    AppNavigation(
        navController = navController,
        appContainer = appContainer
    )
}