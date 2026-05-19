package com.example.medicinreminder.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ReminderScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineId: Long,
    val scheduleId: Long,
    val scheduledAt: Long,
    val actionTaken: String, // "TAKEN", "SKIPPED", "MISSED"
    val actionTime: Long = System.currentTimeMillis()
)
