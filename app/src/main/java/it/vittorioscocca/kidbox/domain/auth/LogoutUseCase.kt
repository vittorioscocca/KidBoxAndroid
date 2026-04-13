package it.vittorioscocca.kidbox.domain.auth

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogoutUseCase @Inject constructor(
    private val database: KidBoxDatabase,
    private val familySessionPreferences: FamilySessionPreferences,
    private val onboardingPreferences: OnboardingPreferences,
) {
    /**
     * Logout standard (allineato iOS): sign-out + wipe locale,
     * per evitare dati sporchi quando si entra con un altro account.
     * I dati vengono poi ricaricati da Firebase al login successivo.
     */
    suspend fun logout() {
        FirebaseAuth.getInstance().signOut()
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        familySessionPreferences.clearActiveFamilyId()
    }

    /**
     * Logout distruttivo completo: usato per eliminazione account / reset forzato.
     */
    suspend fun logoutAndWipeLocalData() {
        logout()
        onboardingPreferences.reset()
    }
}
