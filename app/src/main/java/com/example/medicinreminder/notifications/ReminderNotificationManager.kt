package com.example.medicinreminder.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.medicinreminder.MainActivity
import com.example.medicinreminder.R

class ReminderNotificationManager(private val context: Context) {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val MARK_ALL_TAKEN_ACTION = "com.example.medicinreminder.MARK_ALL_TAKEN_ACTION"
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            TimeOfDayPeriod.entries.forEach { period ->
                val channel = NotificationChannel(
                    period.channelId,
                    period.channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for ${period.displayName.lowercase()} medicine reminders"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    fun showReminderNotification(medicineName: String, dosage: String, scheduleId: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, TimeOfDayPeriod.MORNING.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Medicine Reminder")
            .setContentText("Time to take $medicineName - $dosage")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + scheduleId.toInt(), notification)
    }

    fun showGroupedReminderNotification(
        period: TimeOfDayPeriod,
        medicines: List<GroupedReminderItem>,
        medicineIdsCsv: String
    ) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderScheduler.EXTRA_PERIOD, period.name)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            7000 + period.ordinal,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val markAllIntent = Intent(context, MarkAllTakenReceiver::class.java).apply {
            action = MARK_ALL_TAKEN_ACTION
            putExtra(ReminderScheduler.EXTRA_PERIOD, period.name)
            putExtra(ReminderScheduler.EXTRA_MEDICINE_IDS, medicineIdsCsv)
        }
        val markAllPendingIntent = PendingIntent.getBroadcast(
            context,
            7100 + period.ordinal,
            markAllIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bigTextBody = medicines.joinToString("\n") {
            "• ${it.name} ${it.dosage.ifBlank { "As prescribed" }} — ${it.foodRelation.ifBlank { "No food preference" }}"
        }

        val notification = NotificationCompat.Builder(context, period.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${period.displayName} Medicines — Time to take your doses")
            .setContentText("${medicines.size} medicines scheduled")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Mark All Taken", markAllPendingIntent)
            .addAction(0, "Open App", openAppPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + (period.ordinal * 100), notification)
    }
}

data class GroupedReminderItem(
    val medicineId: Long,
    val name: String,
    val dosage: String,
    val foodRelation: String
)
