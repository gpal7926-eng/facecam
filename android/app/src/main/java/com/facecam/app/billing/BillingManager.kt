package com.facecam.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.facecam.app.data.OwnedCamerasStore
import com.facecam.app.data.ProStore

/**
 * Wraps Google Play Billing for the one-time purchases FaceCam offers:
 * the PRO unlock and individual cameras.
 *
 * This is a thin, defensive layer. It never crashes on a disconnected Play
 * service - the app remains fully usable offline and simply cannot buy anything
 * until Play is reachable.
 */
class BillingManager(
    private val context: Context,
    private val ownedStore: OwnedCamerasStore,
    private val proStore: ProStore,
    private val onStateChanged: () -> Unit
) {

    private var billingClient: BillingClient? = null
    private var productDetails: Map<String, ProductDetails> = emptyMap()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) handlePurchase(purchase)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.w(TAG, "Purchase update: ${result.debugMessage}")
        }
    }

    fun start() {
        if (billingClient != null) return
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restorePurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    fun end() {
        billingClient?.endConnection()
        billingClient = null
    }

    private fun queryProducts() {
        val client = billingClient ?: return
        val products = ProductIds.ALL.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsList.associateBy { it.productId }
                onStateChanged()
            } else {
                Log.w(TAG, "queryProductDetails: ${result.debugMessage}")
            }
        }
    }

    /** Price string for a product, e.g. "Rs 99", or null if not loaded yet. */
    fun priceFor(productId: String): String? =
        productDetails[productId]?.oneTimePurchaseOfferDetails?.formattedPrice

    /** Launch the Play purchase flow for a product. */
    fun purchase(activity: Activity, productId: String) {
        val client = billingClient ?: return
        val details = productDetails[productId] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    fun restorePurchases() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = mutableSetOf<String>()
                var pro = false
                for (p in purchases) {
                    if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        for (product in p.products) {
                            if (product == ProductIds.PRO) {
                                pro = true
                            } else {
                                ProductIds.cameraIdFor(product)?.let { owned.add(it) }
                            }
                        }
                        acknowledgeIfNeeded(p)
                    }
                }
                ownedStore.addAll(owned)
                proStore.setProFromBilling(pro)
                onStateChanged()
            } else {
                Log.w(TAG, "queryPurchases: ${result.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        for (product in purchase.products) {
            if (product == ProductIds.PRO) {
                proStore.setProFromBilling(true)
            } else {
                ProductIds.cameraIdFor(product)?.let { ownedStore.add(it) }
            }
        }
        acknowledgeIfNeeded(purchase)
        onStateChanged()
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    companion object {
        private const val TAG = "BillingManager"
    }
}
