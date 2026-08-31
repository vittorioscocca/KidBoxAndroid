package it.vittorioscocca.kidbox.ui.screens.home

import it.vittorioscocca.kidbox.data.remote.ActiveFamilyRemoteStore
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.ActiveFamilyResolver
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBSharedLocationDao
import it.vittorioscocca.kidbox.data.local.dao.OnboardingSignalsDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.family.FamilyHeroPhotoService
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.data.repository.WalletRepository
import it.vittorioscocca.kidbox.domain.family.ownershipUidFromFamilyFirestore
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import it.vittorioscocca.kidbox.data.sync.MembershipSyncService
import it.vittorioscocca.kidbox.domain.auth.LogoutUseCase
import it.vittorioscocca.kidbox.ui.screens.ai.planning.FamilyMemoryService
import it.vittorioscocca.kidbox.ui.screens.home.HeroCrop
import it.vittorioscocca.kidbox.ui.permissions.RuntimePermissions
import it.vittorioscocca.kidbox.ui.screens.home.onboarding.OnboardingChecklistState
import it.vittorioscocca.kidbox.ui.screens.home.onboarding.OnboardingChecklistUiState
import it.vittorioscocca.kidbox.ui.screens.home.onboarding.OnboardingStep
import it.vittorioscocca.kidbox.ui.screens.home.onboarding.computeOnboardingChecklist
import it.vittorioscocca.kidbox.util.analytics.KBAnalytics
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "HomeViewModel"
private const val MEMBERS_SYNC_TAG = "HomeMembersSync"
private const val MEMBERS_SYNC_TIMEOUT_MS = 10_000L

data class HomeUiState(
    val isLoading: Boolean = true,
    val familyId: String = "",
    val familyName: String = "",
    val heroPhotoUrl: String? = null,
    val heroPhotoLocalPath: String? = null,
    val heroPhotoScale: Float = 1f,
    val heroPhotoOffsetX: Float = 0f,
    val heroPhotoOffsetY: Float = 0f,
    val isUploadingHero: Boolean = false,
    val errorMessage: String? = null,
    val memberCount: Int = 0,
    val isLocationSharing: Boolean = false,
    val isMembersSyncing: Boolean = false,
    val membersSyncWarning: String? = null,
    /** Pull-to-refresh in corso: sync forzata chiesta dall'utente, non quella automatica. */
    val isRefreshing: Boolean = false,
    val todayLabel: String = "",
    val avatarUrl: String? = null,
    val isFabExpanded: Boolean = false,
    val topQuickActions: List<HomeQuickAction> = emptyList(),
    val featureOrder: List<String> = emptyList(),
    val badgeChat: Int = 0,
    val badgeDocuments: Int = 0,
    val badgePhotos: Int = 0,
    val badgeLocation: Int = 0,
    val badgeTodos: Int = 0,
    val badgeShopping: Int = 0,
    val badgeNotes: Int = 0,
    val badgeCalendar: Int = 0,
    val badgeExpenses: Int = 0,
    val badgeWallet: Int = 0,
    val badgePasswords: Int = 0,
    /** Piano abbonamento famiglia (Firestore); aggiorna card Assistente e paywall come iOS. */
    val familyPlan: KBPlan = KBPlan.FREE,
    /** Conteggio utilizzi per feature-id (tutte le categorie Home), per le Scorciatoie. */
    val featureUsage: Map<String, Int> = emptyMap(),
)

private sealed class HeroDownloadOutcome {
    data class Ok(val path: String) : HeroDownloadOutcome()
    data object Absent : HeroDownloadOutcome()
    data object Failed : HeroDownloadOutcome()
}

enum class HomeQuickAction(val key: String, val label: String) {
    EXPENSE("expense", "Spesa"),
    EVENT("event", "Evento"),
    TODO("todo", "To-Do"),
    NOTE("note", "Nota"),
    SHOPPING_LIST("shopping", "Lista spesa"),
    MESSAGE("message", "Messaggio"),
    HEALTH("health", "Salute"),
    DOCUMENTS("documents", "Documenti"),
    PETS("pets", "Animali domestici"),
    HOME_ITEMS("home_items", "Casa"),
    VEHICLES("vehicles", "Garage"),
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val sharedLocationDao: KBSharedLocationDao,
    private val onboardingSignalsDao: OnboardingSignalsDao,
    private val heroPhotoService: FamilyHeroPhotoService,
    private val familySyncCenter: FamilySyncCenter,
    private val membershipSyncService: MembershipSyncService,
    private val familySessionPreferences: FamilySessionPreferences,
    private val homeBadgeManager: HomeBadgeManager,
    private val walletRepository: WalletRepository,
    private val passwordsRepository: PasswordsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val aiService: AIService,
    private val familyMemoryService: FamilyMemoryService,
    private val homeViewModePreference: it.vittorioscocca.kidbox.data.local.HomeViewModePreference,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val homeViewMode: StateFlow<it.vittorioscocca.kidbox.data.local.HomeViewMode> =
        homeViewModePreference.getViewModeFlow()

    /** Dashboard in Home: preferenza per-device, spenta di default. */
    val showDashboard: StateFlow<Boolean> =
        homeViewModePreference.getShowDashboardFlow()

    fun setHomeViewMode(mode: it.vittorioscocca.kidbox.data.local.HomeViewMode) {
        homeViewModePreference.setViewMode(mode)
    }

    fun getGridOrder(): List<String>? = homeViewModePreference.getOrder()

    fun setGridOrder(ids: List<String>) {
        homeViewModePreference.setOrder(ids)
    }

    private val db get() = FirebaseFirestore.getInstance()
    private val prefs = appContext.getSharedPreferences("home_quick_actions", Context.MODE_PRIVATE)
    private val featureOrderKey = "feature_order_v1"
    private var syncedFamilyId: String? = null
    /** Evita download hero paralleli (burst App Check) e rifetch quando l'oggetto non esiste. */
    private val heroCacheMutex = Mutex()
    private val heroAbsentCacheKeys = mutableSetOf<String>()
    @Volatile
    private var initialSyncCompleted: Boolean = false
    private var membersSyncTimeoutJob: Job? = null
    private var passwordBadgeJob: Job? = null
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Checklist "Per iniziare". Sta nel ViewModel della Home e non in uno suo
     * perché il suo unico momento utile è quello in cui l'utente guarda la Home
     * e non sa cosa fare, e perché deve ricalcolarsi ogni volta che la Home
     * torna visibile.
     */
    private val _onboarding = MutableStateFlow(OnboardingChecklistUiState())
    val onboarding: StateFlow<OnboardingChecklistUiState> = _onboarding.asStateFlow()

    // ── Reactive observation — avviato una volta sola, sempre attivo ──────────
    // FIX principale: invece di one-shot con hasLoaded, osserviamo Room con un
    // Flow combine. Ogni volta che FamilySyncCenter aggiorna la famiglia in Room
    // (es. heroPhotoURL arriva da un altro device via Firestore), la UI si aggiorna.
    init {
        observeHomeData()
        // La famiglia attiva arriva in modo asincrono (Room, poi eventuale
        // bootstrap da Firestore): la checklist va ricalcolata quando cambia,
        // non una volta sola alla costruzione del ViewModel.
        viewModelScope.launch {
            _uiState.map { it.familyId }.distinctUntilChanged().collectLatest { refreshOnboarding() }
        }
        observeFamilyPlanFromFirestore()
        observeBadges()
        observeInitialSyncState()
        startMembershipListenerIfAuthenticated()
        viewModelScope.launch { refreshAvatarUrl() }
        viewModelScope.launch {
            familySyncCenter.accessLostEvent.collect {
                membershipSyncService.stop()
                _uiState.value = HomeUiState(isLoading = false, familyId = "")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.home_vm_removed_from_family_toast),
                        Toast.LENGTH_LONG,
                    ).show()
                    val intent = appContext.packageManager
                        .getLaunchIntentForPackage(appContext.packageName)
                        ?.apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                            )
                        }
                    intent?.let { appContext.startActivity(it) }
                }
            }
        }
    }

    private fun observeHomeData() {
        viewModelScope.launch {
            familyDao.observeAll().collectLatest { families ->
                if (families.isEmpty()) {
                    val familyId = ""
                    homeBadgeManager.stopListening()
                    walletRepository.stopRealtime()
                    passwordsRepository.stopRealtime()
                    syncedFamilyId = null
                    initialSyncCompleted = false
                    cancelMembersSyncTimeout()
                    _uiState.value = _uiState.value.copy(
                        isMembersSyncing = false,
                        membersSyncWarning = null,
                    )
                    if (familySessionPreferences.consumeSkipHomeBootstrapOnce()) {
                        KBLog.ui.info("observeHomeData: skip Firestore bootstrap (leave / access revoked)", TAG)
                        _uiState.value = HomeUiState(isLoading = false, familyId = "")
                        return@collectLatest
                    }
                    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    if (uid.isNotEmpty()) {
                        // Nessun familyId stale (kidbox_prefs / KidBoxPrefs) prima del bootstrap
                        familySessionPreferences.clearActiveFamilyId()
                        try {
                            bootstrapFromFirestore(uid)
                            // After bootstrap, Room will emit again automatically via the Flow
                            return@collectLatest
                        } catch (e: Exception) {
                            KBLog.ui.warning("HomeViewModel bootstrap failed: ${e.message}", TAG)
                        }
                    }
                    _uiState.value = HomeUiState(isLoading = false, familyId = "")
                    return@collectLatest
                }

                val uidCorrente = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val familyId = ActiveFamilyResolver.resolveFamilyId(
                    families,
                    familySessionPreferences.getActiveFamilyId()
                        ?: familySessionPreferences.getLastActiveFamilyId(uidCorrente),
                )
                if (familyId.isBlank()) {
                    _uiState.value = HomeUiState(isLoading = false, familyId = "")
                    return@collectLatest
                }
                val selectedFamily = families.firstOrNull { it.id == familyId } ?: families.first()
                familySessionPreferences.setActiveFamilyId(familyId)

                if (syncedFamilyId != familyId) {
                    syncedFamilyId = familyId
                    heroAbsentCacheKeys.clear()
                    initialSyncCompleted = false
                    KBLog.ui.info("startSync familyId=$familyId", MEMBERS_SYNC_TAG)
                    _uiState.value = _uiState.value.copy(
                        isMembersSyncing = true,
                        membersSyncWarning = null,
                    )
                    scheduleMembersSyncTimeout(familyId)
                    familySyncCenter.startSync(familyId)
                    viewModelScope.launch(Dispatchers.IO) {
                        familyMemoryService.loadFromFirestore(familyId)
                    }
                    passwordBadgeJob?.cancel()
                    passwordBadgeJob = viewModelScope.launch {
                        passwordsRepository.observeVisibleEntries(familyId).collectLatest { entries ->
                            _uiState.value = _uiState.value.copy(
                                badgePasswords = entries.count { it.deletedAtEpochMillis == null && (it.pwnedCount ?: 0) > 0 },
                            )
                        }
                    }
                }

                // `initialSyncDone` è una sorgente del combine, non una lettura
                // secca: prima `shouldSyncMembers` nasceva da una copia del flag
                // dentro un collect che si risveglia SOLO quando cambia Room. Se
                // il sync finiva dopo l'ultima emissione di Room — ed è la norma
                // per chi entra da invito, perché il join avvia il sync e ne
                // aspetta la fine PRIMA di arrivare in Home — quella riga non
                // veniva più ricalcolata e lo spinner restava acceso per sempre.
                combine(
                    familyDao.observeAll(),
                    familyMemberDao.observeActiveByFamilyId(familyId),
                    sharedLocationDao.observeActiveByFamilyId(familyId),
                    familySyncCenter.initialSyncDone,
                ) { fams, members, sharedUsers, syncDone ->
                    Triple(
                        fams.firstOrNull { it.id == familyId },
                        // Stessa deduplica di FamilySettingsViewModel: con doc id
                        // legacy diversi dall'uid lo stesso utente può avere due
                        // righe, e Home e Impostazioni mostravano numeri diversi.
                        members.distinctBy { it.userId }.size,
                        sharedUsers,
                    ) to syncDone
                }.collect { (snapshot, syncDone) ->
                    val (fam, memberCount, sharedUsers) = snapshot
                    initialSyncCompleted = syncDone
                    homeBadgeManager.startListening(familyId)
                    walletRepository.startRealtime(familyId)
                    passwordsRepository.startRealtime(familyId)
                    val shouldSyncMembers = !syncDone || memberCount <= 0
                    val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    val isLocationSharing = if (currentUid.isBlank()) {
                        // Avoid flicker when auth is briefly unavailable during resume/navigation.
                        _uiState.value.isLocationSharing
                    } else {
                        sharedUsers.any { it.id == currentUid }
                    }
                    KBLog.ui.debug("members update familyId=$familyId count=$memberCount initialDone=$initialSyncCompleted syncing=$shouldSyncMembers locationSharing=$isLocationSharing", MEMBERS_SYNC_TAG)
                    val remoteUrl = fam?.heroPhotoURL

                    // File locale se esiste già su disco
                    val localPath = fam?.heroPhotoLocalPath?.takeIf { File(it).exists() }

                    // Se non abbiamo il file locale ma abbiamo l'URL, scarica in background
                    // Nel frattempo Coil usa l'URL remoto direttamente
                    if (localPath == null && remoteUrl != null) {
                        val fid = fam!!.id
                        val heroUrl = remoteUrl
                        val absentKey = "$fid|$heroUrl"
                        if (absentKey !in heroAbsentCacheKeys) {
                            viewModelScope.launch(Dispatchers.IO) {
                                heroCacheMutex.withLock {
                                    val row = familyDao.getById(fid)
                                    val onDisk = row?.heroPhotoLocalPath?.takeIf { File(it).exists() }
                                    if (onDisk != null) {
                                        withContext(Dispatchers.Main) {
                                            _uiState.value =
                                                _uiState.value.copy(heroPhotoLocalPath = onDisk)
                                        }
                                        return@withLock
                                    }
                                    if (absentKey in heroAbsentCacheKeys) return@withLock

                                    when (
                                        val outcome = downloadHeroToCache(fid, heroUrl)
                                    ) {
                                        is HeroDownloadOutcome.Ok -> {
                                            withContext(Dispatchers.Main) {
                                                _uiState.value = _uiState.value.copy(
                                                    heroPhotoLocalPath = outcome.path,
                                                )
                                            }
                                        }
                                        HeroDownloadOutcome.Absent -> {
                                            heroAbsentCacheKeys.add(absentKey)
                                        }
                                        HeroDownloadOutcome.Failed -> Unit
                                    }
                                }
                            }
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        familyId = familyId,
                        familyName = fam?.name.orEmpty(),
                        heroPhotoUrl = remoteUrl,
                        heroPhotoLocalPath = localPath,
                        heroPhotoScale = fam?.heroPhotoScale?.toFloat() ?: 1f,
                        heroPhotoOffsetX = fam?.heroPhotoOffsetX?.toFloat() ?: 0f,
                        heroPhotoOffsetY = fam?.heroPhotoOffsetY?.toFloat() ?: 0f,
                        memberCount = memberCount,
                        isLocationSharing = isLocationSharing,
                        isMembersSyncing = shouldSyncMembers,
                        membersSyncWarning = if (memberCount > 0) null else _uiState.value.membersSyncWarning,
                        todayLabel = todayLabel(),
                        avatarUrl = _uiState.value.avatarUrl
                            ?: com.google.firebase.auth.FirebaseAuth.getInstance()
                                .currentUser?.photoUrl?.toString(),
                        topQuickActions = topQuickActions(),
                        featureOrder = loadFeatureOrder(),
                        featureUsage = featureUsage(),
                    )
                    if (!shouldSyncMembers || memberCount > 0) {
                        cancelMembersSyncTimeout()
                    }
                }
            }
        }
    }

    private fun observeFamilyPlanFromFirestore() {
        viewModelScope.launch {
            familyDao.observeAll().collectLatest { families ->
                if (families.isEmpty()) {
                    _uiState.value = _uiState.value.copy(familyPlan = KBPlan.FREE)
                    return@collectLatest
                }
                val familyId = ActiveFamilyResolver.resolveFamilyId(
                    families,
                    familySessionPreferences.getActiveFamilyId(),
                )
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                if (familyId.isBlank()) {
                    _uiState.value = _uiState.value.copy(familyPlan = KBPlan.FREE)
                    return@collectLatest
                }
                subscriptionRepository.planFlow(familyId, uid).collectLatest { plan ->
                    _uiState.value = _uiState.value.copy(familyPlan = plan)
                    it.vittorioscocca.kidbox.ai.CurrentPlanStore.update(plan)
                    // Equivalente Android di refreshAIQuotaStatus() su iOS: al caricamento
                    // del piano, aggiorna subito lo stato AI (bonus Free esaurito o no).
                    // Le chiamate AI successive lo terranno aggiornato in tempo reale.
                    refreshAIQuotaStatus(familyId)
                }
            }
        }
    }

    /**
     * Fail-open by design: se la fetch fallisce (rete assente, ecc.) non blocchiamo
     * l'accesso all'AI — l'enforcement reale resta lato backend sulle callable.
     */
    private fun refreshAIQuotaStatus(familyId: String) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching { aiService.fetchUsage(familyId) }
        }
    }

    private fun observeInitialSyncState() {
        viewModelScope.launch {
            familySyncCenter.initialSyncDone.collectLatest { done ->
                val current = _uiState.value
                if (current.familyId.isBlank()) return@collectLatest
                initialSyncCompleted = done
                val isSyncing = !done || current.memberCount <= 0
                if (current.isMembersSyncing != isSyncing) {
                    KBLog.ui.info(if (isSyncing) {
                            "initial sync pending familyId=${current.familyId} count=${current.memberCount}"
                        } else {
                            "initial sync completed familyId=${current.familyId} count=${current.memberCount}"
                        }, MEMBERS_SYNC_TAG)
                    _uiState.value = current.copy(
                        isMembersSyncing = isSyncing,
                        membersSyncWarning = if (current.memberCount > 0) null else current.membersSyncWarning,
                    )
                }
                if (!isSyncing || current.memberCount > 0) {
                    cancelMembersSyncTimeout()
                } else {
                    scheduleMembersSyncTimeout(current.familyId)
                }
            }
        }
    }

    private fun scheduleMembersSyncTimeout(familyId: String) {
        if (familyId.isBlank()) return
        membersSyncTimeoutJob?.cancel()
        membersSyncTimeoutJob = viewModelScope.launch {
            delay(MEMBERS_SYNC_TIMEOUT_MS)
            val current = _uiState.value
            if (current.familyId != familyId || current.memberCount > 0 || !current.isMembersSyncing) return@launch
            KBLog.ui.warning("timeout familyId=$familyId after=${MEMBERS_SYNC_TIMEOUT_MS}ms, release loading with warning", MEMBERS_SYNC_TAG)
            _uiState.value = current.copy(
                isMembersSyncing = false,
                membersSyncWarning = appContext.getString(R.string.home_vm_members_sync_slow_warning),
            )
        }
    }

    private fun cancelMembersSyncTimeout() {
        membersSyncTimeoutJob?.cancel()
        membersSyncTimeoutJob = null
    }

    private fun observeBadges() {
        viewModelScope.launch {
            homeBadgeManager.badges.collectLatest { badges ->
                _uiState.value = _uiState.value.copy(
                    badgeChat = badges.chat,
                    badgeDocuments = badges.documents,
                    badgePhotos = badges.photos,
                    badgeLocation = badges.location,
                    badgeTodos = badges.todos,
                    badgeShopping = badges.shopping,
                    badgeNotes = badges.notes,
                    badgeCalendar = badges.calendar,
                    badgeExpenses = badges.expenses,
                    badgeWallet = badges.wallet,
                )
            }
        }
    }

    /**
     * Pull-to-refresh della Home: rilancia la sincronizzazione da capo.
     *
     * Serve perché la sincronizzazione automatica non è ripetibile — [FamilySyncCenter.startSync]
     * salta se i listener sono già attivi, e Firestore non rimanda documenti invariati:
     * una volta che un membro è sparito da Room non c'è evento che lo riporti.
     * [FamilySyncCenter.forceResync] riconcilia Room contro una lettura dal server.
     *
     * Senza famiglia locale rifà il bootstrap da Firestore, che è il caso
     * "sono entrato con un invito e la Home è rimasta vuota".
     */
    fun forceRefresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, membersSyncWarning = null)
            try {
                val familyId = _uiState.value.familyId
                if (familyId.isBlank()) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    if (uid.isNotEmpty()) {
                        runCatching { bootstrapFromFirestore(uid) }
                            .onFailure { KBLog.ui.warning("forceRefresh bootstrap fallito: ${it.message}", MEMBERS_SYNC_TAG) }
                    }
                } else {
                    // Niente da azzerare qui: forceResync stacca e riattacca i
                    // listener per conto suo, scavalcando il guard di startSync.
                    KBLog.ui.info("forceRefresh familyId=$familyId", MEMBERS_SYNC_TAG)
                    runCatching { familySyncCenter.forceResync(familyId) }
                        .onFailure { KBLog.ui.warning("forceRefresh forceResync fallito: ${it.message}", MEMBERS_SYNC_TAG) }

                    // Anche la dashboard va rinfrescata, non solo la famiglia: i
                    // badge delle sezioni vengono dal documento `counters` e le
                    // card da wallet e password, ciascuno col proprio listener.
                    // Senza questo, il pull sistemava i membri e lasciava i
                    // numeri delle sezioni fermi a prima.
                    homeBadgeManager.stopListening()
                    homeBadgeManager.startListening(familyId)
                    runCatching { walletRepository.awaitForceRestartRealtime(familyId) }
                        .onFailure { KBLog.ui.warning("forceRefresh wallet fallito: ${it.message}", MEMBERS_SYNC_TAG) }
                    runCatching { passwordsRepository.awaitForceRestartRealtime(familyId) }
                        .onFailure { KBLog.ui.warning("forceRefresh password fallito: ${it.message}", MEMBERS_SYNC_TAG) }
                }
                refreshAvatarUrl()
                refreshOnboarding()
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    fun onScreenVisible() {
        viewModelScope.launch { refreshAvatarUrl() }
        // Il permesso notifiche può cambiare fuori dall'app, nelle impostazioni
        // di sistema: al rientro va riletto, non dato per quello che era. Vale
        // anche per i contenuti, creati in un'altra schermata e sincronizzati
        // mentre la Home era in secondo piano.
        refreshOnboarding()
    }

    // ── Checklist "Per iniziare" ─────────────────────────────────────────────

    /**
     * Ricalcola la checklist dai dati locali. Da chiamare a ogni ritorno in
     * primo piano e al ritorno sulla Home: l'utente ha appena caricato il
     * documento o creato l'evento, e la riga deve spuntarsi sotto i suoi occhi.
     */
    fun refreshOnboarding() {
        viewModelScope.launch {
            val familyId = _uiState.value.familyId
            val authorized = RuntimePermissions.hasNotificationPermission(appContext)
            val (state, events) = runCatching {
                computeOnboardingChecklist(
                    context = appContext,
                    dao = onboardingSignalsDao,
                    familyId = familyId,
                    notificationsAuthorized = authorized,
                )
            }.getOrElse {
                KBLog.ui.error("Onboarding: calcolo fallito: ${it.message}", TAG)
                return@launch
            }
            _onboarding.value = state
            events.forEach { (action, step) ->
                KBAnalytics.logOnboarding(action = action, step = step.raw)
            }
        }
    }

    fun dismissOnboarding() {
        OnboardingChecklistState.setDismissed(appContext, true)
        _onboarding.value = OnboardingChecklistUiState()
        KBAnalytics.logOnboarding(action = "dismissed", step = "card")
    }

    /**
     * Registra il tap su una riga e restituisce la rotta da aprire, se il passo
     * ne ha una. La navigazione resta responsabilità della Home, l'unica a
     * possedere il NavController.
     */
    fun onOnboardingStepTapped(step: OnboardingStep): String? {
        KBAnalytics.logOnboarding(action = "tapped", step = step.raw)
        return step.route(_uiState.value.familyId)
    }

    private suspend fun refreshAvatarUrl() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val remote = runCatching {
            db.collection("users").document(uid).get().await()
        }.getOrNull()
        val url = (remote?.data?.get("avatarURL") as? String)?.trim().takeIf { !it.isNullOrEmpty() }
            ?: FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
        _uiState.value = _uiState.value.copy(avatarUrl = url)
    }

    private suspend fun bootstrapFromFirestore(uid: String) {
        try {
            KBLog.ui.info("bootstrapFromFirestore start uid=$uid", TAG)
            val membershipDocs = db.collection("users")
                .document(uid)
                .collection("memberships")
                .get()
                .await()
                .documents

            val candidateFamilyIds = mutableListOf<String>()
            membershipDocs
                .asSequence()
                .mapNotNull { doc ->
                    doc.id.takeIf { it.isNotBlank() }?.also { candidateFamilyIds.add(it) }
                    (doc.data?.get("familyId") as? String)?.trim()?.takeIf { it.isNotEmpty() }
                }
                .forEach { candidateFamilyIds.add(it) }

            if (candidateFamilyIds.isEmpty()) {
                KBLog.ui.warning("bootstrapFromFirestore: memberships empty/incoerenti, fallback members collectionGroup", TAG)
                val memberDocs = db.collectionGroup("members")
                    .whereEqualTo("uid", uid)
                    .get()
                    .await()
                    .documents
                memberDocs
                    .filter { it.data?.get("isDeleted") as? Boolean != true }
                    .mapNotNull { it.reference.parent.parent?.id }
                    .forEach { candidateFamilyIds.add(it) }
            }
            val distinctCandidates = candidateFamilyIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            if (distinctCandidates.isEmpty()) {
                KBLog.ui.warning("bootstrapFromFirestore: memberships empty uid=$uid", TAG)
                _uiState.value = HomeUiState(isLoading = false, familyId = "")
                return
            }

            // La chiave di sessione è appena stata azzerata (vedi observeHomeData):
            // al rientro con lo stesso account vale la memoria per uid, altrimenti
            // si ripartirebbe dalla prima famiglia che risponde — di solito la più
            // vecchia, non quella in cui si stava.
            // Ordine: preferenza di sessione → famiglia attiva salvata
            // sull'account (sopravvive a logout e reinstallazioni, e vale anche
            // se l'ultimo accesso era da un altro dispositivo) → memoria locale
            // per uid. Senza, si ripartirebbe dalla prima famiglia che risponde:
            // di solito la più vecchia, non quella in cui si stava.
            val preferredId = familySessionPreferences.getActiveFamilyId()
                ?: ActiveFamilyRemoteStore.load(uid)
                ?: familySessionPreferences.getLastActiveFamilyId(uid)
            val orderedCandidates = if (
                preferredId != null && distinctCandidates.contains(preferredId)
            ) {
                listOf(preferredId) + distinctCandidates.filter { it != preferredId }
            } else {
                distinctCandidates
            }

            var selectedFamilyId: String? = null
            var selectedFamilyData: Map<String, Any> = emptyMap()
            for (candidateId in orderedCandidates) {
                try {
                    val myMemberDoc = db.collection("families")
                        .document(candidateId)
                        .collection("members")
                        .document(uid)
                        .get()
                        .await()
                    if (!myMemberDoc.exists() || myMemberDoc.data?.get("isDeleted") as? Boolean == true) {
                        KBLog.ui.warning("bootstrapFromFirestore: skip familyId=$candidateId (member missing/deleted)", TAG)
                        continue
                    }
                    val familySnap = db.collection("families").document(candidateId).get().await()
                    if (!familySnap.exists()) {
                        KBLog.ui.warning("bootstrapFromFirestore: skip familyId=$candidateId (family missing)", TAG)
                        continue
                    }
                    val familyData = familySnap.data.orEmpty()
                    if (familyData["isDeleted"] as? Boolean == true) {
                        KBLog.ui.warning("bootstrapFromFirestore: skip familyId=$candidateId (family deleted)", TAG)
                        continue
                    }
                    selectedFamilyId = candidateId
                    selectedFamilyData = familyData
                    break
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        KBLog.ui.warning("bootstrapFromFirestore: skip familyId=$candidateId (PERMISSION_DENIED)", TAG)
                        continue
                    }
                    throw e
                }
            }
            val familyId = selectedFamilyId
            if (familyId.isNullOrBlank()) {
                _uiState.value = HomeUiState(isLoading = false, familyId = "")
                return
            }
            KBLog.ui.info("bootstrapFromFirestore: selected familyId=$familyId from candidates=${distinctCandidates.size}", TAG)
            val familyData = selectedFamilyData
            val now = System.currentTimeMillis()
            val createdBy = ownershipUidFromFamilyFirestore(familyData) ?: uid
            val family = KBFamilyEntity(
                id = familyId,
                name = (familyData["name"] as? String).orEmpty(),
                heroPhotoURL = familyData["heroPhotoURL"] as? String,
                heroPhotoLocalPath = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = createdBy,
                updatedBy = (familyData["updatedBy"] as? String) ?: uid,
                createdAtEpochMillis = (familyData["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now,
                updatedAtEpochMillis = (familyData["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now,
                lastSyncAtEpochMillis = null,
                lastSyncError = null,
            )
            familyDao.upsert(family)
            KBLog.ui.info("bootstrapFromFirestore: family upserted familyId=$familyId", TAG)

            val memberDocs = db.collection("families")
                .document(familyId)
                .collection("members")
                .get()
                .await()
                .documents

            memberDocs.forEach { doc ->
                val d = doc.data.orEmpty()
                if (d["isDeleted"] as? Boolean != true) {
                    val memberUid = (d["uid"] as? String) ?: doc.id
                    val displayName = (d["displayName"] as? String)
                        ?: (d["name"] as? String)
                        ?: (d["fullName"] as? String)
                        ?: (d["email"] as? String)
                        ?: appContext.getString(R.string.home_vm_member_default_name)
                    familyMemberDao.upsert(
                        KBFamilyMemberEntity(
                            id = doc.id,
                            familyId = familyId,
                            userId = memberUid,
                            role = (d["role"] as? String) ?: "member",
                            displayName = displayName,
                            email = (d["email"] as? String),
                            photoURL = d["photoURL"] as? String,
                            createdAtEpochMillis = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now,
                            updatedAtEpochMillis = (d["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now,
                            updatedBy = (d["updatedBy"] as? String) ?: uid,
                            isDeleted = false,
                        )
                    )
                }
            }
            KBLog.ui.info("bootstrapFromFirestore: members upserted count=${memberDocs.size} familyId=$familyId", TAG)
            familyMemoryService.loadFromFirestore(familyId)
        } catch (e: Exception) {
            KBLog.ui.warning("HomeViewModel bootstrap failed: ${e.message}", TAG)
            _uiState.value = HomeUiState(isLoading = false, familyId = "")
        } finally {
            if (_uiState.value.isLoading) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // ── Hero photo: upload da picker ──────────────────────────────────────────


    // ── Hero photo: flusso a due step identico a iOS ──────────────────────────
    // Step 1: picker → salva URI + bytes → mostra cropper
    // Step 2: cropper → onSave(crop) → upload + scrivi su Firestore

    // URI e bytes pending (usati dal cropper)
    private val _pendingHeroUri = MutableStateFlow<Uri?>(null)
    val pendingHeroUri: StateFlow<Uri?> = _pendingHeroUri.asStateFlow()

    // Bytes già letti sul main thread — evita problemi MIUI con URI temporanee su IO thread
    private var pendingHeroBytes: ByteArray? = null

    /** Chiamato quando l'utente seleziona una foto dalla galleria. */
    fun onHeroPhotoSelected(uri: Uri, context: Context) {
        if (_uiState.value.familyId.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = appContext.getString(R.string.home_vm_error_create_family_first),
            )
            return
        }
        // Leggi i bytes subito sul thread corrente (main) prima che l'URI scada
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = compressJpeg(context, uri)
                pendingHeroBytes = bytes
                _pendingHeroUri.value = uri
            } catch (e: Exception) {
                KBLog.ui.error("hero read failed: ${e.message}", TAG)
                _uiState.value = _uiState.value.copy(errorMessage = appContext.getString(R.string.home_vm_error_read_image))
            }
        }
    }

    fun onHeroCropCancelled() {
        _pendingHeroUri.value = null
        pendingHeroBytes = null
    }

    fun onHeroCropSaved(uri: Uri, crop: HeroCrop, context: Context) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return

        val bytes = pendingHeroBytes ?: run {
            KBLog.ui.error("onHeroCropSaved: no pending bytes", TAG)
            _uiState.value = _uiState.value.copy(errorMessage = appContext.getString(R.string.home_vm_error_image_unavailable))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isUploadingHero = true, errorMessage = null)
            try {
                KBLog.ui.debug("hero upload start familyId=$familyId bytes=${bytes.size}", TAG)
                val remoteUrl = heroPhotoService.setHeroPhoto(familyId, bytes, crop)
                KBLog.ui.debug("hero upload OK familyId=$familyId", TAG)

                val cacheFile = saveToLocalCache(familyId, bytes, context)

                familyDao.getById(familyId)?.let { family ->
                    familyDao.upsert(
                        family.copy(
                            heroPhotoURL = remoteUrl,
                            heroPhotoLocalPath = cacheFile.absolutePath,
                            heroPhotoUpdatedAtEpochMillis = System.currentTimeMillis(),
                            heroPhotoScale = crop.scale.toDouble(),
                            heroPhotoOffsetX = crop.offsetX.toDouble(),
                            heroPhotoOffsetY = crop.offsetY.toDouble(),
                        ),
                    )
                }

                pendingHeroBytes = null
                _pendingHeroUri.value = null
                _uiState.value = _uiState.value.copy(isUploadingHero = false)

            } catch (e: Exception) {
                KBLog.ui.error("hero upload failed: ${e.message}", TAG, e)
                _uiState.value = _uiState.value.copy(
                    isUploadingHero = false,
                    errorMessage = e.localizedMessage ?: appContext.getString(R.string.home_vm_error_upload_photo),
                )
            }
        }
    }


    // ── Download hero da URL remota → cache locale ────────────────────────────
    // USA Firebase Storage SDK (non URL.readBytes()) così rispetta le auth rules.
    // L'URL in Firestore è del tipo:
    //   https://firebasestorage.googleapis.com/v0/b/.../o/families%2F...%2Fhero%2Fhero.jpg?alt=media&token=...
    // Firebase Storage SDK riconosce il path e aggiunge l'auth header automaticamente.

    private suspend fun downloadHeroToCache(
        familyId: String,
        url: String,
    ): HeroDownloadOutcome {
        if (familyId.isBlank() || url.isBlank()) return HeroDownloadOutcome.Failed
        return try {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                KBLog.ui.warning("downloadHeroToCache: not authenticated yet, skip", TAG)
                return HeroDownloadOutcome.Failed
            }
            KBLog.ui.debug("downloadHeroToCache start familyId=$familyId uid=$uid", TAG)
            val ref = FirebaseStorage.getInstance()
                .reference.child("families/$familyId/hero/hero.jpg")
            val bytes: ByteArray = ref.getBytes(5 * 1024 * 1024).await()
            val file = saveToLocalCache(familyId, bytes, appContext)
            familyDao.getById(familyId)?.let { family ->
                familyDao.upsert(family.copy(heroPhotoLocalPath = file.absolutePath))
            }
            KBLog.ui.debug("hero cached OK familyId=$familyId bytes=${bytes.size}", TAG)
            HeroDownloadOutcome.Ok(file.absolutePath)
        } catch (e: Exception) {
            val storageErr = e as? StorageException ?: (e.cause as? StorageException)
            if (storageErr?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                KBLog.ui.debug("downloadHeroToCache: nessun hero su Storage familyId=$familyId", TAG)
                HeroDownloadOutcome.Absent
            } else {
                KBLog.ui.warning("hero download failed familyId=$familyId: ${e.message}", TAG)
                HeroDownloadOutcome.Failed
            }
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    fun toggleFab() {
        _uiState.value = _uiState.value.copy(isFabExpanded = !_uiState.value.isFabExpanded)
    }

    fun closeFab() {
        _uiState.value = _uiState.value.copy(isFabExpanded = false)
    }

    fun recordQuickAction(action: HomeQuickAction) {
        val key = "usage_${action.key}"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        _uiState.value = _uiState.value.copy(topQuickActions = topQuickActions())
    }

    /** Registra l'apertura di una categoria Home (per feature-id) — alimenta le Scorciatoie
     *  su TUTTE le voci, non solo il set limitato di [HomeQuickAction]. */
    fun recordFeatureUsage(featureId: String) {
        val key = "feat_usage_$featureId"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        _uiState.value = _uiState.value.copy(featureUsage = featureUsage())
    }

    private fun featureUsage(): Map<String, Int> =
        prefs.all.entries
            .filter { it.key.startsWith("feat_usage_") && it.value is Int }
            .associate { it.key.removePrefix("feat_usage_") to (it.value as Int) }

    fun saveFeatureOrder(order: List<String>) {
        val normalized = order
            .filter { it in defaultFeatureOrder() }
            .distinct()
            .toMutableList()
        defaultFeatureOrder().forEach { if (it !in normalized) normalized.add(it) }
        prefs.edit().putString(featureOrderKey, normalized.joinToString(",")).apply()
        _uiState.value = _uiState.value.copy(featureOrder = normalized)
    }

    fun onFeatureOpened(counterField: CounterField?) {
        val familyId = _uiState.value.familyId
        if (counterField == null || familyId.isBlank()) return
        homeBadgeManager.clearLocal(counterField)
        viewModelScope.launch {
            runCatching { homeBadgeManager.resetRemote(familyId, counterField) }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            membershipSyncService.stop()
            logoutUseCase.logout()
            onComplete()
        }
    }

    /**
     * Avvia [MembershipSyncService] (listener real-time su `users/{uid}/memberships`)
     * non appena `HomeViewModel` viene creato. Garantisce che le famiglie create
     * su un altro device (es. nuova famiglia creata su iOS) compaiano in Room
     * entro pochi secondi senza dover ri-loggare o riavviare l'app.
     */
    private fun startMembershipListenerIfAuthenticated() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isEmpty()) {
            KBLog.ui.debug("startMembershipListener skipped (no uid)", TAG)
            return
        }
        membershipSyncService.start(uid)
    }

    private fun compressJpeg(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error(appContext.getString(R.string.home_vm_error_image_unreadable))
        val bitmap = input.use { BitmapFactory.decodeStream(it) } ?: error(appContext.getString(R.string.home_vm_error_image_invalid))
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    private fun saveToLocalCache(familyId: String, bytes: ByteArray, context: Context): File {
        val dir = File(context.cacheDir, "KBPhotos").apply { mkdirs() }
        val file = File(dir, "hero_$familyId.jpg")
        file.writeBytes(bytes)
        return file
    }

    private fun topQuickActions(): List<HomeQuickAction> =
        HomeQuickAction.entries
            .sortedByDescending { prefs.getInt("usage_${it.key}", 0) }
            .take(4)

    private fun loadFeatureOrder(): List<String> {
        val raw = prefs.getString(featureOrderKey, null).orEmpty()
        if (raw.isBlank()) return defaultFeatureOrder()
        val parsed = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val normalized = parsed
            .filter { it in defaultFeatureOrder() }
            .distinct()
            .toMutableList()
        defaultFeatureOrder().forEach { if (it !in normalized) normalized.add(it) }
        return normalized
    }

    private fun defaultFeatureOrder(): List<String> = listOf(
        "notes",
        "todo",
        "shopping",
        "calendar",
        "health",
        "chat",
        "expenses",
        "documents",
        "wallet",
        "location",
        "photos",
        "pets",
        "home_items",
        "vehicles",
        "ai",
        "family",
    )

    private fun todayLabel(): String {
        val formatter = java.text.SimpleDateFormat("EEEE, d MMMM", it.vittorioscocca.kidbox.util.KBLocale.current())
        return formatter.format(java.util.Date())
    }

    fun reset() {
        familySessionPreferences.markSkipHomeBootstrapOnce()
        _uiState.value = HomeUiState(isLoading = false)
    }

    override fun onCleared() {
        cancelMembersSyncTimeout()
        homeBadgeManager.stopListening()
        walletRepository.stopRealtime()
        passwordsRepository.stopRealtime()
        super.onCleared()
    }
}