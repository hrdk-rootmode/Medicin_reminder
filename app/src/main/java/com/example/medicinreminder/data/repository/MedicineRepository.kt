package com.example.medicinreminder.data.repository

import com.example.medicinreminder.data.dao.MedicineDao
import com.example.medicinreminder.data.entity.MedicineEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MedicineRepository(private val medicineDao: MedicineDao) {

    fun getAllMedicines(): Flow<List<MedicineEntity>> {
        return medicineDao.getAllMedicines()
    }

    fun getAllMedicinesIncludingArchived(): Flow<List<MedicineEntity>> {
        return medicineDao.getAllMedicinesIncludingArchived()
    }

    suspend fun getMedicineById(medicineId: Long): MedicineEntity? {
        return withContext(Dispatchers.IO) {
            medicineDao.getMedicineById(medicineId)
        }
    }

    suspend fun getMedicinesByIds(medicineIds: List<Long>): List<MedicineEntity> {
        if (medicineIds.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            medicineDao.getMedicinesByIds(medicineIds)
        }
    }

    fun getRecentMedicines(limit: Int = 10): Flow<List<MedicineEntity>> {
        return medicineDao.getRecentMedicines(limit)
    }

    suspend fun insertMedicine(medicine: MedicineEntity): Long {
        return withContext(Dispatchers.IO) { medicineDao.insertMedicine(medicine) }
    }

    suspend fun updateMedicine(medicine: MedicineEntity) {
        withContext(Dispatchers.IO) {
            medicineDao.updateMedicine(medicine.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteMedicine(medicine: MedicineEntity) {
        withContext(Dispatchers.IO) {
            medicineDao.deleteMedicine(medicine)
        }
    }

    suspend fun archiveMedicine(medicineId: Long) {
        withContext(Dispatchers.IO) {
            medicineDao.archiveMedicine(medicineId)
        }
    }

    suspend fun unarchiveMedicine(medicineId: Long) {
        withContext(Dispatchers.IO) {
            medicineDao.unarchiveMedicine(medicineId)
        }
    }

    suspend fun toggleArchiveStatus(medicineId: Long) {
        withContext(Dispatchers.IO) {
            val medicine = medicineDao.getMedicineById(medicineId)
            if (medicine != null) {
                if (medicine.isArchived) {
                    medicineDao.unarchiveMedicine(medicineId)
                } else {
                    medicineDao.archiveMedicine(medicineId)
                }
            }
        }
    }

    suspend fun getActiveMedicineCount(): Int {
        return withContext(Dispatchers.IO) { medicineDao.getActiveMedicineCount() }
    }
}
