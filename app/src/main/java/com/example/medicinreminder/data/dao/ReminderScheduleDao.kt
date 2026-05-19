package com.example.medicinreminder.data.dao

import androidx.room.*
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderScheduleDao {
    @Query("SELECT * FROM reminder_schedules WHERE medicineId = :medicineId")
    fun getSchedulesForMedicine(medicineId: Long): Flow<List<ReminderScheduleEntity>>

    @Query("SELECT * FROM reminder_schedules WHERE isActive = 1 AND isPausedByPlanLimit = 0")
    fun getAllActiveSchedules(): Flow<List<ReminderScheduleEntity>>

    @Query("SELECT * FROM reminder_schedules WHERE isActive = 1 AND isPausedByPlanLimit = 0 AND medicineId IN (SELECT id FROM medicines WHERE isArchived = 0)")
    fun getActiveSchedulesForActiveMedicines(): Flow<List<ReminderScheduleEntity>>

    @Query("SELECT * FROM reminder_schedules WHERE isActive = 1 AND isPausedByPlanLimit = 0 AND medicineId IN (SELECT id FROM medicines WHERE isArchived = 0)")
    fun getActiveSchedulesForActiveMedicinesOnce(): List<ReminderScheduleEntity>

    @Query("SELECT * FROM reminder_schedules WHERE isActive = 1 AND isPausedByPlanLimit = 0 AND medicineId IN (:medicineIds)")
    fun getActiveSchedulesForMedicineIds(medicineIds: List<Long>): List<ReminderScheduleEntity>

    @Query("SELECT COUNT(*) FROM reminder_schedules WHERE isActive = 1 AND isPausedByPlanLimit = 0")
    fun getActiveScheduleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSchedule(schedule: ReminderScheduleEntity): Long

    @Update
    fun updateSchedule(schedule: ReminderScheduleEntity): Int

    @Delete
    fun deleteSchedule(schedule: ReminderScheduleEntity): Int

    @Query("UPDATE reminder_schedules SET isPausedByPlanLimit = :isPaused WHERE id = :scheduleId")
    fun updatePauseStatus(scheduleId: Long, isPaused: Boolean): Int

    @Query("UPDATE reminder_schedules SET isPausedByPlanLimit = 0")
    fun unpauseAllSchedules(): Int
}
