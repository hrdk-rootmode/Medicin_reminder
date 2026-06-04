package com.example.medicinreminder.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.example.medicinreminder.data.repository.EntitlementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BillingManager (single-file, clean implementation).
 * This is a scaffold for Play Billing integration. Replace product IDs and add server-side
 * validation before production.
 */
class BillingManager(
    private val context: Context,
    private val entitlementRepository: EntitlementRepository
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val LIFETIME_PRODUCT_ID = "lifetime_premium"
    }

    private var billingClient: BillingClient? = null
    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()

    fun initialize(onReady: (Boolean) -> Unit) {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(this)
        onReady(true)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            queryPurchases()
        }
    }

    override fun onBillingServiceDisconnected() {
        billingClient?.startConnection(this)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        CoroutineScope(Dispatchers.IO).launch {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                    // Try to extract product id from purchase (v5 API: purchase.products)
                    val productId = try {
                        purchase.products?.firstOrNull() ?: LIFETIME_PRODUCT_ID
                    } catch (_: Exception) {
                        LIFETIME_PRODUCT_ID
                    }
                    // Verify server-side and grant premium if validated
                    entitlementRepository.verifyPurchaseAndGrant(context.packageName, productId, purchase.purchaseToken)
                }
            }
            _purchaseInProgress.value = false
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(acknowledgeParams) { _ -> }
    }

    fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String = LIFETIME_PRODUCT_ID) {
        _purchaseInProgress.value = true
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
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
            } else {
                _purchaseInProgress.value = false
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
