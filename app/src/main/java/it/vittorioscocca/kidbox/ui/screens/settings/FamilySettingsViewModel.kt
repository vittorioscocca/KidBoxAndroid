package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.remote.family.FamilyLeaveService
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class FamilySettingsUiState(
    val isLoading: Boolean = true,
    val family: KBFamilyEntity? = null,
    val members: List<KBFamilyMemberEntity> = emptyList(),
    val children: List<KBChildEntity> = emptyList(),
    val isOwner: Boolean = false,
    val currentUid: String = "",
    val error: String? = null,
)

data class ChildInput(
    val id: String,
    val name: String,
    val birthDateEpochMillis: Long?,
)

@HiltViewModel
class FamilySettingsViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val childDao: KBChildDao,
    private val leaveService: FamilyLeaveService,
) : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val inviteRemoteStore = InviteRemoteStore()
    private val _uiState = MutableStateFlow(FamilySettingsUiState())
    val uiState: StateFlow<FamilySettingsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val family = familyDao.observeAll().first().firstOrNull()
            if (family == null) {
                _uiState.value = FamilySettingsUiState(isLoading = false, currentUid = uid)
                return@launch
            }
            val members = familyMemberDao.observeActiveByFamilyId(family.id).first()
            val children = childDao.observeByFamilyId(family.id).first()
            val owner = members.any { it.userId == uid && it.role.equals("owner", true) }
            _uiState.value = FamilySettingsUiState(
                isLoading = false,
                family = family,
                members = members,
                children = children,
                isOwner = owner,
                currentUid = uid,
            )
        }
    }

    fun removeMember(member: KBFamilyMemberEntity) {
        val familyId = _uiState.value.family?.id ?: return
        viewModelScope.launch {
            try {
                db.collection("families")
                    .document(familyId)
                    .collection("members")
                    .document(member.userId)
                    .set(
                        mapOf(
                            "isDeleted" to true,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge(),
                    ).await()
                familyMemberDao.deleteById(member.id)
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun leaveFamily(onDone: () -> Unit) {
        val familyId = _uiState.value.family?.id ?: return
        viewModelScope.launch {
            try {
                leaveService.leaveFamily(familyId)
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun joinWithCode(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val familyId = inviteRemoteStore.resolveInvite(code.trim())
                inviteRemoteStore.addMember(familyId)
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun saveFamilyName(name: String, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val updated = family.copy(name = name.trim(), updatedAtEpochMillis = System.currentTimeMillis())
                familyDao.upsert(updated)
                db.collection("families").document(family.id)
                    .set(
                        mapOf(
                            "name" to updated.name,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge(),
                    ).await()
                load()
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun saveFamilyWithChildren(
        newName: String,
        childrenInputs: List<ChildInput>,
        onDone: () -> Unit,
    ) {
        val family = _uiState.value.family ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return

        viewModelScope.launch {
            try {
                // a) Update Room family name
                val now = System.currentTimeMillis()
                val updatedFamily = family.copy(name = trimmedName, updatedAtEpochMillis = now)
                familyDao.upsert(updatedFamily)

                // b) Update Firestore family name
                db.collection("families")
                    .document(family.id)
                    .update(
                        "name", trimmedName,
                        "updatedAt", FieldValue.serverTimestamp(),
                    ).await()

                // c) Upsert children (new + existing) in Room + Firestore
                val existingIds = _uiState.value.children.map { it.id }.toSet()
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

                childrenInputs
                    .filter { it.name.isNotBlank() }
                    .forEach { input ->
                        if (input.id in existingIds) {
                            val existing = childDao.getById(input.id)
                            if (existing != null) {
                                val updatedChild = existing.copy(
                                    name = input.name.trim(),
                                    birthDateEpochMillis = input.birthDateEpochMillis,
                                    updatedBy = uid,
                                    updatedAtEpochMillis = now,
                                )
                                childDao.upsert(updatedChild)
                                db.collection("families")
                                    .document(family.id)
                                    .collection("children")
                                    .document(updatedChild.id)
                                    .set(
                                        mapOf(
                                            "name" to updatedChild.name,
                                            "birthDateMillis" to updatedChild.birthDateEpochMillis,
                                            "updatedAt" to FieldValue.serverTimestamp(),
                                        ),
                                        com.google.firebase.firestore.SetOptions.merge(),
                                    ).await()
                            }
                        } else {
                            val newChild = KBChildEntity(
                                id = input.id,
                                familyId = family.id,
                                name = input.name.trim(),
                                birthDateEpochMillis = input.birthDateEpochMillis,
                                weightKg = null,
                                heightCm = null,
                                createdBy = uid,
                                createdAtEpochMillis = now,
                                updatedBy = uid,
                                updatedAtEpochMillis = now,
                            )
                            childDao.upsert(newChild)
                            db.collection("families")
                                .document(family.id)
                                .collection("children")
                                .document(newChild.id)
                                .set(
                                    mapOf(
                                        "name" to newChild.name,
                                        "birthDateMillis" to newChild.birthDateEpochMillis,
                                        "isDeleted" to false,
                                        "updatedAt" to FieldValue.serverTimestamp(),
                                    ),
                                    com.google.firebase.firestore.SetOptions.merge(),
                                ).await()
                        }
                    }

                load()
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Errore salvataggio")
            }
        }
    }

    fun addChild(name: String, birthEpochMillis: Long?) {
        val family = _uiState.value.family ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val id = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val child = KBChildEntity(
                id = id,
                familyId = family.id,
                name = name.trim(),
                birthDateEpochMillis = birthEpochMillis,
                weightKg = null,
                heightCm = null,
                createdBy = uid,
                createdAtEpochMillis = now,
                updatedBy = uid,
                updatedAtEpochMillis = now,
            )
            childDao.upsert(child)
            db.collection("families").document(family.id).collection("children").document(id)
                .set(
                    mapOf(
                        "name" to child.name,
                        "birthDateMillis" to birthEpochMillis,
                        "isDeleted" to false,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
            load()
        }
    }

    fun saveChild(childId: String, name: String, birthEpochMillis: Long?, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        viewModelScope.launch {
            val existing = childDao.getById(childId) ?: return@launch
            val updated = existing.copy(
                name = name.trim(),
                birthDateEpochMillis = birthEpochMillis,
                updatedBy = FirebaseAuth.getInstance().currentUser?.uid,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            childDao.upsert(updated)
            db.collection("families").document(family.id).collection("children").document(childId)
                .set(
                    mapOf(
                        "name" to updated.name,
                        "birthDateMillis" to updated.birthDateEpochMillis,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
            load()
            onDone()
        }
    }

    fun deleteChild(childId: String, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        viewModelScope.launch {
            childDao.deleteById(childId)
            db.collection("families").document(family.id).collection("children").document(childId).delete().await()
            load()
            onDone()
        }
    }
}
