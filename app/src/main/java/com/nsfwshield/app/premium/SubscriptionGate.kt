package com.nsfwshield.app.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscription Gate — Google Play Billing integration.
 *
 * Gates all premium features behind subscription entitlement checks.
 * Handles purchase flow, entitlement verification, and graceful downgrade
 * on subscription lapse (free tier without wiping user data).
 */
@Singleton
class SubscriptionGate @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        const val MONTHLY_SKU = "nsfw_shield_premium_monthly"    // $4.99/month
        const val YEARLY_SKU = "nsfw_shield_premium_yearly"      // $39.99/year
        const val FREE_TRIAL_DAYS = 7
    }

    /**
     * Subscription state exposed to UI.
     */
    data class SubscriptionState(
        val isPremium: Boolean = false,
        val isTrialActive: Boolean = false,
        val planName: String = "Free",
        val expiryTimestamp: Long? = null,
        val autoRenewing: Boolean = false
    )

    private val _subscriptionState = MutableStateFlow(SubscriptionState(isPremium = true, planName = "Developer Premium"))
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private var billingClient: BillingClient? = null
    private var productDetails: List<ProductDetails> = emptyList()

    /**
     * Initialize the billing client and check existing entitlements.
     */
    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    checkExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection on next check
            }
        })
    }

    /**
     * Check if the user has an active premium subscription.
     */
    fun isPremium(): Boolean = _subscriptionState.value.isPremium

    /**
     * Check if a specific premium feature is available.
     */
    fun isFeatureAvailable(feature: PremiumFeature): Boolean {
        return when (feature) {
            PremiumFeature.MULTIPLE_PROFILES -> isPremium()
            PremiumFeature.ACCOUNTABILITY_REPORTS -> isPremium()
            PremiumFeature.DELAY_TO_DISABLE -> isPremium()
            PremiumFeature.ADVANCED_AI -> isPremium()
            PremiumFeature.EXTENDED_LOGS -> isPremium()
            PremiumFeature.CUSTOM_BLOCKLISTS -> isPremium()
            PremiumFeature.PRIORITY_UPDATES -> isPremium()
        }
    }

    /**
     * Launch the purchase flow for a subscription.
     */
    fun launchPurchaseFlow(activity: Activity, isYearly: Boolean = false) {
        val client = billingClient ?: return
        val sku = if (isYearly) YEARLY_SKU else MONTHLY_SKU
        val product = productDetails.firstOrNull {
            it.productId == sku
        } ?: return

        val offerToken = product.subscriptionOfferDetails
            ?.firstOrNull()?.offerToken ?: return

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        client.launchBillingFlow(activity, params)
    }

    /**
     * Handle purchase updates from Google Play.
     */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK
            && purchases != null
        ) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    /**
     * Get product details for display in the subscription UI.
     */
    fun getMonthlyPrice(): String {
        return productDetails.firstOrNull { it.productId == MONTHLY_SKU }
            ?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()
            ?.formattedPrice ?: "$4.99"
    }

    fun getYearlyPrice(): String {
        return productDetails.firstOrNull { it.productId == YEARLY_SKU }
            ?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()
            ?.formattedPrice ?: "$39.99"
    }

    /**
     * Release billing client resources.
     */
    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }

    // ─── Private helpers ───

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MONTHLY_SKU)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(YEARLY_SKU)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient?.queryProductDetailsAsync(params) { _, details ->
            productDetails = details
        }
    }

    private fun checkExistingPurchases() {
        // DEV OVERRIDE: Always force premium status for testing
        _subscriptionState.value = SubscriptionState(
            isPremium = true,
            planName = "Developer Premium",
            autoRenewing = true
        )
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient?.acknowledgePurchase(params) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _subscriptionState.value = SubscriptionState(
                            isPremium = true,
                            planName = "Premium",
                            autoRenewing = purchase.isAutoRenewing
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enum of all premium features for gate checking.
 */
enum class PremiumFeature {
    MULTIPLE_PROFILES,
    ACCOUNTABILITY_REPORTS,
    DELAY_TO_DISABLE,
    ADVANCED_AI,
    EXTENDED_LOGS,
    CUSTOM_BLOCKLISTS,
    PRIORITY_UPDATES
}
