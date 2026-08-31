package it.vittorioscocca.kidbox.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import it.vittorioscocca.kidbox.util.KBLog
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.family.isFamilySubscriptionManager
import it.vittorioscocca.kidbox.domain.family.resolveActiveFamilyId
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Singleton
class KBBillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subscriptionRepository: SubscriptionRepository,
    private val familyDao: KBFamilyDao,
    private val familySessionPreferences: FamilySessionPreferences,
    private val familyMemberDao: KBFamilyMemberDao,
    private val auth: FirebaseAuth,
) : PurchasesResponseListener {

    // TODO - Google Play Console setup required:
    // 1. Create subscription group: "kidbox_premium"
    // 2. Add base plan "pro-monthly": product ID "it.vittorioscocca.kidbox.pro.monthly"
    //    - Billing period: 1 month
    //    - Price: EUR 4.99
    //    - Free trial: (optional, decide with Vittorio)
    // 3. Add base plan "max-monthly": product ID "it.vittorioscocca.kidbox.max.monthly"
    //    - Billing period: 1 month
    //    - Price: EUR 9.99
    // 4. Add SERVICE ACCOUNT to Play Console with "Financial data" permission
    //    for server-side purchase verification (Cloud Function updatePlan)
    // 5. Enable Real-time developer notifications (Pub/Sub) if needed for
    //    server-side cancellation detection

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentPlan = MutableStateFlow(KBPlan.FREE)
    val currentPlan: StateFlow<KBPlan> = _currentPlan.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    private val _isFamilyOwner = MutableStateFlow(false)
    val isFamilyOwner: StateFlow<Boolean> = _isFamilyOwner.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private var currentFamilyId: String = ""
    private var currentUid: String = ""
    private var retryCount = 0
    private val trialOfferByProductId = mutableMapOf<String, Boolean>()

    /** Completamento opzionale per schermate che devono attendere [onQueryPurchasesResponse]. */
    private val restoreAwaitLock = Any()
    private var pendingRestoreAwait: CompletableDeferred<String?>? = null

    private val purchasesUpdatedListener: PurchasesUpdatedListener = object : PurchasesUpdatedListener {
        override fun onPurchasesUpdated(
            result: BillingResult,
            purchases: MutableList<Purchase>?,
        ) {
            handlePurchasesUpdated(result, purchases)
        }
    }

    private val billingClient: BillingClient = run {
        val pending: PendingPurchasesParams =
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(pending)
            .build()
    }

    fun clearError() {
        _purchaseError.value = null
    }

    /** Usato dalle schermate che chiamano [restorePurchases] dopo [start] (connessione async). */
    fun isBillingReady(): Boolean = billingClient.isReady

    fun start() {
        scope.launch {
            _isLoading.value = true
            currentFamilyId = resolveActiveFamilyId(familySessionPreferences, familyDao)
            currentUid = auth.currentUser?.uid.orEmpty()
            _isFamilyOwner.value = isFamilySubscriptionManager(
                familyDao,
                familyMemberDao,
                currentFamilyId,
                currentUid,
            )
            _currentPlan.value = subscriptionRepository.loadPlan(currentFamilyId, currentUid)
            connectBillingClient()
            _isLoading.value = false
        }
    }

    fun purchase(plan: KBPlan, activity: Activity) {
        val productId = plan.productId ?: return
        val product = _products.value.firstOrNull { it.productId == productId }
        if (product == null) {
            _purchaseError.value = "Prodotto non disponibile sullo store."
            return
        }
        if (!_isFamilyOwner.value) {
            _purchaseError.value = "Solo il proprietario famiglia può gestire l'abbonamento."
            return
        }
        val offers: List<ProductDetails.SubscriptionOfferDetails>? = product.subscriptionOfferDetails
        val firstOffer: ProductDetails.SubscriptionOfferDetails? = offers?.firstOrNull()
        val offerToken: String? = firstOffer?.offerToken
        if (offerToken.isNullOrBlank()) {
            _purchaseError.value = "Offerta non disponibile per questo piano."
            return
        }
        val hasTrial = firstOffer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        trialOfferByProductId[product.productId] = hasTrial
        val params: BillingFlowParams.ProductDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()
        val flowParams: BillingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .build()
        val result: BillingResult = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseError.value = result.debugMessage.ifBlank { "Impossibile avviare acquisto." }
        }
    }

    /** Avvia query acquisti subscription; per attendere risultati usare [restorePurchasesAwaitUi]. */
    fun restorePurchases() {
        querySubscriptionPurchases()
    }

    /**
     * Ripristino con attesa sicura dopo [start]/connessione Play Billing.
     * Ritorna messaggio errore utente-friendly o `null` se ok / nessun acquisto recuperabile qui.
     */
    suspend fun restorePurchasesAwaitUi(timeoutMs: Long = 30_000L): String? {
        clearError()
        val deferred = CompletableDeferred<String?>()
        synchronized(restoreAwaitLock) {
            pendingRestoreAwait = deferred
        }
        querySubscriptionPurchases()
        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            synchronized(restoreAwaitLock) {
                if (pendingRestoreAwait === deferred) {
                    pendingRestoreAwait = null
                }
            }
            "Ripristino in timeout. Riprova tra poco."
        }
    }

    private fun querySubscriptionPurchases() {
        if (!billingClient.isReady) {
            _purchaseError.value = "Billing non ancora pronto."
            completePendingRestoreAwaitIfNeeded()
            return
        }
        _isLoading.value = true
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
            this,
        )
    }

    /** Non sovrascrivere un completamento pendente dalla UI quando arriva anche il ripristino automatico al connect. */
    private fun querySubscriptionPurchasesForAutoReconnect() {
        if (!billingClient.isReady) return
        _isLoading.value = true
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
            this,
        )
    }

    private fun completePendingRestoreAwaitIfNeeded() {
        synchronized(restoreAwaitLock) {
            val d = pendingRestoreAwait ?: return
            pendingRestoreAwait = null
            d.complete(_purchaseError.value)
        }
    }

    private fun connectBillingClient() {
        if (billingClient.isReady) {
            queryProducts()
            return
        }
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    if (retryCount < 3) {
                        retryCount++
                        connectBillingClient()
                    } else {
                        _purchaseError.value = "Servizio Google Play non disponibile."
                    }
                }

                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        retryCount = 0
                        queryProducts()
                        querySubscriptionPurchasesForAutoReconnect()
                    } else {
                        _purchaseError.value = result.debugMessage.ifBlank { "Errore inizializzazione billing." }
                    }
                }
            },
        )
    }

    private fun queryProducts() {
        val proId: String = KBPlan.PRO.productId ?: return
        val maxId: String = KBPlan.MAX.productId ?: return

        val proProduct: QueryProductDetailsParams.Product =
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(proId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        val maxProduct: QueryProductDetailsParams.Product =
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(maxId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        val productsQuery: List<QueryProductDetailsParams.Product> = listOf(proProduct, maxProduct)
        val params: QueryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(productsQuery)
                .build()

        billingClient.queryProductDetailsAsync(
            params,
            // Billing 8: la callback non consegna più una `List<ProductDetails>`
            // ma un `QueryProductDetailsResult`, che oltre ai prodotti trovati
            // porta la lista di quelli NON trovati. È la ragione del cambio di
            // firma, ed è informazione utile: un prodotto assente dallo store
            // (id sbagliato, non pubblicato, paese non coperto) prima si
            // manifestava solo come lista più corta del previsto.
            object : ProductDetailsResponseListener {
                override fun onProductDetailsResponse(
                    result: BillingResult,
                    productDetailsResult: QueryProductDetailsResult,
                ) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        _purchaseError.value =
                            result.debugMessage.ifBlank { "Errore caricamento piani dallo store." }
                        return
                    }
                    val unfetched = productDetailsResult.unfetchedProductList
                    if (unfetched.isNotEmpty()) {
                        KBLog.app.warning(
                            "Billing: prodotti non trovati nello store: " +
                                unfetched.joinToString { it.productId },
                            "Billing",
                        )
                    }
                    _products.value = productDetailsResult.productDetailsList
                }
            },
        )
    }

    private fun handlePurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _isLoading.value = false
            return
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            _isLoading.value = false
            _purchaseError.value = result.debugMessage.ifBlank { "Acquisto non riuscito." }
            return
        }
        scope.launch {
            _isLoading.value = true
            for (purchase: Purchase in purchases) {
                processPurchase(purchase, isNewPurchase = true)
            }
            _isLoading.value = false
        }
    }

    override fun onQueryPurchasesResponse(result: BillingResult, purchases: MutableList<Purchase>) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _isLoading.value = false
            _purchaseError.value = result.debugMessage.ifBlank { "Ripristino acquisti non riuscito." }
            completePendingRestoreAwaitIfNeeded()
            return
        }
        scope.launch {
            for (purchase: Purchase in purchases) {
                processPurchase(purchase, isNewPurchase = false)
            }
            _isLoading.value = false
            completePendingRestoreAwaitIfNeeded()
        }
    }

    private suspend fun processPurchase(purchase: Purchase, isNewPurchase: Boolean) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val detectedPlan = mapPurchaseToPlan(purchase) ?: return
        val token = purchase.purchaseToken
        if (token.isBlank()) return
        val updateResult: Result<Unit> = subscriptionRepository.updatePlanAfterPurchase(
            plan = detectedPlan,
            purchaseToken = token,
            familyId = currentFamilyId,
            uid = currentUid,
        )
        if (updateResult.isFailure) {
            _purchaseError.value = updateResult.exceptionOrNull()?.localizedMessage ?: "Errore aggiornamento piano."
            return
        }
        if (isNewPurchase && !purchase.isAcknowledged) {
            val hasTrialOffer = purchase.products.firstOrNull()?.let { trialOfferByProductId[it] } ?: false
            AppAnalytics.subscriptionStarted(context, plan = detectedPlan.rawValue, trial = hasTrialOffer)
        }
        if (!purchase.isAcknowledged) {
            val ackParams: AcknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token)
                .build()
            billingClient.acknowledgePurchase(
                ackParams,
                object : AcknowledgePurchaseResponseListener {
                    override fun onAcknowledgePurchaseResponse(ackResult: BillingResult) {
                        if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            _purchaseError.value = "Acquisto riuscito ma conferma non completata. Riprova."
                        }
                    }
                },
            )
        }
        _currentPlan.value = subscriptionRepository.loadPlan(currentFamilyId, currentUid)
    }

    private fun mapPurchaseToPlan(purchase: Purchase): KBPlan? {
        val productId = purchase.products.firstOrNull() ?: return null
        return when (productId) {
            KBPlan.PRO.productId -> KBPlan.PRO
            KBPlan.MAX.productId -> KBPlan.MAX
            else -> null
        }
    }

}
