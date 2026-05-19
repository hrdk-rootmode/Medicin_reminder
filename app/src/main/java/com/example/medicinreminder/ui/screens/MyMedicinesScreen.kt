package com.example.medicinreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.medicinreminder.data.entity.MedicineEntity
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.entity.UserEntitlementEntity
import com.example.medicinreminder.data.repository.AppContainer
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMedicinesScreen(
    navController: NavHostController,
    appContainer: AppContainer
) {
    val medicines by appContainer.medicineRepository.getAllMedicinesIncludingArchived().collectAsState(initial = emptyList())
    val schedules by appContainer.reminderRepository.getAllActiveSchedules().collectAsState(initial = emptyList())
    val entitlement by appContainer.entitlementRepository.getEntitlement().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var selectedMedicine by remember { mutableStateOf<MedicineEntity?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Medicines") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("today") },
                    icon = { Text("Today") },
                    label = { Text("Today") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("scan_add") },
                    icon = { Text("Add") },
                    label = { Text("Add Medicine") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Text("My") },
                    label = { Text("My Medicines") }
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
                EntitlementCard(
                    entitlement = entitlement,
                    onUpgradeClick = { /* TODO: Launch billing flow */ },
                    onRestoreClick = { /* TODO: Restore purchase */ }
                )
            }
            
            item {
                Text(
                    text = "My Medicines",
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
                        Text("No medicines added yet")
                    }
                }
            } else {
                items(medicines) { medicine ->
                    MedicineLibraryCard(
                        medicine = medicine,
                        schedules = schedules.filter { it.medicineId == medicine.id },
                        isArchived = medicine.isArchived,
                        onClick = { selectedMedicine = medicine },
                        onDelete = {
                            scope.launch {
                                appContainer.medicineRepository.archiveMedicine(medicine.id)
                            }
                        }
                    )
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
            }
        )
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
                text = if (entitlement?.isPremium == true) "Premium Active" else "Free Plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Active reminders: ${entitlement?.activeReminderLimit ?: 5}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (entitlement?.isPremium == false) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUpgradeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upgrade to Premium")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onRestoreClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore Purchase")
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Thank you for supporting us!",
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
    onDelete: () -> Unit
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
                                text = "Disabled",
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
                TextButton(onClick = onDelete) {
                    Text("Disable")
                }
            }
        }
    }
}
