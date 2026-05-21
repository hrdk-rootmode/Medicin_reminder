package com.example.medicinreminder.data.worker

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.medicinreminder.data.repository.AppContainer
import com.example.medicinreminder.notifications.ReminderScheduler
import com.example.medicinreminder.notifications.TimeOfDayPeriod

class BootRescheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val tag = "BootRescheduleWorker"

    override suspend fun doWork(): Result {
        return try {
            Log.d(tag, "Boot reschedule worker running")
            val appContainer = AppContainer(applicationContext)
            val scheduler = ReminderScheduler(applicationContext, appContainer.reminderRepository)
            scheduler.scheduleAllReminders()
            Log.d(tag, "Boot reminders rescheduled successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Failed to reschedule reminders after boot: ${e.message}", e)
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, TimeOfDayPeriod.MORNING.channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(com.example.medicinreminder.R.string.restoring_reminders))
            .setContentText(applicationContext.getString(com.example.medicinreminder.R.string.restoring_reminders_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(9999, notification)
    }
}
