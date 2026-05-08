package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsSubscriptionViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val subscriptionRepository: SubscriptionRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {
    private val _plan = MutableStateFlow(KBPlan.FREE)
    val plan: StateFlow<KBPlan> = _plan.asStateFlow()

    private val _isFamilyOwner = MutableStateFlow(false)
    val isFamilyOwner: StateFlow<Boolean> = _isFamilyOwner.asStateFlow()

    init {
        viewModelScope.launch {
            val familyId = familyDao.peekAnyFamilyId().orEmpty()
            if (familyId.isBlank()) return@launch
            _plan.value = subscriptionRepository.getPlan(familyId)
            _isFamilyOwner.value = familyDao.getById(familyId)?.createdBy == auth.currentUser?.uid
        }
    }
}
