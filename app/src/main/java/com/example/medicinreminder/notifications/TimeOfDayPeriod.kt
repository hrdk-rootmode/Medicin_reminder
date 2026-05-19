package com.example.medicinreminder.notifications

import java.util.Calendar

enum class TimeOfDayPeriod(
    val displayName: String,
    val channelId: String,
    val channelName: String,
    val defaultHour: Int,
    val defaultMinute: Int
) {
    MORNING("Morning", "channel_morning", "Morning Medicines", 8, 0),
    AFTERNOON("Afternoon", "channel_afternoon", "Afternoon Medicines", 13, 0),
    EVENING("Evening", "channel_evening", "Evening Medicines", 18, 0),
    NIGHT("Night", "channel_night", "Night Medicines", 21, 0);

    companion object {
        fun fromTime(timeOfDay: String): TimeOfDayPeriod {
            val hour = timeOfDay.split(":").firstOrNull()?.toIntOrNull() ?: return MORNING
            return when {
                hour in 5..11 -> MORNING
                hour in 12..16 -> AFTERNOON
                hour in 17..20 -> EVENING
                else -> NIGHT
            }
        }

        fun fromName(name: String?): TimeOfDayPeriod {
            return entries.firstOrNull { it.name == name } ?: MORNING
        }
    }

    fun nextTriggerTimeMillis(triggerHour: Int = defaultHour, triggerMinute: Int = defaultMinute): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, triggerHour)
            set(Calendar.MINUTE, triggerMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return calendar.timeInMillis
    }
}
