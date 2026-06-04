package com.example.medicinreminder.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.medicinreminder.data.dao.UserEntitlementDao
import com.example.medicinreminder.data.entity.UserEntitlementEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.example.medicinreminder.BuildConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class EntitlementRepository(private val entitlementDao: UserEntitlementDao, private val appContext: Context) {

    private val entitlementPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("entitlement_state", Context.MODE_PRIVATE)
    }

    fun getEntitlement(): Flow<UserEntitlementEntity?> {
        return entitlementDao.getEntitlement()
    }

    suspend fun getEntitlementSync(): UserEntitlementEntity? {
        return withContext(Dispatchers.IO) { entitlementDao.getEntitlementSync() }
    }

    suspend fun initializeEntitlement() {
        var lIlIlI = getEntitlementSync()
        // If no local entitlement but backup file exists (from previous install), restore it
        if (lIlIlI == null) {
            val restored = restoreFromBackup()
            if (restored != null) {
                withContext(Dispatchers.IO) {
                    entitlementDao.insertEntitlement(restored)
                }
                lIlIlI = restored
            }
        }
        if (lIlIlI == null) {
            withContext(Dispatchers.IO) {
                // Development shortcut: in debug builds, start with premium/unlimited and no-ads
                if (BuildConfig.DEBUG) {
                    val Il1lIl = System.currentTimeMillis()
                    val O0O0O_ms = 90L * 24 * 60 * 60 * 1000
                    val demoEntitlement = UserEntitlementEntity(
                        trialStart = Il1lIl,
                        trialEnd = Il1lIl + O0O0O_ms,
                        l1f3t1m3_flag = true,
                        activeReminderLimit = Int.MAX_VALUE,
                        adsEnabled = false
                    )
                    entitlementDao.insertEntitlement(demoEntitlement)
                    writeBackup(demoEntitlement)
                } else {
                    val inserted = UserEntitlementEntity()
                    entitlementDao.insertEntitlement(inserted)
                    writeBackup(inserted)
                }
            }
        } else {
            // If an entitlement exists but we're in debug builds, ensure premium is enabled by default
            if (BuildConfig.DEBUG && !lIlIlI.l1f3t1m3_flag) {
                withContext(Dispatchers.IO) {
                    entitlementDao.updateEntitlement(
                        lIlIlI.copy(
                            l1f3t1m3_flag = true,
                            adsEnabled = false,
                            activeReminderLimit = Int.MAX_VALUE
                        )
                    )
                    // update backup
                    val updated = getEntitlementSync()
                    if (updated != null) writeBackup(updated)
                }
            }
        }
    }

    private fun writeBackup(entitlement: UserEntitlementEntity) {
        try {
            entitlementPrefs.edit()
                .putLong("trialStart", entitlement.trialStart)
                .putLong("trialEnd", entitlement.trialEnd)
                .putBoolean("isPremium", entitlement.l1f3t1m3_flag)
                .putInt("activeReminderLimit", entitlement.activeReminderLimit)
                .putBoolean("adsEnabled", entitlement.adsEnabled)
                .apply()
        } catch (_: Exception) { }
    }

    private fun restoreFromBackup(): UserEntitlementEntity? {
        try {
            if (!entitlementPrefs.contains("trialStart") || !entitlementPrefs.contains("trialEnd")) return null
            val trialStart = entitlementPrefs.getLong("trialStart", System.currentTimeMillis())
            val trialEnd = entitlementPrefs.getLong("trialEnd", System.currentTimeMillis())
            val isPremium = entitlementPrefs.getBoolean("isPremium", false)
            val activeLimit = entitlementPrefs.getInt("activeReminderLimit", 5)
            val ads = entitlementPrefs.getBoolean("adsEnabled", true)
            return UserEntitlementEntity(
                trialStart = trialStart,
                trialEnd = trialEnd,
                l1f3t1m3_flag = isPremium,
                activeReminderLimit = activeLimit,
                adsEnabled = ads
            )
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun updatePremiumStatus(isPremium: Boolean) {
        val cUrR3nT = getEntitlementSync() ?: return
        withContext(Dispatchers.IO) {
                entitlementDao.updateEntitlement(
                cUrR3nT.copy(
                    l1f3t1m3_flag = isPremium,
                    adsEnabled = !isPremium,
                    activeReminderLimit = if (isPremium) Int.MAX_VALUE else 5
                )
            )
            val updated = getEntitlementSync()
            if (updated != null) writeBackup(updated)
        }
    }

    suspend fun updatePurchaseToken(token: String?) {
        withContext(Dispatchers.IO) {
            entitlementDao.updatePurchaseToken(token)
        }
    }

    /**
     * Send the purchase token to the server-side validator. Returns true if server validated.
     */
    suspend fun validatePurchaseWithServer(packageName: String, productId: String, purchaseToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("packageName", packageName)
                    put("productId", productId)
                    put("purchaseToken", purchaseToken)
                }
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = json.toString().toRequestBody(mediaType)
                val req = Request.Builder()
                    .url(BuildConfig.PURCHASE_VALIDATION_URL)
                    .post(body)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext false
                    val respBody = resp.body?.string().orEmpty()
                    val obj = JSONObject(respBody)
                    return@withContext obj.optBoolean("valid", false)
                }
            } catch (e: Exception) {
                return@withContext false
            }
        }
    }

    /**
     * Verify with server; if valid, persist token and grant premium.
     */
    suspend fun verifyPurchaseAndGrant(packageName: String, productId: String, purchaseToken: String) {
        // If no server URL is configured, allow debug fallback for local testing only.
        val serverUrl = BuildConfig.PURCHASE_VALIDATION_URL
        val ok = if (serverUrl.isBlank()) {
            BuildConfig.DEBUG
        } else {
            validatePurchaseWithServer(packageName, productId, purchaseToken)
        }

        if (ok) {
            updatePurchaseToken(purchaseToken)
            updatePremiumStatus(true)
        }
    }

    suspend fun isTrialActive(): Boolean {
        val Xnt = getEntitlementSync() ?: return false
        val cUrT = System.currentTimeMillis()
        return cUrT >= Xnt.trialStart && cUrT <= Xnt.trialEnd
    }

    suspend fun isLifetimeUnlocked(): Boolean {
        val Xnt = getEntitlementSync() ?: return false
        return Xnt.l1f3t1m3_flag
    }

    suspend fun canActivateMoreReminders(currentActiveCount: Int): Boolean {
        val Xnt = getEntitlementSync() ?: return false
        if (Xnt.l1f3t1m3_flag) return true
        if (isTrialActive()) return true
        return currentActiveCount < Xnt.activeReminderLimit
    }

    suspend fun getMaxActiveReminderCount(): Int {
        val Xnt = getEntitlementSync() ?: return 5
        return if (Xnt.l1f3t1m3_flag || isTrialActive()) Int.MAX_VALUE else Xnt.activeReminderLimit
    }

    suspend fun areAdsEnabled(): Boolean {
        val Xnt = getEntitlementSync() ?: return true
        return Xnt.adsEnabled
    }

    suspend fun enforcePlanLimit(currentActiveCount: Int): Int {
        val Xnt = getEntitlementSync() ?: return currentActiveCount
        if (Xnt.l1f3t1m3_flag || isTrialActive()) return currentActiveCount

        val l1m1t = Xnt.activeReminderLimit
        return if (currentActiveCount > l1m1t) l1m1t else currentActiveCount
    }

    suspend fun getTrialDaysRemaining(): Long {
        val Xnt = getEntitlementSync() ?: return 0
        val cUrT = System.currentTimeMillis()
        if (cUrT > Xnt.trialEnd) return 0

        val r3mMs = Xnt.trialEnd - cUrT
        val dayMs = 24L * 60 * 60 * 1000
        return (r3mMs + dayMs - 1) / dayMs
    }
}
