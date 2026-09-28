package com.shindo91.trainerbattle.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.shindo91.trainerbattle.core.economy.GameRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Google Play Billing for one-time products:
 *  - gems_small / gems_medium / gems_large: consumable gem packs
 *  - remove_ads: non-consumable, disables interstitials and banners
 *
 * The products must be created with exactly these ids in the Play Console.
 *
 * [grant] must credit the purchase idempotently (keyed by purchase token) and persist it
 * before returning; only then is the purchase consumed/acknowledged. Unacknowledged
 * purchases are refunded by Google after 3 days, so nothing is lost if the app crashes.
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val grant: suspend (productId: String, purchaseToken: String) -> Unit,
) {
    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        loadProducts()
                        restorePurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.i(TAG, "Billing service disconnected")
            }
        })
    }

    private suspend fun loadProducts() {
        val ids = GameRules.gemProducts.keys + GameRules.REMOVE_ADS_PRODUCT
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _products.value = result.productDetailsList.orEmpty().associateBy { it.productId }
        } else {
            Log.w(TAG, "Product query failed: ${result.billingResult.debugMessage}")
        }
    }

    /** Re-processes owned purchases (e.g. remove_ads after reinstall, unconsumed gems). */
    suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val result = client.queryPurchasesAsync(params)
        result.purchasesList.forEach { handlePurchase(it) }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val details = _products.value[productId]
        if (details == null) {
            _messages.value = "Shop ist gerade nicht verfügbar. Bitte später erneut versuchen."
            start()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build(),
            ))
            .build()
        client.launchBillingFlow(activity, params)
    }

    fun clearMessage() { _messages.value = null }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { scope.launch { handlePurchase(it) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { restorePurchases() }
            else -> _messages.value = "Kauf fehlgeschlagen (${result.responseCode})"
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                _messages.value = "Zahlung ausstehend – du erhältst den Artikel, sobald sie bestätigt ist."
            }
            return
        }
        // TODO(production): verify purchase.originalJson/signature on your own server before
        // granting, otherwise modified clients can fake purchases.
        for (productId in purchase.products) {
            grant(productId, purchase.purchaseToken)
        }
        val consumable = purchase.products.all { it in GameRules.gemProducts }
        if (consumable) {
            val consume = client.consumePurchase(
                ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
            if (consume.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Consume failed: ${consume.billingResult.debugMessage}")
            }
        } else if (!purchase.isAcknowledged) {
            val ack = client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
            if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledge failed: ${ack.debugMessage}")
            }
        }
    }

    fun end() = client.endConnection()

    private companion object {
        const val TAG = "BillingManager"
    }
}
