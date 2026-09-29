package it.vittorioscocca.kidbox.ui.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.billing.KBBillingManager
import it.vittorioscocca.kidbox.data.repository.KBTrialState
import it.vittorioscocca.kidbox.domain.model.KBPlan
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val currentPlan: KBPlan = KBPlan.FREE,
    val isLoading: Boolean = false,
    val purchaseError: String? = null,
    val isFamilyOwner: Boolean = false,
    val availableProducts: List<ProductDetails> = emptyList(),
    val subscriptionExpirationDate: Long? = null,
    val trial: KBTrialState = KBTrialState(),
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billingManager: KBBillingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                billingManager.currentPlan,
                billingManager.isLoading,
                billingManager.purchaseError,
                billingManager.isFamilyOwner,
                billingManager.products,
            ) { currentPlan, isLoading, purchaseError, isFamilyOwner, products ->
                SubscriptionUiState(
                    currentPlan = currentPlan,
                    isLoading = isLoading,
                    purchaseError = purchaseError,
                    isFamilyOwner = isFamilyOwner,
                    availableProducts = products,
                    subscriptionExpirationDate = null,
                )
            }.combine(billingManager.trialState) { state, trial ->
                state.copy(trial = trial)
            }.collect { _uiState.value = it }
        }
    }

    /** Prezzo dello store per il piano, mensile o annuale (null se Play non lo ha). */
    fun priceLabel(plan: KBPlan, yearly: Boolean): String? =
        billingManager.basePriceLabel(if (yearly) plan.productIdYearly else plan.productId)

    /** Risparmio percentuale dell'annuale sul mensile ×12, dai prezzi di Play. */
    fun yearlySavingPercent(plan: KBPlan): Int? {
        val monthly = billingManager.basePriceMicros(plan.productId) ?: return null
        val yearly = billingManager.basePriceMicros(plan.productIdYearly) ?: return null
        if (monthly <= 0) return null
        val pct = Math.round((1.0 - yearly.toDouble() / (monthly * 12.0)) * 100).toInt()
        return pct.takeIf { it > 0 }
    }

    fun loadPlan() {
        billingManager.start()
    }

    fun purchase(plan: KBPlan, activity: Activity, triggerFeature: String, yearly: Boolean = false) {
        billingManager.purchase(plan, activity, triggerFeature, yearly)
    }

    fun restorePurchases() {
        billingManager.restorePurchases()
    }

    fun clearError() {
        billingManager.clearError()
    }
}
