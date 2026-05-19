package com.example.medicinreminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicinreminder.data.entity.DoseLogEntity
import com.example.medicinreminder.data.repository.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarkAllTakenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val period = TimeOfDayPeriod.fromName(intent.getStringExtra(ReminderScheduler.EXTRA_PERIOD))
        val medicineIds = intent.getStringExtra(ReminderScheduler.EXTRA_MEDICINE_IDS)
            .orEmpty()
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }

        if (medicineIds.isEmpty()) return

        val appContainer = AppContainer(context.applicationContext)
        val scheduler = ReminderScheduler(context.applicationContext, appContainer.reminderRepository)

        CoroutineScope(Dispatchers.IO).launch {
            val schedules = appContainer.reminderRepository.getActiveSchedulesForMedicineIds(medicineIds)
                .filter { TimeOfDayPeriod.fromTime(it.timeOfDay) == period }

            val now = System.currentTimeMillis()
            schedules.forEach { schedule ->
                appContainer.reminderRepository.insertLog(
                    DoseLogEntity(
                        medicineId = schedule.medicineId,
                        scheduleId = schedule.id,
                        scheduledAt = now,
                        actionTaken = "TAKEN",
                        actionTime = now
                    )
                )
            }

            scheduler.cancelPeriodReminder(period)
            scheduler.scheduleGroupedReminderForPeriod(period)
        }
    }
}
