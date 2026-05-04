package it.vittorioscocca.kidbox.ui.screens.health

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class HealthSubject(
    val id: String,
    val name: String,
    val isChild: Boolean,
    /** Per adulti: ruolo da Firestore/Room (`owner`, `admin`, …). */
    val memberRole: String? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val birthDateEpochMillis: Long? = null,
)

data class HealthSubjectSelectorState(
    val subjects: List<HealthSubject> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HealthSubjectSelectorViewModel @Inject constructor(
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val familyDao: KBFamilyDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthSubjectSelectorState())
    val uiState: StateFlow<HealthSubjectSelectorState> = _uiState.asStateFlow()

    private val firestore get() = FirebaseFirestore.getInstance()

    fun saveChildWeightKg(familyId: String, childId: String, kg: Double) {
        viewModelScope.launch {
            try {
                persistChildMetric(familyId, childId) { it.copy(weightKg = kg) }
            } catch (e: Exception) {
                Log.e(TAG, "saveChildWeightKg: ${e.message}", e)
            }
        }
    }

    fun saveChildHeightCm(familyId: String, childId: String, cm: Double) {
        viewModelScope.launch {
            try {
                persistChildMetric(familyId, childId) { it.copy(heightCm = cm) }
            } catch (e: Exception) {
                Log.e(TAG, "saveChildHeightCm: ${e.message}", e)
            }
        }
    }

    private suspend fun persistChildMetric(
        familyId: String,
        childId: String,
        transform: (KBChildEntity) -> KBChildEntity,
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val now = System.currentTimeMillis()
        val base = childDao.getById(childId) ?: return
        val updated = transform(base).copy(
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
            /** Stesso documento su tutti i device: orario server evita skew iOS/Android sul LWW. */
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

    fun load(familyId: String) {
        viewModelScope.launch {
            combine(
                childDao.observeByFamilyId(familyId),
                memberDao.observeActiveByFamilyId(familyId),
                familyDao.observeById(familyId),
            ) { children, members, family ->
                val mapped = mutableListOf<HealthSubject>()
                children.forEach { c ->
                    mapped += HealthSubject(
                        id = c.id,
                        name = c.name,
                        isChild = true,
                        memberRole = null,
                        weightKg = c.weightKg,
                        heightCm = c.heightCm,
                        birthDateEpochMillis = c.birthDateEpochMillis,
                    )
                }
                val childUserIds = children.map { it.id }.toSet()
                val ownerUid = family?.createdBy?.takeIf { it.isNotBlank() }
                members
                    .filter { it.userId !in childUserIds }
                    .sortedBy { it.displayName?.lowercase().orEmpty() }
                    .forEach { m ->
                        val name = m.displayName?.takeIf { it.isNotBlank() } ?: "Membro"
                        val roleTrim = m.role.trim()
                        val effectiveRole =
                            when {
                                ownerUid != null && m.userId == ownerUid -> "owner"
                                roleTrim.isNotEmpty() -> roleTrim
                                else -> "member"
                            }
                        mapped += HealthSubject(
                            id = m.userId,
                            name = name,
                            isChild = false,
                            memberRole = effectiveRole,
                            weightKg = null,
                            heightCm = null,
                            birthDateEpochMillis = null,
                        )
                    }
                HealthSubjectSelectorState(subjects = mapped, isLoading = false)
            }.collect { _uiState.value = it }
        }
    }

    private companion object {
        private const val TAG = "HealthSubjectSelectorVM"
    }
}
