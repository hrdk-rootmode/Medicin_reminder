package com.example.medicinreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.medicinreminder.R
import com.example.medicinreminder.data.entity.MedicineEntity
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.entity.DoseLogEntity
import com.example.medicinreminder.data.repository.AppContainer
import com.example.medicinreminder.notifications.TimeOfDayPeriod
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    navController: NavHostController,
    appContainer: AppContainer
) {
    val medicines by appContainer.medicineRepository.getAllMedicinesIncludingArchived().collectAsState(initial = emptyList())
    val schedules by appContainer.reminderRepository.getAllActiveSchedules().collectAsState(initial = emptyList())
    var activeReminderCount by remember { mutableIntStateOf(0) }
    var selectedMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
    val scope = rememberCoroutineScope()
    var todayLogs by remember { mutableStateOf<List<DoseLogEntity>>(emptyList()) }
    var dayRefreshTick by remember { mutableIntStateOf(0) }
    val periodOrder = remember {
        listOf(
            TimeOfDayPeriod.MORNING,
            TimeOfDayPeriod.AFTERNOON,
            TimeOfDayPeriod.EVENING,
            TimeOfDayPeriod.NIGHT
        )
    }

    val (todayStart, todayEnd) = remember(dayRefreshTick) { getTodayTimeRange() }

    val schedulesByMedicine = remember(schedules) {
        schedules.groupBy { it.medicineId }
    }

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
        while (true) {
            val waitMillis = millisUntilNextMidnight() + 1_000L
            delay(waitMillis)
            dayRefreshTick++
        }
    }

    LaunchedEffect(todayStart, todayEnd, appContainer) {
        activeReminderCount = appContainer.reminderRepository.getActiveScheduleCount()
        appContainer.reminderRepository.getLogsInRange(todayStart, todayEnd).collect { logs ->
            todayLogs = logs
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    Text(
                        text = stringResource(R.string.active_reminders_suffix, activeReminderCount),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
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
                    selected = false,
                    onClick = { navController.navigate("my_medicines") },
                    icon = { Text(stringResource(R.string.my_medicines_tab)) },
                    label = { Text(stringResource(R.string.my_medicines_tab)) }
                )
            }
        }
    ) { paddingValues ->
        if (medicines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.no_medicines_yet),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.tap_add_medicine),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                periodOrder.forEach { period ->
                    val medicinesInPeriod = groupedMedicines[period].orEmpty()
                    if (medicinesInPeriod.isNotEmpty()) {
                        val periodSchedules = medicinesInPeriod.flatMap { medicine ->
                            schedulesByMedicine[medicine.id].orEmpty()
                                .filter { TimeOfDayPeriod.fromTime(it.timeOfDay) == period }
                        }
                        val headerTime = periodSchedules
                            .map { it.timeOfDay }
                            .sorted()
                            .firstOrNull()
                            ?.to12HourFormat()
                            ?: "--"

                        item(key = "header_${period.name}") {
                            PeriodHeader(
                                title = period.displayName,
                                time = headerTime,
                                count = medicinesInPeriod.size
                            )
                        }

                        items(items = medicinesInPeriod, key = { "${period.name}_${it.id}" }) { medicine ->
                            MedicineCard(
                                medicine = medicine,
                                schedules = schedulesByMedicine[medicine.id].orEmpty()
                                    .filter { TimeOfDayPeriod.fromTime(it.timeOfDay) == period },
                                todayLogs = todayLogs.filter { it.medicineId == medicine.id },
                                onClick = { selectedMedicine = medicine },
                                onMarkTaken = { scheduleId, isTaken ->
                                    scope.launch {
                                        val log = DoseLogEntity(
                                            medicineId = medicine.id,
                                            scheduleId = scheduleId,
                                            scheduledAt = System.currentTimeMillis(),
                                            actionTaken = if (isTaken) "TAKEN" else "SKIPPED"
                                        )
                                        appContainer.reminderRepository.insertLog(log)
                                    }
                                },
                                onToggleArchive = {
                                    scope.launch {
                                        appContainer.medicineRepository.archiveMedicine(medicine.id)
                                    }
                                }
                            )
                        }
                    }
                }

                if (otherMedicines.isNotEmpty()) {
                    item(key = "header_other") {
                        PeriodHeader(
                            title = stringResource(R.string.other_as_needed),
                            time = "--",
                            count = otherMedicines.size
                        )
                    }
                    items(items = otherMedicines, key = { "other_${it.id}" }) { medicine ->
                        MedicineCard(
                            medicine = medicine,
                            schedules = emptyList(),
                            todayLogs = todayLogs.filter { it.medicineId == medicine.id },
                            onClick = { selectedMedicine = medicine },
                            onMarkTaken = { _, _ -> },
                            onToggleArchive = {
                                scope.launch {
                                    appContainer.medicineRepository.archiveMedicine(medicine.id)
                                }
                            }
                        )
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
                    appContainer.medicineRepository.archiveMedicine(medicine.id)
                    selectedMedicine = null
                }
            },
            onEdit = {
                selectedMedicine = null
                navController.navigate("scan_add?editId=${medicine.id}")
            }
        )
    }
}

@Composable
private fun PeriodHeader(title: String, time: String, count: Int) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.period_header, title, time, count),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun MedicineCard(
    medicine: MedicineEntity,
    schedules: List<ReminderScheduleEntity>,
    todayLogs: List<DoseLogEntity> = emptyList(),
    onClick: () -> Unit,
    onMarkTaken: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleArchive: () -> Unit = {}
) {
    val cardAlpha = if (medicine.isArchived) 0.5f else 1f
    val cardColor = if (medicine.isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!medicine.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = medicine.imageUri,
                        contentDescription = stringResource(R.string.medicine_image_desc, medicine.title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.medicine_placeholder),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicine.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (medicine.dosageText.isNotEmpty()) {
                        Text(
                            text = medicine.dosageText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (medicine.isArchived) {
                    Button(
                        onClick = onToggleArchive,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(stringResource(R.string.enable))
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
                DoseTableCard(
                    schedules = schedules,
                    todayLogs = todayLogs,
                    onMarkTaken = onMarkTaken,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.no_schedule_saved),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DoseTableCard(
    schedules: List<ReminderScheduleEntity>,
    todayLogs: List<DoseLogEntity> = emptyList(),
    onMarkTaken: (Long, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val dosePeriods = listOf("Morning", "Afternoon", "Evening", "Night")
    val schedulesByPeriod = dosePeriods.associateWith { period ->
        schedules.filter { it.timeOfDay.getDosePeriodForTime() == period }
    }
    
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dose_table), style = MaterialTheme.typography.titleSmall)
            
            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                dosePeriods.forEach { period ->
                    Text(
                        period,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Enabled row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.enabled), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                dosePeriods.forEach { period ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (schedulesByPeriod[period]?.isNotEmpty() == true) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.enabled),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text("—", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // Time row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.time), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                dosePeriods.forEach { period ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val schedule = schedulesByPeriod[period]?.firstOrNull()
                        if (schedule != null) {
                            Text(schedule.timeOfDay.to12HourFormat(), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("—", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // Taken row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.taken), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                dosePeriods.forEach { period ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val schedule = schedulesByPeriod[period]?.firstOrNull()
                        if (schedule != null) {
                            val isTaken = todayLogs.any { it.scheduleId == schedule.id && it.actionTaken == "TAKEN" }
                            Checkbox(
                                checked = isTaken,
                                onCheckedChange = { checked ->
                                    onMarkTaken(schedule.id, checked)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun millisUntilNextMidnight(): Long {
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return (calendar.timeInMillis - now).coerceAtLeast(60_000L)
}
