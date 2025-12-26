package kr.jm.voicesummary.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface BillingRepository {
    val isPremium: StateFlow<Boolean>
    val billingState: StateFlow<BillingState>
    
    fun startConnection()
    fun launchPurchaseFlow(activityProvider: () -> android.app.Activity)
    fun endConnection()
}

sealed class BillingState {
    data object Idle : BillingState()
    data object Connecting : BillingState()
    data object Ready : BillingState()
    data object Purchasing : BillingState()
    data object PurchaseSuccess : BillingState()
    data class Error(val message: String) : BillingState()
}
