package com.example.medicinreminder.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_entitlement")
data class UserEntitlementEntity(
    @PrimaryKey
    val id: Int = 1, // Singleton, always 1
    val trialStart: Long = System.currentTimeMillis(),
    val trialEnd: Long = System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000), // 3 months
    @ColumnInfo(name = "isPremium")
    val l1f3t1m3_flag: Boolean = false,
    val purchaseToken: String? = null,
    val activeReminderLimit: Int = 5,
    val adsEnabled: Boolean = true
)
