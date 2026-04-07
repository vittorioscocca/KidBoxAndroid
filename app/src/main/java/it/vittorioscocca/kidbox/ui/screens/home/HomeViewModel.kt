package it.vittorioscocca.kidbox.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.remote.family.FamilyHeroPhotoService
import it.vittorioscocca.kidbox.domain.auth.LogoutUseCase
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val familyId: String = "",
    val familyName: String = "",
    val heroPhotoUrl: String? = null,
    val heroPhotoLocalPath: String? = null,
    val isUploadingHero: Boolean = false,
    val errorMessage: String? = null,
    val memberCount: Int = 0,
    val todayLabel: String = "",
    val avatarUrl: String? = null,
    val isFabExpanded: Boolean = false,
    val topQuickActions: List<HomeQuickAction> = emptyList(),
)

enum class HomeQuickAction(
    val key: String,
    val label: String,
) {
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
    private var hasLoaded = false

    fun onScreenVisible() {
        if (hasLoaded) return
        hasLoaded = true
        viewModelScope.launch {
            loadHomeData()
        }
    }

    fun toggleFab() {
        _uiState.value = _uiState.value.copy(isFabExpanded = !_uiState.value.isFabExpanded)
    }

    fun closeFab() {
        _uiState.value = _uiState.value.copy(isFabExpanded = false)
    }

    fun recordQuickAction(action: HomeQuickAction) {
        val key = "usage_${action.key}"
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
        _uiState.value = _uiState.value.copy(topQuickActions = topQuickActions())
    }

    fun onHeroPhotoSelected(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value
            if (current.familyId.isBlank()) return@launch

            _uiState.value = current.copy(isUploadingHero = true, errorMessage = null)
            try {
                val compressed = compressJpeg(context, uri)
                val remoteUrl = heroPhotoService.setHeroPhoto(current.familyId, compressed)
                val cacheFile = saveToLocalCache(current.familyId, compressed, context)

                val family = familyDao.getById(current.familyId)
                if (family != null) {
                    familyDao.upsert(
                        family.copy(
                            heroPhotoURL = remoteUrl,
                            heroPhotoLocalPath = cacheFile.absolutePath,
                            heroPhotoUpdatedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isUploadingHero = false,
                    heroPhotoUrl = remoteUrl,
                    heroPhotoLocalPath = cacheFile.absolutePath,
                    errorMessage = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingHero = false,
                    errorMessage = e.localizedMessage ?: "Errore upload foto",
                )
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase.logout()
            onComplete()
        }
    }

    private suspend fun loadHomeData() {
        val family = familyDao.observeAll().first().firstOrNull()
        val familyId = family?.id.orEmpty()
        val membersCount = if (familyId.isNotBlank()) {
            familyMemberDao.observeActiveByFamilyId(familyId).first().size
        } else {
            0
        }

        _uiState.value = HomeUiState(
            isLoading = false,
            familyId = familyId,
            familyName = family?.name.orEmpty(),
            heroPhotoUrl = family?.heroPhotoURL,
            heroPhotoLocalPath = family?.heroPhotoLocalPath,
            memberCount = membersCount,
            todayLabel = todayLabel(),
            avatarUrl = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString(),
            isFabExpanded = false,
            topQuickActions = topQuickActions(),
        )
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

    private fun topQuickActions(): List<HomeQuickAction> {
        return HomeQuickAction.entries
            .sortedByDescending { prefs.getInt("usage_${it.key}", 0) }
            .take(4)
    }

    private fun todayLabel(): String {
        val formatter = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
        return formatter.format(java.util.Date())
    }
}
