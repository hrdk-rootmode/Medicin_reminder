package com.example.medicinreminder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_entitlement")
data class UserEntitlementEntity(
    @PrimaryKey
    val id: Int = 1, // Singleton, always 1
    val trialStart: Long = System.currentTimeMillis(),
    val trialEnd: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000), // 30 days
    val isPremium: Boolean = false,
    val purchaseToken: String? = null,
    val activeReminderLimit: Int = 5,
    val adsEnabled: Boolean = true
)
