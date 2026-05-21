package com.example.medicinreminder.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderScheduler(
    private val context: Context,
    private val reminderRepository: ReminderRepository
) {
    
    companion object {
        private const val TAG = "ReminderScheduler"
        private const val GROUPED_REMINDER_ACTION = "com.example.medicinreminder.GROUPED_REMINDER_ACTION"
        const val EXTRA_PERIOD = "period"
        const val EXTRA_MEDICINE_IDS = "medicine_ids"
        private const val EXTRA_IS_SECOND_RING = "is_second_ring"
        const val EXTRA_REPEAT_INDEX = "repeat_index"

        fun getPrimaryRequestCode(period: TimeOfDayPeriod): Int = 5000 + period.ordinal
        fun getSecondRingRequestCode(period: TimeOfDayPeriod): Int = 5100 + period.ordinal
        fun getRepeatRequestCode(period: TimeOfDayPeriod, repeatIndex: Int): Int = 5200 + (period.ordinal * 10) + repeatIndex
    }
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = ReminderNotificationManager(context)
    
    fun scheduleAllReminders() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                TimeOfDayPeriod.entries.forEach { period ->
                    scheduleGroupedReminderForPeriod(period)
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to schedule all reminders", error)
            }
        }
    }
    
    fun scheduleReminder(
        schedule: ReminderScheduleEntity,
        medicineName: String,
        reminderTitle: String,
        dosage: String
    ) {
        val period = TimeOfDayPeriod.fromTime(schedule.timeOfDay)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                scheduleGroupedReminderForPeriod(period)
            }.onFailure { error ->
                Log.e(TAG, "Failed to schedule reminder for $medicineName", error)
            }
        }
    }
    
    fun cancelReminder(scheduleId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                scheduleAllReminders()
            }.onFailure { error ->
                Log.e(TAG, "Failed to cancel reminder $scheduleId", error)
            }
        }
    }
    
    fun cancelAllReminders() {
        TimeOfDayPeriod.entries.forEach { period ->
            cancelPeriodReminder(period)
        }
    }

    suspend fun scheduleGroupedReminderForPeriod(period: TimeOfDayPeriod) {
        val activeSchedules = withContext(Dispatchers.IO) {
            reminderRepository.getActiveSchedulesForActiveMedicinesOnce()
        }
        val periodSchedules = activeSchedules.filter { TimeOfDayPeriod.fromTime(it.timeOfDay) == period }

        if (periodSchedules.isEmpty()) {
            cancelPeriodReminder(period)
            return
        }

        val medicineIds = periodSchedules.map { it.medicineId }.distinct()
        val triggerMinutes = periodSchedules.mapNotNull {
            val hour = it.timeOfDay.split(":").firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
            val minute = it.timeOfDay.split(":").getOrNull(1)?.toIntOrNull() ?: 0
            (hour * 60) + minute
        }
        val earliest = triggerMinutes.minOrNull()
        val triggerHour = earliest?.div(60) ?: period.defaultHour
        val triggerMinute = earliest?.rem(60) ?: period.defaultMinute
        val repeatCount = periodSchedules.maxOfOrNull { it.alertRepeatCount.coerceIn(1, 5) } ?: 2

        val commonIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = GROUPED_REMINDER_ACTION
            putExtra(EXTRA_PERIOD, period.name)
            putExtra(EXTRA_MEDICINE_IDS, medicineIds.joinToString(","))
        }

        val primaryPendingIntent = PendingIntent.getBroadcast(
            context,
            getPrimaryRequestCode(period),
            commonIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        scheduleAlarm(
            triggerAtMillis = period.nextTriggerTimeMillis(triggerHour, triggerMinute),
            pendingIntent = primaryPendingIntent
        )

        val secondRingIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = GROUPED_REMINDER_ACTION
            putExtra(EXTRA_PERIOD, period.name)
            putExtra(EXTRA_MEDICINE_IDS, medicineIds.joinToString(","))
            putExtra(EXTRA_IS_SECOND_RING, true)
        }
        val secondRingPendingIntent = PendingIntent.getBroadcast(
            context,
            getSecondRingRequestCode(period),
            secondRingIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        scheduleAlarm(
            triggerAtMillis = period.nextTriggerTimeMillis(triggerHour, triggerMinute) + 60_000L,
            pendingIntent = secondRingPendingIntent
        )

        for (repeatIndex in 1..repeatCount) {
            val repeatIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = GROUPED_REMINDER_ACTION
                putExtra(EXTRA_PERIOD, period.name)
                putExtra(EXTRA_MEDICINE_IDS, medicineIds.joinToString(","))
                putExtra(EXTRA_REPEAT_INDEX, repeatIndex)
            }
            val repeatPendingIntent = PendingIntent.getBroadcast(
                context,
                getRepeatRequestCode(period, repeatIndex),
                repeatIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            scheduleAlarm(
                triggerAtMillis = period.nextTriggerTimeMillis(triggerHour, triggerMinute) + (repeatIndex * 60_000L),
                pendingIntent = repeatPendingIntent
            )
        }
    }

    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }.onFailure { exactAlarmError ->
            Log.w(TAG, "Exact alarm unavailable, using inexact scheduling instead", exactAlarmError)
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }.onFailure { fallbackError ->
                Log.e(TAG, "Failed to schedule reminder alarm", fallbackError)
            }
        }
    }

    fun cancelPeriodReminder(period: TimeOfDayPeriod) {
        val baseIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = GROUPED_REMINDER_ACTION
        }
        val primaryPendingIntent = PendingIntent.getBroadcast(
            context,
            getPrimaryRequestCode(period),
            baseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val secondPendingIntent = PendingIntent.getBroadcast(
            context,
            getSecondRingRequestCode(period),
            baseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(primaryPendingIntent)
        alarmManager.cancel(secondPendingIntent)

        for (repeatIndex in 1..5) {
            val repeatIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = GROUPED_REMINDER_ACTION
                putExtra(EXTRA_PERIOD, period.name)
                putExtra(EXTRA_MEDICINE_IDS, "")
                putExtra(EXTRA_REPEAT_INDEX, repeatIndex)
            }
            val repeatPendingIntent = PendingIntent.getBroadcast(
                context,
                getRepeatRequestCode(period, repeatIndex),
                repeatIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(repeatPendingIntent)
        }
    }
}
