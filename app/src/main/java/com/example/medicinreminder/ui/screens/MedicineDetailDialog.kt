package com.example.medicinreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.medicinreminder.data.entity.MedicineEntity
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.repository.MedicineInfoRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineDetailDialog(
    medicine: MedicineEntity,
    schedules: List<ReminderScheduleEntity>,
    medicineInfoRepository: MedicineInfoRepository,
    onDismiss: () -> Unit,
    onSave: (MedicineEntity) -> Unit,
    onArchive: () -> Unit
) {
    var title by remember(medicine.id) { mutableStateOf(medicine.title) }
    var reminderTitle by remember(medicine.id) { mutableStateOf(medicine.reminderTitle) }
    var dosageText by remember(medicine.id) { mutableStateOf(medicine.dosageText) }
    var notes by remember(medicine.id) { mutableStateOf(medicine.notes) }
    var imageUri by remember(medicine.id) { mutableStateOf(medicine.imageUri.orEmpty()) }
    var infoText by remember { mutableStateOf<MedicineEntity?>(null) }
    var info by remember { mutableStateOf<com.example.medicinreminder.data.model.MedicineInfo?>(null) }

    LaunchedEffect(title) {
        info = medicineInfoRepository.lookup(title)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "Medicine details", style = MaterialTheme.typography.headlineSmall)

                if (imageUri.isNotBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Medicine photo",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Medicine name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reminderTitle,
                    onValueChange = { reminderTitle = it },
                    label = { Text("Custom reminder title") },
                    supportingText = { Text("This is what the reminder will speak.") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dosageText,
                    onValueChange = { dosageText = it },
                    label = { Text("Dosage / strength") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Text(text = "Schedules", style = MaterialTheme.typography.titleMedium)
                if (schedules.isEmpty()) {
                    Text("No reminder schedules saved.")
                } else {
                    schedules.forEach { schedule ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(schedule.toReadableSummary())
                                Text("Days: ${schedule.repeatDays.toReadableDays()}")
                                Text("Food relation: ${schedule.foodRelation.toReadableFoodRelation()}")
                                Text(if (schedule.isActive) "Active" else "Paused")
                            }
                        }
                    }
                }

                Text(text = "Medicine info", style = MaterialTheme.typography.titleMedium)
                if (info == null) {
                    Text("No trusted medicine info found for this title yet. Add an alias or entry to app/src/main/assets/medicine_info.json if you want it to show here.")
                } else {
                    Text(info!!.displayName, style = MaterialTheme.typography.titleLarge)
                    Text("Uses: ${info!!.commonUses.joinToString()}")
                    Text("Side effects: ${info!!.commonSideEffects.joinToString()}")
                    Text("Warnings: ${info!!.warnings.joinToString()}")
                    Text("Storage: ${info!!.storageGuidance}")
                    Text(info!!.disclaimer, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f)) {
                        Text(if (medicine.isArchived) "Enable" else "Disable")
                    }
                    Button(
                        onClick = {
                            onSave(
                                medicine.copy(
                                    title = title.trim(),
                                    reminderTitle = reminderTitle.trim(),
                                    normalizedTitle = title.trim().lowercase(),
                                    dosageText = dosageText.trim(),
                                    notes = notes.trim(),
                                    imageUri = imageUri.ifBlank { null }
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}