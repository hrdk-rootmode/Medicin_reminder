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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.medicinreminder.R
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
    onArchive: () -> Unit,
    onEdit: () -> Unit
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
                Text(text = stringResource(R.string.medicine_details_screen), style = MaterialTheme.typography.headlineSmall)

                if (imageUri.isNotBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = stringResource(R.string.medicine_photo),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.medicine_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reminderTitle,
                    onValueChange = { reminderTitle = it },
                    label = { Text(stringResource(R.string.custom_reminder_title)) },
                    supportingText = { Text(stringResource(R.string.custom_reminder_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dosageText,
                    onValueChange = { dosageText = it },
                    label = { Text(stringResource(R.string.dosage_strength)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Text(text = stringResource(R.string.schedules), style = MaterialTheme.typography.titleMedium)
                if (schedules.isEmpty()) {
                    Text(stringResource(R.string.no_reminder_schedules_saved))
                } else {
                    schedules.forEach { schedule ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(schedule.toReadableSummary())
                                Text(stringResource(R.string.days_prefix, schedule.repeatDays.toReadableDays()))
                                Text(stringResource(R.string.food_relation_prefix, schedule.foodRelation.toReadableFoodRelation()))
                                Text(if (schedule.isActive) stringResource(R.string.active) else stringResource(R.string.paused))
                            }
                        }
                    }
                }

                Text(text = stringResource(R.string.medicine_info), style = MaterialTheme.typography.titleMedium)
                if (info == null) {
                    Text(stringResource(R.string.no_trusted_medicine_info_found))
                } else {
                    Text(info!!.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.uses_prefix, info!!.commonUses.joinToString()))
                    Text(stringResource(R.string.side_effects_prefix, info!!.commonSideEffects.joinToString()))
                    Text(stringResource(R.string.warnings_prefix, info!!.warnings.joinToString()))
                    Text(stringResource(R.string.storage_guidance) + ": ${info!!.storageGuidance}")
                    Text(info!!.disclaimer, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f)) {
                        Text(if (medicine.isArchived) stringResource(R.string.enable) else stringResource(R.string.disable))
                    }
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.edit))
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
                        Text(stringResource(R.string.save))
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}