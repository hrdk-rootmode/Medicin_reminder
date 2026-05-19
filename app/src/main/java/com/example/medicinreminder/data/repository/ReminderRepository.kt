package com.example.medicinreminder.data.repository

import com.example.medicinreminder.data.dao.ReminderScheduleDao
import com.example.medicinreminder.data.dao.DoseLogDao
import com.example.medicinreminder.data.entity.ReminderScheduleEntity
import com.example.medicinreminder.data.entity.DoseLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ReminderRepository(
    private val scheduleDao: ReminderScheduleDao,
    private val doseLogDao: DoseLogDao
) {

    fun getSchedulesForMedicine(medicineId: Long): Flow<List<ReminderScheduleEntity>> {
        return scheduleDao.getSchedulesForMedicine(medicineId)
    }

    fun getAllActiveSchedules(): Flow<List<ReminderScheduleEntity>> {
        return scheduleDao.getAllActiveSchedules()
    }

    fun getActiveSchedulesForActiveMedicines(): Flow<List<ReminderScheduleEntity>> {
        return scheduleDao.getActiveSchedulesForActiveMedicines()
    }

    suspend fun getActiveSchedulesForActiveMedicinesOnce(): List<ReminderScheduleEntity> {
        return withContext(Dispatchers.IO) { scheduleDao.getActiveSchedulesForActiveMedicinesOnce() }
    }

    suspend fun getActiveSchedulesForMedicineIds(medicineIds: List<Long>): List<ReminderScheduleEntity> {
        if (medicineIds.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) { scheduleDao.getActiveSchedulesForMedicineIds(medicineIds) }
    }

    suspend fun getActiveScheduleCount(): Int {
        return withContext(Dispatchers.IO) { scheduleDao.getActiveScheduleCount() }
    }

    suspend fun insertSchedule(schedule: ReminderScheduleEntity): Long {
        return withContext(Dispatchers.IO) { scheduleDao.insertSchedule(schedule) }
    }

    suspend fun updateSchedule(schedule: ReminderScheduleEntity) {
        withContext(Dispatchers.IO) {
            scheduleDao.updateSchedule(schedule)
        }
    }

    suspend fun deleteSchedule(schedule: ReminderScheduleEntity) {
        withContext(Dispatchers.IO) {
            scheduleDao.deleteSchedule(schedule)
        }
    }

    suspend fun updatePauseStatus(scheduleId: Long, isPaused: Boolean) {
        withContext(Dispatchers.IO) {
            scheduleDao.updatePauseStatus(scheduleId, isPaused)
        }
    }

    suspend fun unpauseAllSchedules() {
        withContext(Dispatchers.IO) {
            scheduleDao.unpauseAllSchedules()
        }
    }

    // Dose logging
    fun getLogsForMedicine(medicineId: Long): Flow<List<DoseLogEntity>> {
        return doseLogDao.getLogsForMedicine(medicineId)
    }

    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<DoseLogEntity>> {
        return doseLogDao.getLogsInRange(startTime, endTime)
    }

    fun getLogsForSchedule(scheduleId: Long): Flow<List<DoseLogEntity>> {
        return doseLogDao.getLogsForSchedule(scheduleId)
    }

    suspend fun getLogForScheduledTime(scheduledAt: Long, scheduleId: Long): DoseLogEntity? {
        return withContext(Dispatchers.IO) { doseLogDao.getLogForScheduledTime(scheduledAt, scheduleId) }
    }

    suspend fun insertLog(log: DoseLogEntity): Long {
        return withContext(Dispatchers.IO) { doseLogDao.insertLog(log) }
    }

    suspend fun updateLog(log: DoseLogEntity) {
        withContext(Dispatchers.IO) {
            doseLogDao.updateLog(log)
        }
    }

    suspend fun deleteLogsForMedicine(medicineId: Long) {
        withContext(Dispatchers.IO) {
            doseLogDao.deleteLogsForMedicine(medicineId)
        }
    }
}
