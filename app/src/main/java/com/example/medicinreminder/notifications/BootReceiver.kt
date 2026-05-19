package com.example.medicinreminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.medicinreminder.data.worker.BootRescheduleWorker

class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(tag, "Device boot completed, scheduling reminder restoration")
            try {
                val workRequest = OneTimeWorkRequestBuilder<BootRescheduleWorker>().build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    "boot_reschedule",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to schedule boot work: ${e.message}", e)
            }
        }
    }
}
