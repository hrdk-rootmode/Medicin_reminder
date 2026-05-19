package com.example.medicinreminder.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReminderScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineId: Long,
    val timeOfDay: String, // Format: "HH:mm"
    val repeatType: String, // "daily", "weekdays", "custom", "interval"
    val repeatDays: String, // Comma-separated days: "1,2,3,4,5" for weekdays
    val startDate: Long,
    val endDate: Long?, // null for no end date
    val foodRelation: String, // "before_food", "after_food", "none"
    val isActive: Boolean = true,
    val isPausedByPlanLimit: Boolean = false,
    @ColumnInfo(name = "alert_repeat_count")
    val alertRepeatCount: Int = 2
)
