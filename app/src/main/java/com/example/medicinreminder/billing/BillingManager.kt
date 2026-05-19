package com.example.medicinreminder.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.example.medicinreminder.data.repository.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingManager(
    private val context: Context,
    private val entitlementRepository: EntitlementRepository
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val LIFETIME_PRODUCT_ID = "lifetime_premium"
    }

    private var billingClient: BillingClient? = null
    private var purchasesUpdatedListener: PurchasesUpdatedListener? = null

    fun initialize(onReady: (Boolean) -> Unit) {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(this)
        
        // For simplicity, we'll assume ready after connection attempt
        onReady(true)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            // Query existing purchases
            queryPurchases()
        }
    }

    override fun onBillingServiceDisconnected() {
        // Try to reconnect
        billingClient?.startConnection(this)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle cancellation
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        CoroutineScope(Dispatchers.IO).launch {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                    // Grant premium
                    entitlementRepository.updatePremiumStatus(true)
                    entitlementRepository.updatePurchaseToken(purchase.purchaseToken)
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Purchase acknowledged
            }
        }
    }

    fun queryPurchases() {
        val queryPurchaseParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(queryPurchaseParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String = LIFETIME_PRODUCT_ID) {
        val productDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient?.queryProductDetailsAsync(productDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()

                billingClient?.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }

    fun restorePurchases() {
        queryPurchases()
    }

    fun destroy() {
        billingClient?.endConnection()
    }
}
