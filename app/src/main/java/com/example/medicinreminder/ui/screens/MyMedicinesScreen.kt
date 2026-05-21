package com.example.medicinreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.medicinreminder.R
import com.example.medicinreminder.data.entity.MedicineEntity
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.entity.UserEntitlementEntity
import com.example.medicinreminder.data.repository.AppContainer
import com.example.medicinreminder.data.settings.LanguagePreferences
import com.example.medicinreminder.notifications.ReminderScheduler
import com.example.medicinreminder.notifications.TimeOfDayPeriod
import com.example.medicinreminder.ui.model.LocaleOption
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMedicinesScreen(
    navController: NavHostController,
    appContainer: AppContainer
) {
    val context = LocalContext.current
    val medicines by appContainer.medicineRepository.getAllMedicinesIncludingArchived().collectAsState(initial = emptyList())
    val schedules by appContainer.reminderRepository.getAllActiveSchedules().collectAsState(initial = emptyList())
    val entitlement by appContainer.entitlementRepository.getEntitlement().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var selectedMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
    var alertSettingsMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
    var pendingDisableMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf<String?>(null) }
    val languageOptions = remember {
        listOf(
            LocaleOption(null, R.string.language_system_default),
            LocaleOption("en", R.string.language_english),
            LocaleOption("hi", R.string.language_hindi),
            LocaleOption("ta", R.string.language_tamil),
            LocaleOption("te", R.string.language_telugu),
            LocaleOption("mr", R.string.language_marathi)
        )
    }
    val periodOrder = remember {
        listOf(
            TimeOfDayPeriod.MORNING,
            TimeOfDayPeriod.AFTERNOON,
            TimeOfDayPeriod.EVENING,
            TimeOfDayPeriod.NIGHT
        )
    }
    val schedulesByMedicine = remember(schedules) { schedules.groupBy { it.medicineId } }
    val groupedMedicines = remember(medicines, schedulesByMedicine) {
        periodOrder.associateWith { period ->
            medicines.filter { medicine ->
                schedulesByMedicine[medicine.id].orEmpty()
                    .any { TimeOfDayPeriod.fromTime(it.timeOfDay) == period }
            }
        }
    }
    val otherMedicines = remember(medicines, schedulesByMedicine) {
        medicines.filter { medicine -> schedulesByMedicine[medicine.id].isNullOrEmpty() }
    }

    LaunchedEffect(Unit) {
        selectedLanguageTag = LanguagePreferences.getLanguageTag(context)
    }

    val selectedLanguageLabelRes = languageOptions.firstOrNull { it.tag == selectedLanguageTag }?.labelRes
        ?: R.string.language_system_default
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_medicines_title)) },
                actions = {
                    TextButton(onClick = { showLanguageDialog = true }) {
                        Text(stringResource(R.string.language_picker_action))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("today") },
                    icon = { Text(stringResource(R.string.today_tab)) },
                    label = { Text(stringResource(R.string.today_tab)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("scan_add") },
                    icon = { Text(stringResource(R.string.add_tab)) },
                    label = { Text(stringResource(R.string.add_tab)) }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Text(stringResource(R.string.my_medicines_tab)) },
                    label = { Text(stringResource(R.string.my_medicines_tab)) }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.language_picker_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.language_picker_current, stringResource(selectedLanguageLabelRes)), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showLanguageDialog = true }) {
                            Text(stringResource(R.string.language_picker_action))
                        }
                    }
                }
            }

            item {
                EntitlementCard(
                    entitlement = entitlement,
                    onUpgradeClick = { /* TODO: Launch billing flow */ },
                    onRestoreClick = { /* TODO: Restore purchase */ }
                )
            }
            
            item {
                Text(
                    text = stringResource(R.string.my_medicines_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (medicines.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_medicines_added_yet))
                    }
                }
            } else {
                periodOrder.forEach { period ->
                    val medicinesInPeriod = groupedMedicines[period].orEmpty()
                    if (medicinesInPeriod.isNotEmpty()) {
                        item(key = "header_${period.name}") {
                            MedicineCategoryHeader(
                                title = period.displayName,
                                count = medicinesInPeriod.size
                            )
                        }

                        items(items = medicinesInPeriod, key = { "${period.name}_${it.id}" }) { medicine ->
                            MedicineLibraryCard(
                                medicine = medicine,
                                schedules = schedulesByMedicine[medicine.id].orEmpty()
                                    .filter { TimeOfDayPeriod.fromTime(it.timeOfDay) == period },
                                isArchived = medicine.isArchived,
                                onClick = { selectedMedicine = medicine },
                                onAlertSettingsClick = { alertSettingsMedicine = medicine },
                                onDisableRequest = { pendingDisableMedicine = medicine },
                                onEnable = {
                                    scope.launch {
                                        appContainer.medicineRepository.unarchiveMedicine(medicine.id)
                                    }
                                }
                            )
                        }
                    }
                }

                if (otherMedicines.isNotEmpty()) {
                    item(key = "header_other") {
                        MedicineCategoryHeader(title = "Other / As Needed", count = otherMedicines.size)
                    }
                    items(items = otherMedicines, key = { "other_${it.id}" }) { medicine ->
                        MedicineLibraryCard(
                            medicine = medicine,
                            schedules = emptyList(),
                            isArchived = medicine.isArchived,
                            onClick = { selectedMedicine = medicine },
                            onAlertSettingsClick = { alertSettingsMedicine = medicine },
                            onDisableRequest = { pendingDisableMedicine = medicine },
                            onEnable = {
                                scope.launch {
                                    appContainer.medicineRepository.unarchiveMedicine(medicine.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    pendingDisableMedicine?.let { medicine ->
        AlertDialog(
            onDismissRequest = { pendingDisableMedicine = null },
            title = { Text(stringResource(R.string.disable_medicine_title)) },
            text = { Text(stringResource(R.string.disable_medicine_message, medicine.title)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        appContainer.medicineRepository.archiveMedicine(medicine.id)
                        pendingDisableMedicine = null
                    }
                }) {
                    Text(stringResource(R.string.disable))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisableMedicine = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_picker_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    languageOptions.forEach { option ->
                        TextButton(onClick = {
                            selectedLanguageTag = option.tag
                            scope.launch {
                                LanguagePreferences.setLanguageTag(context, option.tag)
                                AppCompatDelegate.setApplicationLocales(
                                    if (option.tag.isNullOrBlank()) {
                                        LocaleListCompat.getEmptyLocaleList()
                                    } else {
                                        LocaleListCompat.forLanguageTags(option.tag)
                                    }
                                )
                                // Recreate the hosting Activity so resources and Compose recompose
                                (context as? android.app.Activity)?.recreate()
                                showLanguageDialog = false
                            }
                        }) {
                            Text(stringResource(option.labelRes))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    selectedMedicine?.let { medicine ->
        MedicineDetailDialog(
            medicine = medicine,
            schedules = schedules.filter { it.medicineId == medicine.id },
            medicineInfoRepository = appContainer.medicineInfoRepository,
            onDismiss = { selectedMedicine = null },
            onSave = { updatedMedicine ->
                scope.launch {
                    appContainer.medicineRepository.updateMedicine(updatedMedicine)
                    selectedMedicine = null
                }
            },
            onArchive = {
                scope.launch {
                    appContainer.medicineRepository.toggleArchiveStatus(medicine.id)
                    selectedMedicine = null
                }
            },
            onEdit = {
                // Close dialog and navigate to add screen with editId
                selectedMedicine = null
                navController.navigate("scan_add?editId=${medicine.id}")
            }
        )
    }

    alertSettingsMedicine?.let { medicine ->
        val medicineSchedules = schedules.filter { it.medicineId == medicine.id }
        val currentRepeatCount = (medicineSchedules.maxOfOrNull { it.alertRepeatCount } ?: 2).coerceIn(1, 5)
        val currentSpoken = medicineSchedules.any { it.spokenReminderEnabled }
        var repeatCount by remember(medicine.id) { mutableStateOf(currentRepeatCount.toFloat()) }
        var spokenEnabled by remember(medicine.id) { mutableStateOf(currentSpoken) }

        ModalBottomSheet(
            onDismissRequest = { alertSettingsMedicine = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.alert_settings_for, medicine.title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(stringResource(R.string.repeat_alert_times, repeatCount.toInt()))
                Slider(
                    value = repeatCount,
                    onValueChange = { repeatCount = it },
                    valueRange = 1f..5f,
                    steps = 3
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.spoken_reminder))
                        Text(stringResource(R.string.spoken_reminder_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = spokenEnabled, onCheckedChange = { spokenEnabled = it })
                }

                Button(
                    onClick = {
                        scope.launch {
                            val newRepeat = repeatCount.toInt().coerceIn(1, 5)
                            medicineSchedules.forEach { schedule ->
                                appContainer.reminderRepository.updateSchedule(
                                    schedule.copy(
                                        alertRepeatCount = newRepeat,
                                        spokenReminderEnabled = spokenEnabled
                                    )
                                )
                            }
                            ReminderScheduler(context, appContainer.reminderRepository).scheduleAllReminders()
                            alertSettingsMedicine = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save))
                }

                TextButton(
                    onClick = { alertSettingsMedicine = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun EntitlementCard(
    entitlement: UserEntitlementEntity?,
    onUpgradeClick: () -> Unit = {},
    onRestoreClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entitlement?.isPremium == true) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = if (entitlement?.isPremium == true) stringResource(R.string.premium_active) else stringResource(R.string.free_plan),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.active_reminders, entitlement?.activeReminderLimit ?: 5),
                style = MaterialTheme.typography.bodyMedium
            )
            if (entitlement?.isPremium == false) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUpgradeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.upgrade_to_premium))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onRestoreClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.restore_purchase))
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.thanks_for_supporting_us),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MedicineLibraryCard(
    medicine: MedicineEntity,
    schedules: List<ReminderScheduleEntity>,
    isArchived: Boolean = false,
    onClick: () -> Unit,
    onAlertSettingsClick: () -> Unit,
    onDisableRequest: () -> Unit,
    onEnable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!medicine.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = medicine.imageUri,
                        contentDescription = medicine.title,
                        modifier = Modifier.size(72.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = medicine.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isArchived) {
                            Text(
                                text = stringResource(R.string.disabled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (medicine.dosageText.isNotEmpty()) {
                        Text(
                            text = medicine.dosageText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            if (medicine.notes.isNotEmpty()) {
                Text(
                    text = medicine.notes,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (schedules.isNotEmpty()) {
                Text(
                    text = schedules.joinToString(" • ") { it.toReadableSummary() },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onAlertSettingsClick) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = stringResource(R.string.language_picker_title))
                }
                TextButton(onClick = if (isArchived) onEnable else onDisableRequest) {
                    Text(if (isArchived) stringResource(R.string.enable) else stringResource(R.string.disable))
                }
            }
        }
    }
}

@Composable
private fun MedicineCategoryHeader(title: String, count: Int) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.medicine_category_header, title, count),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
