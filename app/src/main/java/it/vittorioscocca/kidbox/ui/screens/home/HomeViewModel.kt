package it.vittorioscocca.kidbox.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.remote.family.FamilyHeroPhotoService
import it.vittorioscocca.kidbox.domain.auth.LogoutUseCase
import it.vittorioscocca.kidbox.ui.screens.home.HeroCrop
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("home_quick_actions", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ── Reactive observation — avviato una volta sola, sempre attivo ──────────
    // FIX principale: invece di one-shot con hasLoaded, osserviamo Room con un
    // Flow combine. Ogni volta che FamilySyncCenter aggiorna la famiglia in Room
    // (es. heroPhotoURL arriva da un altro device via Firestore), la UI si aggiorna.
    init {
        observeHomeData()
    }

    private fun observeHomeData() {
        viewModelScope.launch {
            familyDao.observeAll().collectLatest { families ->
                val family = families.firstOrNull()
                val familyId = family?.id.orEmpty()

                if (familyId.isBlank()) {
                    _uiState.value = HomeUiState(isLoading = false)
                    return@collectLatest
                }

                combine(
                    familyDao.observeAll(),
                    familyMemberDao.observeActiveByFamilyId(familyId),
                ) { fams, members ->
                    Pair(fams.firstOrNull(), members.size)
                }.collect { (fam, memberCount) ->
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
                        avatarUrl = com.google.firebase.auth.FirebaseAuth.getInstance()
                            .currentUser?.photoUrl?.toString(),
                        topQuickActions = topQuickActions(),
                    )
                }
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

    private fun todayLabel(): String {
        val formatter = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
        return formatter.format(java.util.Date())
    }
}