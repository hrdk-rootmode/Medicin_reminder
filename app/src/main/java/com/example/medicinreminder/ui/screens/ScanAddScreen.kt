package com.example.medicinreminder.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.medicinreminder.data.api.OpenFDAClient
import com.example.medicinreminder.data.entity.MedicineEntity
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.model.MedicineInfo
import com.example.medicinreminder.data.model.MedicineNameSuggestion
import com.example.medicinreminder.data.model.OpenFdaMedicineInfo
import com.example.medicinreminder.data.ocr.MedicineOcrParser
import com.example.medicinreminder.data.ocr.MedicineOcrSuggestion
import com.example.medicinreminder.data.repository.AppContainer
import com.example.medicinreminder.notifications.ReminderScheduler
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay

private data class DoseSlotState(
    val label: String,
    val defaultTime: String,
    val enabled: Boolean = false,
    val time: String = defaultTime
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScanAddScreen(
    navController: NavHostController,
    appContainer: AppContainer
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    var cameraStatusMessage by remember { mutableStateOf("") }
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var title by remember { mutableStateOf("") }
    var reminderTitle by remember { mutableStateOf("") }
    var dosageText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf("08:00") }
    var foodRelation by remember { mutableStateOf("none") }
    var endDate by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrText by remember { mutableStateOf("") }
    var ocrSuggestion by remember { mutableStateOf(MedicineOcrSuggestion()) }
    var medicineInfo by remember { mutableStateOf<MedicineInfo?>(null) }
    var openFdaInfo by remember { mutableStateOf<OpenFdaMedicineInfo?>(null) }
    var openFdaLoading by remember { mutableStateOf(false) }
    var openFdaError by remember { mutableStateOf<String?>(null) }
    var nameSuggestions by remember { mutableStateOf<List<MedicineNameSuggestion>>(emptyList()) }
    var suggestionLoading by remember { mutableStateOf(false) }
    var ocrValidationMessage by remember { mutableStateOf<String?>(null) }
    var titleAutoFilled by remember { mutableStateOf(false) }
    var dosageAutoFilled by remember { mutableStateOf(false) }
    var notesAutoFilled by remember { mutableStateOf(false) }

    val weekdays = listOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )
    var selectedWeekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
    val doseSlots = remember {
        mutableStateListOf(
            DoseSlotState("Morning", "08:00"),
            DoseSlotState("Afternoon", "13:00"),
            DoseSlotState("Evening", "18:00"),
            DoseSlotState("Night", "21:00")
        )
    }

    fun applyOcr(text: String) {
        ocrText = text
        val parsedSuggestion = MedicineOcrParser.parse(text)
        val looksLikeMedicine = MedicineOcrParser.isLikelyMedicineText(text)

        if (!looksLikeMedicine && parsedSuggestion.title.isBlank() && parsedSuggestion.dosageText.isBlank()) {
            ocrSuggestion = MedicineOcrSuggestion()
            ocrValidationMessage = "This does not look like a medicine label. Try scanning the front or back of the medicine pack."
            return
        }

        ocrSuggestion = parsedSuggestion
        ocrValidationMessage = null

        if (title.isBlank() && ocrSuggestion.title.isNotBlank()) {
            title = ocrSuggestion.title
            titleAutoFilled = true
        }
        if (dosageText.isBlank() && ocrSuggestion.dosageText.isNotBlank()) {
            dosageText = ocrSuggestion.dosageText
            dosageAutoFilled = true
        }
        if (notes.isBlank() && ocrSuggestion.timingHint.isNotBlank()) {
            notes = ocrSuggestion.timingHint
            notesAutoFilled = true
        }
        if (notes.isBlank() && ocrSuggestion.batchOrExpiry.isNotBlank()) {
            notes = ocrSuggestion.batchOrExpiry
            notesAutoFilled = true
        }
        if (reminderTime == "08:00" && ocrSuggestion.timeHint.isNotBlank()) reminderTime = ocrSuggestion.timeHint
    }

    fun processBitmap(bitmap: Bitmap) {
        scope.launch {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            applyOcr(result.text)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            applyOcr(result.text)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        selectedImageUri = Uri.fromFile(saveBitmapToCache(context, bitmap))
        processBitmap(bitmap)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        if (granted) {
            cameraStatusMessage = ""
            cameraLauncher.launch(null)
        } else {
            cameraStatusMessage = "Camera permission denied. Use gallery scan instead."
            galleryLauncher.launch("image/*")
        }
    }

    val cameraAction = {
        if (!hasCamera) {
            cameraStatusMessage = "This device does not have a camera feature. Use gallery scan instead."
            galleryLauncher.launch("image/*")
        } else if (cameraPermissionGranted) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(title) {
        val query = title.trim()
        if (query.isBlank()) {
            medicineInfo = null
            openFdaInfo = null
            openFdaLoading = false
            openFdaError = null
            return@LaunchedEffect
        }

        delay(600)
        medicineInfo = appContainer.medicineInfoRepository.lookup(query)
        openFdaLoading = true
        openFdaError = null

        val cached = appContainer.remoteMedicineRepository.getCachedOpenFdaMedicineInfo(query)
        openFdaInfo = cached

        if (OpenFDAClient.isConnectedToInternet(context)) {
            val fetched = runCatching {
                appContainer.remoteMedicineRepository.fetchOpenFdaMedicineInfo(query)
            }.getOrNull()

            if (fetched != null) {
                openFdaInfo = fetched
            } else if (cached == null) {
                openFdaError = "No OpenFDA label match found yet."
            }
        } else if (cached == null) {
            openFdaError = "Offline: using local medicine info only."
        }

        openFdaLoading = false
    }

    LaunchedEffect(title, ocrText) {
        val query = title.trim().ifBlank { ocrSuggestion.title.trim() }
        if (query.length < 2) {
            nameSuggestions = emptyList()
            suggestionLoading = false
            return@LaunchedEffect
        }

        delay(250)
        suggestionLoading = true

        val localAndCached = appContainer.medicineInfoRepository.suggestNames(query, limit = 10)
        val onlineSuggestions = if (OpenFDAClient.isConnectedToInternet(context)) {
            runCatching {
                appContainer.remoteMedicineRepository.fetchOpenFdaSuggestions(query, limit = 10)
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }

        nameSuggestions = (localAndCached + onlineSuggestions)
            .distinctBy { it.name.lowercase() }
            .sortedByDescending { suggestion ->
                val normalized = suggestion.name.lowercase()
                when {
                    normalized == query.lowercase() -> 120
                    normalized.startsWith(query.lowercase()) -> 100
                    normalized.contains(query.lowercase()) -> 70
                    else -> 10
                }
            }
            .take(8)

        suggestionLoading = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add Medicine") }) }
    ) { paddingValues ->
        ocrValidationMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { ocrValidationMessage = null },
                title = { Text("Scan validation") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = {
                        ocrValidationMessage = null
                        cameraAction()
                    }) {
                        Text("Retry Scan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ocrValidationMessage = null }) {
                        Text("Enter Manually")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Scan or add quickly", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Use camera or gallery to scan a pack. Auto fill stays conservative so the wrong medicine name is less likely to be saved.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = cameraAction, modifier = Modifier.weight(1f)) {
                    Text("Camera scan")
                }
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Text("Gallery")
                }
            }

            if (cameraStatusMessage.isNotBlank()) {
                Text(text = cameraStatusMessage, style = MaterialTheme.typography.bodySmall)
            }

            selectedImageUri?.let { uri ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clickable { cameraAction() },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected medicine image",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleAutoFilled = false
                },
                label = { Text("Medicine name") },
                supportingText = { Text("Edit this before saving if OCR guessed wrong.") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (titleAutoFilled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                    focusedContainerColor = if (titleAutoFilled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (suggestionLoading) {
                Text("Finding medicines...", style = MaterialTheme.typography.bodySmall)
            }

            if (nameSuggestions.isNotEmpty()) {
                SuggestionListCard(
                    suggestions = nameSuggestions,
                    onSuggestionSelected = { suggestion ->
                        title = suggestion.name
                        titleAutoFilled = true
                        if (dosageText.isBlank() && suggestion.dosageHint.isNotBlank()) {
                            dosageText = suggestion.dosageHint
                            dosageAutoFilled = true
                        }
                    }
                )
            }

            OutlinedTextField(
                value = reminderTitle,
                onValueChange = { reminderTitle = it },
                label = { Text("Custom reminder title") },
                supportingText = { Text("This is the name the reminder will speak.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = dosageText,
                onValueChange = {
                    dosageText = it
                    dosageAutoFilled = false
                },
                label = { Text("Dosage / strength") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (dosageAutoFilled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                    focusedContainerColor = if (dosageAutoFilled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = {
                    notes = it
                    notesAutoFilled = false
                },
                label = { Text("Notes") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (notesAutoFilled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                    focusedContainerColor = if (notesAutoFilled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Text("Repeat days", style = MaterialTheme.typography.titleMedium)
            Text("Select the days you want reminders to repeat.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { (dayNumber, dayLabel) ->
                    FilterChip(
                        selected = selectedWeekdays.contains(dayNumber),
                        onClick = {
                            selectedWeekdays = if (selectedWeekdays.contains(dayNumber)) {
                                selectedWeekdays - dayNumber
                            } else {
                                selectedWeekdays + dayNumber
                            }
                        },
                        label = { Text(dayLabel) }
                    )
                }
            }

            DoseTableSection(
                doseSlots = doseSlots,
                onToggleSlot = { index ->
                    doseSlots[index] = doseSlots[index].copy(enabled = !doseSlots[index].enabled)
                },
                onPickSlotTime = { index ->
                    openTimePicker(context) { hour, minute ->
                        doseSlots[index] = doseSlots[index].copy(time = formatTime(hour, minute), enabled = true)
                    }
                }
            )

            Text("Food relation", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("none", "before_food", "after_food").forEach { option ->
                    FilterChip(
                        selected = foodRelation == option,
                        onClick = { foodRelation = option },
                        label = { Text(option.replace('_', ' ')) }
                    )
                }
            }

            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                label = { Text("End date optional") },
                supportingText = { Text("Use YYYY-MM-DD or leave empty for no end date") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Reminder alert title uses the medicine name, and you can still edit it before saving.",
                style = MaterialTheme.typography.bodySmall
            )

            if (ocrText.isNotBlank()) {
                Text(text = "OCR result", style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                    Text(text = ocrText, modifier = Modifier.padding(12.dp))
                }
            }

            Text("Trusted medicine info", style = MaterialTheme.typography.titleLarge)
            if (medicineInfo == null || title.isBlank()) {
                Text("Type or scan a medicine name to preview trusted uses, warnings, and storage guidance.")
            } else {
                MedicineInfoPreview(medicineInfo = medicineInfo!!)
            }

            Text("Medicine Information (from OpenFDA)", style = MaterialTheme.typography.titleLarge)
            when {
                openFdaLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Looking up label information...")
                    }
                }
                openFdaInfo != null -> {
                    OpenFdaInfoCard(openFdaInfo = openFdaInfo!!)
                }
                openFdaError != null -> {
                    Text(openFdaError!!, style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text("Type a medicine name to fetch label guidance from OpenFDA.")
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        val finalTitle = title.trim().ifBlank { ocrSuggestion.title.ifBlank { "Medicine" } }
                        val finalReminderTitle = reminderTitle.trim().ifBlank { finalTitle }
                        val finalDosage = dosageText.trim().ifBlank { ocrSuggestion.dosageText }
                        val resolvedEndDate = parseIsoDateToMillis(endDate)
                        val repeatDaysValue = selectedWeekdays.toList().sorted().joinToString(",")
                        val checkedSlots = doseSlots.filter { it.enabled }.ifEmpty {
                            listOf(DoseSlotState("Dose", reminderTime, enabled = true, time = reminderTime))
                        }

                        val medicine = MedicineEntity(
                            title = finalTitle,
                            reminderTitle = finalReminderTitle,
                            normalizedTitle = finalTitle.lowercase(),
                            dosageText = finalDosage,
                            notes = notes,
                            imageUri = selectedImageUri?.toString()
                        )
                        val medicineId = appContainer.medicineRepository.insertMedicine(medicine)

                        checkedSlots.forEach { slot ->
                            val schedule = ReminderScheduleEntity(
                                medicineId = medicineId,
                                timeOfDay = slot.time,
                                repeatType = if (selectedWeekdays.size == 7) "daily" else "custom",
                                repeatDays = repeatDaysValue,
                                startDate = System.currentTimeMillis(),
                                endDate = resolvedEndDate,
                                foodRelation = foodRelation,
                                isActive = true
                            )
                            val scheduleId = appContainer.reminderRepository.insertSchedule(schedule)

                            ReminderScheduler(context, appContainer.reminderRepository).scheduleReminder(
                                schedule = schedule.copy(id = scheduleId),
                                medicineName = finalTitle,
                                reminderTitle = finalReminderTitle,
                                dosage = if (checkedSlots.size > 1) "${slot.label} - ${finalDosage.ifBlank { "As prescribed" }}" else finalDosage.ifBlank { "As prescribed" }
                            )
                        }

                        navController.navigate("today")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = (title.isNotBlank() || ocrSuggestion.title.isNotBlank()) && selectedWeekdays.isNotEmpty()
            ) {
                Text("Save Medicine")
            }
        }
    }
}

@Composable
private fun DoseTableSection(
    doseSlots: List<DoseSlotState>,
    onToggleSlot: (Int) -> Unit,
    onPickSlotTime: (Int) -> Unit
) {
    Text("How many times a day?", style = MaterialTheme.typography.titleMedium)
    Text("Tick the dose rows you need. Each checked row gets its own alarm time.", style = MaterialTheme.typography.bodySmall)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Dose", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(text = "Tick", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                Text(text = "Time", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            }

            doseSlots.forEachIndexed { index, slot ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = slot.label, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = slot.enabled,
                        onCheckedChange = { onToggleSlot(index) }
                    )
                    TextButton(
                        onClick = { onPickSlotTime(index) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(slot.time)
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicineInfoPreview(medicineInfo: MedicineInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = medicineInfo.displayName, style = MaterialTheme.typography.titleLarge)
        SimpleBulletCard("Common uses", medicineInfo.commonUses)
        SimpleBulletCard("Common side effects", medicineInfo.commonSideEffects)
        SimpleBulletCard("Warnings", medicineInfo.warnings)
        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Storage guidance", style = MaterialTheme.typography.titleMedium)
                Text(medicineInfo.storageGuidance)
            }
        }
        Text("Source: ${medicineInfo.sourceName}", style = MaterialTheme.typography.bodySmall)
        Text(medicineInfo.disclaimer, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SimpleBulletCard(title: String, items: List<String>) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEach { item -> Text("• $item") }
        }
    }
}

@Composable
private fun SuggestionListCard(
    suggestions: List<MedicineNameSuggestion>,
    onSuggestionSelected: (MedicineNameSuggestion) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose medicine", style = MaterialTheme.typography.titleMedium)
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionSelected(suggestion) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(suggestion.name, style = MaterialTheme.typography.bodyLarge)
                        if (suggestion.dosageHint.isNotBlank()) {
                            Text(
                                suggestion.dosageHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (suggestion.source.isNotBlank()) {
                        Text(suggestion.source, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
private fun OpenFdaInfoCard(openFdaInfo: OpenFdaMedicineInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(openFdaInfo.displayName, style = MaterialTheme.typography.titleLarge)
            OpenFdaExpandableRow(label = "What it's for", icon = Icons.Filled.Info, value = openFdaInfo.purpose)
            OpenFdaExpandableRow(label = "Warnings", icon = Icons.Filled.Info, value = openFdaInfo.warnings)
            OpenFdaExpandableRow(label = "How to take", icon = Icons.Filled.Info, value = openFdaInfo.dosageAndAdministration)
            OpenFdaExpandableRow(label = "Side effects", icon = Icons.Filled.Info, value = openFdaInfo.adverseReactions)
            OpenFdaExpandableRow(label = "Storage", icon = Icons.Filled.Info, value = openFdaInfo.storageAndHandling)
            Text("Source: ${openFdaInfo.sourceName}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OpenFdaExpandableRow(
    label: String,
    icon: ImageVector,
    value: String
) {
    if (value.isBlank()) return

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.bodySmall)
        }
        if (expanded) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 32.dp, bottom = 4.dp)
            )
        }
        Divider()
    }
}

private fun openTimePicker(context: Context, onPicked: (Int, Int) -> Unit) {
    val calendar = Calendar.getInstance()
    TimePickerDialog(
        context,
        { _, hourOfDay, minute -> onPicked(hourOfDay, minute) },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    ).show()
}

private fun formatTime(hour: Int, minute: Int): String = String.format("%02d:%02d", hour, minute)

private fun saveBitmapToCache(context: Context, bitmap: Bitmap): File {
    val outputFile = File(context.cacheDir, "medicine_scan_${System.currentTimeMillis()}.png")
    FileOutputStream(outputFile).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    return outputFile
}

private fun parseIsoDateToMillis(value: String): Long? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null
    return runCatching {
        LocalDate.parse(normalized)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { exception -> continuation.resumeWithException(exception) }
    }