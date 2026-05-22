package com.example.medicinreminder.data.ads

import android.content.Context

/**
 * Minimal rewarded-ad helper. Replace with real ad SDK integration.
 * For now, this simulates an immediately-granted reward so the UI flow can be tested.
 */
object RewardedAdManager {
    suspend fun showRewardedAd(context: Context): Boolean {
        // Dummy ad: wait 3 seconds to simulate viewing an ad, then grant reward.
        // Replace with real ad SDK integration (AdMob) for production.
        kotlinx.coroutines.delay(3000)
        return true
    }
}
