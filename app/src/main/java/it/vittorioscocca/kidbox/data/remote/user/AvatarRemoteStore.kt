package it.vittorioscocca.kidbox.data.remote.user

import com.google.firebase.storage.FirebaseStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class AvatarRemoteStore @Inject constructor() {
    private val storage = FirebaseStorage.getInstance()

    suspend fun uploadAvatar(uid: String, imageData: ByteArray, familyId: String?): String {
        val safeFamily = familyId?.takeIf { it.isNotBlank() }
        val path = if (safeFamily != null) {
            "families/$safeFamily/avatars/$uid.jpg"
        } else {
            "users/$uid/avatar.jpg"
        }
        val ref = storage.reference.child(path)
        ref.putBytes(imageData).await()
        return ref.downloadUrl.await().toString()
    }
}
