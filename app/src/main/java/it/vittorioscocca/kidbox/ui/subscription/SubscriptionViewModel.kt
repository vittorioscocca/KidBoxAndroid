package it.vittorioscocca.kidbox.ui.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.billing.KBBillingManager
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
            }.collect { _uiState.value = it }
        }
    }

    fun loadPlan() {
        billingManager.start()
    }

    fun purchase(plan: KBPlan, activity: Activity) {
        billingManager.purchase(plan, activity)
    }

    fun restorePurchases() {
        billingManager.restorePurchases()
    }

    fun clearError() {
        billingManager.clearError()
    }
}
