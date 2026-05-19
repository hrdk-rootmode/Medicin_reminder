package com.example.medicinreminder.data.dao

import androidx.room.*
import com.example.medicinreminder.data.entity.MedicineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE isArchived = 0 ORDER BY title ASC")
    fun getAllMedicines(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines ORDER BY isArchived ASC, title ASC")
    fun getAllMedicinesIncludingArchived(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE id = :medicineId")
    fun getMedicineById(medicineId: Long): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE id IN (:medicineIds)")
    fun getMedicinesByIds(medicineIds: List<Long>): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE isArchived = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentMedicines(limit: Int = 10): Flow<List<MedicineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMedicine(medicine: MedicineEntity): Long

    @Update
    fun updateMedicine(medicine: MedicineEntity): Int

    @Delete
    fun deleteMedicine(medicine: MedicineEntity): Int

    @Query("UPDATE medicines SET isArchived = 1 WHERE id = :medicineId")
    fun archiveMedicine(medicineId: Long): Int

    @Query("UPDATE medicines SET isArchived = 0 WHERE id = :medicineId")
    fun unarchiveMedicine(medicineId: Long): Int

    @Query("SELECT COUNT(*) FROM medicines WHERE isArchived = 0")
    fun getActiveMedicineCount(): Int
}
