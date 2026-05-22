package com.example.medicinreminder.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.launch
import com.example.medicinreminder.data.model.MedicineInfo
import com.example.medicinreminder.data.ads.RewardedAdManager
import coil.compose.AsyncImage
import androidx.compose.material3.OutlinedButton
import com.example.medicinreminder.R
import com.example.medicinreminder.data.entity.MedicineEntity
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.repository.MedicineInfoRepository
import java.io.File
import java.io.FileOutputStream

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
    val context = LocalContext.current
    val aiUnavailableText = stringResource(R.string.ai_summary_unavailable)
    var title by remember(medicine.id) { mutableStateOf(medicine.title) }
    var reminderTitle by remember(medicine.id) { mutableStateOf(medicine.reminderTitle) }
    var dosageText by remember(medicine.id) { mutableStateOf(medicine.dosageText) }
    var notes by remember(medicine.id) { mutableStateOf(medicine.notes) }
    var imageUri by remember(medicine.id) { mutableStateOf(medicine.imageUri.orEmpty()) }
    var infoText by remember { mutableStateOf<MedicineEntity?>(null) }
    var info by remember { mutableStateOf<com.example.medicinreminder.data.model.MedicineInfo?>(null) }
    var useAi by remember { mutableStateOf(false) }
    var aiInfo by remember { mutableStateOf<MedicineInfo?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var useAppLanguage by remember { mutableStateOf(false) }
    var useHinglish by remember { mutableStateOf(false) }
    var detailedAi by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUri = uri.toString()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            imageUri = Uri.fromFile(saveBitmapToCache(context, bitmap)).toString()
        }
    }

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

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (imageUri.isNotBlank()) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = stringResource(R.string.medicine_photo),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 1.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = stringResource(R.string.no_medicine_image),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { cameraLauncher.launch(null) }) {
                                Text(stringResource(R.string.capture))
                            }
                            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                                Text(stringResource(R.string.gallery))
                            }
                            if (imageUri.isNotBlank()) {
                                TextButton(onClick = { imageUri = "" }) {
                                    Text(stringResource(R.string.remove_image))
                                }
                            }
                        }
                    }
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.use_ai_summary))
                    Switch(checked = useAi, onCheckedChange = { useAi = it })
                }

                if (useAi && info != null) {
                    if (aiLoading) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    aiError = null
                                    coroutineScope.launch {
                                        aiLoading = true
                                        val rewarded = RewardedAdManager.showRewardedAd(context)
                                        if (!rewarded) {
                                            aiError = "Ad not completed"
                                            aiLoading = false
                                            return@launch
                                        }
                                        val result = runCatching {
                                            medicineInfoRepository.summarizeWithAi(title, useAppLanguage, useHinglish, detailedAi)
                                        }.getOrNull()
                                        if (result == null) {
                                            aiError = aiUnavailableText
                                        } else {
                                            aiInfo = result
                                        }
                                        aiLoading = false
                                    }
                                }) {
                                    Text(stringResource(R.string.summarize_with_ai))
                                }
                                if (aiInfo != null) {
                                    OutlinedButton(onClick = {
                                        coroutineScope.launch {
                                            medicineInfoRepository.cacheAiSummary(title, aiInfo!!)
                                        }
                                    }) {
                                        Text(stringResource(R.string.save_ai_summary))
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.Checkbox(checked = useAppLanguage, onCheckedChange = { useAppLanguage = it })
                                Text(stringResource(R.string.use_app_language))
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Checkbox(checked = useHinglish, onCheckedChange = { useHinglish = it })
                                Text(stringResource(R.string.use_hinglish))
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Checkbox(checked = detailedAi, onCheckedChange = { detailedAi = it })
                                Text(stringResource(R.string.detailed_ai_summary))
                            }
                        }
                        aiError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }

                if (info == null) {
                    Text(stringResource(R.string.no_trusted_medicine_info_found))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(info!!.displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            // Trigger AI summarize via the same flow as the button above
                            aiError = null
                            coroutineScope.launch {
                                aiLoading = true
                                val rewarded = RewardedAdManager.showRewardedAd(context)
                                if (!rewarded) {
                                    aiError = "Ad not completed"
                                    aiLoading = false
                                    return@launch
                                }
                                val result = runCatching {
                                    medicineInfoRepository.summarizeWithAi(title, useAppLanguage, useHinglish, detailedAi)
                                }.getOrNull()
                                if (result == null) {
                                    aiError = aiUnavailableText
                                } else {
                                    aiInfo = result
                                }
                                aiLoading = false
                            }
                        }) {
                            Text("AI")
                        }
                    }
                    // If an AI summary exists in memory, show it above the trusted info
                    aiInfo?.let { summary ->
                        Text(text = "AI Summary", style = MaterialTheme.typography.titleMedium)
                        BulletSection(
                            title = stringResource(R.string.what_its_for),
                            items = summary.commonUses
                        )
                        BulletSection(
                            title = stringResource(R.string.side_effects),
                            items = summary.commonSideEffects
                        )
                        BulletSection(
                            title = stringResource(R.string.warnings),
                            items = summary.warnings
                        )
                    }
                    BulletSection(
                        title = stringResource(R.string.what_its_for),
                        items = info!!.commonUses
                    )
                    BulletSection(
                        title = stringResource(R.string.side_effects),
                        items = info!!.commonSideEffects
                    )
                    BulletSection(
                        title = stringResource(R.string.warnings),
                        items = info!!.warnings
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.storage_guidance), style = MaterialTheme.typography.titleMedium)
                            Text(info!!.storageGuidance)
                        }
                    }
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

private fun saveBitmapToCache(context: android.content.Context, bitmap: Bitmap): File {
    val outputFile = File(context.cacheDir, "medicine_image_${System.currentTimeMillis()}.png")
    FileOutputStream(outputFile).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    return outputFile
}

@Composable
private fun BulletSection(title: String, items: List<String>) {
    if (items.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Text("• $item", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}