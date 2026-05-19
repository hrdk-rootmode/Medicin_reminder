package com.example.medicinreminder.ui.model

import com.example.medicinreminder.data.entity.MedicineEntity
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.entity.DoseLogEntity

data class MedicineReminder(
    val medicine: MedicineEntity,
    val schedule: ReminderScheduleEntity,
    val doseLog: DoseLogEntity?,
    val isOverdue: Boolean = false
)
