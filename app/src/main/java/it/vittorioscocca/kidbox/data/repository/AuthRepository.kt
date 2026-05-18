package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {
    val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid
}
