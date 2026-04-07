package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FamilyHeroPhotoService @Inject constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun setHeroPhoto(
        familyId: String,
        imageData: ByteArray,
    ): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("Not authenticated")

        require(imageData.isNotEmpty()) { "Invalid image data" }

        val path = "families/$familyId/hero/hero.jpg"
        val ref = storage.reference.child(path)

        val metadata = storageMetadata { contentType = "image/jpeg" }
        ref.putBytes(imageData, metadata).await()
        val url = ref.downloadUrl.await().toString()

        db.collection("families").document(familyId).set(
            mapOf(
                "heroPhotoURL" to url,
                "heroPhotoUpdatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        return url
    }
}
