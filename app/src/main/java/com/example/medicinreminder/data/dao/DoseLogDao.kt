package com.example.medicinreminder.data.dao

import androidx.room.*
import com.example.medicinreminder.data.entity.DoseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE medicineId = :medicineId ORDER BY scheduledAt DESC")
    fun getLogsForMedicine(medicineId: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE scheduledAt >= :startTime AND scheduledAt <= :endTime ORDER BY scheduledAt ASC")
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE scheduleId = :scheduleId ORDER BY scheduledAt DESC")
    fun getLogsForSchedule(scheduleId: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE scheduledAt = :scheduledAt AND scheduleId = :scheduleId")
    fun getLogForScheduledTime(scheduledAt: Long, scheduleId: Long): DoseLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLog(log: DoseLogEntity): Long

    @Update
    fun updateLog(log: DoseLogEntity): Int

    @Query("DELETE FROM dose_logs WHERE medicineId = :medicineId")
    fun deleteLogsForMedicine(medicineId: Long): Int
}
