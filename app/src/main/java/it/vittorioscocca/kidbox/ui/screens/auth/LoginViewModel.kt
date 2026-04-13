package it.vittorioscocca.kidbox.ui.screens.auth

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.data.user.UserProfileRepository
import it.vittorioscocca.kidbox.data.remote.auth.AuthError
import it.vittorioscocca.kidbox.data.remote.auth.AuthFacade
import it.vittorioscocca.kidbox.data.remote.auth.AuthPresentation
import it.vittorioscocca.kidbox.data.remote.auth.AuthProvider
import it.vittorioscocca.kidbox.data.remote.auth.EmailAuthService
import it.vittorioscocca.kidbox.data.remote.auth.FacebookAuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthFacade,
    private val facebookAuth: FacebookAuthService,
    private val emailAuth: EmailAuthService,
    private val onboardingPreferences: OnboardingPreferences,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    sealed class AuthCheckState {
        data object Checking : AuthCheckState()
        data object NotAuthenticated : AuthCheckState()
        data class Authenticated(val hasFamily: Boolean) : AuthCheckState()
    }

    private val _authCheckState = MutableStateFlow<AuthCheckState>(AuthCheckState.Checking)
    val authCheckState: StateFlow<AuthCheckState> = _authCheckState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _resetPasswordSent = MutableStateFlow(false)
    val resetPasswordSent: StateFlow<Boolean> = _resetPasswordSent.asStateFlow()

    private val _registrationPendingVerification = MutableStateFlow(false)
    val registrationPendingVerification: StateFlow<Boolean> =
        _registrationPendingVerification.asStateFlow()

    init {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                userProfileRepository.ensureSeededFromAuth()
                resetFirestoreClientAfterAuthChange()
            }
            val hasFamily = if (user != null) checkHasFamily() else false
            val hasOnboarding = onboardingPreferences.hasSeenOnboarding()

            Log.d(
                "KidBoxDebug",
                "user=${user?.uid} hasFamily=$hasFamily hasOnboarding=$hasOnboarding",
            )

            _authCheckState.value = if (user == null) {
                AuthCheckState.NotAuthenticated
            } else {
                AuthCheckState.Authenticated(hasFamily)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun signInGoogle(activity: ComponentActivity) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                auth.signIn(
                    AuthProvider.GOOGLE,
                    AuthPresentation.ActivityContext(activity),
                )
                onSignedInSuccessfully()
            } catch (e: Exception) {
                if (e is AuthError.Cancelled) return@launch
                _errorMessage.value = friendlyError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun signInFacebook(activity: ComponentActivity) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                facebookAuth.signInWithFacebook(activity)
                onSignedInSuccessfully()
            } catch (e: Exception) {
                if (e is AuthError.Cancelled) return@launch
                _errorMessage.value = friendlyError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun signInEmail(email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                emailAuth.signInWithEmail(email, password)
                onSignedInSuccessfully()
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun registerEmail(email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            _registrationPendingVerification.value = false
            try {
                emailAuth.registerEmail(email, password)
                _registrationPendingVerification.value = true
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) return
        viewModelScope.launch {
            _resetPasswordSent.value = false
            _errorMessage.value = null
            try {
                emailAuth.sendPasswordReset(email)
                _resetPasswordSent.value = true
            } catch (e: Exception) {
                _errorMessage.value = friendlyError(e)
            }
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // log only — come su iOS non esponiamo errore UI per signOut
        }
    }

    private suspend fun onSignedInSuccessfully() {
        if (FirebaseAuth.getInstance().currentUser != null) {
            userProfileRepository.ensureSeededFromAuth()
            resetFirestoreClientAfterAuthChange()
            _authCheckState.value =
                AuthCheckState.Authenticated(checkHasFamily())
        }
    }

    /**
     * Dopo login/logout con wipe locale, forza nuovo token e resetta il client Firestore
     * per evitare PERMISSION_DENIED transitori dovuti a credenziali/cache stale.
     */
    private suspend fun resetFirestoreClientAfterAuthChange() {
        try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
            delay(400)
            FirebaseFirestore.getInstance().terminate().await()
            FirebaseFirestore.getInstance().clearPersistence().await()
            delay(250)
            Log.d("KidBoxDebug", "resetFirestoreClientAfterAuthChange: OK")
        } catch (e: Exception) {
            Log.w("KidBoxDebug", "resetFirestoreClientAfterAuthChange: ${e.message}")
        }
    }

    private suspend fun checkHasFamily(): Boolean {
        return try {
            checkHasFamilyOnce()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.e("KidBoxDebug", "checkHasFamily firestore error: ${e.message}")
                return false
            }
            Log.w("KidBoxDebug", "checkHasFamily: PERMISSION_DENIED, reset+retry")
            resetFirestoreClientAfterAuthChange()
            runCatching { checkHasFamilyOnce() }
                .onFailure { t -> Log.e("KidBoxDebug", "checkHasFamily retry failed: ${t.message}") }
                .getOrDefault(false)
        } catch (e: Exception) {
            Log.e("KidBoxDebug", "checkHasFamily error: ${e.message}")
            false
        }
    }

    private suspend fun checkHasFamilyOnce(): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val membershipsSnap = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("memberships")
            .get(Source.SERVER)
            .await()
        val candidateFamilyIds = buildList {
            membershipsSnap.documents.forEach { doc ->
                if (doc.id.isNotBlank()) add(doc.id)
                (doc.data?.get("familyId") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { add(it) }
            }
        }.distinct()

        // Fallback robusto: se memberships è vuota/incoerente, ricava famiglie da members collectionGroup.
        val resolvedFamilyIds = if (candidateFamilyIds.isNotEmpty()) {
            candidateFamilyIds
        } else {
            Log.w("KidBoxDebug", "checkHasFamily: memberships vuote, fallback collectionGroup(members)")
            val memberDocs = FirebaseFirestore.getInstance()
                .collectionGroup("members")
                .whereEqualTo("uid", uid)
                .get(Source.SERVER)
                .await()
                .documents
            memberDocs
                .filter { it.data?.get("isDeleted") as? Boolean != true }
                .mapNotNull { it.reference.parent.parent?.id }
                .distinct()
        }

        if (resolvedFamilyIds.isEmpty()) {
            Log.d("KidBoxDebug", "checkHasFamily: no family ids on server -> false")
            return false
        }

        // Verifica definitiva: devo avere almeno un member doc valido nella family.
        val hasValidMembership = resolvedFamilyIds.any { familyId ->
            if (familyId.isBlank()) return@any false
            val memberSnap = FirebaseFirestore.getInstance()
                .collection("families")
                .document(familyId)
                .collection("members")
                .document(uid)
                .get(Source.SERVER)
                .await()
            memberSnap.exists() && (memberSnap.data?.get("isDeleted") as? Boolean != true)
        }

        Log.d(
            "KidBoxDebug",
            "checkHasFamily server candidates=${resolvedFamilyIds.size} hasValidMembership=$hasValidMembership",
        )
        return hasValidMembership
    }

    private fun friendlyError(error: Throwable): String {
        val fe = error as? FirebaseAuthException ?: return error.localizedMessage.orEmpty()
        return when (fe.errorCode) {
            ERROR_EMAIL_ALREADY_IN_USE ->
                "Questa email è già registrata. Prova ad accedere."
            ERROR_INVALID_EMAIL -> "Indirizzo email non valido."
            ERROR_WEAK_PASSWORD -> "La password è troppo debole (min. 6 caratteri)."
            ERROR_WRONG_PASSWORD ->
                "Password errata. Riprova o usa \"Password dimenticata\"."
            ERROR_USER_NOT_FOUND -> "Nessun account trovato con questa email."
            ERROR_NETWORK_REQUEST_FAILED -> "Errore di rete. Controlla la connessione."
            ERROR_TOO_MANY_REQUESTS -> "Troppi tentativi. Riprova tra qualche minuto."
            ERROR_USER_DISABLED -> "Account disabilitato. Contatta il supporto."
            else -> fe.localizedMessage ?: error.localizedMessage.orEmpty()
        }
    }

    private companion object {
        private const val ERROR_EMAIL_ALREADY_IN_USE = "ERROR_EMAIL_ALREADY_IN_USE"
        private const val ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"
        private const val ERROR_WEAK_PASSWORD = "ERROR_WEAK_PASSWORD"
        private const val ERROR_WRONG_PASSWORD = "ERROR_WRONG_PASSWORD"
        private const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
        private const val ERROR_NETWORK_REQUEST_FAILED = "ERROR_NETWORK_REQUEST_FAILED"
        private const val ERROR_TOO_MANY_REQUESTS = "ERROR_TOO_MANY_REQUESTS"
        private const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"
    }
}
