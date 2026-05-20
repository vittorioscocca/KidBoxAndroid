package it.vittorioscocca.kidbox.ui.screens.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.health.HealthConnectGateway
import it.vittorioscocca.kidbox.data.health.HealthLinkStore
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.domain.health.HealthAgeFormatting
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class HealthConnectAppState(
    val snapshot: HealthImportSnapshot? = null,
    val isImporting: Boolean = false,
    val healthConnectAvailable: Boolean = true,
    val childWeightKg: Double? = null,
    val ageDescription: String? = null,
    val error: String? = null,
)

@HiltViewModel
class HealthConnectAppViewModel @Inject constructor(
    private val healthGateway: HealthConnectGateway,
    private val healthLinkStore: HealthLinkStore,
    private val childDao: KBChildDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthConnectAppState())
    val uiState: StateFlow<HealthConnectAppState> = _uiState.asStateFlow()

    val healthPermissions: Set<String> get() = healthGateway.requiredPermissions

    private var familyId: String = ""
    private var childId: String = ""
    private val firestore get() = FirebaseFirestore.getInstance()

    fun bind(familyId: String, childId: String) {
        this.familyId = familyId
        this.childId = childId
        viewModelScope.launch { refresh() }
    }

    fun importFromHealthConnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            runCatching { healthGateway.fetchSnapshot() }
                .onSuccess { imported ->
                    healthLinkStore.save(childId, imported)
                    applyToChild(imported)
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(error = err.message ?: "Importazione non riuscita")
                    }
                }
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    private suspend fun refresh() {
        val child = childDao.getById(childId)
        val snapshot = healthLinkStore.load(childId)
        _uiState.update {
            it.copy(
                snapshot = snapshot,
                healthConnectAvailable = healthGateway.isAvailable(),
                childWeightKg = child?.weightKg ?: snapshot?.weightKg,
                ageDescription = snapshot?.ageDescription
                    ?: child?.birthDateEpochMillis?.let(HealthAgeFormatting::ageDescriptionFromBirth),
            )
        }
    }

    private suspend fun applyToChild(imported: HealthImportSnapshot) {
        val base = childDao.getById(childId) ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val now = System.currentTimeMillis()
        val updated = base.copy(
            weightKg = imported.weightKg ?: base.weightKg,
            birthDateEpochMillis = imported.birthDateEpochMillis ?: base.birthDateEpochMillis,
            updatedBy = uid.ifBlank { base.updatedBy },
            updatedAtEpochMillis = now,
        )
        childDao.upsert(updated)
        mergeChildToFirestore(familyId, updated)
    }

    private suspend fun mergeChildToFirestore(familyId: String, e: KBChildEntity) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val data = hashMapOf<String, Any?>(
            "id" to e.id,
            "familyId" to (e.familyId ?: familyId),
            "name" to e.name,
            "weightKg" to e.weightKg,
            "heightCm" to e.heightCm,
            "createdBy" to e.createdBy,
            "isDeleted" to false,
            "updatedBy" to (e.updatedBy ?: uid),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        e.birthDateEpochMillis?.let { millis ->
            data["birthDate"] = com.google.firebase.Timestamp(
                millis / 1000,
                ((millis % 1000) * 1_000_000).toInt(),
            )
        }
        firestore.collection("families").document(familyId)
            .collection("children").document(e.id)
            .set(data, SetOptions.merge())
            .await()
    }

}
