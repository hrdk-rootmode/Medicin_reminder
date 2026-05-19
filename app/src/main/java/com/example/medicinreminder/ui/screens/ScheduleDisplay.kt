package com.example.medicinreminder.ui.screens

import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import java.util.Calendar
import java.util.Locale

fun ReminderScheduleEntity.toReadableSummary(): String {
    val repeatLabel = when (repeatType.lowercase(Locale.getDefault())) {
        "daily" -> "Every day"
        "weekdays" -> "Weekdays"
        "custom" -> "Custom days"
        "interval" -> "Interval"
        else -> repeatType.replace('_', ' ').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
    return "$repeatLabel at $timeOfDay"
}

fun String.toReadableDays(): String {
    if (isBlank()) return "Not set"
    val dayMap = mapOf(
        "1" to "Mon",
        "2" to "Tue",
        "3" to "Wed",
        "4" to "Thu",
        "5" to "Fri",
        "6" to "Sat",
        "7" to "Sun"
    )
    return split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ") { dayMap[it] ?: it }
}

fun String.toReadableFoodRelation(): String {
    return when (lowercase(Locale.getDefault())) {
        "before_food" -> "Before food"
        "after_food" -> "After food"
        else -> "No preference"
    }
}

fun String.toDosePeriodLabel(index: Int = -1): String {
    val hour = split(":").firstOrNull()?.toIntOrNull()
    val period = when {
        index == 0 -> "Morning"
        index == 1 -> "Afternoon"
        index == 2 -> "Evening"
        index == 3 -> "Night"
        hour == null -> return "Dose"
        hour in 5..11 -> "Morning"
        hour in 12..16 -> "Afternoon"
        hour in 17..20 -> "Evening"
        else -> "Night"
    }
    val ampm = if (hour != null && hour < 12) "AM" else "PM"
    return "$period ($ampm)"
}

fun String.to12HourFormat(): String {
    try {
        val (hourStr, minuteStr) = split(":").take(2).let { it[0] to (it.getOrNull(1) ?: "00") }
        val hour = hourStr.toIntOrNull() ?: return this
        val ampm = if (hour < 12) "AM" else "PM"
        val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return "$hour12:${minuteStr.padStart(2, '0')} $ampm"
    } catch (e: Exception) {
        return this
    }
}

fun getTodayTimeRange(): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val todayEnd = todayStart + (24 * 60 * 60 * 1000)
    return todayStart to todayEnd
}

fun String.getDosePeriodForTime(): String {
    val hour = split(":").firstOrNull()?.toIntOrNull() ?: return "Dose"
    return when {
        hour in 5..11 -> "Morning"
        hour in 12..16 -> "Afternoon"
        hour in 17..20 -> "Evening"
        else -> "Night"
    }
}