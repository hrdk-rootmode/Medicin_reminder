package com.example.medicinreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import kotlinx.coroutines.flow.firstOrNull

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
    var pendingDeleteMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
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

    val groupedMedicines by remember(medicines, schedulesByMedicine) {
        derivedStateOf {
            periodOrder.associateWith { period ->
                medicines.filter { medicine ->
                    schedulesByMedicine[medicine.id].orEmpty()
                        .any { TimeOfDayPeriod.fromTime(it.timeOfDay) == period }
                }
            }
        }
    }

    val otherMedicines by remember(medicines, schedulesByMedicine) {
        derivedStateOf { medicines.filter { medicine -> schedulesByMedicine[medicine.id].isNullOrEmpty() } }
    }

    // Precompute normalized groups per period to avoid repeated groupBy during recomposition
    val normalizedGroupsByPeriod by remember(medicines, schedulesByMedicine) {
        derivedStateOf {
            periodOrder.associateWith { period ->
                groupedMedicines[period].orEmpty().groupBy { it.normalizedTitle }
            }
        }
    }

    val normalizedGroupsOther by remember(otherMedicines) {
        derivedStateOf { otherMedicines.groupBy { it.normalizedTitle } }
    }

    LaunchedEffect(Unit) {
        selectedLanguageTag = LanguagePreferences.getLanguageTag(context)
    }

    val selectedLanguageLabelRes = languageOptions.firstOrNull { it.tag == selectedLanguageTag }?.labelRes
        ?: R.string.language_system_default
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_medicines_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                    )
                ) {
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
                val activity = LocalContext.current as? android.app.Activity
                val isPurchasing by appContainer.billingManager.purchaseInProgress.collectAsState(initial = false)
                EntitlementCard(
                    entitlement = entitlement,
                    isPurchasing = isPurchasing,
                    onUpgradeClick = {
                        activity?.let { appContainer.billingManager.launchPurchaseFlow(it) }
                    },
                    onRestoreClick = {
                        // re-query purchases to pick up restored purchases
                        appContainer.billingManager.queryPurchases()
                    },
                    onDevToggle = { enabled ->
                        scope.launch {
                            appContainer.entitlementRepository.updatePremiumStatus(enabled)
                        }
                    }
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
                                count = normalizedGroupsByPeriod[period].orEmpty().size
                            )
                        }
                        val grouped = normalizedGroupsByPeriod[period].orEmpty()
                        val distinctList = grouped.values.map { it.first() }
                        items(items = distinctList, key = { "${period.name}_${it.id}" }) { medicine ->
                            val dupCount = grouped[medicine.normalizedTitle]?.size ?: 1
                            MedicineLibraryCard(
                                medicine = medicine,
                                schedules = schedulesByMedicine[medicine.id].orEmpty()
                                    .filter { TimeOfDayPeriod.fromTime(it.timeOfDay) == period },
                                isArchived = medicine.isArchived,
                                duplicateCount = dupCount,
                                onClick = { selectedMedicine = medicine },
                                onAlertSettingsClick = { alertSettingsMedicine = medicine },
                                onDisableRequest = { pendingDisableMedicine = medicine },
                                onDeleteRequest = { pendingDeleteMedicine = medicine },
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
                        MedicineCategoryHeader(
                            title = "Other / As Needed",
                            count = normalizedGroupsOther.values.size
                        )
                    }
                    val groupedOther = normalizedGroupsOther
                    val distinctOther = groupedOther.values.map { it.first() }
                    items(items = distinctOther, key = { "other_${it.id}" }) { medicine ->
                        val dupCount = groupedOther[medicine.normalizedTitle]?.size ?: 1
                        MedicineLibraryCard(
                            medicine = medicine,
                            schedules = emptyList(),
                            isArchived = medicine.isArchived,
                            duplicateCount = dupCount,
                            onClick = { selectedMedicine = medicine },
                            onAlertSettingsClick = { alertSettingsMedicine = medicine },
                            onDisableRequest = { pendingDisableMedicine = medicine },
                            onDeleteRequest = { pendingDeleteMedicine = medicine },
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

    pendingDeleteMedicine?.let { medicine ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMedicine = null },
            title = { Text(stringResource(R.string.delete_medicine_title)) },
            text = { Text(stringResource(R.string.delete_medicine_message, medicine.title)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val schedulesToDelete = appContainer.reminderRepository.getSchedulesForMedicine(medicine.id).firstOrNull().orEmpty()
                        schedulesToDelete.forEach { schedule ->
                            appContainer.reminderRepository.deleteSchedule(schedule)
                        }
                        appContainer.reminderRepository.deleteLogsForMedicine(medicine.id)
                        appContainer.medicineRepository.deleteMedicine(medicine)
                        pendingDeleteMedicine = null
                    }
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMedicine = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.language_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.language_picker_current, stringResource(selectedLanguageLabelRes)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languageOptions.forEach { option ->
                            Button(
                                onClick = {
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
                                        (context as? android.app.Activity)?.recreate()
                                        showLanguageDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = stringResource(option.labelRes),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { showLanguageDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
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
    isPurchasing: Boolean = false,
    onUpgradeClick: () -> Unit = {},
    onRestoreClick: () -> Unit = {},
    onDevToggle: (Boolean) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entitlement?.l1f3t1m3_flag == true) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            val trialDaysRemaining = remember(entitlement) {
                entitlement?.let {
                    val now = System.currentTimeMillis()
                    val remaining = ((it.trialEnd - now) / (24 * 60 * 60 * 1000)).toInt()
                    if (remaining > 0) remaining else 0
                } ?: 0
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (entitlement?.l1f3t1m3_flag == true) stringResource(R.string.premium_active) else stringResource(R.string.free_plan),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (entitlement?.l1f3t1m3_flag == true) {
                            stringResource(R.string.unlimited_reminders)
                        } else {
                            stringResource(R.string.active_reminders, entitlement?.activeReminderLimit ?: 5)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (trialDaysRemaining > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = trialDaysRemaining.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.days_left_short),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (com.example.medicinreminder.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Dev Premium", modifier = Modifier.weight(1f))
                    val devEnabled = entitlement?.l1f3t1m3_flag == true
                    Switch(checked = devEnabled, onCheckedChange = { onDevToggle(it) })
                }
            }
            if (entitlement?.l1f3t1m3_flag == false) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUpgradeClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPurchasing
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.upgrade_to_premium))
                    } else {
                        Text(stringResource(R.string.upgrade_to_premium))
                    }
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
@OptIn(ExperimentalFoundationApi::class)
fun MedicineLibraryCard(
    medicine: MedicineEntity,
    schedules: List<ReminderScheduleEntity>,
    isArchived: Boolean = false,
    duplicateCount: Int = 1,
    onClick: () -> Unit,
    onAlertSettingsClick: () -> Unit,
    onDisableRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onEnable: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDeleteRequest
            ),
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
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Crop
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
                        if (duplicateCount > 1) {
                            Text(
                                text = "(${duplicateCount})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        if (isArchived) {
                            Text(
                                text = stringResource(R.string.disabled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp)
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
                val scheduleSummary = remember(schedules) { schedules.joinToString(" • ") { it.toReadableSummary() } }
                Text(
                    text = scheduleSummary,
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
