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
import it.vittorioscocca.kidbox.data.repository.KBTrialAskResult
import it.vittorioscocca.kidbox.data.repository.KBTrialOfferUi
import it.vittorioscocca.kidbox.data.repository.KBTrialState
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
import kotlinx.coroutines.flow.update
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

    /** Prova Pro della famiglia attiva (concessa dal server, vedi functions/proTrial.js). */
    private val _trialState = MutableStateFlow(KBTrialState())
    val trialState: StateFlow<KBTrialState> = _trialState.asStateFlow()

    /**
     * Card della prova Pro in Spazio e Piani. La prova non parte più da sola:
     * la attiva il proprietario col pulsante, e gli altri membri possono
     * chiedergliela («Chiedi di attivarla», push al proprietario).
     */
    private val _trialOffer = MutableStateFlow(KBTrialOfferUi())
    val trialOffer: StateFlow<KBTrialOfferUi> = _trialOffer.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private var currentFamilyId: String = ""
    private var currentUid: String = ""
    private var retryCount = 0
    private val trialOfferByProductId = mutableMapOf<String, Boolean>()

    /**
     * Acquisto avviato e non ancora concluso: l'esito arriva più tardi in
     * [handlePurchasesUpdated], e senza questo non sapremmo più per quale piano
     * né da quale schermata era partito (funnel d'acquisto in GA4).
     */
    @Volatile private var pendingPurchase: PendingPurchase? = null

    private data class PendingPurchase(val plan: KBPlan, val label: String, val trigger: String)

    /**
     * Offerta da comprare: quella con la prova gratuita se Play la propone
     * (Play elenca solo le offerte a cui l'utente ha diritto), altrimenti il
     * piano base. Prima si prendeva la prima della lista, che con più offerte
     * sullo stesso prodotto era una scelta a caso.
     */
    private fun preferredOffer(product: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = product.subscriptionOfferDetails.orEmpty()
        return offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { it.offerId == null }
            ?: offers.firstOrNull()
    }

    /** Prezzo di listino dello store (es. «39,99 €»), dal piano base del prodotto. */
    fun basePriceLabel(productId: String?): String? {
        val product = _products.value.firstOrNull { it.productId == productId } ?: return null
        val base = product.subscriptionOfferDetails.orEmpty().firstOrNull { it.offerId == null }
            ?: product.subscriptionOfferDetails?.firstOrNull()
        return base?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
    }

    /** Micro-unità del prezzo base, per calcolare il risparmio dell'annuale. */
    fun basePriceMicros(productId: String?): Long? {
        val product = _products.value.firstOrNull { it.productId == productId } ?: return null
        val base = product.subscriptionOfferDetails.orEmpty().firstOrNull { it.offerId == null }
            ?: product.subscriptionOfferDetails?.firstOrNull()
        return base?.pricingPhases?.pricingPhaseList?.lastOrNull()?.priceAmountMicros
    }

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
            _trialState.value = subscriptionRepository.loadTrialState(currentFamilyId)
            refreshTrialOffer()
            connectBillingClient()
            _isLoading.value = false
        }
    }

    /** Solo su Free: con un piano o una prova in corso la card non serve. */
    private suspend fun refreshTrialOffer() {
        val freeHere = _currentPlan.value == KBPlan.FREE &&
            !_trialState.value.ended &&
            currentFamilyId.isNotBlank()
        val status = if (freeHere) subscriptionRepository.loadTrialOffer(currentFamilyId) else null
        _trialOffer.update {
            it.copy(
                ownerDays = status?.takeIf { s -> s.eligible }?.days,
                askOwnerDays = status?.takeIf { s -> !s.eligible && s.ownerCanStart }?.days,
                ownerAsked = status?.askedOwner ?: false,
                aiLimit = status?.aiLimit ?: it.aiLimit,
            )
        }
    }

    /** Pulsante «Prova Pro per 14 giorni»: attiva la prova e ricarica piano e stato. */
    fun startTrial(triggerFeature: String) {
        if (_trialOffer.value.isStarting) return
        scope.launch {
            _trialOffer.update { it.copy(isStarting = true, startFailed = false) }
            val familyId = currentFamilyId.ifBlank { resolveActiveFamilyId(familySessionPreferences, familyDao) }
            subscriptionRepository.startTrial(familyId)
                .onSuccess {
                    AppAnalytics.proTrialStarted(context, triggerFeature)
                    _trialOffer.update { it.copy(ownerDays = null) }
                    _currentPlan.value = subscriptionRepository.loadPlan(familyId, currentUid)
                    _trialState.value = subscriptionRepository.loadTrialState(familyId)
                }
                .onFailure { _trialOffer.update { it.copy(startFailed = true) } }
            _trialOffer.update { it.copy(isStarting = false) }
        }
    }

    /** Chiusura dell'avviso: il rifiuto può voler dire che la prova non spetta più. */
    fun clearTrialStartFailed() {
        _trialOffer.update { it.copy(startFailed = false) }
        scope.launch { refreshTrialOffer() }
    }

    /** «Chiedi di attivarla»: push al proprietario (al massimo una al giorno). */
    fun askOwnerForTrial() {
        if (_trialOffer.value.isAsking) return
        scope.launch {
            _trialOffer.update { it.copy(isAsking = true) }
            val familyId = currentFamilyId.ifBlank { resolveActiveFamilyId(familySessionPreferences, familyDao) }
            val esito = subscriptionRepository.askOwnerForTrial(familyId)
            val sent = esito.getOrNull() == true
            if (sent) AppAnalytics.proTrialOwnerAsked(context)
            _trialOffer.update {
                it.copy(
                    isAsking = false,
                    ownerAsked = it.ownerAsked || sent,
                    askResult = when {
                        esito.isFailure -> KBTrialAskResult.FAILED
                        sent -> KBTrialAskResult.SENT
                        else -> KBTrialAskResult.NOT_DELIVERED
                    },
                )
            }
        }
    }

    fun clearTrialAskResult() {
        _trialOffer.update { it.copy(askResult = null) }
    }

    /**
     * @param triggerFeature la schermata che ha aperto il paywall (funnel d'acquisto).
     * @param yearly abbonamento annuale invece del mensile.
     */
    fun purchase(plan: KBPlan, activity: Activity, triggerFeature: String = "unknown", yearly: Boolean = false) {
        val productId = (if (yearly) plan.productIdYearly else plan.productId) ?: return
        // Nel funnel l'annuale si distingue dal mensile: "pro" / "pro_yearly".
        val planLabel = if (yearly) "${plan.rawValue}_yearly" else plan.rawValue
        if (!_isFamilyOwner.value) {
            _purchaseError.value = "Solo il proprietario famiglia può gestire l'abbonamento."
            AppAnalytics.purchaseFailed(context, planLabel, triggerFeature, reason = "not_owner")
            return
        }
        AppAnalytics.purchaseStarted(context, planLabel, triggerFeature)
        val product = _products.value.firstOrNull { it.productId == productId }
        if (product == null) {
            _purchaseError.value = "Prodotto non disponibile sullo store."
            AppAnalytics.purchaseFailed(context, planLabel, triggerFeature, reason = "product_unavailable")
            return
        }
        val firstOffer: ProductDetails.SubscriptionOfferDetails? = preferredOffer(product)
        val offerToken: String? = firstOffer?.offerToken
        if (firstOffer == null || offerToken.isNullOrBlank()) {
            _purchaseError.value = "Offerta non disponibile per questo piano."
            AppAnalytics.purchaseFailed(context, planLabel, triggerFeature, reason = "no_offer")
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
        pendingPurchase = PendingPurchase(plan, planLabel, triggerFeature)
        val result: BillingResult = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            _purchaseError.value = result.debugMessage.ifBlank { "Impossibile avviare acquisto." }
            AppAnalytics.purchaseFailed(
                context, planLabel, triggerFeature,
                reason = "launch_error", responseCode = result.responseCode,
            )
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
        // Mensili e annuali: un annuale non ancora creato su Play finisce in
        // `unfetchedProductList` e il paywall non propone la scelta.
        val productsQuery: List<QueryProductDetailsParams.Product> =
            listOf(KBPlan.PRO, KBPlan.MAX).flatMap { it.allProductIds }.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
        if (productsQuery.isEmpty()) return
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
        // Gli aggiornamenti arrivano anche senza un acquisto avviato da qui
        // (rinnovi, acquisti in sospeso che si sbloccano): allora niente funnel.
        val started = pendingPurchase
        pendingPurchase = null
        if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _isLoading.value = false
            started?.let { AppAnalytics.purchaseCancelled(context, it.label, it.trigger) }
            return
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            _isLoading.value = false
            _purchaseError.value = result.debugMessage.ifBlank { "Acquisto non riuscito." }
            started?.let {
                AppAnalytics.purchaseFailed(
                    context, it.label, it.trigger,
                    reason = "billing_error", responseCode = result.responseCode,
                )
            }
            return
        }
        started?.let {
            if (purchases.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) {
                AppAnalytics.purchaseFailed(context, it.label, it.trigger, reason = "pending")
            }
        }
        scope.launch {
            _isLoading.value = true
            for (purchase: Purchase in purchases) {
                processPurchase(
                    purchase,
                    isNewPurchase = true,
                    triggerFeature = started?.trigger ?: "unknown",
                    planLabel = started?.label,
                )
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

    private suspend fun processPurchase(
        purchase: Purchase,
        isNewPurchase: Boolean,
        triggerFeature: String = "unknown",
        planLabel: String? = null,
    ) {
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
            if (isNewPurchase) {
                AppAnalytics.purchaseFailed(context, planLabel ?: detectedPlan.rawValue, triggerFeature, reason = "server_error")
            }
            return
        }
        if (isNewPurchase && !purchase.isAcknowledged) {
            val hasTrialOffer = purchase.products.firstOrNull()?.let { trialOfferByProductId[it] } ?: false
            AppAnalytics.subscriptionStarted(
                context,
                plan = planLabel ?: detectedPlan.rawValue,
                trial = hasTrialOffer,
                triggerFeature = triggerFeature,
            )
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
        // Un acquisto durante la prova la chiude: il banner e il riquadro spariscono.
        _trialState.value = subscriptionRepository.loadTrialState(currentFamilyId)
    }

    private fun mapPurchaseToPlan(purchase: Purchase): KBPlan? {
        // Mensile o annuale: stesso piano.
        return KBPlan.fromProductId(purchase.products.firstOrNull())
    }

}
