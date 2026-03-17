package com.nsfwshield.app.ui.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsfwshield.app.premium.SubscriptionGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val isPremium: Boolean = false,
    val planName: String = "Free",
    val monthlyPrice: String = "$4.99",
    val yearlyPrice: String = "$39.99",
    val selectedPlan: String = "yearly"
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionGate: SubscriptionGate
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        observeSubscription()
        loadPrices()
    }

    fun selectPlan(plan: String) {
        _uiState.update { it.copy(selectedPlan = plan) }
    }

    fun launchPurchase(activity: Activity) {
        val isYearly = _uiState.value.selectedPlan == "yearly"
        subscriptionGate.launchPurchaseFlow(activity, isYearly)
    }

    private fun observeSubscription() {
        viewModelScope.launch {
            subscriptionGate.subscriptionState.collect { sub ->
                _uiState.update {
                    it.copy(
                        isPremium = sub.isPremium,
                        planName = sub.planName
                    )
                }
            }
        }
    }

    private fun loadPrices() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    monthlyPrice = subscriptionGate.getMonthlyPrice(),
                    yearlyPrice = subscriptionGate.getYearlyPrice()
                )
            }
        }
    }
}
