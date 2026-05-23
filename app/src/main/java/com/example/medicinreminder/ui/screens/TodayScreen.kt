package com.example.medicinreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.medicinreminder.R
import com.example.medicinreminder.data.entity.DoseLogEntity
import com.example.medicinreminder.data.entity.MedicineEntity
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.repository.AppContainer
import com.example.medicinreminder.notifications.TimeOfDayPeriod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var timeRefreshTick by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    val periodOrder = remember {
        listOf(
            TimeOfDayPeriod.MORNING,
            TimeOfDayPeriod.AFTERNOON,
            TimeOfDayPeriod.EVENING,
            TimeOfDayPeriod.NIGHT
        )
    }

    val currentPeriod = remember(timeRefreshTick) { currentTimePeriod() }

    val (todayStart, todayEnd) = remember(dayRefreshTick) { getTodayTimeRange() }

    val schedulesByMedicine = remember(schedules) { schedules.groupBy { it.medicineId } }
    val todayLogsByMedicine = remember(todayLogs) { todayLogs.groupBy { it.medicineId } }

    val groupedMedicines = remember(medicines, schedulesByMedicine) {
        periodOrder.associateWith { period ->
            medicines.filter { medicine ->
                schedulesByMedicine[medicine.id].orEmpty().any { TimeOfDayPeriod.fromTime(it.timeOfDay) == period }
            }
        }
    }

    val otherMedicines = remember(medicines, schedulesByMedicine) {
        medicines.filter { medicine -> schedulesByMedicine[medicine.id].isNullOrEmpty() }
    }

    val periodSections = remember(medicines, schedulesByMedicine, todayLogsByMedicine) {
        periodOrder.mapNotNull { period ->
            val medicinesInPeriod = groupedMedicines[period].orEmpty()
            if (medicinesInPeriod.isEmpty()) {
                null
            } else {
                val medicineRows = medicinesInPeriod.map { medicine ->
                    val periodSchedules = schedulesByMedicine[medicine.id].orEmpty().filter { TimeOfDayPeriod.fromTime(it.timeOfDay) == period }
                    TodayMedicineRow(
                        medicine = medicine,
                        schedules = periodSchedules,
                        todayLogs = todayLogsByMedicine[medicine.id].orEmpty()
                    )
                }
                val headerTime = medicineRows
                    .asSequence()
                    .flatMap { it.schedules.asSequence() }
                    .map { it.timeOfDay }
                    .sorted()
                    .firstOrNull()
                    ?.to12HourFormat()
                    ?: "--"

                TodayPeriodSection(
                    period = period,
                    headerTime = headerTime,
                    medicineRows = medicineRows
                )
            }
        }
    }

    val sectionAnchors = remember(periodSections, otherMedicines) {
        val periodHeaderIndices = mutableMapOf<TimeOfDayPeriod, Int>()
        var currentIndex = 1

        periodSections.forEach { section ->
            periodHeaderIndices[section.period] = currentIndex
            currentIndex += 1 + section.medicineRows.size
        }

        SectionAnchors(
            periodHeaderIndices = periodHeaderIndices,
            otherHeaderIndex = if (otherMedicines.isNotEmpty()) currentIndex else null
        )
    }

    val focusTarget = remember(currentPeriod, periodOrder, periodSections, otherMedicines) {
        resolveFocusTarget(
            currentPeriod = currentPeriod,
            periodOrder = periodOrder,
            availablePeriods = periodSections.map { it.period }.toSet(),
            otherMedicines = otherMedicines
        )
    }

    val focusHeaderIndex = remember(focusTarget, sectionAnchors) {
        when (focusTarget) {
            is FocusTarget.Other -> sectionAnchors.otherHeaderIndex
            is FocusTarget.Period -> sectionAnchors.periodHeaderIndices[focusTarget.period]
            null -> null
        }
    }

    val focusedPeriod = when (focusTarget) {
        is FocusTarget.Period -> focusTarget.period
        else -> null
    }

    LaunchedEffect(focusHeaderIndex) {
        val targetIndex = focusHeaderIndex ?: return@LaunchedEffect
        if (listState.firstVisibleItemIndex != targetIndex) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val waitUntilMinute = 60_000L - (now % 60_000L)
            delay(waitUntilMinute + 250L)
            timeRefreshTick++
        }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
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
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.no_medicines_yet),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.tap_add_medicine),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { navController.navigate("scan_add") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(R.string.add_medicine_title))
                        }
                        OutlinedButton(onClick = { navController.navigate("scan_add") }) {
                            Text(stringResource(R.string.scan_or_add_quickly))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "current_period_banner") {
                    CurrentPeriodBanner(
                        currentPeriod = currentPeriod,
                        focusPeriod = focusedPeriod ?: currentPeriod
                    )
                }

                periodSections.forEach { section ->
                    val isFocusedPeriod = section.period == focusedPeriod

                    item(key = "header_${section.period.name}") {
                        PeriodHeader(
                            title = section.period.displayName,
                            time = section.headerTime,
                            count = section.medicineRows.size,
                            isCurrent = isFocusedPeriod
                        )
                    }

                    items(
                        items = section.medicineRows,
                        key = { "${section.period.name}_${it.medicine.id}" },
                        contentType = { "medicine_card" }
                    ) { row ->
                        MedicineCard(
                            medicine = row.medicine,
                            schedules = row.schedules,
                            todayLogs = row.todayLogs,
                            isDimmed = section.period != focusedPeriod,
                            onClick = { selectedMedicine = row.medicine },
                            onMarkTaken = { scheduleId, isTaken ->
                                scope.launch {
                                    val log = DoseLogEntity(
                                        medicineId = row.medicine.id,
                                        scheduleId = scheduleId,
                                        scheduledAt = System.currentTimeMillis(),
                                        actionTaken = if (isTaken) "TAKEN" else "SKIPPED"
                                    )
                                    appContainer.reminderRepository.insertLog(log)
                                }
                            },
                            onToggleArchive = {
                                scope.launch {
                                    appContainer.medicineRepository.archiveMedicine(row.medicine.id)
                                }
                            }
                        )
                    }
                }

                if (otherMedicines.isNotEmpty()) {
                    item(key = "header_other") {
                        PeriodHeader(
                            title = stringResource(R.string.other_as_needed),
                            time = "--",
                            count = otherMedicines.size,
                            isCurrent = false
                        )
                    }
                    items(
                        items = otherMedicines,
                        key = { "other_${it.id}" },
                        contentType = { "other_medicine_card" }
                    ) { medicine ->
                        MedicineCard(
                            medicine = medicine,
                            schedules = emptyList(),
                            todayLogs = todayLogsByMedicine[medicine.id].orEmpty(),
                            isDimmed = true,
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
private fun CurrentPeriodBanner(currentPeriod: TimeOfDayPeriod, focusPeriod: TimeOfDayPeriod) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.current_time_focus),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = focusPeriod.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (focusPeriod != currentPeriod) {
                Text(
                    text = currentPeriod.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PeriodHeader(title: String, time: String, count: Int, isCurrent: Boolean) {
    Surface(
        tonalElevation = if (isCurrent) 5.dp else 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.period_header, title, time, count),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (isCurrent) {
                Text(
                    text = stringResource(R.string.current_time_focus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MedicineCard(
    medicine: MedicineEntity,
    schedules: List<ReminderScheduleEntity>,
    todayLogs: List<DoseLogEntity> = emptyList(),
    isDimmed: Boolean = false,
    onClick: () -> Unit,
    onMarkTaken: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleArchive: () -> Unit = {}
) {
    val cardAlpha = when {
        medicine.isArchived -> 0.45f
        isDimmed -> 0.58f
        else -> 1f
    }
    val cardColor = if (medicine.isArchived) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        modifier = Modifier.padding(start = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
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
                                onCheckedChange = { checked -> onMarkTaken(schedule.id, checked) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun currentTimePeriod(): TimeOfDayPeriod {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..11 -> TimeOfDayPeriod.MORNING
        hour in 12..16 -> TimeOfDayPeriod.AFTERNOON
        hour in 17..20 -> TimeOfDayPeriod.EVENING
        else -> TimeOfDayPeriod.NIGHT
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

private fun resolveFocusTarget(
    currentPeriod: TimeOfDayPeriod,
    periodOrder: List<TimeOfDayPeriod>,
    availablePeriods: Set<TimeOfDayPeriod>,
    otherMedicines: List<MedicineEntity>
): FocusTarget? {
    if (currentPeriod in availablePeriods) {
        return FocusTarget.Period(currentPeriod)
    }

    val currentIndex = periodOrder.indexOf(currentPeriod)
    if (currentIndex >= 0) {
        val previousAvailable = periodOrder
            .take(currentIndex + 1)
            .asReversed()
            .firstOrNull { it in availablePeriods }
        if (previousAvailable != null) {
            return FocusTarget.Period(previousAvailable)
        }

        val nextAvailable = periodOrder
            .drop(currentIndex + 1)
            .firstOrNull { it in availablePeriods }
        if (nextAvailable != null) {
            return FocusTarget.Period(nextAvailable)
        }
    }

    if (otherMedicines.isNotEmpty()) {
        return FocusTarget.Other
    }

    return periodOrder.firstOrNull { it in availablePeriods }?.let {
        FocusTarget.Period(it)
    }
}

private data class TodayPeriodSection(
    val period: TimeOfDayPeriod,
    val headerTime: String,
    val medicineRows: List<TodayMedicineRow>
)

private data class TodayMedicineRow(
    val medicine: MedicineEntity,
    val schedules: List<ReminderScheduleEntity>,
    val todayLogs: List<DoseLogEntity>
)

private data class SectionAnchors(
    val periodHeaderIndices: Map<TimeOfDayPeriod, Int>,
    val otherHeaderIndex: Int?
)

private sealed interface FocusTarget {
    data class Period(val period: TimeOfDayPeriod) : FocusTarget
    data object Other : FocusTarget
}
