package it.vittorioscocca.kidbox.ui.screens.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.remote.family.FamilyHeroPhotoService
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import it.vittorioscocca.kidbox.domain.auth.LogoutUseCase
import it.vittorioscocca.kidbox.ui.screens.home.HeroCrop
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "HomeViewModel"

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
)

enum class HomeQuickAction(val key: String, val label: String) {
    EXPENSE("expense", "Spesa"),
    EVENT("event", "Evento"),
    TODO("todo", "To-Do"),
    NOTE("note", "Nota"),
    SHOPPING_LIST("shopping", "Lista spesa"),
    MESSAGE("message", "Messaggio"),
    HEALTH("health", "Salute"),
    DOCUMENTS("documents", "Documenti"),
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val heroPhotoService: FamilyHeroPhotoService,
    private val familySyncCenter: FamilySyncCenter,
    private val familySessionPreferences: FamilySessionPreferences,
    private val homeBadgeManager: HomeBadgeManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val db get() = FirebaseFirestore.getInstance()
    private val prefs = appContext.getSharedPreferences("home_quick_actions", Context.MODE_PRIVATE)
    private val featureOrderKey = "feature_order_v1"
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ── Reactive observation — avviato una volta sola, sempre attivo ──────────
    // FIX principale: invece di one-shot con hasLoaded, osserviamo Room con un
    // Flow combine. Ogni volta che FamilySyncCenter aggiorna la famiglia in Room
    // (es. heroPhotoURL arriva da un altro device via Firestore), la UI si aggiorna.
    init {
        observeHomeData()
        observeBadges()
        viewModelScope.launch { refreshAvatarUrl() }
        viewModelScope.launch {
            familySyncCenter.accessLostEvent.collect {
                _uiState.value = HomeUiState(isLoading = false, familyId = "")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        "Sei stato rimosso dalla famiglia",
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
                val family = families.firstOrNull()
                val familyId = family?.id.orEmpty()

                if (familyId.isBlank()) {
                    homeBadgeManager.stopListening()
                    if (familySessionPreferences.consumeSkipHomeBootstrapOnce()) {
                        Log.i(TAG, "observeHomeData: skip Firestore bootstrap (leave / access revoked)")
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
                            Log.w(TAG, "HomeViewModel bootstrap failed: ${e.message}")
                        }
                    }
                    _uiState.value = HomeUiState(isLoading = false, familyId = "")
                    return@collectLatest
                }

                combine(
                    familyDao.observeAll(),
                    familyMemberDao.observeActiveByFamilyId(familyId),
                ) { fams, members ->
                    Pair(fams.firstOrNull(), members.size)
                }.collect { (fam, memberCount) ->
                    homeBadgeManager.startListening(familyId)
                    val remoteUrl = fam?.heroPhotoURL

                    // File locale se esiste già su disco
                    val localPath = fam?.heroPhotoLocalPath?.takeIf { File(it).exists() }

                    // Se non abbiamo il file locale ma abbiamo l'URL, scarica in background
                    // Nel frattempo Coil usa l'URL remoto direttamente
                    if (localPath == null && remoteUrl != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val path = downloadHeroToCache(fam!!.id, remoteUrl)
                            if (path != null) {
                                _uiState.value = _uiState.value.copy(heroPhotoLocalPath = path)
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
                        todayLabel = todayLabel(),
                        avatarUrl = _uiState.value.avatarUrl
                            ?: com.google.firebase.auth.FirebaseAuth.getInstance()
                                .currentUser?.photoUrl?.toString(),
                        topQuickActions = topQuickActions(),
                        featureOrder = loadFeatureOrder(),
                    )
                }
            }
        }
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
                )
            }
        }
    }

    fun onScreenVisible() {
        viewModelScope.launch { refreshAvatarUrl() }
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
            Log.i(TAG, "bootstrapFromFirestore start uid=$uid")
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
                Log.w(TAG, "bootstrapFromFirestore: memberships empty/incoerenti, fallback members collectionGroup")
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
                Log.w(TAG, "bootstrapFromFirestore: memberships empty uid=$uid")
                _uiState.value = HomeUiState(isLoading = false, familyId = "")
                return
            }

            var selectedFamilyId: String? = null
            var selectedFamilyData: Map<String, Any> = emptyMap()
            for (candidateId in distinctCandidates) {
                try {
                    val myMemberDoc = db.collection("families")
                        .document(candidateId)
                        .collection("members")
                        .document(uid)
                        .get()
                        .await()
                    if (!myMemberDoc.exists() || myMemberDoc.data?.get("isDeleted") as? Boolean == true) {
                        Log.w(TAG, "bootstrapFromFirestore: skip familyId=$candidateId (member missing/deleted)")
                        continue
                    }
                    val familySnap = db.collection("families").document(candidateId).get().await()
                    if (!familySnap.exists()) {
                        Log.w(TAG, "bootstrapFromFirestore: skip familyId=$candidateId (family missing)")
                        continue
                    }
                    val familyData = familySnap.data.orEmpty()
                    if (familyData["isDeleted"] as? Boolean == true) {
                        Log.w(TAG, "bootstrapFromFirestore: skip familyId=$candidateId (family deleted)")
                        continue
                    }
                    selectedFamilyId = candidateId
                    selectedFamilyData = familyData
                    break
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.w(TAG, "bootstrapFromFirestore: skip familyId=$candidateId (PERMISSION_DENIED)")
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
            Log.i(TAG, "bootstrapFromFirestore: selected familyId=$familyId from candidates=${distinctCandidates.size}")
            val familyData = selectedFamilyData
            val now = System.currentTimeMillis()
            val createdBy = familyData["ownerUid"] as? String ?: uid
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
            Log.i(TAG, "bootstrapFromFirestore: family upserted familyId=$familyId")

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
                        ?: "Membro"
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
            Log.i(TAG, "bootstrapFromFirestore: members upserted count=${memberDocs.size} familyId=$familyId")
        } catch (e: Exception) {
            Log.w(TAG, "HomeViewModel bootstrap failed: ${e.message}")
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
                errorMessage = "Crea prima una famiglia per caricare una foto",
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
                Log.e(TAG, "hero read failed: ${e.message}")
                _uiState.value = _uiState.value.copy(errorMessage = "Impossibile leggere l'immagine")
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
            Log.e(TAG, "onHeroCropSaved: no pending bytes")
            _uiState.value = _uiState.value.copy(errorMessage = "Immagine non disponibile, riprova")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isUploadingHero = true, errorMessage = null)
            try {
                Log.d(TAG, "hero upload start familyId=$familyId bytes=${bytes.size}")
                val remoteUrl = heroPhotoService.setHeroPhoto(familyId, bytes, crop)
                Log.d(TAG, "hero upload OK familyId=$familyId")

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
                Log.e(TAG, "hero upload failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isUploadingHero = false,
                    errorMessage = e.localizedMessage ?: "Errore upload foto",
                )
            }
        }
    }


    // ── Download hero da URL remota → cache locale ────────────────────────────
    // USA Firebase Storage SDK (non URL.readBytes()) così rispetta le auth rules.
    // L'URL in Firestore è del tipo:
    //   https://firebasestorage.googleapis.com/v0/b/.../o/families%2F...%2Fhero%2Fhero.jpg?alt=media&token=...
    // Firebase Storage SDK riconosce il path e aggiunge l'auth header automaticamente.

    private suspend fun downloadHeroToCache(familyId: String, url: String): String? {
        if (familyId.isBlank() || url.isBlank()) return null
        return try {
            // Aspetta che Firebase Auth abbia un utente autenticato
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Log.w(TAG, "downloadHeroToCache: not authenticated yet, skip")
                return null
            }
            Log.d(TAG, "downloadHeroToCache start familyId=$familyId uid=$uid")
            val ref = com.google.firebase.storage.FirebaseStorage.getInstance()
                .reference.child("families/$familyId/hero/hero.jpg")
            val bytes: ByteArray = ref.getBytes(5 * 1024 * 1024).await()
            val file = saveToLocalCache(familyId, bytes, appContext)
            familyDao.getById(familyId)?.let { family ->
                familyDao.upsert(family.copy(heroPhotoLocalPath = file.absolutePath))
            }
            Log.d(TAG, "hero cached OK familyId=$familyId bytes=${bytes.size}")
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "hero download failed familyId=$familyId: ${e.message}")
            null
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
            logoutUseCase.logout()
            onComplete()
        }
    }

    private fun compressJpeg(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Immagine non leggibile")
        val bitmap = input.use { BitmapFactory.decodeStream(it) } ?: error("Immagine non valida")
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
        "location",
        "photos",
        "ai",
        "family",
    )

    private fun todayLabel(): String {
        val formatter = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
        return formatter.format(java.util.Date())
    }

    fun reset() {
        familySessionPreferences.markSkipHomeBootstrapOnce()
        _uiState.value = HomeUiState(isLoading = false)
    }

    override fun onCleared() {
        homeBadgeManager.stopListening()
        super.onCleared()
    }
}