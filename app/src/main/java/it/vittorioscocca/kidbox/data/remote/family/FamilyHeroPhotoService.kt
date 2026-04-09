package it.vittorioscocca.kidbox.data.remote.family

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import it.vittorioscocca.kidbox.ui.screens.home.HeroCrop
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FamilyHeroPhotoService"

/**
 * Allineato a iOS FamilyHeroPhotoService.
 * Scrive su Firestore: heroPhotoURL + heroPhotoUpdatedAt + heroPhotoScale/OffsetX/OffsetY
 */
@Singleton
class FamilyHeroPhotoService @Inject constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Upload JPEG su Storage al path stabile `families/{familyId}/hero/hero.jpg`,
     * poi scrive URL + crop + timestamps su Firestore.
     * Identico a iOS setHeroPhoto(familyId:imageData:crop:).
     */
    suspend fun setHeroPhoto(
        familyId: String,
        imageData: ByteArray,
        crop: HeroCrop = HeroCrop(),
    ): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
        require(imageData.isNotEmpty()) { "Invalid image data" }

        val path = "families/$familyId/hero/hero.jpg"
        val ref = storage.reference.child(path)
        Log.d(TAG, "hero upload start familyId=$familyId bytes=${imageData.size}")

        val metadata = storageMetadata { contentType = "image/jpeg" }
        Log.d(TAG, "hero upload attempting path=$path uid=${auth.currentUser?.uid} token=${auth.currentUser?.getIdToken(false)?.result?.token?.take(20)}")
        ref.putBytes(imageData, metadata).await()
        val url = ref.downloadUrl.await().toString()
        Log.d(TAG, "hero upload OK familyId=$familyId url=$url")

        // Scrivi su Firestore — identico a iOS
        db.collection("families").document(familyId).set(
            mapOf(
                "heroPhotoURL" to url,
                "heroPhotoUpdatedAt" to FieldValue.serverTimestamp(),
                "heroPhotoScale" to crop.scale.toDouble(),
                "heroPhotoOffsetX" to crop.offsetX.toDouble(),
                "heroPhotoOffsetY" to crop.offsetY.toDouble(),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        Log.d(TAG, "hero Firestore updated familyId=$familyId")
        return url
    }

    /**
     * Aggiorna solo i parametri di crop senza re-uploadare l'immagine.
     * Identico a iOS updateHeroCrop(familyId:crop:).
     */
    suspend fun updateHeroCrop(familyId: String, crop: HeroCrop) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("families").document(familyId).set(
            mapOf(
                "heroPhotoScale" to crop.scale.toDouble(),
                "heroPhotoOffsetX" to crop.offsetX.toDouble(),
                "heroPhotoOffsetY" to crop.offsetY.toDouble(),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}