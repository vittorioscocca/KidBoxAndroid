package it.vittorioscocca.kidbox.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPhotoAlbumEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private const val TAG_PHOTO_SYNC = "KB_Photo_Sync"

data class RemoteFamilyPhotoDto(
    val id: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val fileSize: Long = 0L,
    val storagePath: String = "",
    val downloadURL: String? = null,
    val thumbnailBase64: String? = null,
    val caption: String? = null,
    val videoDurationSeconds: Double? = null,
    val takenAtEpochMillis: Long = 0L,
    val createdAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val albumIdsRaw: String = "",
    val isDeleted: Boolean = false,
)

data class RemotePhotoAlbumDto(
    val id: String = "",
    val title: String = "",
    val coverPhotoId: String? = null,
    val sortOrder: Int = 0,
    val createdAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val isDeleted: Boolean = false,
)

sealed interface PhotoRemoteChange {
    data class Upsert(
        val dto: RemoteFamilyPhotoDto,
        val isFromCache: Boolean,
    ) : PhotoRemoteChange

    data class Remove(
        val id: String,
        val isFromCache: Boolean,
    ) : PhotoRemoteChange
}

sealed interface PhotoAlbumRemoteChange {
    data class Upsert(
        val dto: RemotePhotoAlbumDto,
        val isFromCache: Boolean,
    ) : PhotoAlbumRemoteChange

    data class Remove(
        val id: String,
        val isFromCache: Boolean,
    ) : PhotoAlbumRemoteChange
}

@Singleton
class PhotoVideoRemoteStore @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun listenPhotos(
        familyId: String,
        onChange: (PhotoRemoteChange) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = firestore
        .collection("families")
        .document(familyId)
        .collection("photos")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val isFromCache = snapshot.metadata.isFromCache
            var upserts = 0
            var removes = 0
            snapshot.documentChanges.forEach { change ->
                when (change.type) {
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED,
                    -> {
                        val dto = change.document.toRemotePhotoDto()
                        onChange(PhotoRemoteChange.Upsert(dto, isFromCache))
                        upserts += 1
                    }

                    DocumentChange.Type.REMOVED -> {
                        onChange(PhotoRemoteChange.Remove(change.document.id, isFromCache))
                        removes += 1
                    }
                }
            }
            if (upserts > 0 || removes > 0) {
                Log.d(
                    TAG_PHOTO_SYNC,
                    "Snapshot processed. Changes: $upserts added/modified, $removes removed. collection=photos isFromCache=$isFromCache",
                )
            }
        }

    fun listenPhotoAlbums(
        familyId: String,
        onChange: (PhotoAlbumRemoteChange) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = firestore
        .collection("families")
        .document(familyId)
        .collection("photoAlbums")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val isFromCache = snapshot.metadata.isFromCache
            var upserts = 0
            var removes = 0
            snapshot.documentChanges.forEach { change ->
                when (change.type) {
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED,
                    -> {
                        val dto = change.document.toRemoteAlbumDto()
                        onChange(PhotoAlbumRemoteChange.Upsert(dto, isFromCache))
                        upserts += 1
                    }

                    DocumentChange.Type.REMOVED -> {
                        onChange(PhotoAlbumRemoteChange.Remove(change.document.id, isFromCache))
                        removes += 1
                    }
                }
            }
            if (upserts > 0 || removes > 0) {
                Log.d(
                    TAG_PHOTO_SYNC,
                    "Snapshot processed. Changes: $upserts added/modified, $removes removed. collection=photoAlbums isFromCache=$isFromCache",
                )
            }
        }

    suspend fun upsertPhoto(photo: KBFamilyPhotoEntity) {
        firestore.collection("families")
            .document(photo.familyId)
            .collection("photos")
            .document(photo.id)
            .set(photo.toRemoteMap(), SetOptions.merge())
            .await()
    }

    suspend fun softDeletePhoto(
        familyId: String,
        photoId: String,
        updatedAtEpochMillis: Long,
        updatedBy: String,
    ) {
        firestore.collection("families")
            .document(familyId)
            .collection("photos")
            .document(photoId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "updatedAtEpochMillis" to updatedAtEpochMillis,
                    "updatedBy" to updatedBy,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun upsertAlbum(album: KBPhotoAlbumEntity) {
        firestore.collection("families")
            .document(album.familyId)
            .collection("photoAlbums")
            .document(album.id)
            .set(album.toRemoteMap(), SetOptions.merge())
            .await()
    }

    suspend fun softDeleteAlbum(
        familyId: String,
        albumId: String,
        updatedAtEpochMillis: Long,
        updatedBy: String,
    ) {
        firestore.collection("families")
            .document(familyId)
            .collection("photoAlbums")
            .document(albumId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "updatedAtEpochMillis" to updatedAtEpochMillis,
                    "updatedBy" to updatedBy,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toRemotePhotoDto(): RemoteFamilyPhotoDto {
        val data = data.orEmpty()
        return RemoteFamilyPhotoDto(
            id = id,
            fileName = data["fileName"] as? String ?: "",
            mimeType = data["mimeType"] as? String ?: "",
            fileSize = data.longValue("fileSize"),
            storagePath = data["storagePath"] as? String ?: "",
            downloadURL = data["downloadURL"] as? String,
            thumbnailBase64 = data["thumbnailBase64"] as? String,
            caption = data["caption"] as? String,
            videoDurationSeconds = data.doubleValueOrNull("videoDurationSeconds"),
            takenAtEpochMillis = data.longValue("takenAtEpochMillis"),
            createdAtEpochMillis = data.longValueOrNull("createdAtEpochMillis"),
            updatedAtEpochMillis = data.longValueOrNull("updatedAtEpochMillis"),
            createdBy = data["createdBy"] as? String,
            updatedBy = data["updatedBy"] as? String,
            albumIdsRaw = data["albumIdsRaw"] as? String ?: "",
            isDeleted = data["isDeleted"] as? Boolean ?: false,
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toRemoteAlbumDto(): RemotePhotoAlbumDto {
        val data = data.orEmpty()
        return RemotePhotoAlbumDto(
            id = id,
            title = data["title"] as? String ?: "",
            coverPhotoId = data["coverPhotoId"] as? String,
            sortOrder = data.intValue("sortOrder"),
            createdAtEpochMillis = data.longValueOrNull("createdAtEpochMillis"),
            updatedAtEpochMillis = data.longValueOrNull("updatedAtEpochMillis"),
            createdBy = data["createdBy"] as? String,
            updatedBy = data["updatedBy"] as? String,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
        )
    }

    private fun KBFamilyPhotoEntity.toRemoteMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "fileName" to fileName,
        "mimeType" to mimeType,
        "fileSize" to fileSize,
        "storagePath" to storagePath,
        "downloadURL" to downloadURL,
        "thumbnailBase64" to thumbnailBase64,
        "caption" to caption,
        "videoDurationSeconds" to videoDurationSeconds,
        "takenAtEpochMillis" to takenAtEpochMillis,
        "createdAtEpochMillis" to createdAtEpochMillis,
        "updatedAtEpochMillis" to updatedAtEpochMillis,
        "createdBy" to createdBy,
        "updatedBy" to updatedBy,
        "albumIdsRaw" to albumIdsRaw,
        "isDeleted" to isDeleted,
    )

    private fun KBPhotoAlbumEntity.toRemoteMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "coverPhotoId" to coverPhotoId,
        "sortOrder" to sortOrder,
        "createdAtEpochMillis" to createdAtEpochMillis,
        "updatedAtEpochMillis" to updatedAtEpochMillis,
        "createdBy" to createdBy,
        "updatedBy" to updatedBy,
        "isDeleted" to isDeleted,
    )

    private fun Map<String, Any>.longValue(key: String): Long = when (val value = this[key]) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is Float -> value.toLong()
        is Number -> value.toLong()
        else -> 0L
    }

    private fun Map<String, Any>.longValueOrNull(key: String): Long? = when (val value = this[key]) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is Float -> value.toLong()
        is Number -> value.toLong()
        else -> null
    }

    private fun Map<String, Any>.intValue(key: String): Int = when (val value = this[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is Float -> value.toInt()
        is Number -> value.toInt()
        else -> 0
    }

    private fun Map<String, Any>.doubleValueOrNull(key: String): Double? = when (val value = this[key]) {
        is Double -> value
        is Float -> value.toDouble()
        is Long -> value.toDouble()
        is Int -> value.toDouble()
        is Number -> value.toDouble()
        else -> null
    }

}
