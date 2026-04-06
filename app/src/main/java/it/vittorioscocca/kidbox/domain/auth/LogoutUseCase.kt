package it.vittorioscocca.kidbox.domain.auth

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.OnboardingPreferences
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogoutUseCase @Inject constructor(
    private val database: KidBoxDatabase,
    private val onboardingPreferences: OnboardingPreferences,
) {
    suspend fun logout() {
        FirebaseAuth.getInstance().signOut()
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        onboardingPreferences.reset()
    }
}
