package com.example.medicinreminder.data.dao

import androidx.room.*
import com.example.medicinreminder.data.entity.UserEntitlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserEntitlementDao {
    @Query("SELECT * FROM user_entitlement WHERE id = 1")
    fun getEntitlement(): Flow<UserEntitlementEntity?>

    @Query("SELECT * FROM user_entitlement WHERE id = 1")
    fun getEntitlementSync(): UserEntitlementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEntitlement(entitlement: UserEntitlementEntity): Long

    @Update
    fun updateEntitlement(entitlement: UserEntitlementEntity): Int

    @Query("UPDATE user_entitlement SET isPremium = :isPremium WHERE id = 1")
    fun updatePremiumStatus(isPremium: Boolean): Int

    @Query("UPDATE user_entitlement SET purchaseToken = :token WHERE id = 1")
    fun updatePurchaseToken(token: String?): Int
}
