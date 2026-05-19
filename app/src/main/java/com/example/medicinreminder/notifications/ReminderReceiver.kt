package com.example.medicinreminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.medicinreminder.data.repository.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val period = TimeOfDayPeriod.fromName(intent.getStringExtra(ReminderScheduler.EXTRA_PERIOD))
        val medicineIds = intent.getStringExtra(ReminderScheduler.EXTRA_MEDICINE_IDS)
            .orEmpty()
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }

        if (medicineIds.isEmpty()) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val appContainer = AppContainer(context.applicationContext)
            val medicines = appContainer.medicineRepository.getMedicinesByIds(medicineIds)
            val schedules = appContainer.reminderRepository.getActiveSchedulesForMedicineIds(medicineIds)
                .filter { TimeOfDayPeriod.fromTime(it.timeOfDay) == period }

            val groupedItems = medicines.map { medicine ->
                val schedule = schedules.firstOrNull { it.medicineId == medicine.id }
                GroupedReminderItem(
                    medicineId = medicine.id,
                    name = medicine.reminderTitle.ifBlank { medicine.title },
                    dosage = medicine.dosageText,
                    foodRelation = schedule?.foodRelation?.replace('_', ' ') ?: "none"
                )
            }

            if (groupedItems.isNotEmpty()) {
                val notificationManager = ReminderNotificationManager(context)
                notificationManager.showGroupedReminderNotification(
                    period = period,
                    medicines = groupedItems,
                    medicineIdsCsv = medicineIds.joinToString(",")
                )
                val names = groupedItems.joinToString(", ") { it.name }
                val speech = "Good ${period.displayName.lowercase()}. Time to take your ${period.displayName} medicines: $names."
                speakReminder(context.applicationContext, speech, pendingResult)

                val scheduler = ReminderScheduler(context.applicationContext, appContainer.reminderRepository)
                scheduler.scheduleGroupedReminderForPeriod(period)
            } else {
                pendingResult.finish()
            }
        }
    }

    private fun speakReminder(context: Context, speechText: String, pendingResult: PendingResult) {
        var textToSpeech: TextToSpeech? = null
        textToSpeech = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                pendingResult.finish()
                return@TextToSpeech
            }

            val tts = textToSpeech ?: run {
                pendingResult.finish()
                return@TextToSpeech
            }

            tts.language = Locale.getDefault()
            tts.setSpeechRate(0.85f)
            val utteranceId = "medicine_reminder_${System.currentTimeMillis()}"

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    tts.shutdown()
                    pendingResult.finish()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    tts.shutdown()
                    pendingResult.finish()
                }
            })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null)
                pendingResult.finish()
            }
        }
    }
}
