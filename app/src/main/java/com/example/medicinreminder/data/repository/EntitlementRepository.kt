package com.example.medicinreminder.data.repository

import com.example.medicinreminder.data.dao.UserEntitlementDao
import com.example.medicinreminder.data.entity.UserEntitlementEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class EntitlementRepository(private val entitlementDao: UserEntitlementDao) {

    fun getEntitlement(): Flow<UserEntitlementEntity?> {
        return entitlementDao.getEntitlement()
    }

    suspend fun getEntitlementSync(): UserEntitlementEntity? {
        return withContext(Dispatchers.IO) { entitlementDao.getEntitlementSync() }
    }

    suspend fun initializeEntitlement() {
        val existing = getEntitlementSync()
        if (existing == null) {
            withContext(Dispatchers.IO) {
                entitlementDao.insertEntitlement(UserEntitlementEntity())
            }
        }
    }

    suspend fun updatePremiumStatus(isPremium: Boolean) {
        val current = getEntitlementSync() ?: return
        withContext(Dispatchers.IO) {
            entitlementDao.updateEntitlement(
                current.copy(
                    isPremium = isPremium,
                    adsEnabled = !isPremium,
                    activeReminderLimit = if (isPremium) Int.MAX_VALUE else 5
                )
            )
        }
    }

    suspend fun updatePurchaseToken(token: String?) {
        withContext(Dispatchers.IO) {
            entitlementDao.updatePurchaseToken(token)
        }
    }

    suspend fun isTrialActive(): Boolean {
        val entitlement = getEntitlementSync() ?: return false
        val currentTime = System.currentTimeMillis()
        return currentTime >= entitlement.trialStart && currentTime <= entitlement.trialEnd
    }

    suspend fun isLifetimeUnlocked(): Boolean {
        val entitlement = getEntitlementSync() ?: return false
        return entitlement.isPremium
    }

    suspend fun canActivateMoreReminders(currentActiveCount: Int): Boolean {
        val entitlement = getEntitlementSync() ?: return false
        if (entitlement.isPremium) return true
        if (isTrialActive()) return true
        return currentActiveCount < entitlement.activeReminderLimit
    }

    suspend fun getMaxActiveReminderCount(): Int {
        val entitlement = getEntitlementSync() ?: return 5
        return if (entitlement.isPremium || isTrialActive()) Int.MAX_VALUE else entitlement.activeReminderLimit
    }

    suspend fun areAdsEnabled(): Boolean {
        val entitlement = getEntitlementSync() ?: return true
        return entitlement.adsEnabled
    }

    suspend fun enforcePlanLimit(currentActiveCount: Int): Int {
        val entitlement = getEntitlementSync() ?: return currentActiveCount
        if (entitlement.isPremium || isTrialActive()) return currentActiveCount
        
        val limit = entitlement.activeReminderLimit
        return if (currentActiveCount > limit) limit else currentActiveCount
    }

    suspend fun getTrialDaysRemaining(): Long {
        val entitlement = getEntitlementSync() ?: return 0
        val currentTime = System.currentTimeMillis()
        if (currentTime > entitlement.trialEnd) return 0
        
        val remainingMs = entitlement.trialEnd - currentTime
        return remainingMs / (24 * 60 * 60 * 1000)
    }
}
