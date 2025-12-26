package kr.jm.voicesummary.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kr.jm.voicesummary.domain.repository.BillingRepository
import kr.jm.voicesummary.domain.repository.BillingState

class BillingRepositoryImpl(private val context: Context) : BillingRepository {

    companion object {
        private const val TAG = "BillingRepository"
        const val PRODUCT_ID_PREMIUM = "premium_unlock"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val prefs = context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean("is_premium", false))
    override val isPremium: StateFlow<Boolean> = _isPremium

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    override val billingState: StateFlow<BillingState> = _billingState

    private var productDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingState.value = BillingState.Idle
            }
            else -> {
                _billingState.value = BillingState.Error(billingResult.debugMessage)
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    override fun startConnection() {
        if (billingClient.isReady) {
            queryPurchases()
            return
        }

        _billingState.value = BillingState.Connecting

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingState.value = BillingState.Ready
                    queryProductDetails()
                    queryPurchases()
                } else {
                    _billingState.value = BillingState.Error(billingResult.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingState.value = BillingState.Idle
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PREMIUM)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        scope.launch {
            val result = billingClient.queryProductDetails(params)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = result.productDetailsList?.firstOrNull()
            }
        }
    }

    private fun queryPurchases() {
        scope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            val result = billingClient.queryPurchasesAsync(params)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPremium = result.purchasesList.any { purchase ->
                    purchase.products.contains(PRODUCT_ID_PREMIUM) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (hasPremium) {
                    setPremium(true)
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                scope.launch {
                    val acknowledgeParams = com.android.billingclient.api.AcknowledgePurchaseParams
                        .newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    val result = billingClient.acknowledgePurchase(acknowledgeParams)
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        setPremium(true)
                        _billingState.value = BillingState.PurchaseSuccess
                    }
                }
            } else {
                setPremium(true)
                _billingState.value = BillingState.PurchaseSuccess
            }
        }
    }

    override fun launchPurchaseFlow(activityProvider: () -> Activity) {
        val details = productDetails
        if (details == null) {
            _billingState.value = BillingState.Error("상품 정보를 불러올 수 없습니다")
            return
        }

        _billingState.value = BillingState.Purchasing

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activityProvider(), billingFlowParams)
    }

    private fun setPremium(value: Boolean) {
        _isPremium.value = value
        prefs.edit().putBoolean("is_premium", value).apply()
    }

    override fun endConnection() {
        billingClient.endConnection()
    }
}
