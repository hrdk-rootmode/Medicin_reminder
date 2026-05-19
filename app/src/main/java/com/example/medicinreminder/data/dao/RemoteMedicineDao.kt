package com.example.medicinreminder.data.dao

import androidx.room.*
import com.example.medicinreminder.data.entity.RemoteMedicineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteMedicineDao {
    @Query("SELECT * FROM remote_medicines ORDER BY displayName ASC")
    fun getAllRemoteMedicines(): Flow<List<RemoteMedicineEntity>>

    @Query("SELECT * FROM remote_medicines WHERE displayName LIKE '%' || :query || '%' OR brandNames LIKE '%' || :query || '%'")
    fun searchMedicines(query: String): Flow<List<RemoteMedicineEntity>>

    @Query("SELECT * FROM remote_medicines WHERE id = :medicineId")
    fun getMedicineById(medicineId: String): RemoteMedicineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMedicine(medicine: RemoteMedicineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMedicines(medicines: List<RemoteMedicineEntity>): List<Long>

    @Update
    fun updateMedicine(medicine: RemoteMedicineEntity): Int

    @Delete
    fun deleteMedicine(medicine: RemoteMedicineEntity): Int

    @Query("DELETE FROM remote_medicines")
    fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM remote_medicines")
    fun getMedicineCount(): Int
}
