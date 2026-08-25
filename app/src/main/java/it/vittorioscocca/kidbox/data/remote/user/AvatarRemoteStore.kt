package it.vittorioscocca.kidbox.data.remote.user

import com.google.firebase.storage.FirebaseStorage
import it.vittorioscocca.kidbox.data.remote.awaitDownloadUrlAfterWrite
import it.vittorioscocca.kidbox.data.remote.prefetchAppCheckTokenForStorage
import it.vittorioscocca.kidbox.data.remote.userMessageForFirebaseStorage
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
        return try {
            prefetchAppCheckTokenForStorage()
            ref.putBytes(imageData).await()
            ref.awaitDownloadUrlAfterWrite()
        } catch (e: Exception) {
            throw Exception(e.userMessageForFirebaseStorage(), e)
        }
    }

    /**
     * Rimuove il file dell'avatar da Storage.
     *
     * Prova ENTRAMBI i percorsi possibili perché quello usato dipende dal fatto
     * che al momento del caricamento ci fosse una famiglia attiva: chi ha
     * cambiato famiglia nel frattempo potrebbe avere il file nell'altro.
     *
     * Non solleva se il file non c'è: l'obiettivo è che dopo la chiamata
     * l'avatar non esista, e un file già assente soddisfa la condizione.
     */
    suspend fun deleteAvatar(uid: String, familyId: String?) {
        runCatching { prefetchAppCheckTokenForStorage() }
        val paths = buildList {
            familyId?.takeIf { it.isNotBlank() }?.let { add("families/$it/avatars/$uid.jpg") }
            add("users/$uid/avatar.jpg")
        }
        for (path in paths) {
            runCatching { storage.reference.child(path).delete().await() }
        }
    }
}
